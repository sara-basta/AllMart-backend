package com.sara.allmart.service;

import com.sara.allmart.entity.Order;
import com.sara.allmart.entity.OrderStatus;
import com.sara.allmart.entity.VendorOrder;
import com.sara.allmart.event.OrderStatusEvent;
import com.sara.allmart.repository.OrderRepository;
import com.stripe.exception.EventDataObjectDeserializationException;
import com.stripe.exception.SignatureVerificationException;
import com.stripe.model.Event;
import com.stripe.model.PaymentIntent;
import com.stripe.model.checkout.Session;
import com.stripe.net.Webhook;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
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

    @Transactional
    public void processStripeEvent(String payload, String sigHeader) throws SignatureVerificationException {
        Event event = Webhook.constructEvent(payload, sigHeader, endpointSecret);
        log.info("Received Stripe Event: {} ({})", event.getType(), event.getId());

        if ("checkout.session.completed".equals(event.getType())) {
            Object stripeObject = getStripeObject(event);
            if (!(stripeObject instanceof Session session)) {
                throw new IllegalArgumentException("Stripe webhook object is not a checkout Session");
            }

            updateOrderToPaid(readOrderIdFromMetadata(session.getMetadata()));
        } else if ("payment_intent.succeeded".equals(event.getType())) {
            Object stripeObject = getStripeObject(event);
            if (!(stripeObject instanceof PaymentIntent intent)) {
                throw new IllegalArgumentException("Stripe webhook object is not a PaymentIntent");
            }

            updateOrderToPaid(readOrderIdFromMetadata(intent.getMetadata()));
        } else {
            log.info("Ignoring unhandled Stripe event type: {}", event.getType());
        }
    }

    private Object getStripeObject(Event event) {
        Object stripeObject = event.getDataObjectDeserializer().getObject().orElse(null);
        if (stripeObject == null) {
            try {
                stripeObject = event.getDataObjectDeserializer().deserializeUnsafe();
            } catch (EventDataObjectDeserializationException e) {
                throw new IllegalArgumentException("Failed to deserialize Stripe webhook object", e);
            }
        }
        return stripeObject;
    }

    @Transactional
    public void updateOrderToPaid(String orderIdStr) {
        if (orderIdStr == null || orderIdStr.isBlank()) {
            log.warn("Ignoring Stripe event because orderId is missing from metadata.");
            return;
        }

        final Long orderId;
        try {
            orderId = Long.valueOf(orderIdStr);
        } catch (NumberFormatException exception) {
            log.warn("Ignoring Stripe event with invalid orderId metadata: {}", orderIdStr);
            return;
        }

        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new IllegalArgumentException("Order not found for Stripe payment: " + orderId));

        boolean newlyPaid = !order.isPaid();
        order.setPaid(true);
        for (VendorOrder vendorOrder : order.getVendorOrders()) {
            if (vendorOrder.getStatus() == OrderStatus.PENDING
                    || vendorOrder.getStatus() == OrderStatus.CONFIRMED) {
                vendorOrder.setStatus(OrderStatus.PAID);
            }
        }
        orderRepository.save(order);

        if (newlyPaid) {
            eventPublisher.publishEvent(new OrderStatusEvent(this, order, OrderStatus.PAID.name()));
        } else {
            log.info("Webhook duplicate/late event reconciled vendor statuses for order {}.", orderId);
        }
        log.info("Order {} marked paid and vendor orders transitioned for fulfillment.", orderId);
    }

    private String readOrderIdFromMetadata(Map<String, String> metadata) {
        if (metadata == null) {
            return null;
        }
        return metadata.get("orderId");
    }
}