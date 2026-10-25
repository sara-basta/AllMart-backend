package com.sara.allmart.controller;

import com.sara.allmart.dto.request.PaymentRequest;
import com.sara.allmart.service.PaymentService;
import com.stripe.exception.StripeException;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/payments")
public class PaymentController {
    private static final Logger log = LoggerFactory.getLogger(PaymentController.class);
    private final PaymentService paymentService;

    public PaymentController(PaymentService paymentService) {
        this.paymentService = paymentService;
    }

    @PreAuthorize("hasAuthority('CUSTOMER')")
    @PostMapping
    public ResponseEntity<Map<String, String>> processPayment(@AuthenticationPrincipal UserDetails user, @Valid @RequestBody PaymentRequest request) {
        try {
            Map<String, String> response = paymentService.processPayment(user.getUsername(), request);
            return ResponseEntity.ok(response);
        } catch (StripeException | IllegalArgumentException e) {
            log.error("Payment initialization failed for order {}: {}", request.orderId(), e.getMessage(), e);
            Map<String, String> error = new HashMap<>();
            error.put("error", e.getMessage() == null ? "Stripe payment initialization failed." : e.getMessage());
            return ResponseEntity.badRequest().body(error);
        }
    }
}
