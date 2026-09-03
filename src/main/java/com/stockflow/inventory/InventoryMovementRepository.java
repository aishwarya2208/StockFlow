package com.stockflow.inventory;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface InventoryMovementRepository extends JpaRepository<InventoryMovement, Long> {

    @Query(value = "SELECT m FROM InventoryMovement m JOIN FETCH m.product p JOIN FETCH m.warehouse w " +
                   "WHERE (:warehouseId IS NULL OR w.id = :warehouseId) " +
                   "AND (:productId IS NULL OR p.id = :productId) " +
                   "AND (:movementType IS NULL OR m.movementType = :movementType)",
           countQuery = "SELECT COUNT(m) FROM InventoryMovement m " +
                        "WHERE (:warehouseId IS NULL OR m.warehouse.id = :warehouseId) " +
                        "AND (:productId IS NULL OR m.product.id = :productId) " +
                        "AND (:movementType IS NULL OR m.movementType = :movementType)")
    Page<InventoryMovement> findWithFilters(
            @Param("warehouseId") Long warehouseId,
            @Param("productId") Long productId,
            @Param("movementType") MovementType movementType,
            Pageable pageable
    );
}
