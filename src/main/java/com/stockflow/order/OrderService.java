package com.stockflow.order;

import com.stockflow.common.api.PageResponse;
import com.stockflow.common.exception.BusinessRuleException;
import com.stockflow.common.exception.ResourceNotFoundException;
import com.stockflow.inventory.InventoryService;
import com.stockflow.order.dto.CreateOrderRequest;
import com.stockflow.order.dto.OrderItemRequest;
import com.stockflow.order.dto.OrderResponse;
import com.stockflow.product.Product;
import com.stockflow.product.ProductRepository;
import com.stockflow.security.SecurityUtils;
import com.stockflow.user.User;
import com.stockflow.user.UserRepository;
import com.stockflow.warehouse.Warehouse;
import com.stockflow.warehouse.WarehouseRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;
import java.util.UUID;

@Service
public class OrderService {

    private static final Logger log = LoggerFactory.getLogger(OrderService.class);

    private final OrderRepository orderRepository;
    private final WarehouseRepository warehouseRepository;
    private final ProductRepository productRepository;
    private final UserRepository userRepository;
    private final InventoryService inventoryService;

    public OrderService(
            OrderRepository orderRepository,
            WarehouseRepository warehouseRepository,
            ProductRepository productRepository,
            UserRepository userRepository,
            InventoryService inventoryService) {
        this.orderRepository = orderRepository;
        this.warehouseRepository = warehouseRepository;
        this.productRepository = productRepository;
        this.userRepository = userRepository;
        this.inventoryService = inventoryService;
    }

    @Transactional
    public OrderResponse createOrder(CreateOrderRequest request) {
        Warehouse warehouse = warehouseRepository.findById(request.getWarehouseId())
                .orElseThrow(() -> new ResourceNotFoundException("Warehouse", "ID", request.getWarehouseId()));

        if (!warehouse.isActive()) {
            throw new BusinessRuleException("Cannot place order for an inactive warehouse: " + warehouse.getCode());
        }

        String username = SecurityUtils.getCurrentUsername();
        User currentUser = userRepository.findByUsername(username).orElse(null);

        String orderNumber = "ORD-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();

        Order order = new Order(
                orderNumber,
                request.getCustomerName().trim(),
                request.getCustomerEmail().trim().toLowerCase(),
                warehouse,
                request.getNotes(),
                currentUser
        );

        // Deadlock Prevention: Sort items deterministically by Product ID before acquiring row locks
        List<OrderItemRequest> sortedItems = request.getItems().stream()
                .sorted(Comparator.comparing(OrderItemRequest::getProductId))
                .toList();

        for (OrderItemRequest itemRequest : sortedItems) {
            Product product = productRepository.findById(itemRequest.getProductId())
                    .orElseThrow(() -> new ResourceNotFoundException("Product", "ID", itemRequest.getProductId()));

            if (!product.isActive()) {
                throw new BusinessRuleException(String.format("Product '%s' (SKU: %s) is currently inactive and cannot be ordered",
                        product.getName(), product.getSku()));
            }

            // Atomically reserve inventory under pessimistic write lock
            inventoryService.reserveStock(
                    warehouse.getId(),
                    product.getId(),
                    itemRequest.getQuantity(),
                    orderNumber,
                    username
            );

            OrderItem orderItem = new OrderItem(product, itemRequest.getQuantity(), product.getPrice());
            order.addItem(orderItem);
        }

        Order savedOrder = orderRepository.save(order);
        log.info("Order '{}' created successfully with {} items, total: ${}",
                savedOrder.getOrderNumber(), savedOrder.getItems().size(), savedOrder.getTotalAmount());

        return OrderResponse.fromEntity(savedOrder);
    }

    @Transactional
    public OrderResponse confirmOrder(Long orderId) {
        Order order = findOrderByIdWithDetails(orderId);
        order.confirm();
        Order savedOrder = orderRepository.save(order);
        log.info("Order '{}' confirmed", savedOrder.getOrderNumber());
        return OrderResponse.fromEntity(savedOrder);
    }

    @Transactional
    public OrderResponse completeOrder(Long orderId) {
        Order order = findOrderByIdWithDetails(orderId);
        String username = SecurityUtils.getCurrentUsername();

        order.complete();

        // Sort items deterministically by product ID before row locking and deduction
        List<OrderItem> sortedItems = order.getItems().stream()
                .sorted(Comparator.comparing(item -> item.getProduct().getId()))
                .toList();

        for (OrderItem item : sortedItems) {
            inventoryService.deductStock(
                    order.getWarehouse().getId(),
                    item.getProduct().getId(),
                    item.getQuantity(),
                    order.getOrderNumber(),
                    username
            );
        }

        Order savedOrder = orderRepository.save(order);
        log.info("Order '{}' completed and stock deducted", savedOrder.getOrderNumber());
        return OrderResponse.fromEntity(savedOrder);
    }

    @Transactional
    public OrderResponse cancelOrder(Long orderId) {
        Order order = findOrderByIdWithDetails(orderId);
        String username = SecurityUtils.getCurrentUsername();

        order.cancel();

        // Release reserved stock for each item in the order
        List<OrderItem> sortedItems = order.getItems().stream()
                .sorted(Comparator.comparing(item -> item.getProduct().getId()))
                .toList();

        for (OrderItem item : sortedItems) {
            inventoryService.releaseStock(
                    order.getWarehouse().getId(),
                    item.getProduct().getId(),
                    item.getQuantity(),
                    order.getOrderNumber(),
                    username
            );
        }

        Order savedOrder = orderRepository.save(order);
        log.info("Order '{}' cancelled and reserved stock released", savedOrder.getOrderNumber());
        return OrderResponse.fromEntity(savedOrder);
    }

    @Transactional(readOnly = true)
    public OrderResponse getOrderById(Long id) {
        return OrderResponse.fromEntity(findOrderByIdWithDetails(id));
    }

    @Transactional(readOnly = true)
    public OrderResponse getOrderByOrderNumber(String orderNumber) {
        Order order = orderRepository.findByOrderNumberWithDetails(orderNumber)
                .orElseThrow(() -> new ResourceNotFoundException("Order", "orderNumber", orderNumber));
        return OrderResponse.fromEntity(order);
    }

    @Transactional(readOnly = true)
    public PageResponse<OrderResponse> getOrders(
            Long warehouseId,
            OrderStatus status,
            String customerEmail,
            Pageable pageable) {
        Page<Order> page = orderRepository.findWithFilters(warehouseId, status, customerEmail, pageable);
        return PageResponse.of(page, page.getContent().stream().map(OrderResponse::fromEntity).toList());
    }

    private Order findOrderByIdWithDetails(Long id) {
        return orderRepository.findByIdWithDetails(id)
                .orElseThrow(() -> new ResourceNotFoundException("Order", "ID", id));
    }
}
