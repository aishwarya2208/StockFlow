package com.stockflow.common.exception;

public class InsufficientStockException extends RuntimeException {

    public InsufficientStockException(String message) {
        super(message);
    }

    public InsufficientStockException(String sku, String warehouseCode, int requested, int available) {
        super(String.format("Insufficient stock for product SKU '%s' in warehouse '%s'. Requested: %d, Available: %d",
                sku, warehouseCode, requested, available));
    }
}
