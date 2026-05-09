package com.nexhub.backend.service;

import com.nexhub.backend.dto.payment.BalanceResponse;
import com.nexhub.backend.dto.payment.PaymentResponse;
import com.nexhub.backend.dto.payment.PaymentSimulationRequest;
import com.nexhub.backend.dto.payment.TaskPaymentRequest;
import com.nexhub.backend.dto.payment.WalletTransactionResponse;
import com.nexhub.backend.model.Payment;
import com.nexhub.backend.model.Project;
import com.nexhub.backend.model.Task;
import com.nexhub.backend.model.TaskSubmission;
import com.nexhub.backend.model.User;
import com.nexhub.backend.model.WalletTransaction;
import com.nexhub.backend.repository.PaymentRepository;
import com.nexhub.backend.repository.TaskRepository;
import com.nexhub.backend.repository.UserRepository;
import com.nexhub.backend.repository.WalletTransactionRepository;
import com.nexhub.backend.service.payment.PaymentGateway;
import com.nexhub.backend.service.payment.ProviderPaymentIntent;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Date;
import java.util.List;
import java.util.NoSuchElementException;
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
    private static final String FUNDING_PENDING_STATUS = "pending";
    private static final String FUNDED_STATUS = "funded";
    private static final String FUNDING_RELEASED_STATUS = "released";
    private static final String FUNDING_REFUNDED_STATUS = "refunded";

    private static final Set<String> BLOCKING_PAYMENT_STATUSES = Set.of(PENDING_STATUS, APPROVED_STATUS);

    private final PaymentRepository paymentRepository;
    private final WalletTransactionRepository walletTransactionRepository;
    private final TaskRepository taskRepository;
    private final UserRepository userRepository;
    private final PaymentGateway paymentGateway;

    public PaymentResponse createTaskPayment(TaskPaymentRequest request) {
        validateTaskPaymentRequest(request);

        Task task = taskRepository.findById(request.taskId())
                .orElseThrow(() -> new NoSuchElementException("Task not found"));
        User payer = userRepository.findById(request.payerId())
                .orElseThrow(() -> new NoSuchElementException("Payer not found"));

        validatePayerOwnsTaskProject(task, payer);
        validateTaskCanBeFunded(task);

        if (paymentRepository.existsByTask_IdAndStatusIn(task.getId(), BLOCKING_PAYMENT_STATUSES)) {
            throw new IllegalArgumentException("Task already has a pending or approved payment");
        }

        Payment payment = new Payment();
        payment.setTask(task);
        payment.setPayer(payer);
        payment.setAmount(normalizeMoney(task.getRewardAmount()));
        payment.setCurrency(normalizeCurrency(task.getRewardCurrency()));
        payment.setProvider("mercadopago");
        payment.setStatus(PENDING_STATUS);
        payment.setExternalReference(buildExternalReference(task));
        payment.setCreatedAt(now());
        payment.setUpdatedAt(now());

        ProviderPaymentIntent intent = paymentGateway.createTaskPaymentIntent(payment);
        payment.setProviderPaymentId(intent.providerPaymentId());
        payment.setCheckoutUrl(intent.checkoutUrl());

        task.setFundingStatus(FUNDING_PENDING_STATUS);
        taskRepository.save(task);

        return PaymentResponse.fromPayment(paymentRepository.save(payment));
    }

    public PaymentResponse simulateGatewayResult(PaymentSimulationRequest request) {
        if (request == null || request.paymentId() == null) {
            throw new IllegalArgumentException("Payment id is required");
        }

        Payment payment = findExistingPayment(request.paymentId());
        String status = normalizeGatewayStatus(request.status());

        if (APPROVED_STATUS.equals(status)) {
            return PaymentResponse.fromPayment(approvePayment(payment));
        }

        return PaymentResponse.fromPayment(failPayment(payment, request.failureReason()));
    }

    @Transactional(readOnly = true)
    public PaymentResponse getPaymentById(Long id) {
        return PaymentResponse.fromPayment(findExistingPayment(id));
    }

    @Transactional(readOnly = true)
    public List<PaymentResponse> getPaymentsByTask(Long taskId) {
        if (taskId == null) {
            throw new IllegalArgumentException("Task id is required");
        }

        return paymentRepository.findByTask_IdOrderByCreatedAtDesc(taskId).stream()
                .map(PaymentResponse::fromPayment)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<PaymentResponse> getPaymentsByUser(Long userId) {
        if (userId == null) {
            throw new IllegalArgumentException("User id is required");
        }

        return paymentRepository.findByPayer_IdOrderByCreatedAtDesc(userId).stream()
                .map(PaymentResponse::fromPayment)
                .toList();
    }

    @Transactional(readOnly = true)
    public BalanceResponse getUserBalance(Long userId) {
        User user = findExistingUser(userId);
        normalizeBalances(user);
        return BalanceResponse.fromUser(user);
    }

    @Transactional(readOnly = true)
    public List<WalletTransactionResponse> getWalletTransactionsByUser(Long userId) {
        if (userId == null) {
            throw new IllegalArgumentException("User id is required");
        }

        return walletTransactionRepository.findByUser_IdOrderByCreatedAtDesc(userId).stream()
                .map(WalletTransactionResponse::fromWalletTransaction)
                .toList();
    }

    public void releaseRewardForApprovedSubmission(TaskSubmission submission) {
        if (submission == null || submission.getTask() == null || submission.getUser() == null) {
            throw new IllegalArgumentException("Submission must include task and user");
        }

        Task task = submission.getTask();
        Payment payment = paymentRepository
                .findFirstByTask_IdAndStatusOrderByCreatedAtDesc(task.getId(), APPROVED_STATUS)
                .orElseThrow(() -> new IllegalArgumentException("Task reward must be funded before approving submissions"));

        if (RELEASED_STATUS.equalsIgnoreCase(payment.getStatus())) {
            return;
        }

        User owner = payment.getPayer();
        User developer = submission.getUser();
        BigDecimal amount = normalizeMoney(payment.getAmount());

        normalizeBalances(owner);
        normalizeBalances(developer);
        ensureEscrowCanCover(owner, amount);

        owner.setEscrowBalance(owner.getEscrowBalance().subtract(amount));
        developer.setAvailableBalance(developer.getAvailableBalance().add(amount));

        userRepository.save(owner);
        userRepository.save(developer);

        payment.setStatus(RELEASED_STATUS);
        payment.setReleasedAt(now());
        payment.setUpdatedAt(now());
        paymentRepository.save(payment);

        task.setFundingStatus(FUNDING_RELEASED_STATUS);
        taskRepository.save(task);

        saveWalletTransaction(owner, payment, task, "escrow_released", amount.negate(), payment.getCurrency(),
                "Reward released from task escrow");
        saveWalletTransaction(developer, payment, task, "reward_received", amount, payment.getCurrency(),
                "Reward received for approved submission");
    }

    public void refundTaskEscrow(Task task, String description) {
        if (task == null || task.getId() == null) {
            return;
        }

        Payment payment = paymentRepository
                .findFirstByTask_IdAndStatusOrderByCreatedAtDesc(task.getId(), APPROVED_STATUS)
                .orElse(null);

        if (payment == null) {
            cancelPendingTaskPayment(task, description);
            if (FUNDING_PENDING_STATUS.equalsIgnoreCase(nullSafe(task.getFundingStatus()))) {
                task.setFundingStatus(UNFUNDED_STATUS);
                taskRepository.save(task);
            }
            return;
        }

        User owner = payment.getPayer();
        BigDecimal amount = normalizeMoney(payment.getAmount());

        normalizeBalances(owner);
        ensureEscrowCanCover(owner, amount);

        owner.setEscrowBalance(owner.getEscrowBalance().subtract(amount));
        owner.setAvailableBalance(owner.getAvailableBalance().add(amount));
        userRepository.save(owner);

        payment.setStatus(REFUNDED_STATUS);
        payment.setRefundedAt(now());
        payment.setUpdatedAt(now());
        paymentRepository.save(payment);

        task.setFundingStatus(FUNDING_REFUNDED_STATUS);
        taskRepository.save(task);

        saveWalletTransaction(owner, payment, task, "escrow_refunded", amount, payment.getCurrency(),
                normalizeDescription(description, "Task escrow refunded"));
    }

    private void cancelPendingTaskPayment(Task task, String description) {
        Payment pendingPayment = paymentRepository
                .findFirstByTask_IdAndStatusOrderByCreatedAtDesc(task.getId(), PENDING_STATUS)
                .orElse(null);

        if (pendingPayment == null) {
            return;
        }

        pendingPayment.setStatus(CANCELLED_STATUS);
        pendingPayment.setFailureReason(normalizeDescription(description, "Task payment cancelled"));
        pendingPayment.setUpdatedAt(now());
        paymentRepository.save(pendingPayment);
    }

    public boolean taskHasLockedFunding(Task task) {
        if (task == null) {
            return false;
        }

        String fundingStatus = nullSafe(task.getFundingStatus()).toLowerCase();
        return FUNDING_PENDING_STATUS.equals(fundingStatus)
                || FUNDED_STATUS.equals(fundingStatus)
                || FUNDING_RELEASED_STATUS.equals(fundingStatus);
    }

    private Payment approvePayment(Payment payment) {
        validatePendingPayment(payment);

        User payer = payment.getPayer();
        Task task = payment.getTask();
        BigDecimal amount = normalizeMoney(payment.getAmount());

        normalizeBalances(payer);
        payer.setEscrowBalance(payer.getEscrowBalance().add(amount));
        userRepository.save(payer);

        payment.setStatus(APPROVED_STATUS);
        payment.setApprovedAt(now());
        payment.setFailureReason(null);
        payment.setUpdatedAt(now());
        paymentRepository.save(payment);

        task.setFundingStatus(FUNDED_STATUS);
        taskRepository.save(task);

        saveWalletTransaction(payer, payment, task, "escrow_funded", amount, payment.getCurrency(),
                "Task reward funded through Mercado Pago");

        return payment;
    }

    private Payment failPayment(Payment payment, String failureReason) {
        validatePendingPayment(payment);

        payment.setStatus(FAILED_STATUS);
        payment.setFailedAt(now());
        payment.setFailureReason(normalizeDescription(failureReason, "Payment failed"));
        payment.setUpdatedAt(now());
        paymentRepository.save(payment);

        Task task = payment.getTask();
        if (task != null && FUNDING_PENDING_STATUS.equalsIgnoreCase(nullSafe(task.getFundingStatus()))) {
            task.setFundingStatus(UNFUNDED_STATUS);
            taskRepository.save(task);
        }

        return payment;
    }

    private void validateTaskPaymentRequest(TaskPaymentRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("Payment data is required");
        }
        if (request.taskId() == null) {
            throw new IllegalArgumentException("Task id is required");
        }
        if (request.payerId() == null) {
            throw new IllegalArgumentException("Payer id is required");
        }
    }

    private void validatePayerOwnsTaskProject(Task task, User payer) {
        Project project = task.getProject();
        User owner = project != null ? project.getOwner() : null;

        if (owner == null || owner.getId() == null || !owner.getId().equals(payer.getId())) {
            throw new IllegalArgumentException("Only the project owner can fund this task");
        }
    }

    private void validateTaskCanBeFunded(Task task) {
        if (task.getRewardAmount() == null || task.getRewardAmount().compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Task reward amount must be greater than zero");
        }
        if ("cancelled".equalsIgnoreCase(nullSafe(task.getStatus()))) {
            throw new IllegalArgumentException("Cancelled tasks cannot be funded");
        }
        if (taskHasLockedFunding(task)) {
            throw new IllegalArgumentException("Task reward is already pending, funded, or released");
        }
    }

    private void validatePendingPayment(Payment payment) {
        if (!PENDING_STATUS.equalsIgnoreCase(nullSafe(payment.getStatus()))) {
            throw new IllegalArgumentException("Only pending payments can be updated by the gateway");
        }
    }

    private String normalizeGatewayStatus(String status) {
        if (status == null || status.isBlank()) {
            throw new IllegalArgumentException("Payment status is required");
        }

        String normalizedStatus = status.trim().toLowerCase();
        if (!APPROVED_STATUS.equals(normalizedStatus) && !FAILED_STATUS.equals(normalizedStatus)) {
            throw new IllegalArgumentException("Payment status must be approved or failed");
        }
        return normalizedStatus;
    }

    private Payment findExistingPayment(Long id) {
        if (id == null) {
            throw new IllegalArgumentException("Payment id is required");
        }

        return paymentRepository.findById(id)
                .orElseThrow(() -> new NoSuchElementException("Payment not found"));
    }

    private User findExistingUser(Long id) {
        if (id == null) {
            throw new IllegalArgumentException("User id is required");
        }

        return userRepository.findById(id)
                .orElseThrow(() -> new NoSuchElementException("User not found"));
    }

    private void ensureEscrowCanCover(User user, BigDecimal amount) {
        if (user.getEscrowBalance().compareTo(amount) < 0) {
            throw new IllegalArgumentException("User escrow balance is not enough to release this reward");
        }
    }

    private void normalizeBalances(User user) {
        if (user.getAvailableBalance() == null) {
            user.setAvailableBalance(BigDecimal.ZERO);
        }
        if (user.getEscrowBalance() == null) {
            user.setEscrowBalance(BigDecimal.ZERO);
        }
    }

    private BigDecimal normalizeMoney(BigDecimal amount) {
        if (amount == null) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }
        return amount.setScale(2, RoundingMode.HALF_UP);
    }

    private String normalizeCurrency(String currency) {
        return currency == null || currency.isBlank() ? "USD" : currency.trim().toUpperCase();
    }

    private String buildExternalReference(Task task) {
        return "task-" + task.getId() + "-" + UUID.randomUUID();
    }

    private void saveWalletTransaction(
            User user,
            Payment payment,
            Task task,
            String type,
            BigDecimal amount,
            String currency,
            String description
    ) {
        WalletTransaction transaction = new WalletTransaction();
        transaction.setUser(user);
        transaction.setPayment(payment);
        transaction.setTask(task);
        transaction.setType(type);
        transaction.setAmount(normalizeMoney(amount));
        transaction.setCurrency(normalizeCurrency(currency));
        transaction.setAvailableBalanceAfter(normalizeMoney(user.getAvailableBalance()));
        transaction.setEscrowBalanceAfter(normalizeMoney(user.getEscrowBalance()));
        transaction.setDescription(description);
        transaction.setCreatedAt(now());
        walletTransactionRepository.save(transaction);
    }

    private String normalizeDescription(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim();
    }

    private String nullSafe(String value) {
        return value == null ? "" : value;
    }

    private Date now() {
        return new Date(System.currentTimeMillis());
    }
}
