package com.nexhub.backend.service;

import com.nexhub.backend.dto.payment.BalanceResponse;
import com.nexhub.backend.dto.payment.PaymentResponse;
import com.nexhub.backend.dto.payment.WalletTransactionResponse;
import com.nexhub.backend.model.Payment;
import com.nexhub.backend.model.Project;
import com.nexhub.backend.model.RewardDistribution;
import com.nexhub.backend.model.Task;
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
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Date;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional
public class PaymentService {
    private static final String PENDING_STATUS = "pending";
    private static final String APPROVED_STATUS = "approved";
    private static final String FAILED_STATUS = "failed";
    private static final String CANCELLED_STATUS = "cancelled";
    private static final String RELEASED_STATUS = "released";
    private static final String REFUNDED_STATUS = "refunded";
    private static final String UNFUNDED_STATUS = "unfunded";
    private static final String FUNDED_STATUS = "funded";
    private static final String SETTLEMENT_CURRENCY = "ARS";
    private static final Set<String> BLOCKING_STATUSES = Set.of(PENDING_STATUS, APPROVED_STATUS, RELEASED_STATUS);

    private final PaymentRepository paymentRepository;
    private final WalletTransactionRepository walletTransactionRepository;
    private final RewardDistributionRepository rewardDistributionRepository;
    private final TaskRepository taskRepository;
    private final UserRepository userRepository;
    private final PaymentGateway paymentGateway;
    private final MercadoPagoWebhookVerifier mercadoPagoWebhookVerifier;

    public PaymentResponse fundTask(Long taskId, String authenticatedEmail) {
        Task task = findExistingTask(taskId);
        User payer = findAuthenticatedUser(authenticatedEmail);

        validatePayerOwnsTaskProject(task, payer);
        validateTaskCanBeFunded(task);
        validateSettlementCurrency(task);
        if (paymentRepository.existsByTask_IdAndStatusIn(task.getId(), BLOCKING_STATUSES)) {
            throw new IllegalArgumentException("Task already has active funding");
        }

        Payment payment = new Payment();
        payment.setTask(task);
        payment.setPayer(payer);
        payment.setAmount(normalizeMoney(task.getRewardAmount()));
        payment.setCurrency(normalizeCurrency(task.getRewardCurrency()));
        payment.setProvider("mercadopago_checkout_pro");
        payment.setStatus(PENDING_STATUS);
        payment.setExternalReference("task-" + task.getId() + "-" + UUID.randomUUID());

        ProviderPaymentIntent intent = paymentGateway.createFundingIntent(payment);
        payment.setProviderPreferenceId(intent.providerPreferenceId());
        payment.setCheckoutUrl(intent.checkoutUrl());

        task.setFundingStatus(PENDING_STATUS);
        taskRepository.save(task);

        return PaymentResponse.fromPayment(paymentRepository.save(payment));
    }

    public void processMercadoPagoNotification(
            String providerPaymentId,
            String requestId,
            String signatureHeader
    ) {
        mercadoPagoWebhookVerifier.validate(providerPaymentId, requestId, signatureHeader);

        ProviderPaymentResult result = paymentGateway.getPayment(providerPaymentId);
        Payment payment = paymentRepository.findByExternalReference(result.externalReference()).orElse(null);
        if (payment == null) {
            return;
        }

        if (payment.getProviderPaymentId() != null
                && !payment.getProviderPaymentId().equals(result.providerPaymentId())) {
            throw new IllegalArgumentException("Mercado Pago payment does not match the stored checkout");
        }
        validateGatewayPaymentMatchesFunding(payment, result);
        payment.setProviderPaymentId(result.providerPaymentId());

        String status = result.status().trim().toLowerCase();
        if (APPROVED_STATUS.equals(status) && PENDING_STATUS.equalsIgnoreCase(payment.getStatus())) {
            approvePayment(payment);
            return;
        }
        if (("rejected".equals(status) || CANCELLED_STATUS.equals(status))
                && PENDING_STATUS.equalsIgnoreCase(payment.getStatus())) {
            failPayment(payment, result.statusDetail());
            return;
        }

        paymentRepository.save(payment);
    }

