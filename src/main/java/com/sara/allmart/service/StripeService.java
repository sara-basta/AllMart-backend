package com.sara.allmart.service;

import com.sara.allmart.entity.Order;
import com.sara.allmart.entity.User;
import com.sara.allmart.exception.ResourceNotFoundException;
import com.sara.allmart.repository.OrderRepository;
import com.stripe.Stripe;
import com.stripe.exception.StripeException;
import com.stripe.model.PaymentIntent;
import com.stripe.model.checkout.Session;
import com.stripe.param.PaymentIntentCreateParams;
import com.stripe.param.checkout.SessionCreateParams;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;

@Service
public class StripeService {
    private static final BigDecimal PLATFORM_FEE_RATE = new BigDecimal("0.10");
    private static final Logger log = LoggerFactory.getLogger(StripeService.class);

    @Value("${stripe.api.key}")
    private String stripeApiKey;

    @Value("${app.cors.allowed-origins}")
    private String frontendUrl;

    private final OrderRepository orderRepository;

    public StripeService(OrderRepository orderRepository) {
        this.orderRepository = orderRepository;
    }

    @PostConstruct
    public void init() {
        Stripe.apiKey = stripeApiKey;
    }

    public PaymentIntent createPaymentIntent(BigDecimal amount, String orderId) throws StripeException {
        long amountInCents = amount
                .setScale(2, RoundingMode.HALF_UP)
                .movePointRight(2)
                .longValueExact();
        String sellerStripeAccountId = resolveSellerStripeAccountId(Long.valueOf(orderId));
        long platformFeeInCents = BigDecimal.valueOf(amountInCents)
                .multiply(PLATFORM_FEE_RATE)
                .setScale(0, RoundingMode.HALF_UP)
                .longValueExact();

        PaymentIntentCreateParams.Builder paramsBuilder = PaymentIntentCreateParams.builder()
                .setAmount(amountInCents)
                .setCurrency("mad")
                .putMetadata("orderId", orderId)
                .setAutomaticPaymentMethods(
                        PaymentIntentCreateParams.AutomaticPaymentMethods.builder().setEnabled(true).build()
                );

        if (isValidSellerStripeAccountId(sellerStripeAccountId)) {
            paramsBuilder
                    .setApplicationFeeAmount(platformFeeInCents)
                    .setTransferData(
                            PaymentIntentCreateParams.TransferData.builder()
                                    .setDestination(sellerStripeAccountId)
                                    .build()
                    );
        } else {
            log.warn(
                    "No valid Stripe Connect account configured for order {}; creating a platform charge",
                    orderId
            );
        }

        PaymentIntentCreateParams params = paramsBuilder.build();

        try {
            return PaymentIntent.create(params);
        } catch (StripeException e) {
            log.error(
                    "Stripe payment intent failed for order {} using Connect account {}: {}",
                    orderId,
                    sellerStripeAccountId,
                    e.getMessage(),
                    e
            );
            throw e;
        }
    }

    public String createCheckoutSession(BigDecimal amount, Long orderId, String guestEmail) throws StripeException {
        long amountInCents = amount
                .setScale(2, RoundingMode.HALF_UP)
                .movePointRight(2)
                .longValueExact();
        String sellerStripeAccountId = resolveSellerStripeAccountId(orderId);
        long platformFeeInCents = BigDecimal.valueOf(amountInCents)
                .multiply(PLATFORM_FEE_RATE)
                .setScale(0, RoundingMode.HALF_UP)
                .longValueExact();

        SessionCreateParams params = SessionCreateParams.builder()
                .setMode(SessionCreateParams.Mode.PAYMENT)
                .setCustomerEmail(guestEmail)
                .setLocale(SessionCreateParams.Locale.EN)
                .putMetadata("orderId", String.valueOf(orderId))
                .setPaymentIntentData(
                        SessionCreateParams.PaymentIntentData.builder()
                                .putMetadata("orderId", String.valueOf(orderId))
                        .setApplicationFeeAmount(platformFeeInCents)
                        .setTransferData(
                                SessionCreateParams.PaymentIntentData.TransferData.builder()
                                        .setDestination(sellerStripeAccountId)
                                        .build()
                        )
                        .build()
                )
                .setSuccessUrl(frontendUrl + "/payment-success?orderId=" + orderId)
                .setCancelUrl(frontendUrl + "/cart")
                .addLineItem(
                        SessionCreateParams.LineItem.builder()
                                .setQuantity(1L)
                                .setPriceData(
                                        SessionCreateParams.LineItem.PriceData.builder()
                                                .setCurrency("mad")
                                                .setUnitAmount(amountInCents)
                                                .setProductData(
                                                        SessionCreateParams.LineItem.PriceData.ProductData.builder()
                                                                .setName("AllMart Order #" + orderId)
                                                                .build())
                                                .build())
                                .build())
                .build();

        return Session.create(params).getUrl();
    }

    private String resolveSellerStripeAccountId(Long orderId) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Order not found!"));

        if (order.getVendorOrders() == null || order.getVendorOrders().size() != 1) {
            throw new IllegalStateException(
                    "Destination charges require an order with exactly one seller."
            );
        }

        User seller = order.getVendorOrders().get(0).getSeller();
        if (seller == null) {
            return null;
        }

        return seller.getStripeAccountId();
    }

    private boolean isValidSellerStripeAccountId(String accountId) {
        return accountId != null && accountId.matches("acct_[A-Za-z0-9]+");
    }
}