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
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;

@Service
public class StripeService {
    private static final BigDecimal PLATFORM_FEE_RATE = new BigDecimal("0.10");

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

        PaymentIntentCreateParams params = PaymentIntentCreateParams.builder()
                .setAmount(amountInCents)
                .setCurrency("mad")
                .putMetadata("orderId", orderId)
                .setApplicationFeeAmount(platformFeeInCents)
                .setTransferData(
                        PaymentIntentCreateParams.TransferData.builder()
                                .setDestination(sellerStripeAccountId)
                                .build()
                )
                .setAutomaticPaymentMethods(
                        PaymentIntentCreateParams.AutomaticPaymentMethods.builder().setEnabled(true).build()
                ).build();

        return PaymentIntent.create(params);
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
        if (seller == null || seller.getStripeAccountId() == null
                || seller.getStripeAccountId().isBlank()) {
            throw new IllegalStateException(
                    "Seller does not have a configured Stripe account."
            );
        }

        return seller.getStripeAccountId();
    }
}