    @Transactional(readOnly = true)
    public List<PaymentResponse> getTaskPayments(Long taskId, String authenticatedEmail) {
        Task task = findExistingTask(taskId);
        User actor = findAuthenticatedUser(authenticatedEmail);
        validatePayerOwnsTaskProject(task, actor);

        return paymentRepository.findByTask_IdOrderByCreatedAtDesc(taskId).stream()
                .map(PaymentResponse::fromPayment)
                .toList();
    }

    @Transactional(readOnly = true)
    public BalanceResponse getMyBalance(String authenticatedEmail) {
        User user = findAuthenticatedUser(authenticatedEmail);
        normalizeBalances(user);
        return BalanceResponse.fromUser(user);
    }

    @Transactional(readOnly = true)
    public List<WalletTransactionResponse> getMyWalletTransactions(String authenticatedEmail) {
        User user = findAuthenticatedUser(authenticatedEmail);
        return walletTransactionRepository.findByUser_IdOrderByCreatedAtDesc(user.getId()).stream()
                .map(WalletTransactionResponse::fromWalletTransaction)
                .toList();
    }

    public void validateTaskCanReceiveWork(Task task) {
        if (!FUNDED_STATUS.equalsIgnoreCase(fundingStatus(task))) {
            throw new IllegalArgumentException("Task reward must be funded before work can begin");
        }
    }

    public boolean taskHasPaymentHistory(Task task) {
        return task != null && task.getId() != null && paymentRepository.existsByTask_Id(task.getId());
    }

    public boolean taskHasLockedFunding(Task task) {
        return task != null && BLOCKING_STATUSES.contains(fundingStatus(task));
    }

    private void validateSettlementCurrency(Task task) {
        if (!SETTLEMENT_CURRENCY.equals(normalizeCurrency(task.getRewardCurrency()))) {
            throw new IllegalArgumentException("Only ARS rewards can be funded in the current Mercado Pago wallet flow");
        }
    }

    public void releaseRewardForApprovedSubmission(TaskSubmission submission) {
        validateSubmissionForRewardRelease(submission);

        if (rewardDistributionRepository.existsBySubmission_Id(submission.getId())) {
            throw new IllegalArgumentException("Reward has already been distributed for this submission");
        }

        Task task = submission.getTask();
        Payment payment = paymentRepository
                .findFirstByTask_IdAndStatusOrderByCreatedAtDesc(task.getId(), APPROVED_STATUS)
                .orElseThrow(() -> new IllegalArgumentException("Task reward must be funded before approving submissions"));

        RewardDistribution distribution = new RewardDistribution();
        distribution.setTask(task);
        distribution.setPayment(payment);
        distribution.setSubmission(submission);
        distribution.setAssignment(submission.getAssignment());
        distribution.setRecipient(submission.getUser());
        distribution.setAmount(normalizeMoney(payment.getAmount()));
        distribution.setCurrency(normalizeCurrency(payment.getCurrency()));
        distribution.setStatus(PENDING_STATUS);

        releaseDistributions(payment, List.of(rewardDistributionRepository.save(distribution)));
    }

    public void refundTaskEscrow(Task task, String description) {
        if (task == null || task.getId() == null) {
            return;
        }
        if (RELEASED_STATUS.equalsIgnoreCase(fundingStatus(task))) {
            throw new IllegalArgumentException("Task reward was already released and cannot be refunded");
        }

        Payment fundedPayment = paymentRepository
                .findFirstByTask_IdAndStatusOrderByCreatedAtDesc(task.getId(), APPROVED_STATUS)
                .orElse(null);
        if (fundedPayment != null) {
            refundFundedPayment(fundedPayment, task, description);
            return;
        }

        Payment pendingPayment = paymentRepository
                .findFirstByTask_IdAndStatusOrderByCreatedAtDesc(task.getId(), PENDING_STATUS)
                .orElse(null);
        if (pendingPayment != null) {
            pendingPayment.setStatus(CANCELLED_STATUS);
            pendingPayment.setFailureReason(normalizeDescription(description, "Task payment cancelled"));
            paymentRepository.save(pendingPayment);
        }

        if (PENDING_STATUS.equalsIgnoreCase(fundingStatus(task))) {
            task.setFundingStatus(UNFUNDED_STATUS);
            taskRepository.save(task);
        }
    }

