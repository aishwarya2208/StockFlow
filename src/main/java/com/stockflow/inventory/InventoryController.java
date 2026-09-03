package com.stockflow.inventory;

import com.stockflow.common.api.ApiResponse;
import com.stockflow.common.api.PageResponse;
import com.stockflow.inventory.dto.InventoryMovementResponse;
import com.stockflow.inventory.dto.InventoryResponse;
import com.stockflow.inventory.dto.LowStockReportResponse;
import com.stockflow.inventory.dto.StockAdjustmentRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/inventory")
@Tag(name = "Inventory", description = "Stock tracking, adjustments, low-stock reports, and movement audit history")
public class InventoryController {

    private final InventoryService inventoryService;

    public InventoryController(InventoryService inventoryService) {
        this.inventoryService = inventoryService;
    }

    @PostMapping("/adjust")
    @PreAuthorize("hasAnyRole('ADMIN', 'STAFF')")
    @Operation(summary = "Adjust inventory stock", description = "Performs inbound/outbound/manual stock adjustment and logs an audit movement")
    public ResponseEntity<ApiResponse<InventoryResponse>> adjustStock(@Valid @RequestBody StockAdjustmentRequest request) {
        InventoryResponse response = inventoryService.adjustStock(request);
        return ResponseEntity.ok(ApiResponse.ok("Stock adjusted successfully", response));
    }

    @GetMapping
    @Operation(summary = "Get inventory stock", description = "Retrieves paginated inventory levels, filterable by warehouse and product")
    public ResponseEntity<ApiResponse<PageResponse<InventoryResponse>>> getInventories(
            @RequestParam(required = false) Long warehouseId,
            @RequestParam(required = false) Long productId,
            @PageableDefault(size = 20, sort = "id", direction = Sort.Direction.ASC) Pageable pageable) {
        PageResponse<InventoryResponse> response = inventoryService.getInventories(warehouseId, productId, pageable);
        return ResponseEntity.ok(ApiResponse.ok(response));
    }

    @GetMapping("/low-stock")
    @Operation(summary = "Get low-stock report", description = "Returns items whose available stock is at or below the low-stock threshold")
    public ResponseEntity<ApiResponse<PageResponse<LowStockReportResponse>>> getLowStockReport(
            @RequestParam(required = false) Long warehouseId,
            @PageableDefault(size = 20) Pageable pageable) {
        PageResponse<LowStockReportResponse> response = inventoryService.getLowStockReport(warehouseId, pageable);
        return ResponseEntity.ok(ApiResponse.ok(response));
    }

    @GetMapping("/movements")
    @Operation(summary = "Get inventory audit movements", description = "Retrieves an immutable audit log of stock movements filtered by warehouse, product, and movement type")
    public ResponseEntity<ApiResponse<PageResponse<InventoryMovementResponse>>> getMovements(
            @RequestParam(required = false) Long warehouseId,
            @RequestParam(required = false) Long productId,
            @RequestParam(required = false) MovementType movementType,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        PageResponse<InventoryMovementResponse> response = inventoryService.getMovements(warehouseId, productId, movementType, pageable);
        return ResponseEntity.ok(ApiResponse.ok(response));
    }
}
