package com.stockflow.inventory;

import com.stockflow.common.entity.BaseEntity;
import com.stockflow.common.exception.BusinessRuleException;
import com.stockflow.common.exception.InsufficientStockException;
import com.stockflow.product.Product;
import com.stockflow.warehouse.Warehouse;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;

@Entity
@Table(
        name = "inventories",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_inventory_warehouse_product", columnNames = {"warehouse_id", "product_id"})
        },
        indexes = {
                @Index(name = "idx_inventories_wh_prod", columnList = "warehouse_id, product_id"),
                @Index(name = "idx_inventories_product", columnList = "product_id"),
                @Index(name = "idx_inventories_warehouse", columnList = "warehouse_id")
        }
)
public class Inventory extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "warehouse_id", nullable = false)
    private Warehouse warehouse;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @Column(name = "quantity_on_hand", nullable = false)
    private int quantityOnHand = 0;

    @Column(name = "reserved_quantity", nullable = false)
    private int reservedQuantity = 0;

    @Column(name = "low_stock_threshold", nullable = false)
    private int lowStockThreshold = 10;

    @Version
    @Column(name = "version", nullable = false)
    private Long version = 0L;

    public Inventory() {
    }

    public Inventory(Warehouse warehouse, Product product, int quantityOnHand, int lowStockThreshold) {
        this.warehouse = warehouse;
        this.product = product;
        this.quantityOnHand = quantityOnHand;
        this.reservedQuantity = 0;
        this.lowStockThreshold = lowStockThreshold;
    }

    public int getAvailableQuantity() {
        return quantityOnHand - reservedQuantity;
    }

    public boolean isLowStock() {
        return getAvailableQuantity() <= lowStockThreshold;
    }

    public void reserve(int quantity) {
        if (quantity <= 0) {
            throw new IllegalArgumentException("Reservation quantity must be positive");
        }
        int available = getAvailableQuantity();
        if (available < quantity) {
            throw new InsufficientStockException(
                    product.getSku(),
                    warehouse.getCode(),
                    quantity,
                    available
            );
        }
        this.reservedQuantity += quantity;
    }

    public void release(int quantity) {
        if (quantity <= 0) {
            throw new IllegalArgumentException("Release quantity must be positive");
        }
        if (this.reservedQuantity < quantity) {
            throw new BusinessRuleException(String.format(
                    "Cannot release %d units; only %d units are currently reserved for SKU '%s' in warehouse '%s'",
                    quantity, this.reservedQuantity, product.getSku(), warehouse.getCode()));
        }
        this.reservedQuantity -= quantity;
    }

    public void deductPhysical(int quantity) {
        if (quantity <= 0) {
            throw new IllegalArgumentException("Deduct quantity must be positive");
        }
        if (this.reservedQuantity < quantity) {
            throw new BusinessRuleException(String.format(
                    "Cannot deduct %d units; reserved quantity is %d for SKU '%s' in warehouse '%s'",
                    quantity, this.reservedQuantity, product.getSku(), warehouse.getCode()));
        }
        if (this.quantityOnHand < quantity) {
            throw new BusinessRuleException(String.format(
                    "Cannot deduct %d units; quantity on hand is %d for SKU '%s' in warehouse '%s'",
                    quantity, this.quantityOnHand, product.getSku(), warehouse.getCode()));
        }
        this.reservedQuantity -= quantity;
        this.quantityOnHand -= quantity;
    }

    public void adjust(int quantityChange) {
        int newOnHand = this.quantityOnHand + quantityChange;
        if (newOnHand < 0) {
            throw new BusinessRuleException(String.format(
                    "Stock adjustment would result in negative physical quantity (%d) for SKU '%s' in warehouse '%s'",
                    newOnHand, product.getSku(), warehouse.getCode()));
        }
        if (newOnHand < this.reservedQuantity) {
            throw new BusinessRuleException(String.format(
                    "Stock adjustment would result in physical quantity (%d) below current reserved quantity (%d) for SKU '%s' in warehouse '%s'",
                    newOnHand, this.reservedQuantity, product.getSku(), warehouse.getCode()));
        }
        this.quantityOnHand = newOnHand;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Warehouse getWarehouse() {
        return warehouse;
    }

    public void setWarehouse(Warehouse warehouse) {
        this.warehouse = warehouse;
    }

    public Product getProduct() {
        return product;
    }

    public void setProduct(Product product) {
        this.product = product;
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

    public int getLowStockThreshold() {
        return lowStockThreshold;
    }

    public void setLowStockThreshold(int lowStockThreshold) {
        this.lowStockThreshold = lowStockThreshold;
    }

    public Long getVersion() {
        return version;
    }

    public void setVersion(Long version) {
        this.version = version;
    }
}
