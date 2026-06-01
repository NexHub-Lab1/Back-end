package com.nexhub.backend.controller;

import com.nexhub.backend.dto.ApiResponse;
import com.nexhub.backend.dto.payment.BalanceResponse;
import com.nexhub.backend.dto.payment.PaymentResponse;
import com.nexhub.backend.dto.payment.WalletTransactionResponse;
import com.nexhub.backend.service.PaymentService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.NoSuchElementException;

@RestController
@RequestMapping("/api/payments")
@RequiredArgsConstructor
public class PaymentController {
    private final PaymentService paymentService;

    @PostMapping("/tasks/{taskId}/fund")
    public ResponseEntity<ApiResponse<PaymentResponse>> fundTask(
            @PathVariable Long taskId,
            Authentication authentication
    ) {
        try {
            return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(
                    "Task funding created correctly",
                    paymentService.fundTask(taskId, authentication.getName())
            ));
        } catch (NoSuchElementException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiResponse.error(e.getMessage()));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ApiResponse.error(e.getMessage()));
        }
    }

    @PostMapping("/webhooks/mercadopago")
    public ResponseEntity<Void> receiveMercadoPagoNotification(
            @RequestParam(name = "data.id") String providerPaymentId,
            @RequestHeader(name = "x-request-id") String requestId,
            @RequestHeader(name = "x-signature") String signatureHeader
    ) {
        try {
            paymentService.processMercadoPagoNotification(providerPaymentId, requestId, signatureHeader);
            return ResponseEntity.ok().build();
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().build();
        }
    }

    @GetMapping("/tasks/{taskId}")
    public ResponseEntity<ApiResponse<List<PaymentResponse>>> getTaskPayments(
            @PathVariable Long taskId,
            Authentication authentication
    ) {
        try {
            return ResponseEntity.ok(ApiResponse.success(
                    "Task payments",
                    paymentService.getTaskPayments(taskId, authentication.getName())
            ));
        } catch (NoSuchElementException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiResponse.error(e.getMessage()));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ApiResponse.error(e.getMessage()));
        }
    }

    @GetMapping("/me/balance")
    public ResponseEntity<ApiResponse<BalanceResponse>> getMyBalance(Authentication authentication) {
        try {
            return ResponseEntity.ok(ApiResponse.success(
                    "Wallet balance",
                    paymentService.getMyBalance(authentication.getName())
            ));
        } catch (NoSuchElementException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiResponse.error(e.getMessage()));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ApiResponse.error(e.getMessage()));
        }
    }

    @GetMapping("/me/transactions")
    public ResponseEntity<ApiResponse<List<WalletTransactionResponse>>> getMyWalletTransactions(Authentication authentication) {
        try {
            return ResponseEntity.ok(ApiResponse.success(
                    "Wallet transactions",
                    paymentService.getMyWalletTransactions(authentication.getName())
            ));
        } catch (NoSuchElementException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiResponse.error(e.getMessage()));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ApiResponse.error(e.getMessage()));
        }
    }
}