    private Payment approvePayment(Payment payment) {
        validatePendingPayment(payment);

        User payer = payment.getPayer();
        normalizeBalances(payer);
        payer.setEscrowBalance(payer.getEscrowBalance().add(normalizeMoney(payment.getAmount())));
        userRepository.save(payer);

        payment.setStatus(APPROVED_STATUS);
        payment.setApprovedAt(now());
        payment.setFailureReason(null);
        Payment savedPayment = paymentRepository.save(payment);

        Task task = payment.getTask();
        task.setFundingStatus(FUNDED_STATUS);
        taskRepository.save(task);

        saveWalletTransaction(
                payer,
                savedPayment,
                task,
                "escrow_funded",
                payment.getAmount(),
                "Task reward funded through Mercado Pago"
        );
        return savedPayment;
    }

    private Payment failPayment(Payment payment, String failureReason) {
        validatePendingPayment(payment);

        payment.setStatus(FAILED_STATUS);
        payment.setFailedAt(now());
        payment.setFailureReason(normalizeDescription(failureReason, "Payment failed"));
        Payment savedPayment = paymentRepository.save(payment);

        Task task = payment.getTask();
        task.setFundingStatus(UNFUNDED_STATUS);
        taskRepository.save(task);
        return savedPayment;
    }

    private void releaseDistributions(Payment payment, List<RewardDistribution> distributions) {
        BigDecimal distributionTotal = distributions.stream()
                .map(RewardDistribution::getAmount)
                .map(this::normalizeMoney)
                .reduce(BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP), BigDecimal::add);
        BigDecimal paymentAmount = normalizeMoney(payment.getAmount());
        if (distributionTotal.compareTo(paymentAmount) != 0) {
            throw new IllegalArgumentException("Reward distributions must total the funded amount");
        }

        User owner = payment.getPayer();
        normalizeBalances(owner);
        ensureEscrowCanCover(owner, distributionTotal);
        owner.setEscrowBalance(owner.getEscrowBalance().subtract(distributionTotal));
        userRepository.save(owner);

        Task task = payment.getTask();
        saveWalletTransaction(
                owner,
                payment,
                task,
                "escrow_released",
                distributionTotal.negate(),
                "Reward released from task escrow"
        );

        for (RewardDistribution distribution : distributions) {
            User recipient = distribution.getRecipient();
            normalizeBalances(recipient);
            recipient.setAvailableBalance(recipient.getAvailableBalance().add(normalizeMoney(distribution.getAmount())));
            userRepository.save(recipient);

            distribution.setStatus(RELEASED_STATUS);
            distribution.setReleasedAt(now());
            rewardDistributionRepository.save(distribution);

            saveWalletTransaction(
                    recipient,
                    payment,
                    task,
                    "reward_received",
                    distribution.getAmount(),
                    "Reward received for approved submission"
            );
        }

