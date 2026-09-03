package com.stockflow.warehouse;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.stockflow.TestAuthHelper;
import com.stockflow.warehouse.dto.WarehouseCreateRequest;
import com.stockflow.warehouse.dto.WarehouseUpdateRequest;
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

import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class WarehouseControllerIntegrationTest {

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
        adminToken = authHelper.createAdminToken("admin_wh_user");
        staffToken = authHelper.createStaffToken("staff_wh_user");
    }

    @Test
    @DisplayName("POST /api/v1/warehouses: Admin can create warehouse")
    void createWarehouse_adminSuccess() throws Exception {
        WarehouseCreateRequest request = new WarehouseCreateRequest(
                "WH-TEXAS-01",
                "Texas Central Hub",
                "123 Logistics Way, Dallas, TX"
        );

        mockMvc.perform(post("/api/v1/warehouses")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.data.code", is("WH-TEXAS-01")))
                .andExpect(jsonPath("$.data.name", is("Texas Central Hub")));
    }

    @Test
    @DisplayName("POST /api/v1/warehouses: Staff cannot create warehouse (403 Forbidden)")
    void createWarehouse_staffForbidden() throws Exception {
        WarehouseCreateRequest request = new WarehouseCreateRequest(
                "WH-STAFF-FORBIDDEN",
                "Staff WH",
                "Address"
        );

        mockMvc.perform(post("/api/v1/warehouses")
                        .header("Authorization", "Bearer " + staffToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("POST /api/v1/warehouses: Rejects duplicate warehouse code (409 Conflict)")
    void createWarehouse_duplicateCode() throws Exception {
        WarehouseCreateRequest request = new WarehouseCreateRequest(
                "WH-DUP-01",
                "Warehouse One",
                "Address 1"
        );
        mockMvc.perform(post("/api/v1/warehouses")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/v1/warehouses")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status", is(409)));
    }

    @Test
    @DisplayName("GET /api/v1/warehouses: Authenticated users can list warehouses")
    void getWarehouses_success() throws Exception {
        mockMvc.perform(get("/api/v1/warehouses")
                        .header("Authorization", "Bearer " + staffToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.data.content", notNullValue()));
    }
}
