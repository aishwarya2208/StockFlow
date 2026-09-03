package com.stockflow.config;

import com.stockflow.inventory.Inventory;
import com.stockflow.inventory.InventoryMovement;
import com.stockflow.inventory.InventoryMovementRepository;
import com.stockflow.inventory.InventoryRepository;
import com.stockflow.inventory.MovementType;
import com.stockflow.product.Product;
import com.stockflow.product.ProductRepository;
import com.stockflow.user.Role;
import com.stockflow.user.User;
import com.stockflow.user.UserRepository;
import com.stockflow.warehouse.Warehouse;
import com.stockflow.warehouse.WarehouseRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

@Component
@Profile("!test")
public class DataInitializer implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(DataInitializer.class);

    private final UserRepository userRepository;
    private final WarehouseRepository warehouseRepository;
    private final ProductRepository productRepository;
    private final InventoryRepository inventoryRepository;
    private final InventoryMovementRepository movementRepository;
    private final PasswordEncoder passwordEncoder;

    public DataInitializer(
            UserRepository userRepository,
            WarehouseRepository warehouseRepository,
            ProductRepository productRepository,
            InventoryRepository inventoryRepository,
            InventoryMovementRepository movementRepository,
            PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.warehouseRepository = warehouseRepository;
        this.productRepository = productRepository;
        this.inventoryRepository = inventoryRepository;
        this.movementRepository = movementRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    @Transactional
    public void run(String... args) {
        if (userRepository.count() > 0) {
            log.info("Database already initialized, skipping seed data.");
            return;
        }

        log.info("Seeding initial reference data for StockFlow...");

        // 1. Create Default Users
        User admin = userRepository.save(new User(
                "admin",
                "admin@stockflow.internal",
                passwordEncoder.encode("Admin@12345"),
                Role.ROLE_ADMIN,
                "System",
                "Administrator"
        ));

        User staff = userRepository.save(new User(
                "staff",
                "staff@stockflow.internal",
                passwordEncoder.encode("Staff@12345"),
                Role.ROLE_STAFF,
                "Inventory",
                "Specialist"
        ));

        // 2. Create Warehouses
        Warehouse whEast = warehouseRepository.save(new Warehouse(
                "WH-EAST-01",
                "East Coast Distribution Center",
                "100 Industrial Parkway, Newark, NJ"
        ));

        Warehouse whWest = warehouseRepository.save(new Warehouse(
                "WH-WEST-01",
                "West Coast Fulfillment Hub",
                "500 Harbor Blvd, Long Beach, CA"
        ));

        // 3. Create Sample Products
        Product laptop = productRepository.save(new Product(
                "PROD-LAPTOP-01",
                "ThinkPad P16 Workstation",
                "High performance mobile workstation with Intel i9 and 64GB RAM",
                "Computers",
                new BigDecimal("2199.99")
        ));

        Product mouse = productRepository.save(new Product(
                "PROD-MOUSE-01",
                "Ergonomic Wireless Mouse",
                "Bluetooth 5.0 rechargeable ergonomic mouse with silent switches",
                "Accessories",
                new BigDecimal("49.99")
        ));

        Product keyboard = productRepository.save(new Product(
                "PROD-KEYBOARD-01",
                "Mechanical TKL Keyboard",
                "Cherry MX Brown switches with customizable RGB backlighting",
                "Accessories",
                new BigDecimal("119.99")
        ));

        Product monitor = productRepository.save(new Product(
                "PROD-MONITOR-01",
                "34-inch 4K Curved Monitor",
                "Ultra-wide 144Hz IPS display with USB-C power delivery",
                "Displays",
                new BigDecimal("599.99")
        ));

        Product chair = productRepository.save(new Product(
                "PROD-CHAIR-01",
                "Executive Ergonomic Office Chair",
                "Breathable mesh with 4D armrests and adjustable lumbar support",
                "Furniture",
                new BigDecimal("349.99")
        ));

        // 4. Initialize Stock & Audit Movements
        initStock(whEast, laptop, 25, 5, admin.getUsername());
        initStock(whEast, mouse, 150, 20, admin.getUsername());
        initStock(whEast, keyboard, 80, 15, admin.getUsername());
        initStock(whEast, monitor, 30, 10, admin.getUsername());
        initStock(whEast, chair, 15, 5, admin.getUsername());

        initStock(whWest, laptop, 10, 5, admin.getUsername());
        initStock(whWest, mouse, 5, 20, admin.getUsername()); // Low stock scenario!
        initStock(whWest, keyboard, 40, 10, admin.getUsername());
        initStock(whWest, monitor, 8, 10, admin.getUsername()); // Low stock scenario!

        log.info("StockFlow reference data seeding complete. Default accounts: admin/Admin@12345, staff/Staff@12345");
    }

    private void initStock(Warehouse wh, Product prod, int quantity, int threshold, String user) {
        Inventory inventory = inventoryRepository.save(new Inventory(wh, prod, quantity, threshold));
        movementRepository.save(new InventoryMovement(
                prod,
                wh,
                MovementType.INBOUND,
                quantity,
                quantity,
                0,
                "INITIAL_SEED",
                null,
                "Initial inventory intake",
                user
        ));
    }
}
