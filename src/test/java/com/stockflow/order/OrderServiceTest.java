package com.stockflow.order;

import com.stockflow.common.exception.BusinessRuleException;
import com.stockflow.common.exception.InvalidOrderStateException;
import com.stockflow.inventory.InventoryService;
import com.stockflow.order.dto.CreateOrderRequest;
import com.stockflow.order.dto.OrderItemRequest;
import com.stockflow.order.dto.OrderResponse;
import com.stockflow.product.Product;
import com.stockflow.product.ProductRepository;
import com.stockflow.user.Role;
import com.stockflow.user.User;
import com.stockflow.user.UserRepository;
import com.stockflow.warehouse.Warehouse;
import com.stockflow.warehouse.WarehouseRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrderServiceTest {

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private WarehouseRepository warehouseRepository;

    @Mock
    private ProductRepository productRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private InventoryService inventoryService;

    @InjectMocks
    private OrderService orderService;

    private Warehouse warehouse;
    private Product product1;
    private Product product2;
    private User staffUser;

    @BeforeEach
    void setUp() {
        warehouse = new Warehouse("WH-01", "Central WH", "100 Ave");
        warehouse.setId(1L);

        product1 = new Product("SKU-101", "Keyboard", "Mechanical", "Accessories", new BigDecimal("100.00"));
        product1.setId(10L);

        product2 = new Product("SKU-102", "Mouse", "Ergonomic", "Accessories", new BigDecimal("50.00"));
        product2.setId(20L);

        staffUser = new User("staff1", "staff1@example.com", "pass", Role.ROLE_STAFF, "John", "Staff");
        staffUser.setId(5L);
    }

    @Test
    @DisplayName("createOrder: creates order in CREATED state and reserves inventory")
    void createOrder_success() {
        CreateOrderRequest request = new CreateOrderRequest(
                "Alice Smith",
                "alice@example.com",
                1L,
                "Urgent delivery",
                List.of(
                        new OrderItemRequest(10L, 2),
                        new OrderItemRequest(20L, 1)
                )
        );

        when(warehouseRepository.findById(1L)).thenReturn(Optional.of(warehouse));
        when(productRepository.findById(10L)).thenReturn(Optional.of(product1));
        when(productRepository.findById(20L)).thenReturn(Optional.of(product2));
        doNothing().when(inventoryService).reserveStock(anyLong(), anyLong(), anyInt(), anyString(), anyString());

        when(orderRepository.save(any(Order.class))).thenAnswer(invocation -> {
            Order o = invocation.getArgument(0);
            o.setId(99L);
            return o;
        });

        OrderResponse response = orderService.createOrder(request);

        assertThat(response).isNotNull();
        assertThat(response.getStatus()).isEqualTo(OrderStatus.CREATED);
        assertThat(response.getCustomerName()).isEqualTo("Alice Smith");
        assertThat(response.getItems()).hasSize(2);
        // (2 * 100) + (1 * 50) = 250.00
        assertThat(response.getTotalAmount()).isEqualByComparingTo("250.00");

        verify(inventoryService).reserveStock(1L, 10L, 2, response.getOrderNumber(), "system");
        verify(inventoryService).reserveStock(1L, 20L, 1, response.getOrderNumber(), "system");
    }

    @Test
    @DisplayName("createOrder: rejects order when product is inactive")
    void createOrder_inactiveProduct() {
        product1.setActive(false);

        CreateOrderRequest request = new CreateOrderRequest(
                "Alice Smith",
                "alice@example.com",
                1L,
                null,
                List.of(new OrderItemRequest(10L, 1))
        );

        when(warehouseRepository.findById(1L)).thenReturn(Optional.of(warehouse));
        when(productRepository.findById(10L)).thenReturn(Optional.of(product1));

        assertThatThrownBy(() -> orderService.createOrder(request))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("is currently inactive and cannot be ordered");
    }

    @Test
    @DisplayName("confirmOrder: transitions from CREATED to CONFIRMED")
    void confirmOrder_success() {
        Order order = new Order("ORD-001", "Alice", "alice@example.com", warehouse, null, staffUser);
        order.setId(1L);

        when(orderRepository.findByIdWithDetails(1L)).thenReturn(Optional.of(order));
        when(orderRepository.save(any(Order.class))).thenAnswer(invocation -> invocation.getArgument(0));

        OrderResponse response = orderService.confirmOrder(1L);

        assertThat(response.getStatus()).isEqualTo(OrderStatus.CONFIRMED);
    }

    @Test
    @DisplayName("confirmOrder: throws InvalidOrderStateException when order is not in CREATED status")
    void confirmOrder_invalidState() {
        Order order = new Order("ORD-001", "Alice", "alice@example.com", warehouse, null, staffUser);
        order.setId(1L);
        order.setStatus(OrderStatus.CONFIRMED);

        when(orderRepository.findByIdWithDetails(1L)).thenReturn(Optional.of(order));

        assertThatThrownBy(() -> orderService.confirmOrder(1L))
                .isInstanceOf(InvalidOrderStateException.class)
                .hasMessageContaining("Cannot perform 'CONFIRM' on order 'ORD-001' in current state 'CONFIRMED'");
    }

    @Test
    @DisplayName("completeOrder: transitions CONFIRMED to COMPLETED and deducts physical stock")
    void completeOrder_success() {
        Order order = new Order("ORD-001", "Alice", "alice@example.com", warehouse, null, staffUser);
        order.setId(1L);
        order.setStatus(OrderStatus.CONFIRMED);
        order.addItem(new OrderItem(product1, 2, product1.getPrice()));

        when(orderRepository.findByIdWithDetails(1L)).thenReturn(Optional.of(order));
        when(orderRepository.save(any(Order.class))).thenAnswer(invocation -> invocation.getArgument(0));
        doNothing().when(inventoryService).deductStock(anyLong(), anyLong(), anyInt(), anyString(), anyString());

        OrderResponse response = orderService.completeOrder(1L);

        assertThat(response.getStatus()).isEqualTo(OrderStatus.COMPLETED);
        verify(inventoryService).deductStock(1L, 10L, 2, "ORD-001", "system");
    }

    @Test
    @DisplayName("cancelOrder: transitions to CANCELLED and releases reserved stock")
    void cancelOrder_success() {
        Order order = new Order("ORD-001", "Alice", "alice@example.com", warehouse, null, staffUser);
        order.setId(1L);
        order.setStatus(OrderStatus.CREATED);
        order.addItem(new OrderItem(product1, 3, product1.getPrice()));

        when(orderRepository.findByIdWithDetails(1L)).thenReturn(Optional.of(order));
        when(orderRepository.save(any(Order.class))).thenAnswer(invocation -> invocation.getArgument(0));
        doNothing().when(inventoryService).releaseStock(anyLong(), anyLong(), anyInt(), anyString(), anyString());

        OrderResponse response = orderService.cancelOrder(1L);

        assertThat(response.getStatus()).isEqualTo(OrderStatus.CANCELLED);
        verify(inventoryService).releaseStock(1L, 10L, 3, "ORD-001", "system");
    }

    @Test
    @DisplayName("cancelOrder: throws InvalidOrderStateException when order is already COMPLETED")
    void cancelOrder_alreadyCompleted() {
        Order order = new Order("ORD-001", "Alice", "alice@example.com", warehouse, null, staffUser);
        order.setId(1L);
        order.setStatus(OrderStatus.COMPLETED);

        when(orderRepository.findByIdWithDetails(1L)).thenReturn(Optional.of(order));

        assertThatThrownBy(() -> orderService.cancelOrder(1L))
                .isInstanceOf(InvalidOrderStateException.class)
                .hasMessageContaining("Cannot perform 'CANCEL' on order 'ORD-001' in current state 'COMPLETED'");
    }
}
