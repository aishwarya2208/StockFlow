package com.stockflow.order;

import com.stockflow.common.exception.InsufficientStockException;
import com.stockflow.inventory.Inventory;
import com.stockflow.inventory.InventoryMovementRepository;
import com.stockflow.inventory.InventoryRepository;
import com.stockflow.inventory.MovementType;
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
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
class ConcurrentInventoryReservationIntegrationTest {

    @Autowired
    private OrderService orderService;

    @Autowired
    private InventoryRepository inventoryRepository;

    @Autowired
    private InventoryMovementRepository movementRepository;

    @Autowired
    private WarehouseRepository warehouseRepository;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private UserRepository userRepository;

    private Warehouse warehouse;
    private Product product;
    private Inventory inventory;

    @BeforeEach
    void setUp() {
        // Clean database state
        orderRepository.deleteAll();
        movementRepository.deleteAll();
        inventoryRepository.deleteAll();
        productRepository.deleteAll();
        warehouseRepository.deleteAll();
        userRepository.deleteAll();

        // Create user
        userRepository.save(new User("concurrent_user", "conc@example.com", "pass", Role.ROLE_STAFF, "C", "U"));

        // Setup warehouse
        warehouse = warehouseRepository.save(new Warehouse("WH-CONC-01", "Concurrency Test WH", "123 Port St"));

        // Setup product
        product = productRepository.save(new Product(
                "SKU-CONC-LIMITED",
                "Limited Edition Smartphone",
                "Flagship phone with strict limited availability",
                "Smartphones",
                new BigDecimal("999.00")
        ));

        // Setup initial inventory: Exactly 5 units available!
        inventory = inventoryRepository.save(new Inventory(warehouse, product, 5, 2));
    }

    @AfterEach
    void tearDown() {
        orderRepository.deleteAll();
        movementRepository.deleteAll();
        inventoryRepository.deleteAll();
        productRepository.deleteAll();
        warehouseRepository.deleteAll();
        userRepository.deleteAll();
    }

    @Test
    @DisplayName("Concurrency Test: 10 threads attempt to reserve the last 5 units simultaneously; exactly 5 succeed and 5 fail with no overselling")
    void concurrentOrderReservation_preventsOverselling() throws InterruptedException {
        int numberOfThreads = 10;
        int stockAvailable = 5;
        int requestedQuantityPerOrder = 1;

        ExecutorService executorService = Executors.newFixedThreadPool(numberOfThreads);
        CountDownLatch readyLatch = new CountDownLatch(numberOfThreads);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(numberOfThreads);

        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger insufficientStockCount = new AtomicInteger(0);
        AtomicInteger unexpectedErrorCount = new AtomicInteger(0);

        for (int i = 0; i < numberOfThreads; i++) {
            final int threadIndex = i;
            executorService.submit(() -> {
                readyLatch.countDown();
                try {
                    // Wait for all threads to be ready to execute simultaneously
                    startLatch.await();

                    CreateOrderRequest request = new CreateOrderRequest(
                            "Customer " + threadIndex,
                            "customer" + threadIndex + "@example.com",
                            warehouse.getId(),
                            "Concurrent order attempt #" + threadIndex,
                            List.of(new OrderItemRequest(product.getId(), requestedQuantityPerOrder))
                    );

                    OrderResponse response = orderService.createOrder(request);
                    if (response != null && response.getStatus() == OrderStatus.CREATED) {
                        successCount.incrementAndGet();
                    }
                } catch (InsufficientStockException e) {
                    insufficientStockCount.incrementAndGet();
                } catch (Exception e) {
                    unexpectedErrorCount.incrementAndGet();
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        // Wait for all threads to reach start line
        readyLatch.await(5, TimeUnit.SECONDS);

        // Fire all threads at the exact same millisecond
        startLatch.countDown();

        // Wait for all threads to finish execution
        boolean completed = doneLatch.await(15, TimeUnit.SECONDS);
        executorService.shutdown();

        assertThat(completed).isTrue();

        // Verification of concurrency invariants
        assertThat(unexpectedErrorCount.get())
                .as("There should be zero unexpected errors or unhandled deadlock crashes")
                .isEqualTo(0);

        assertThat(successCount.get())
                .as("Exactly 5 orders should succeed because only 5 units were available")
                .isEqualTo(stockAvailable);

        assertThat(insufficientStockCount.get())
                .as("Exactly 5 orders should fail with InsufficientStockException")
                .isEqualTo(numberOfThreads - stockAvailable);

        // Verify database inventory state: zero overselling!
        Inventory finalInventory = inventoryRepository
                .findByWarehouseIdAndProductId(warehouse.getId(), product.getId())
                .orElseThrow();

        assertThat(finalInventory.getQuantityOnHand())
                .as("Physical stock on hand must remain 5")
                .isEqualTo(5);

        assertThat(finalInventory.getReservedQuantity())
                .as("Reserved quantity must equal total successfully reserved units (5)")
                .isEqualTo(5);

        assertThat(finalInventory.getAvailableQuantity())
                .as("Available quantity must now be exactly 0")
                .isEqualTo(0);

        // Verify order repository contains exactly 5 orders
        assertThat(orderRepository.count())
                .as("Exactly 5 orders should have been persisted in the database")
                .isEqualTo(5);

        // Verify inventory movements logged exactly 5 RESERVATION movements
        long reservationMovements = movementRepository.findAll().stream()
                .filter(m -> m.getMovementType() == MovementType.RESERVATION)
                .count();

        assertThat(reservationMovements)
                .as("Exactly 5 reservation audit movements should be recorded")
                .isEqualTo(5);
    }
}
