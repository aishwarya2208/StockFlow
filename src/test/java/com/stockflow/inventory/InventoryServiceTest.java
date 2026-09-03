package com.stockflow.inventory;

import com.stockflow.common.exception.BusinessRuleException;
import com.stockflow.common.exception.InsufficientStockException;
import com.stockflow.inventory.dto.InventoryResponse;
import com.stockflow.inventory.dto.StockAdjustmentRequest;
import com.stockflow.product.Product;
import com.stockflow.product.ProductRepository;
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
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class InventoryServiceTest {

    @Mock
    private InventoryRepository inventoryRepository;

    @Mock
    private InventoryMovementRepository movementRepository;

    @Mock
    private WarehouseRepository warehouseRepository;

    @Mock
    private ProductRepository productRepository;

    @InjectMocks
    private InventoryService inventoryService;

    private Warehouse warehouse;
    private Product product;
    private Inventory inventory;

    @BeforeEach
    void setUp() {
        warehouse = new Warehouse("WH-01", "Main Warehouse", "123 Port St");
        warehouse.setId(1L);

        product = new Product("SKU-01", "Gaming Laptop", "16GB RAM", "Electronics", new BigDecimal("1200.00"));
        product.setId(2L);

        inventory = new Inventory(warehouse, product, 10, 5);
        inventory.setId(100L);
    }

    @Test
    @DisplayName("adjustStock: increases physical stock on INBOUND adjustment")
    void adjustStock_inboundSuccess() {
        StockAdjustmentRequest request = new StockAdjustmentRequest(
                1L, 2L, 15, MovementType.INBOUND, "Supplier delivery"
        );

        when(warehouseRepository.findById(1L)).thenReturn(Optional.of(warehouse));
        when(productRepository.findById(2L)).thenReturn(Optional.of(product));
        when(inventoryRepository.findByWarehouseIdAndProductIdWithLock(1L, 2L)).thenReturn(Optional.of(inventory));
        when(inventoryRepository.save(any(Inventory.class))).thenAnswer(invocation -> invocation.getArgument(0));

        InventoryResponse response = inventoryService.adjustStock(request);

        assertThat(response.getQuantityOnHand()).isEqualTo(25);
        assertThat(response.getReservedQuantity()).isEqualTo(0);
        assertThat(response.getAvailableQuantity()).isEqualTo(25);
        verify(movementRepository).save(any(InventoryMovement.class));
    }

    @Test
    @DisplayName("adjustStock: throws BusinessRuleException when reduction exceeds on-hand stock")
    void adjustStock_negativeStockRejected() {
        StockAdjustmentRequest request = new StockAdjustmentRequest(
                1L, 2L, -20, MovementType.ADJUSTMENT, "Damaged goods write-off"
        );

        when(warehouseRepository.findById(1L)).thenReturn(Optional.of(warehouse));
        when(productRepository.findById(2L)).thenReturn(Optional.of(product));
        when(inventoryRepository.findByWarehouseIdAndProductIdWithLock(1L, 2L)).thenReturn(Optional.of(inventory));

        assertThatThrownBy(() -> inventoryService.adjustStock(request))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("negative physical quantity");
    }

    @Test
    @DisplayName("reserveStock: successfully reserves stock when available")
    void reserveStock_success() {
        when(inventoryRepository.findByWarehouseIdAndProductIdWithLock(1L, 2L)).thenReturn(Optional.of(inventory));
        when(inventoryRepository.save(any(Inventory.class))).thenAnswer(invocation -> invocation.getArgument(0));

        inventoryService.reserveStock(1L, 2L, 4, "ORD-123", "admin");

        assertThat(inventory.getReservedQuantity()).isEqualTo(4);
        assertThat(inventory.getAvailableQuantity()).isEqualTo(6);
        verify(movementRepository).save(any(InventoryMovement.class));
    }

    @Test
    @DisplayName("reserveStock: throws InsufficientStockException when requested > available")
    void reserveStock_insufficientStock() {
        when(inventoryRepository.findByWarehouseIdAndProductIdWithLock(1L, 2L)).thenReturn(Optional.of(inventory));

        assertThatThrownBy(() -> inventoryService.reserveStock(1L, 2L, 15, "ORD-123", "admin"))
                .isInstanceOf(InsufficientStockException.class)
                .hasMessageContaining("Insufficient stock for product SKU 'SKU-01'");
    }

    @Test
    @DisplayName("releaseStock: successfully decreases reserved stock")
    void releaseStock_success() {
        inventory.setReservedQuantity(5);

        when(inventoryRepository.findByWarehouseIdAndProductIdWithLock(1L, 2L)).thenReturn(Optional.of(inventory));
        when(inventoryRepository.save(any(Inventory.class))).thenAnswer(invocation -> invocation.getArgument(0));

        inventoryService.releaseStock(1L, 2L, 3, "ORD-123", "admin");

        assertThat(inventory.getReservedQuantity()).isEqualTo(2);
        assertThat(inventory.getAvailableQuantity()).isEqualTo(8);
        verify(movementRepository).save(any(InventoryMovement.class));
    }

    @Test
    @DisplayName("deductStock: successfully decreases physical and reserved stock on fulfillment")
    void deductStock_success() {
        inventory.setReservedQuantity(5);

        when(inventoryRepository.findByWarehouseIdAndProductIdWithLock(1L, 2L)).thenReturn(Optional.of(inventory));
        when(inventoryRepository.save(any(Inventory.class))).thenAnswer(invocation -> invocation.getArgument(0));

        inventoryService.deductStock(1L, 2L, 5, "ORD-123", "admin");

        assertThat(inventory.getQuantityOnHand()).isEqualTo(5);
        assertThat(inventory.getReservedQuantity()).isEqualTo(0);
        assertThat(inventory.getAvailableQuantity()).isEqualTo(5);
        verify(movementRepository).save(any(InventoryMovement.class));
    }
}
