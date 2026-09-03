package com.stockflow.product;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.stockflow.TestAuthHelper;
import com.stockflow.product.dto.ProductCreateRequest;
import com.stockflow.product.dto.ProductUpdateRequest;
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

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class ProductControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private TestAuthHelper authHelper;

    private String adminToken;
    private String staffToken;

    @BeforeEach
    void setUp() {
        adminToken = authHelper.createAdminToken("admin_prod_user");
        staffToken = authHelper.createStaffToken("staff_prod_user");
    }

    @Test
    @DisplayName("POST /api/v1/products: Admin can create a product")
    void createProduct_adminSuccess() throws Exception {
        ProductCreateRequest request = new ProductCreateRequest(
                "SKU-INT-001",
                "Integration Monitor",
                "27 inch 1440p monitor",
                "Displays",
                new BigDecimal("399.99")
        );

        mockMvc.perform(post("/api/v1/products")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.data.sku", is("SKU-INT-001")))
                .andExpect(jsonPath("$.data.name", is("Integration Monitor")));
    }

    @Test
    @DisplayName("POST /api/v1/products: Staff cannot create a product (HTTP 403 Forbidden)")
    void createProduct_staffForbidden() throws Exception {
        ProductCreateRequest request = new ProductCreateRequest(
                "SKU-STAFF-001",
                "Forbidden Item",
                null,
                "Displays",
                new BigDecimal("99.99")
        );

        mockMvc.perform(post("/api/v1/products")
                        .header("Authorization", "Bearer " + staffToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("PUT /api/v1/products/{id}: Admin can update a product")
    void updateProduct_adminSuccess() throws Exception {
        ProductCreateRequest createReq = new ProductCreateRequest(
                "SKU-UPDATE-001",
                "Old Title",
                "Old Desc",
                "Displays",
                new BigDecimal("199.99")
        );
        MvcResult createResult = mockMvc.perform(post("/api/v1/products")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createReq)))
                .andExpect(status().isCreated())
                .andReturn();

        long productId = objectMapper.readTree(createResult.getResponse().getContentAsString())
                .get("data").get("id").asLong();

        ProductUpdateRequest updateReq = new ProductUpdateRequest(
                "New Title",
                "New Desc",
                "Hardware",
                new BigDecimal("249.99"),
                true
        );

        mockMvc.perform(put("/api/v1/products/" + productId)
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.data.name", is("New Title")))
                .andExpect(jsonPath("$.data.category", is("Hardware")));
    }

    @Test
    @DisplayName("GET /api/v1/products: search and filter by category and price")
    void searchProducts_filterSuccess() throws Exception {
        mockMvc.perform(post("/api/v1/products")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new ProductCreateRequest(
                                "SKU-SEARCH-01", "Gaming Headset", "Pro surround", "Audio", new BigDecimal("79.99")))))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/v1/products")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new ProductCreateRequest(
                                "SKU-SEARCH-02", "Studio Microphone", "Cardioid USB mic", "Audio", new BigDecimal("149.99")))))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/v1/products")
                        .header("Authorization", "Bearer " + staffToken)
                        .param("category", "Audio")
                        .param("maxPrice", "100.00"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.data.content", hasSize(1)))
                .andExpect(jsonPath("$.data.content[0].sku", is("SKU-SEARCH-01")));
    }

    @Test
    @DisplayName("DELETE /api/v1/products/{id}: Admin can soft-deactivate product")
    void deleteProduct_adminSuccess() throws Exception {
        MvcResult createResult = mockMvc.perform(post("/api/v1/products")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new ProductCreateRequest(
                                "SKU-DELETE-01", "Obsolete Item", null, "Misc", new BigDecimal("10.00")))))
                .andExpect(status().isCreated())
                .andReturn();

        long productId = objectMapper.readTree(createResult.getResponse().getContentAsString())
                .get("data").get("id").asLong();

        mockMvc.perform(delete("/api/v1/products/" + productId)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/products/" + productId)
                        .header("Authorization", "Bearer " + staffToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.active", is(false)));
    }
}
