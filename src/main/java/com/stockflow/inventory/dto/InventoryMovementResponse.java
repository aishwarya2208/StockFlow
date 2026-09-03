package com.stockflow.inventory.dto;

import com.stockflow.inventory.InventoryMovement;
import com.stockflow.inventory.MovementType;

import java.time.Instant;

public class InventoryMovementResponse {

    private Long id;
    private Long productId;
    private String productSku;
    private String productName;
    private Long warehouseId;
    private String warehouseCode;
    private String warehouseName;
    private MovementType movementType;
    private int quantityChange;
    private int quantityOnHandAfter;
    private int reservedQuantityAfter;
    private String referenceType;
    private String referenceId;
    private String notes;
    private String createdBy;
    private Instant createdAt;

    public InventoryMovementResponse() {
    }

    public static InventoryMovementResponse fromEntity(InventoryMovement movement) {
        InventoryMovementResponse response = new InventoryMovementResponse();
        response.setId(movement.getId());
        response.setProductId(movement.getProduct().getId());
        response.setProductSku(movement.getProduct().getSku());
        response.setProductName(movement.getProduct().getName());
        response.setWarehouseId(movement.getWarehouse().getId());
        response.setWarehouseCode(movement.getWarehouse().getCode());
        response.setWarehouseName(movement.getWarehouse().getName());
        response.setMovementType(movement.getMovementType());
        response.setQuantityChange(movement.getQuantityChange());
        response.setQuantityOnHandAfter(movement.getQuantityOnHandAfter());
        response.setReservedQuantityAfter(movement.getReservedQuantityAfter());
        response.setReferenceType(movement.getReferenceType());
        response.setReferenceId(movement.getReferenceId());
        response.setNotes(movement.getNotes());
        response.setCreatedBy(movement.getCreatedBy());
        response.setCreatedAt(movement.getCreatedAt());
        return response;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getProductId() {
        return productId;
    }

    public void setProductId(Long productId) {
        this.productId = productId;
    }

    public String getProductSku() {
        return productSku;
    }

    public void setProductSku(String productSku) {
        this.productSku = productSku;
    }

    public String getProductName() {
        return productName;
    }

    public void setProductName(String productName) {
        this.productName = productName;
    }

    public Long getWarehouseId() {
        return warehouseId;
    }

    public void setWarehouseId(Long warehouseId) {
        this.warehouseId = warehouseId;
    }

    public String getWarehouseCode() {
        return warehouseCode;
    }

    public void setWarehouseCode(String warehouseCode) {
        this.warehouseCode = warehouseCode;
    }

    public String getWarehouseName() {
        return warehouseName;
    }

    public void setWarehouseName(String warehouseName) {
        this.warehouseName = warehouseName;
    }

    public MovementType getMovementType() {
        return movementType;
    }

    public void setMovementType(MovementType movementType) {
        this.movementType = movementType;
    }

    public int getQuantityChange() {
        return quantityChange;
    }

    public void setQuantityChange(int quantityChange) {
        this.quantityChange = quantityChange;
    }

    public int getQuantityOnHandAfter() {
        return quantityOnHandAfter;
    }

    public void setQuantityOnHandAfter(int quantityOnHandAfter) {
        this.quantityOnHandAfter = quantityOnHandAfter;
    }

    public int getReservedQuantityAfter() {
        return reservedQuantityAfter;
    }

    public void setReservedQuantityAfter(int reservedQuantityAfter) {
        this.reservedQuantityAfter = reservedQuantityAfter;
    }

    public String getReferenceType() {
        return referenceType;
    }

    public void setReferenceType(String referenceType) {
        this.referenceType = referenceType;
    }

    public String getReferenceId() {
        return referenceId;
    }

    public void setReferenceId(String referenceId) {
        this.referenceId = referenceId;
    }

    public String getNotes() {
        return notes;
    }

    public void setNotes(String notes) {
        this.notes = notes;
    }

    public String getCreatedBy() {
        return createdBy;
    }

    public void setCreatedBy(String createdBy) {
        this.createdBy = createdBy;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}
