package com.sara.allmart.service;

import com.sara.allmart.dto.request.PaymentRequest;
import com.sara.allmart.entity.Order;
import com.sara.allmart.entity.OrderStatus;
import com.sara.allmart.entity.PaymentMethod;
import com.sara.allmart.entity.User;
import com.sara.allmart.entity.VendorOrder;
import com.sara.allmart.event.OrderStatusEvent;
import com.sara.allmart.exception.ResourceNotFoundException;
import com.sara.allmart.repository.OrderRepository;
import com.sara.allmart.repository.UserRepository;
import com.stripe.model.PaymentIntent;
import jakarta.transaction.Transactional;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

@Service
public class PaymentService {
    private final OrderRepository orderRepository;
    private final UserRepository userRepository;
    private final StripeService stripeService;
    private final ApplicationEventPublisher eventPublisher;

    public PaymentService(OrderRepository orderRepository, UserRepository userRepository, StripeService stripeService, ApplicationEventPublisher eventPublisher) {
        this.orderRepository = orderRepository;
        this.userRepository = userRepository;
        this.stripeService = stripeService;
        this.eventPublisher = eventPublisher;
    }

    @Transactional
    public Map<String, String> processPayment(String email, PaymentRequest request) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("User not found!"));
        Order order = orderRepository.findById(request.orderId())
                .orElseThrow(() -> new ResourceNotFoundException("Order not found!"));
        if(!user.getId().equals(order.getUser().getId())){
            throw new RuntimeException("This order isn't owned by this user. Access Denied!");
        }
        if(order.isPaid()){
            throw new IllegalStateException("This order had being paid.");
        }
        // If any vendor sub-order is CANCELLED, treat the whole order as cancelled
        boolean isCancelled = order.getVendorOrders().stream()
                .allMatch(vo -> vo.getStatus() == OrderStatus.CANCELLED);
        if (isCancelled) {
            throw new IllegalStateException("This order had been cancelled.");
        }
        order.setPaymentMethod(request.paymentMethod());
        Map<String, String> response = new HashMap<>();

        if (request.paymentMethod() == PaymentMethod.CREDIT_CARD) {
            try {
                PaymentIntent intent = stripeService.createPaymentIntent(order.getTotalAmount(), order.getId().toString());
                response.put("clientSecret", intent.getClientSecret());
            } catch (Exception e) {
                throw new RuntimeException("Failed to initialize Stripe payment: " + e.getMessage());
            }
        } else if (request.paymentMethod() == PaymentMethod.CASH_ON_DELIVERY) {
            order.setPaid(false);
            // Status is now per-vendor; confirm all sub-orders for CoD
            for (VendorOrder vo : order.getVendorOrders()) {
                vo.setStatus(OrderStatus.CONFIRMED);
            }
            response.put("message", "Order successfully placed for Cash on Delivery!");
        }
        orderRepository.save(order);

        if(request.paymentMethod() == PaymentMethod.CASH_ON_DELIVERY) {
            eventPublisher.publishEvent(new OrderStatusEvent(this, order, "CONFIRMED"));
        }
        return response;
    }
}
