package com.sara.allmart.service;

import com.sara.allmart.entity.Order;
import com.sara.allmart.event.OrderStatusEvent;
import com.sara.allmart.repository.OrderRepository;
import com.stripe.exception.SignatureVerificationException;
import com.stripe.model.Event;
import com.stripe.model.PaymentIntent;
import com.stripe.model.checkout.Session;
import com.stripe.net.Webhook;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import java.util.Map;

@Slf4j
@Service
public class WebhookService {

    @Value("${stripe.webhook.secret}")
    private String endpointSecret;

    private final OrderRepository orderRepository;
    private final ApplicationEventPublisher eventPublisher;

    public WebhookService(OrderRepository orderRepository, ApplicationEventPublisher eventPublisher) {
        this.orderRepository = orderRepository;
        this.eventPublisher = eventPublisher;
    }

    public void processStripeEvent(String payload, String sigHeader) throws SignatureVerificationException {
        Event event = Webhook.constructEvent(payload, sigHeader, endpointSecret);
        log.info("Received Stripe Event: {} ({})", event.getType(), event.getId());

        try {
            if ("checkout.session.completed".equals(event.getType())) {
                Object stripeObject = event.getDataObjectDeserializer().getObject()
                        .orElse(event.getDataObjectDeserializer().deserializeUnsafe());
                if (!(stripeObject instanceof Session)) {
                    throw new RuntimeException("Stripe webhook object is not a checkout Session");
                }

                Session session = (Session) stripeObject;
                String orderId = readOrderIdFromMetadata(session.getMetadata());
                log.info("Processing Checkout Session for Order ID: {}", orderId);
                updateOrderToPaid(orderId);
            } else if ("payment_intent.succeeded".equals(event.getType())) {
                Object stripeObject = event.getDataObjectDeserializer().getObject()
                        .orElse(event.getDataObjectDeserializer().deserializeUnsafe());
                if (!(stripeObject instanceof PaymentIntent)) {
                    throw new RuntimeException("Stripe webhook object is not a PaymentIntent");
                }

                PaymentIntent intent = (PaymentIntent) stripeObject;
                String orderId = readOrderIdFromMetadata(intent.getMetadata());
                log.info("Processing Payment Intent for Order ID: {}", orderId);
                updateOrderToPaid(orderId);
            }
        } catch (Exception e) {
            log.error("CRITICAL ERROR in Webhook Processing: ", e);
            throw new RuntimeException(e);
        }
    }

    public void updateOrderToPaid(String orderIdStr) {
        if (orderIdStr == null || orderIdStr.isBlank()) {
            log.warn("STILL PENDING: orderId was missing from Stripe metadata.");
            return;
        }

        Long orderId = Long.parseLong(orderIdStr);
        int updatedRows = orderRepository.markPaidIfUnpaid(orderId);
        if (updatedRows == 0) {
            log.info("Webhook duplicate/late event ignored: Order {} is already PAID.", orderId);
            return;
        }

        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new RuntimeException("Order not found after paid update: " + orderId));

        eventPublisher.publishEvent(new OrderStatusEvent(this, order, "PAID"));
        log.info("DATABASE UPDATED: Order {} is now PAID and event published once.", orderId);
    }

    private String readOrderIdFromMetadata(Map<String, String> metadata) {
        if (metadata == null) {
            return null;
        }
        return metadata.get("orderId");
    }
}