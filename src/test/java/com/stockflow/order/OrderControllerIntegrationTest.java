package com.stockflow.order;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.stockflow.TestAuthHelper;
import com.stockflow.inventory.Inventory;
import com.stockflow.inventory.InventoryRepository;
import com.stockflow.order.dto.CreateOrderRequest;
import com.stockflow.order.dto.OrderItemRequest;
import com.stockflow.product.Product;
import com.stockflow.product.ProductRepository;
import com.stockflow.warehouse.Warehouse;
import com.stockflow.warehouse.WarehouseRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class OrderControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private TestAuthHelper authHelper;

    @Autowired
    private WarehouseRepository warehouseRepository;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private InventoryRepository inventoryRepository;

    @Autowired
    private OrderRepository orderRepository;

    private String staffToken;
    private Warehouse warehouse;
    private Product product;
    private Inventory inventory;

    @BeforeEach
    void setUp() {
        staffToken = authHelper.createStaffToken("staff_order_user");

        warehouse = warehouseRepository.save(new Warehouse("WH-ORD-01", "Order WH", "123 Port St"));
        product = productRepository.save(new Product("SKU-ORD-01", "Order Item", "Description", "Gadgets", new BigDecimal("100.00")));
        inventory = inventoryRepository.save(new Inventory(warehouse, product, 10, 2));
    }

    @Test
    @DisplayName("Complete Order Lifecycle: CREATED -> CONFIRMED -> COMPLETED with inventory tracking")
    void orderLifecycle_success() throws Exception {
        // 1. Place order for 4 units
        CreateOrderRequest createRequest = new CreateOrderRequest(
                "Jane Doe",
                "jane@example.com",
                warehouse.getId(),
                "Door delivery",
                List.of(new OrderItemRequest(product.getId(), 4))
        );

        MvcResult createResult = mockMvc.perform(post("/api/v1/orders")
                        .header("Authorization", "Bearer " + staffToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createRequest)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.data.status", is("CREATED")))
                .andExpect(jsonPath("$.data.totalAmount", is(400.00)))
                .andReturn();

        long orderId = objectMapper.readTree(createResult.getResponse().getContentAsString())
                .get("data").get("id").asLong();

        // Verify stock was reserved: onHand = 10, reserved = 4, available = 6
        Inventory invAfterCreate = inventoryRepository.findByWarehouseIdAndProductId(warehouse.getId(), product.getId()).orElseThrow();
        assertThat(invAfterCreate.getQuantityOnHand()).isEqualTo(10);
        assertThat(invAfterCreate.getReservedQuantity()).isEqualTo(4);
        assertThat(invAfterCreate.getAvailableQuantity()).isEqualTo(6);

        // 2. Confirm order
        mockMvc.perform(post("/api/v1/orders/" + orderId + "/confirm")
                        .header("Authorization", "Bearer " + staffToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status", is("CONFIRMED")));

        // 3. Complete order (fulfill and deduct stock)
        mockMvc.perform(post("/api/v1/orders/" + orderId + "/complete")
                        .header("Authorization", "Bearer " + staffToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status", is("COMPLETED")));

        // Verify physical stock was deducted: onHand = 6, reserved = 0, available = 6
        Inventory invAfterComplete = inventoryRepository.findByWarehouseIdAndProductId(warehouse.getId(), product.getId()).orElseThrow();
        assertThat(invAfterComplete.getQuantityOnHand()).isEqualTo(6);
        assertThat(invAfterComplete.getReservedQuantity()).isEqualTo(0);
        assertThat(invAfterComplete.getAvailableQuantity()).isEqualTo(6);
    }

    @Test
    @DisplayName("Order Cancellation: CREATED -> CANCELLED releases reserved stock")
    void orderCancellation_success() throws Exception {
        CreateOrderRequest createRequest = new CreateOrderRequest(
                "Bob Builder",
                "bob@example.com",
                warehouse.getId(),
                null,
                List.of(new OrderItemRequest(product.getId(), 3))
        );

        MvcResult createResult = mockMvc.perform(post("/api/v1/orders")
                        .header("Authorization", "Bearer " + staffToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createRequest)))
                .andExpect(status().isCreated())
                .andReturn();

        long orderId = objectMapper.readTree(createResult.getResponse().getContentAsString())
                .get("data").get("id").asLong();

        // Cancel order
        mockMvc.perform(post("/api/v1/orders/" + orderId + "/cancel")
                        .header("Authorization", "Bearer " + staffToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status", is("CANCELLED")));

        // Verify reserved stock was restored: onHand = 10, reserved = 0, available = 10
        Inventory invAfterCancel = inventoryRepository.findByWarehouseIdAndProductId(warehouse.getId(), product.getId()).orElseThrow();
        assertThat(invAfterCancel.getQuantityOnHand()).isEqualTo(10);
        assertThat(invAfterCancel.getReservedQuantity()).isEqualTo(0);
        assertThat(invAfterCancel.getAvailableQuantity()).isEqualTo(10);
    }

    @Test
    @DisplayName("Create Order: rejects when inventory is insufficient (HTTP 422 Unprocessable Entity)")
    void createOrder_insufficientStock() throws Exception {
        CreateOrderRequest createRequest = new CreateOrderRequest(
                "Greedy Buyer",
                "greedy@example.com",
                warehouse.getId(),
                null,
                List.of(new OrderItemRequest(product.getId(), 99)) // Only 10 available!
        );

        mockMvc.perform(post("/api/v1/orders")
                        .header("Authorization", "Bearer " + staffToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createRequest)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.status", is(422)))
                .andExpect(jsonPath("$.message", notNullValue()));
    }

    @Test
    @DisplayName("Order Transition: rejects invalid transition with HTTP 422")
    void orderTransition_invalidTransition() throws Exception {
        CreateOrderRequest createRequest = new CreateOrderRequest(
                "Direct Completer",
                "direct@example.com",
                warehouse.getId(),
                null,
                List.of(new OrderItemRequest(product.getId(), 1))
        );

        MvcResult createResult = mockMvc.perform(post("/api/v1/orders")
                        .header("Authorization", "Bearer " + staffToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createRequest)))
                .andExpect(status().isCreated())
                .andReturn();

        long orderId = objectMapper.readTree(createResult.getResponse().getContentAsString())
                .get("data").get("id").asLong();

        // Cannot complete directly from CREATED without confirming first!
        mockMvc.perform(post("/api/v1/orders/" + orderId + "/complete")
                        .header("Authorization", "Bearer " + staffToken))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.status", is(422)))
                .andExpect(jsonPath("$.message", notNullValue()));
    }
}
