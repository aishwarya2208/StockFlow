package com.stockflow.inventory.dto;

import com.stockflow.inventory.Inventory;

public class LowStockReportResponse {

    private Long inventoryId;
    private Long warehouseId;
    private String warehouseCode;
    private String warehouseName;
    private Long productId;
    private String productSku;
    private String productName;
    private String category;
    private int quantityOnHand;
    private int reservedQuantity;
    private int availableQuantity;
    private int lowStockThreshold;
    private int deficit;

    public LowStockReportResponse() {
    }

    public static LowStockReportResponse fromEntity(Inventory inventory) {
        LowStockReportResponse response = new LowStockReportResponse();
        response.setInventoryId(inventory.getId());
        response.setWarehouseId(inventory.getWarehouse().getId());
        response.setWarehouseCode(inventory.getWarehouse().getCode());
        response.setWarehouseName(inventory.getWarehouse().getName());
        response.setProductId(inventory.getProduct().getId());
        response.setProductSku(inventory.getProduct().getSku());
        response.setProductName(inventory.getProduct().getName());
        response.setCategory(inventory.getProduct().getCategory());
        response.setQuantityOnHand(inventory.getQuantityOnHand());
        response.setReservedQuantity(inventory.getReservedQuantity());
        response.setAvailableQuantity(inventory.getAvailableQuantity());
        response.setLowStockThreshold(inventory.getLowStockThreshold());
        response.setDeficit(Math.max(0, inventory.getLowStockThreshold() - inventory.getAvailableQuantity()));
        return response;
    }

    public Long getInventoryId() {
        return inventoryId;
    }

    public void setInventoryId(Long inventoryId) {
        this.inventoryId = inventoryId;
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

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public int getQuantityOnHand() {
        return quantityOnHand;
    }

    public void setQuantityOnHand(int quantityOnHand) {
        this.quantityOnHand = quantityOnHand;
    }

    public int getReservedQuantity() {
        return reservedQuantity;
    }

    public void setReservedQuantity(int reservedQuantity) {
        this.reservedQuantity = reservedQuantity;
    }

    public int getAvailableQuantity() {
        return availableQuantity;
    }

    public void setAvailableQuantity(int availableQuantity) {
        this.availableQuantity = availableQuantity;
    }

    public int getLowStockThreshold() {
        return lowStockThreshold;
    }

    public void setLowStockThreshold(int lowStockThreshold) {
        this.lowStockThreshold = lowStockThreshold;
    }

    public int getDeficit() {
        return deficit;
    }

    public void setDeficit(int deficit) {
        this.deficit = deficit;
    }
}
