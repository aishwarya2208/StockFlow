package com.stockflow.inventory;

import com.stockflow.common.api.PageResponse;
import com.stockflow.common.exception.ResourceNotFoundException;
import com.stockflow.inventory.dto.InventoryMovementResponse;
import com.stockflow.inventory.dto.InventoryResponse;
import com.stockflow.inventory.dto.LowStockReportResponse;
import com.stockflow.inventory.dto.StockAdjustmentRequest;
import com.stockflow.product.Product;
import com.stockflow.product.ProductRepository;
import com.stockflow.security.SecurityUtils;
import com.stockflow.warehouse.Warehouse;
import com.stockflow.warehouse.WarehouseRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class InventoryService {

    private static final Logger log = LoggerFactory.getLogger(InventoryService.class);

    private final InventoryRepository inventoryRepository;
    private final InventoryMovementRepository movementRepository;
    private final WarehouseRepository warehouseRepository;
    private final ProductRepository productRepository;

    public InventoryService(
            InventoryRepository inventoryRepository,
            InventoryMovementRepository movementRepository,
            WarehouseRepository warehouseRepository,
            ProductRepository productRepository) {
        this.inventoryRepository = inventoryRepository;
        this.movementRepository = movementRepository;
        this.warehouseRepository = warehouseRepository;
        this.productRepository = productRepository;
    }

    @Transactional
    public InventoryResponse adjustStock(StockAdjustmentRequest request) {
        String performedBy = SecurityUtils.getCurrentUsername();
        Warehouse warehouse = warehouseRepository.findById(request.getWarehouseId())
                .orElseThrow(() -> new ResourceNotFoundException("Warehouse", "ID", request.getWarehouseId()));
        Product product = productRepository.findById(request.getProductId())
                .orElseThrow(() -> new ResourceNotFoundException("Product", "ID", request.getProductId()));

        // Acquire pessimistic row-level lock on inventory record
        Inventory inventory = inventoryRepository
                .findByWarehouseIdAndProductIdWithLock(warehouse.getId(), product.getId())
                .orElseGet(() -> {
                    // Create new inventory record if none exists for this warehouse/product pair
                    Inventory newInv = new Inventory(warehouse, product, 0, 10);
                    return inventoryRepository.save(newInv);
                });

        inventory.adjust(request.getQuantityChange());
        Inventory saved = inventoryRepository.save(inventory);

        InventoryMovement movement = new InventoryMovement(
                product,
                warehouse,
                request.getMovementType(),
                request.getQuantityChange(),
                saved.getQuantityOnHand(),
                saved.getReservedQuantity(),
                "MANUAL_ADJUSTMENT",
                null,
                request.getNotes(),
                performedBy
        );
        movementRepository.save(movement);

        log.info("Adjusted stock for SKU '{}' in WH '{}' by {}. New on-hand: {}, reserved: {}",
                product.getSku(), warehouse.getCode(), request.getQuantityChange(),
                saved.getQuantityOnHand(), saved.getReservedQuantity());

        return InventoryResponse.fromEntity(saved);
    }

    @Transactional
    public void reserveStock(Long warehouseId, Long productId, int quantity, String orderNumber, String performedBy) {
        Inventory inventory = inventoryRepository.findByWarehouseIdAndProductIdWithLock(warehouseId, productId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        String.format("No inventory found for Product ID %d in Warehouse ID %d", productId, warehouseId)));

        inventory.reserve(quantity);
        Inventory saved = inventoryRepository.save(inventory);

        InventoryMovement movement = new InventoryMovement(
                saved.getProduct(),
                saved.getWarehouse(),
                MovementType.RESERVATION,
                quantity,
                saved.getQuantityOnHand(),
                saved.getReservedQuantity(),
                "ORDER",
                orderNumber,
                "Stock reserved for order " + orderNumber,
                performedBy
        );
        movementRepository.save(movement);
    }

    @Transactional
    public void releaseStock(Long warehouseId, Long productId, int quantity, String orderNumber, String performedBy) {
        Inventory inventory = inventoryRepository.findByWarehouseIdAndProductIdWithLock(warehouseId, productId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        String.format("No inventory found for Product ID %d in Warehouse ID %d", productId, warehouseId)));

        inventory.release(quantity);
        Inventory saved = inventoryRepository.save(inventory);

        InventoryMovement movement = new InventoryMovement(
                saved.getProduct(),
                saved.getWarehouse(),
                MovementType.RELEASE,
                -quantity,
                saved.getQuantityOnHand(),
                saved.getReservedQuantity(),
                "ORDER",
                orderNumber,
                "Stock released from order " + orderNumber,
                performedBy
        );
        movementRepository.save(movement);
    }

    @Transactional
    public void deductStock(Long warehouseId, Long productId, int quantity, String orderNumber, String performedBy) {
        Inventory inventory = inventoryRepository.findByWarehouseIdAndProductIdWithLock(warehouseId, productId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        String.format("No inventory found for Product ID %d in Warehouse ID %d", productId, warehouseId)));

        inventory.deductPhysical(quantity);
        Inventory saved = inventoryRepository.save(inventory);

        InventoryMovement movement = new InventoryMovement(
                saved.getProduct(),
                saved.getWarehouse(),
                MovementType.OUTBOUND,
                -quantity,
                saved.getQuantityOnHand(),
                saved.getReservedQuantity(),
                "ORDER",
                orderNumber,
                "Stock fulfilled for order " + orderNumber,
                performedBy
        );
        movementRepository.save(movement);
    }

    @Transactional(readOnly = true)
    public PageResponse<InventoryResponse> getInventories(Long warehouseId, Long productId, Pageable pageable) {
        Page<Inventory> page = inventoryRepository.findWithFilters(warehouseId, productId, pageable);
        return PageResponse.of(page, page.getContent().stream().map(InventoryResponse::fromEntity).toList());
    }

    @Transactional(readOnly = true)
    public PageResponse<LowStockReportResponse> getLowStockReport(Long warehouseId, Pageable pageable) {
        Page<Inventory> page = inventoryRepository.findLowStockInventories(warehouseId, pageable);
        return PageResponse.of(page, page.getContent().stream().map(LowStockReportResponse::fromEntity).toList());
    }

    @Transactional(readOnly = true)
    public PageResponse<InventoryMovementResponse> getMovements(
            Long warehouseId, Long productId, MovementType movementType, Pageable pageable) {
        Page<InventoryMovement> page = movementRepository.findWithFilters(warehouseId, productId, movementType, pageable);
        return PageResponse.of(page, page.getContent().stream().map(InventoryMovementResponse::fromEntity).toList());
    }
}
