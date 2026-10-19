package com.sara.allmart.service;

import com.sara.allmart.dto.request.AddressRequest;
import com.sara.allmart.dto.request.CheckoutItemRequest;
import com.sara.allmart.dto.request.GuestCheckoutRequest;
import com.sara.allmart.dto.request.OrderRequest;
import com.sara.allmart.dto.response.OrderResponse;
import com.sara.allmart.entity.*;
import com.sara.allmart.event.OrderStatusEvent;
import com.sara.allmart.exception.ResourceNotFoundException;
import com.sara.allmart.mapper.OrderMapper;
import com.sara.allmart.repository.OrderRepository;
import com.sara.allmart.repository.ProductRepository;
import com.sara.allmart.repository.SavedAddressRepository;
import com.sara.allmart.repository.UserRepository;
import com.sara.allmart.repository.VendorOrderRepository;
import jakarta.transaction.Transactional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@Slf4j
public class OrderService {

    /** AllMart platform commission rate: 10% of vendorSubtotal */
    private static final BigDecimal PLATFORM_FEE_RATE = new BigDecimal("0.10");

    private final OrderRepository orderRepository;
    private final ProductRepository productRepository;
    private final UserRepository userRepository;
    private final OrderMapper orderMapper;
    private final SavedAddressRepository savedAddressRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final VendorOrderRepository vendorOrderRepository;

    OrderService(OrderRepository orderRepository, ProductRepository productRepository,
                 UserRepository userRepository, OrderMapper orderMapper,
                 SavedAddressRepository savedAddressRepository,
                 ApplicationEventPublisher eventPublisher,
                 VendorOrderRepository vendorOrderRepository) {
        this.orderRepository = orderRepository;
        this.productRepository = productRepository;
        this.userRepository = userRepository;
        this.orderMapper = orderMapper;
        this.savedAddressRepository = savedAddressRepository;
        this.eventPublisher = eventPublisher;
        this.vendorOrderRepository = vendorOrderRepository;
    }

