package com.stockflow.inventory;

import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface InventoryRepository extends JpaRepository<Inventory, Long> {

    Optional<Inventory> findByWarehouseIdAndProductId(Long warehouseId, Long productId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT i FROM Inventory i WHERE i.warehouse.id = :warehouseId AND i.product.id = :productId")
    Optional<Inventory> findByWarehouseIdAndProductIdWithLock(
            @Param("warehouseId") Long warehouseId,
            @Param("productId") Long productId
    );

    @Query(value = "SELECT i FROM Inventory i JOIN FETCH i.product p JOIN FETCH i.warehouse w " +
                   "WHERE (:warehouseId IS NULL OR w.id = :warehouseId) " +
                   "AND (:productId IS NULL OR p.id = :productId)",
           countQuery = "SELECT COUNT(i) FROM Inventory i " +
                        "WHERE (:warehouseId IS NULL OR i.warehouse.id = :warehouseId) " +
                        "AND (:productId IS NULL OR i.product.id = :productId)")
    Page<Inventory> findWithFilters(
            @Param("warehouseId") Long warehouseId,
            @Param("productId") Long productId,
            Pageable pageable
    );

    @Query(value = "SELECT i FROM Inventory i JOIN FETCH i.product p JOIN FETCH i.warehouse w " +
                   "WHERE (:warehouseId IS NULL OR w.id = :warehouseId) " +
                   "AND (i.quantityOnHand - i.reservedQuantity) <= i.lowStockThreshold",
           countQuery = "SELECT COUNT(i) FROM Inventory i " +
                        "WHERE (:warehouseId IS NULL OR i.warehouse.id = :warehouseId) " +
                        "AND (i.quantityOnHand - i.reservedQuantity) <= i.lowStockThreshold")
    Page<Inventory> findLowStockInventories(
            @Param("warehouseId") Long warehouseId,
            Pageable pageable
    );
}
