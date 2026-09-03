package com.stockflow.inventory;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.stockflow.TestAuthHelper;
import com.stockflow.inventory.dto.StockAdjustmentRequest;
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
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

import static org.hamcrest.Matchers.greaterThanOrEqualTo;
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
class InventoryControllerIntegrationTest {

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

    private String adminToken;
    private Warehouse warehouse;
    private Product product;

    @BeforeEach
    void setUp() {
        adminToken = authHelper.createAdminToken("admin_inv_user");

        warehouse = warehouseRepository.save(new Warehouse("WH-INV-TEST", "Inventory Test WH", "Some location"));
        product = productRepository.save(new Product("SKU-INV-TEST", "Item A", "Desc", "Category A", new BigDecimal("50.00")));
    }

    @Test
    @DisplayName("POST /api/v1/inventory/adjust: successfully adjust stock and logs movement")
    void adjustStock_success() throws Exception {
        StockAdjustmentRequest request = new StockAdjustmentRequest(
                warehouse.getId(),
                product.getId(),
                50,
                MovementType.INBOUND,
                "Receiving initial batch"
        );

        mockMvc.perform(post("/api/v1/inventory/adjust")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.data.quantityOnHand", is(50)))
                .andExpect(jsonPath("$.data.availableQuantity", is(50)));

        // Verify movement audit trail
        mockMvc.perform(get("/api/v1/inventory/movements")
                        .header("Authorization", "Bearer " + adminToken)
                        .param("warehouseId", warehouse.getId().toString())
                        .param("productId", product.getId().toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[0].movementType", is("INBOUND")))
                .andExpect(jsonPath("$.data.content[0].quantityChange", is(50)));
    }

    @Test
    @DisplayName("POST /api/v1/inventory/adjust: rejects negative physical stock with HTTP 400")
    void adjustStock_rejectNegative() throws Exception {
        StockAdjustmentRequest request = new StockAdjustmentRequest(
                warehouse.getId(),
                product.getId(),
                -10,
                MovementType.ADJUSTMENT,
                "Negative count on empty inventory"
        );

        mockMvc.perform(post("/api/v1/inventory/adjust")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status", is(400)))
                .andExpect(jsonPath("$.message", notNullValue()));
    }

    @Test
    @DisplayName("GET /api/v1/inventory/low-stock: returns low stock alert items")
    void getLowStockReport_success() throws Exception {
        // Create inventory with stock = 2 and threshold = 10 (Low stock condition!)
        inventoryRepository.save(new Inventory(warehouse, product, 2, 10));

        mockMvc.perform(get("/api/v1/inventory/low-stock")
                        .header("Authorization", "Bearer " + adminToken)
                        .param("warehouseId", warehouse.getId().toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.data.content", notNullValue()))
                .andExpect(jsonPath("$.data.content[0].deficit", greaterThanOrEqualTo(1)));
    }
}