    @Transactional
    public OrderResponse createOrder(String email, OrderRequest request) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("User not found"));
        Product product = productRepository.findById(request.productId())
                .orElseThrow(() -> new ResourceNotFoundException("Product not found"));

        if (product.getStockQuantity() < 1) {
            log.warn("Order failed: Product ID {} is out of stock", request.productId());
            throw new RuntimeException("Not in stock!");
        }

        product.setStockQuantity(product.getStockQuantity() - 1);
        productRepository.save(product);

        BigDecimal itemPrice = product.getPrice();
        BigDecimal total = itemPrice;

        Order order = new Order();
        order.setTotalAmount(total);

        Address shippingAddress;
        if (request.addressId() != null) {
            SavedAddress savedAddress = savedAddressRepository.findById(request.addressId())
                    .orElseThrow(() -> new RuntimeException("Address not found"));
            if (!savedAddress.getUser().getEmail().equals(email)) {
                throw new RuntimeException("Access Denied: You cannot use an address that isn't yours!");
            }
            shippingAddress = new Address(savedAddress.getStreet(), savedAddress.getCity(), savedAddress.getZipCode());
        } else if (request.newAddress() != null) {
            SavedAddress savedAddress = new SavedAddress();
            savedAddress.setStreet(request.newAddress().street());
            savedAddress.setCity(request.newAddress().city());
            savedAddress.setZipCode(request.newAddress().zipCode());
            savedAddress.setUser(user);
            savedAddressRepository.save(savedAddress);
            shippingAddress = new Address(request.newAddress().street(), request.newAddress().city(), request.newAddress().zipCode());
        } else {
            throw new RuntimeException("Checkout failed: You must provide either an existing address ID or a new address.");
        }

        order.setShippingAddress(shippingAddress);
        order.setUser(user);

        // Create a VendorOrder for the product's seller
        VendorOrder vendorOrder = new VendorOrder();
        vendorOrder.setOrder(order);
        vendorOrder.setSeller(product.getSeller());
        vendorOrder.setStatus(OrderStatus.PENDING);
        vendorOrder.setVendorSubtotal(itemPrice);
        // Calculate the 10% platform commission
        BigDecimal fee = itemPrice.multiply(PLATFORM_FEE_RATE).setScale(2, RoundingMode.HALF_UP);
        vendorOrder.setPlatformFee(fee);

        OrderItem item = new OrderItem();
        item.setPriceAtPurchase(itemPrice);
        item.setProduct(product);
        item.setQuantity(1);
        item.setVendorOrder(vendorOrder);

        vendorOrder.setItems(new ArrayList<>(List.of(item)));
        order.setVendorOrders(new ArrayList<>(List.of(vendorOrder)));

        log.info("Order successfully created");
        return orderMapper.toResponse(orderRepository.save(order));
    }

    public OrderResponse getCustomerOrderById(Long orderId, String email) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Order not found!"));

        if (!order.getUser().getEmail().equals(email)) {
            throw new RuntimeException("Access Denied: You cannot view someone else's order.");
        }

        return orderMapper.toResponse(order);
    }

    public OrderResponse getAdminOrderById(Long orderId) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Order not found!"));
        return orderMapper.toResponse(order);
    }

    public List<OrderResponse> getOrderByUser(Long id) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("User not found!"));

        List<Order> ordersByUser = orderRepository.findOrdersByUser(user);

        return ordersByUser.stream()
                .map(orderMapper::toResponse)
                .toList();
    }

    @Transactional
    public OrderResponse updateVendorOrderStatus(Long vendorOrderId, OrderStatus newStatus) {
        // Direct lookup via VendorOrderRepository — replaces the previous full-table scan.
        VendorOrder vendorOrder = vendorOrderRepository.findById(vendorOrderId)
                .orElseThrow(() -> new ResourceNotFoundException("VendorOrder not found!"));

        if (vendorOrder.getStatus() == OrderStatus.DELIVERED) {
            throw new IllegalStateException("You cannot change a delivered vendor order's status!");
        }

        vendorOrder.setStatus(newStatus);
        vendorOrderRepository.save(vendorOrder);

        Order order = vendorOrder.getOrder();
        eventPublisher.publishEvent(new OrderStatusEvent(this, order, newStatus.name()));
        return orderMapper.toResponse(order);
    }

    @Transactional
    public Long createOrderFromCart(String email, List<CartItem> items, Long addressId, AddressRequest newAddress) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        Order order = new Order();
        order.setUser(user);
        order.setCreatedAt(LocalDateTime.now());

        Address shippingAddress;
        if (addressId != null) {
            SavedAddress savedAddress = savedAddressRepository.findById(addressId)
                    .orElseThrow(() -> new RuntimeException("Address not found"));
            if (!savedAddress.getUser().getEmail().equals(email)) {
                throw new RuntimeException("Access Denied: You cannot use an address that isn't yours!");
            }
            shippingAddress = new Address(savedAddress.getStreet(), savedAddress.getCity(), savedAddress.getZipCode());
        } else if (newAddress != null) {
            SavedAddress savedAddress = new SavedAddress();
            savedAddress.setStreet(newAddress.street());
            savedAddress.setCity(newAddress.city());
            savedAddress.setZipCode(newAddress.zipCode());
            savedAddress.setUser(user);
            savedAddressRepository.save(savedAddress);
            shippingAddress = new Address(newAddress.street(), newAddress.city(), newAddress.zipCode());
        } else {
            throw new RuntimeException("Checkout failed: You must provide either an existing address ID or a new address.");
        }

        order.setShippingAddress(shippingAddress);

        // Group cart items by seller — each seller gets their own VendorOrder
        Map<Long, VendorOrder> vendorOrderMap = new LinkedHashMap<>();
        BigDecimal totalOrderAmount = BigDecimal.ZERO;

        for (CartItem cartItem : items) {
            Product product = cartItem.getProduct();
            if (product.getStockQuantity() < cartItem.getQuantity()) {
                throw new RuntimeException("Product " + product.getName() + " is out of stock!");
            }

            product.setStockQuantity(product.getStockQuantity() - cartItem.getQuantity());
            productRepository.save(product);

            User seller = product.getSeller();
            VendorOrder vendorOrder = vendorOrderMap.computeIfAbsent(seller.getId(), key -> {
                VendorOrder vo = new VendorOrder();
                vo.setOrder(order);
                vo.setSeller(seller);
                vo.setStatus(OrderStatus.PENDING);
                vo.setVendorSubtotal(BigDecimal.ZERO);
                vo.setPlatformFee(BigDecimal.ZERO); // will be recalculated after subtotal is final
                vo.setItems(new ArrayList<>());
                return vo;
            });

            BigDecimal lineTotal = cartItem.getPrice().multiply(BigDecimal.valueOf(cartItem.getQuantity()));

            OrderItem orderItem = new OrderItem();
            orderItem.setVendorOrder(vendorOrder);
            orderItem.setProduct(product);
            orderItem.setQuantity(cartItem.getQuantity());
            orderItem.setPriceAtPurchase(cartItem.getPrice());

            vendorOrder.getItems().add(orderItem);
            vendorOrder.setVendorSubtotal(vendorOrder.getVendorSubtotal().add(lineTotal));

            totalOrderAmount = totalOrderAmount.add(lineTotal);
        }

        // Apply 10% platform commission on the final vendorSubtotal of each VendorOrder
        vendorOrderMap.values().forEach(vo -> {
            BigDecimal fee = vo.getVendorSubtotal()
                    .multiply(PLATFORM_FEE_RATE)
                    .setScale(2, RoundingMode.HALF_UP);
            vo.setPlatformFee(fee);
        });

        order.setVendorOrders(new ArrayList<>(vendorOrderMap.values()));
        order.setTotalAmount(totalOrderAmount);

        Order savedOrder = orderRepository.save(order);
        return savedOrder.getId();
    }

    @Transactional
    public OrderResponse createGuestOrder(GuestCheckoutRequest request) {
        Order order = new Order();
        order.setUser(null);
        order.setGuestEmail(request.email());
        order.setCreatedAt(LocalDateTime.now());
        order.setPaymentMethod(request.paymentMethod());

        Address shippingAddress = request.shippingAddress();
        order.setShippingAddress(new Address(
                shippingAddress.getStreet(),
                shippingAddress.getCity(),
                shippingAddress.getZipCode()
        ));

        // Group guest checkout items by seller
        Map<Long, VendorOrder> vendorOrderMap = new LinkedHashMap<>();
        BigDecimal totalOrderAmount = BigDecimal.ZERO;

        for (CheckoutItemRequest checkoutItem : request.items()) {
            Product product = productRepository.findById(checkoutItem.productId())
                    .orElseThrow(() -> new ResourceNotFoundException("Product not found"));

            if (product.getStockQuantity() < checkoutItem.quantity()) {
                throw new RuntimeException("Product " + product.getName() + " is out of stock!");
            }

            product.setStockQuantity(product.getStockQuantity() - checkoutItem.quantity());
            productRepository.save(product);

            User seller = product.getSeller();
            VendorOrder vendorOrder = vendorOrderMap.computeIfAbsent(seller.getId(), key -> {
                VendorOrder vo = new VendorOrder();
                vo.setOrder(order);
                vo.setSeller(seller);
                vo.setStatus(OrderStatus.PENDING);
                vo.setVendorSubtotal(BigDecimal.ZERO);
                vo.setPlatformFee(BigDecimal.ZERO); // will be recalculated after subtotal is final
                vo.setItems(new ArrayList<>());
                return vo;
            });

            BigDecimal lineTotal = product.getPrice().multiply(BigDecimal.valueOf(checkoutItem.quantity()));

            OrderItem orderItem = new OrderItem();
            orderItem.setVendorOrder(vendorOrder);
            orderItem.setProduct(product);
            orderItem.setQuantity(checkoutItem.quantity());
            orderItem.setPriceAtPurchase(product.getPrice());

            vendorOrder.getItems().add(orderItem);
            vendorOrder.setVendorSubtotal(vendorOrder.getVendorSubtotal().add(lineTotal));
            totalOrderAmount = totalOrderAmount.add(lineTotal);
        }

        // Apply 10% platform commission on the final vendorSubtotal of each VendorOrder
        vendorOrderMap.values().forEach(vo -> {
            BigDecimal fee = vo.getVendorSubtotal()
                    .multiply(PLATFORM_FEE_RATE)
                    .setScale(2, RoundingMode.HALF_UP);
            vo.setPlatformFee(fee);
        });

        order.setVendorOrders(new ArrayList<>(vendorOrderMap.values()));
        order.setTotalAmount(totalOrderAmount);

        if (request.paymentMethod() == PaymentMethod.CASH_ON_DELIVERY) {
            order.setPaid(false);
            // Mark all vendor sub-orders as CONFIRMED for CoD
            order.getVendorOrders().forEach(vo -> vo.setStatus(OrderStatus.CONFIRMED));
        }

        Order savedOrder = orderRepository.save(order);

        if (request.paymentMethod() == PaymentMethod.CASH_ON_DELIVERY) {
            eventPublisher.publishEvent(new OrderStatusEvent(this, savedOrder, "CONFIRMED"));
        }

        return orderMapper.toResponse(savedOrder);
    }

    public Page<OrderResponse> getOrdersHistory(String email, int page, int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());

        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        Page<Order> ordersPage = orderRepository.findByUser(user, pageable);
        return ordersPage.map(orderMapper::toResponse);
    }

    @Transactional
    public OrderResponse cancelOrder(String email, Long id) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("User not found"));
        Order order = orderRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Order not found"));

        if (!order.getUser().getId().equals(user.getId())) {
            throw new RuntimeException("You are not allowed to cancel this order");
        }

        // An order can only be cancelled if all vendor sub-orders are still in early stages
        boolean anyTooFar = order.getVendorOrders().stream()
                .anyMatch(vo -> vo.getStatus() != OrderStatus.PENDING
                        && vo.getStatus() != OrderStatus.CONFIRMED);
        if (anyTooFar) {
            throw new IllegalStateException("Order has progressed too far and can no longer be cancelled by the user.");
        }

        // Restore stock for all items across all vendor sub-orders
        for (VendorOrder vo : order.getVendorOrders()) {
            for (OrderItem item : vo.getItems()) {
                Integer quantity = item.getQuantity();
                Long itemId = item.getProduct().getId();

                Product product = productRepository.findById(itemId)
                        .orElseThrow(() -> new ResourceNotFoundException("Product not found"));
                product.setStockQuantity(product.getStockQuantity() + quantity);
            }
            vo.setStatus(OrderStatus.CANCELLED);
        }

        orderRepository.save(order);
        eventPublisher.publishEvent(new OrderStatusEvent(this, order, "CANCELLED"));

        return orderMapper.toResponse(order);
    }
}
