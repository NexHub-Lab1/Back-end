package com.nexhub.backend.service;

import com.nexhub.backend.dto.payment.PaymentResponse;
import com.nexhub.backend.model.Payment;
import com.nexhub.backend.model.Project;
import com.nexhub.backend.model.RewardDistribution;
import com.nexhub.backend.model.Task;
import com.nexhub.backend.model.TaskAssignment;
import com.nexhub.backend.model.TaskSubmission;
import com.nexhub.backend.model.User;
import com.nexhub.backend.model.WalletTransaction;
import com.nexhub.backend.repository.PaymentRepository;
import com.nexhub.backend.repository.RewardDistributionRepository;
import com.nexhub.backend.repository.TaskRepository;
import com.nexhub.backend.repository.UserRepository;
import com.nexhub.backend.repository.WalletTransactionRepository;
import com.nexhub.backend.service.payment.PaymentGateway;
import com.nexhub.backend.service.payment.MercadoPagoWebhookVerifier;
import com.nexhub.backend.service.payment.ProviderPaymentIntent;
import com.nexhub.backend.service.payment.ProviderPaymentResult;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PaymentServiceTest {
    @Mock
    private PaymentRepository paymentRepository;

    @Mock
    private WalletTransactionRepository walletTransactionRepository;

    @Mock
    private RewardDistributionRepository rewardDistributionRepository;

    @Mock
    private TaskRepository taskRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private PaymentGateway paymentGateway;

    @Mock
    private MercadoPagoWebhookVerifier mercadoPagoWebhookVerifier;

    @InjectMocks
    private PaymentService paymentService;

    @Test
    void fundTaskUsesAuthenticatedOwnerAndStartsPendingPayment() {
        User owner = sampleUser(1L, "owner@nexhub.dev");
        Task task = sampleTask(10L, owner);

        when(taskRepository.findById(10L)).thenReturn(Optional.of(task));
        when(userRepository.findByEmail(owner.getEmail())).thenReturn(Optional.of(owner));
        when(paymentGateway.createFundingIntent(any(Payment.class)))
                .thenReturn(new ProviderPaymentIntent("preference-1", "https://sandbox.mercadopago.com/checkout"));
        when(paymentRepository.save(any(Payment.class))).thenAnswer(invocation -> invocation.getArgument(0));

        PaymentResponse response = paymentService.fundTask(10L, owner.getEmail());

        assertThat(response.payerUsername()).isEqualTo(owner.getUsername());
        assertThat(response.status()).isEqualTo("pending");
        assertThat(response.providerPreferenceId()).isEqualTo("preference-1");
        assertThat(task.getFundingStatus()).isEqualTo("pending");
    }

    @Test
    void approvedMercadoPagoNotificationMovesFundedAmountIntoEscrow() {
        User owner = sampleUser(1L, "owner@nexhub.dev");
        Task task = sampleTask(10L, owner);
        Payment payment = samplePayment(30L, task, owner, "pending");

        payment.setExternalReference("task-10-checkout");
        when(paymentGateway.getPayment("payment-30"))
                .thenReturn(new ProviderPaymentResult(
                        "payment-30", "task-10-checkout", "approved", "accredited",
                        new BigDecimal("100.00"), "ARS"
                ));
        when(paymentRepository.findByExternalReference("task-10-checkout")).thenReturn(Optional.of(payment));
        when(paymentRepository.save(any(Payment.class))).thenAnswer(invocation -> invocation.getArgument(0));

        paymentService.processMercadoPagoNotification("payment-30", "request-1", "signed-header");

        verify(mercadoPagoWebhookVerifier).validate("payment-30", "request-1", "signed-header");
        assertThat(payment.getStatus()).isEqualTo("approved");
        assertThat(payment.getProviderPaymentId()).isEqualTo("payment-30");
        assertThat(owner.getEscrowBalance()).isEqualByComparingTo("100.00");
        assertThat(task.getFundingStatus()).isEqualTo("funded");

        ArgumentCaptor<WalletTransaction> transaction = ArgumentCaptor.forClass(WalletTransaction.class);
        verify(walletTransactionRepository).save(transaction.capture());
        assertThat(transaction.getValue().getType()).isEqualTo("escrow_funded");
    }

    @Test
    void rejectsFundingInAnUnsupportedMercadoPagoCurrency() {
        User owner = sampleUser(1L, "owner@nexhub.dev");
        Task task = sampleTask(10L, owner);
        task.setRewardCurrency("USD");

        when(taskRepository.findById(10L)).thenReturn(Optional.of(task));
        when(userRepository.findByEmail(owner.getEmail())).thenReturn(Optional.of(owner));

        assertThatThrownBy(() -> paymentService.fundTask(10L, owner.getEmail()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Only ARS rewards can be funded in the current Mercado Pago wallet flow");
    }

    @Test
    void rejectsApprovedNotificationWithDifferentPaidAmount() {
        User owner = sampleUser(1L, "owner@nexhub.dev");
        Task task = sampleTask(10L, owner);
        Payment payment = samplePayment(30L, task, owner, "pending");
        payment.setExternalReference("task-10-checkout");

        when(paymentGateway.getPayment("payment-30"))
                .thenReturn(new ProviderPaymentResult(
                        "payment-30", "task-10-checkout", "approved", "accredited",
                        new BigDecimal("1.00"), "ARS"
                ));
        when(paymentRepository.findByExternalReference("task-10-checkout")).thenReturn(Optional.of(payment));

        assertThatThrownBy(() -> paymentService.processMercadoPagoNotification(
                "payment-30", "request-1", "signed-header"
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Mercado Pago payment amount does not match the funded reward");

        assertThat(owner.getEscrowBalance()).isEqualByComparingTo("0.00");
        verify(walletTransactionRepository, never()).save(any(WalletTransaction.class));
    }

    @Test
    void approvedSubmissionCreatesDistributionAndReleasesReward() {
        User owner = sampleUser(1L, "owner@nexhub.dev");
        owner.setEscrowBalance(new BigDecimal("100.00"));
        User developer = sampleUser(2L, "dev@nexhub.dev");
        Task task = sampleTask(10L, owner);
        task.setFundingStatus("funded");
        Payment payment = samplePayment(30L, task, owner, "approved");
        TaskSubmission submission = sampleSubmission(40L, task, developer);

        when(paymentRepository.findFirstByTask_IdAndStatusOrderByCreatedAtDesc(10L, "approved"))
                .thenReturn(Optional.of(payment));
        when(rewardDistributionRepository.existsBySubmission_Id(40L)).thenReturn(false);
        when(rewardDistributionRepository.save(any(RewardDistribution.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        paymentService.releaseRewardForApprovedSubmission(submission);

        assertThat(owner.getEscrowBalance()).isEqualByComparingTo("0.00");
        assertThat(developer.getAvailableBalance()).isEqualByComparingTo("100.00");
        assertThat(task.getFundingStatus()).isEqualTo("released");
        assertThat(payment.getStatus()).isEqualTo("released");

        ArgumentCaptor<RewardDistribution> distribution = ArgumentCaptor.forClass(RewardDistribution.class);
        verify(rewardDistributionRepository, atLeastOnce()).save(distribution.capture());
        assertThat(distribution.getValue().getRecipient()).isEqualTo(developer);
        assertThat(distribution.getValue().getAmount()).isEqualByComparingTo("100.00");
        assertThat(distribution.getValue().getStatus()).isEqualTo("released");
    }

    @Test
    void refundedFundedTaskReturnsEscrowToOwnerWallet() {
        User owner = sampleUser(1L, "owner@nexhub.dev");
        owner.setEscrowBalance(new BigDecimal("100.00"));
        Task task = sampleTask(10L, owner);
        task.setFundingStatus("funded");
        Payment payment = samplePayment(30L, task, owner, "approved");

        when(paymentRepository.findFirstByTask_IdAndStatusOrderByCreatedAtDesc(10L, "approved"))
                .thenReturn(Optional.of(payment));

        paymentService.refundTaskEscrow(task, "Task cancelled");

        assertThat(owner.getEscrowBalance()).isEqualByComparingTo("0.00");
        assertThat(owner.getAvailableBalance()).isEqualByComparingTo("100.00");
        assertThat(payment.getStatus()).isEqualTo("refunded");
        assertThat(task.getFundingStatus()).isEqualTo("refunded");
    }

    @Test
    void unfundedTaskCannotReceiveWork() {
        Task task = sampleTask(10L, sampleUser(1L, "owner@nexhub.dev"));

        assertThatThrownBy(() -> paymentService.validateTaskCanReceiveWork(task))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Task reward must be funded before work can begin");
    }

    private static Task sampleTask(Long id, User owner) {
        Project project = new Project();
        setField(project, "id", 5L);
        project.setOwner(owner);

        Task task = new Task();
        setField(task, "id", id);
        task.setProject(project);
        task.setTitle("Implement payouts");
        task.setRewardAmount(new BigDecimal("100.00"));
        task.setRewardCurrency("ARS");
        task.setStatus("open");
        task.setFundingStatus("unfunded");
        return task;
    }

    private static Payment samplePayment(Long id, Task task, User owner, String status) {
        Payment payment = new Payment();
        setField(payment, "id", id);
        payment.setTask(task);
        payment.setPayer(owner);
        payment.setAmount(new BigDecimal("100.00"));
        payment.setCurrency("ARS");
        payment.setStatus(status);
        return payment;
    }

    private static TaskSubmission sampleSubmission(Long id, Task task, User developer) {
        TaskAssignment assignment = new TaskAssignment();
        assignment.setTask(task);
        assignment.setUser(developer);

        TaskSubmission submission = new TaskSubmission();
        setField(submission, "id", id);
        submission.setTask(task);
        submission.setAssignment(assignment);
        submission.setUser(developer);
        return submission;
    }

    private static User sampleUser(Long id, String email) {
        User user = new User();
        setField(user, "id", id);
        user.setEmail(email);
        user.setUsername(email.substring(0, email.indexOf('@')));
        user.setAvailableBalance(BigDecimal.ZERO);
        user.setEscrowBalance(BigDecimal.ZERO);
        return user;
    }

    private static void setField(Object target, String name, Object value) {
        try {
            var field = target.getClass().getDeclaredField(name);
            field.setAccessible(true);
            field.set(target, value);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError(e);
        }
    }
}
