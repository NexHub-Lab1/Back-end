package com.nexhub.backend.controller;

import com.nexhub.backend.dto.ApiResponse;
import com.nexhub.backend.dto.payment.BalanceResponse;
import com.nexhub.backend.dto.payment.PaymentResponse;
import com.nexhub.backend.dto.payment.PaymentSimulationRequest;
import com.nexhub.backend.dto.payment.TaskPaymentRequest;
import com.nexhub.backend.dto.payment.WalletTransactionResponse;
import com.nexhub.backend.service.PaymentService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.NoSuchElementException;

@RestController
@RequestMapping("/api/payments")
@RequiredArgsConstructor
public class PaymentController {
    private final PaymentService paymentService;

    @PostMapping("/task")
    public ResponseEntity<ApiResponse<PaymentResponse>> createTaskPayment(@RequestBody TaskPaymentRequest request) {
        try {
            return ResponseEntity.status(HttpStatus.CREATED)
                    .body(ApiResponse.success("Task payment created correctly", paymentService.createTaskPayment(request)));
        } catch (NoSuchElementException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiResponse.error(e.getMessage()));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ApiResponse.error(e.getMessage()));
        }
    }

    @PostMapping("/simulate")
    public ResponseEntity<ApiResponse<PaymentResponse>> simulateGatewayResult(@RequestBody PaymentSimulationRequest request) {
        try {
            return ResponseEntity.ok(ApiResponse.success("Payment gateway result processed", paymentService.simulateGatewayResult(request)));
        } catch (NoSuchElementException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiResponse.error(e.getMessage()));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ApiResponse.error(e.getMessage()));
        }
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<PaymentResponse>> getById(@PathVariable Long id) {
        try {
            return ResponseEntity.ok(ApiResponse.success("Payment found", paymentService.getPaymentById(id)));
        } catch (NoSuchElementException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiResponse.error(e.getMessage()));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ApiResponse.error(e.getMessage()));
        }
    }

    @GetMapping("/task/{taskId}")
    public ResponseEntity<ApiResponse<List<PaymentResponse>>> getByTask(@PathVariable Long taskId) {
        try {
            return ResponseEntity.ok(ApiResponse.success("Task payments", paymentService.getPaymentsByTask(taskId)));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ApiResponse.error(e.getMessage()));
        }
    }

    @GetMapping("/user/{userId}")
    public ResponseEntity<ApiResponse<List<PaymentResponse>>> getByUser(@PathVariable Long userId) {
        try {
            return ResponseEntity.ok(ApiResponse.success("User payments", paymentService.getPaymentsByUser(userId)));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ApiResponse.error(e.getMessage()));
        }
    }

    @GetMapping("/balance/{userId}")
    public ResponseEntity<ApiResponse<BalanceResponse>> getBalance(@PathVariable Long userId) {
        try {
            return ResponseEntity.ok(ApiResponse.success("User balance", paymentService.getUserBalance(userId)));
        } catch (NoSuchElementException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiResponse.error(e.getMessage()));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ApiResponse.error(e.getMessage()));
        }
    }

    @GetMapping("/wallet-transactions/user/{userId}")
    public ResponseEntity<ApiResponse<List<WalletTransactionResponse>>> getWalletTransactions(@PathVariable Long userId) {
        try {
            return ResponseEntity.ok(ApiResponse.success("User wallet transactions", paymentService.getWalletTransactionsByUser(userId)));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ApiResponse.error(e.getMessage()));
        }
    }
}