        payment.setStatus(RELEASED_STATUS);
        payment.setReleasedAt(now());
        paymentRepository.save(payment);
        task.setFundingStatus(RELEASED_STATUS);
        taskRepository.save(task);
    }

    private void refundFundedPayment(Payment payment, Task task, String description) {
        User owner = payment.getPayer();
        BigDecimal amount = normalizeMoney(payment.getAmount());
        normalizeBalances(owner);
        ensureEscrowCanCover(owner, amount);

        owner.setEscrowBalance(owner.getEscrowBalance().subtract(amount));
        owner.setAvailableBalance(owner.getAvailableBalance().add(amount));
        userRepository.save(owner);

        payment.setStatus(REFUNDED_STATUS);
        payment.setRefundedAt(now());
        paymentRepository.save(payment);
        task.setFundingStatus(REFUNDED_STATUS);
        taskRepository.save(task);

        saveWalletTransaction(
                owner,
                payment,
                task,
                "escrow_refunded",
                amount,
                normalizeDescription(description, "Task escrow refunded")
        );
    }

    private void validateTaskCanBeFunded(Task task) {
        if (task.getRewardAmount() == null || task.getRewardAmount().compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Task reward amount must be greater than zero");
        }
        if ("cancelled".equalsIgnoreCase(task.getStatus())) {
            throw new IllegalArgumentException("Cancelled tasks cannot be funded");
        }
        if (taskHasLockedFunding(task)) {
            throw new IllegalArgumentException("Task reward is already pending, funded, or released");
        }
    }

    private void validateGatewayPaymentMatchesFunding(Payment payment, ProviderPaymentResult result) {
        if (result.amount() == null || normalizeMoney(result.amount()).compareTo(normalizeMoney(payment.getAmount())) != 0) {
            throw new IllegalArgumentException("Mercado Pago payment amount does not match the funded reward");
        }
        if (!normalizeCurrency(result.currency()).equals(normalizeCurrency(payment.getCurrency()))) {
            throw new IllegalArgumentException("Mercado Pago payment currency does not match the funded reward");
        }
    }

    private void validatePayerOwnsTaskProject(Task task, User payer) {
        Project project = task.getProject();
        User owner = project != null ? project.getOwner() : null;
        if (owner == null || !Objects.equals(owner.getId(), payer.getId())) {
            throw new IllegalArgumentException("Only the project owner can fund this task");
        }
    }

    private void validateSubmissionForRewardRelease(TaskSubmission submission) {
        if (submission == null || submission.getId() == null || submission.getTask() == null
                || submission.getAssignment() == null || submission.getUser() == null) {
            throw new IllegalArgumentException("Submission must include task, assignment, and recipient");
        }
    }

    private void validatePendingPayment(Payment payment) {
        if (!PENDING_STATUS.equalsIgnoreCase(payment.getStatus())) {
            throw new IllegalArgumentException("Only pending payments can be processed");
        }
    }

    private Task findExistingTask(Long taskId) {
        if (taskId == null) {
            throw new IllegalArgumentException("Task id is required");
        }
        return taskRepository.findById(taskId)
                .orElseThrow(() -> new NoSuchElementException("Task not found"));
    }

    private User findAuthenticatedUser(String email) {
        if (email == null || email.isBlank()) {
            throw new IllegalArgumentException("Authenticated user is required");
        }
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new NoSuchElementException("Authenticated user not found"));
    }

    private void normalizeBalances(User user) {
        if (user.getAvailableBalance() == null) {
            user.setAvailableBalance(BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP));
        }
        if (user.getEscrowBalance() == null) {
            user.setEscrowBalance(BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP));
        }
    }

    private void ensureEscrowCanCover(User user, BigDecimal amount) {
        if (user.getEscrowBalance().compareTo(amount) < 0) {
            throw new IllegalArgumentException("Escrow balance is not enough to release this reward");
        }
    }

    private void saveWalletTransaction(
            User user,
            Payment payment,
            Task task,
            String type,
            BigDecimal amount,
            String description
    ) {
        WalletTransaction transaction = new WalletTransaction();
        transaction.setUser(user);
        transaction.setPayment(payment);
        transaction.setTask(task);
        transaction.setType(type);
        transaction.setAmount(normalizeMoney(amount));
        transaction.setCurrency(normalizeCurrency(payment.getCurrency()));
        transaction.setAvailableBalanceAfter(normalizeMoney(user.getAvailableBalance()));
        transaction.setEscrowBalanceAfter(normalizeMoney(user.getEscrowBalance()));
        transaction.setDescription(description);
        walletTransactionRepository.save(transaction);
    }

    private String fundingStatus(Task task) {
        return task.getFundingStatus() == null || task.getFundingStatus().isBlank()
                ? UNFUNDED_STATUS
                : task.getFundingStatus().trim().toLowerCase();
    }

    private BigDecimal normalizeMoney(BigDecimal amount) {
        return (amount == null ? BigDecimal.ZERO : amount).setScale(2, RoundingMode.HALF_UP);
    }

    private String normalizeCurrency(String currency) {
        return currency == null || currency.isBlank() ? "ARS" : currency.trim().toUpperCase();
    }

    private String normalizeDescription(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim();
    }

    private Date now() {
        return new Date(System.currentTimeMillis());
    }
}
