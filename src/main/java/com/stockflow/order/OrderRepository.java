package com.stockflow.order;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface OrderRepository extends JpaRepository<Order, Long> {

    Optional<Order> findByOrderNumber(String orderNumber);

    boolean existsByOrderNumber(String orderNumber);

    @Query(value = "SELECT o FROM Order o JOIN FETCH o.warehouse w LEFT JOIN FETCH o.createdByUser u " +
                   "WHERE (:warehouseId IS NULL OR w.id = :warehouseId) " +
                   "AND (:status IS NULL OR o.status = :status) " +
                   "AND (:customerEmail IS NULL OR LOWER(o.customerEmail) = LOWER(:customerEmail))",
           countQuery = "SELECT COUNT(o) FROM Order o " +
                        "WHERE (:warehouseId IS NULL OR o.warehouse.id = :warehouseId) " +
                        "AND (:status IS NULL OR o.status = :status) " +
                        "AND (:customerEmail IS NULL OR LOWER(o.customerEmail) = LOWER(:customerEmail))")
    Page<Order> findWithFilters(
            @Param("warehouseId") Long warehouseId,
            @Param("status") OrderStatus status,
            @Param("customerEmail") String customerEmail,
            Pageable pageable
    );

    @Query("SELECT o FROM Order o JOIN FETCH o.warehouse w LEFT JOIN FETCH o.createdByUser u LEFT JOIN FETCH o.items i JOIN FETCH i.product p WHERE o.id = :id")
    Optional<Order> findByIdWithDetails(@Param("id") Long id);

    @Query("SELECT o FROM Order o JOIN FETCH o.warehouse w LEFT JOIN FETCH o.createdByUser u LEFT JOIN FETCH o.items i JOIN FETCH i.product p WHERE o.orderNumber = :orderNumber")
    Optional<Order> findByOrderNumberWithDetails(@Param("orderNumber") String orderNumber);
}
