package com.stockflow.product;

import com.stockflow.common.api.PageResponse;
import com.stockflow.common.exception.DuplicateResourceException;
import com.stockflow.common.exception.ResourceNotFoundException;
import com.stockflow.product.dto.ProductCreateRequest;
import com.stockflow.product.dto.ProductResponse;
import com.stockflow.product.dto.ProductUpdateRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

@Service
public class ProductService {

    private static final Logger log = LoggerFactory.getLogger(ProductService.class);

    private final ProductRepository productRepository;

    public ProductService(ProductRepository productRepository) {
        this.productRepository = productRepository;
    }

    @Transactional
    public ProductResponse createProduct(ProductCreateRequest request) {
        if (productRepository.existsBySku(request.getSku())) {
            throw new DuplicateResourceException("Product", "SKU", request.getSku());
        }

        Product product = new Product(
                request.getSku().trim().toUpperCase(),
                request.getName().trim(),
                request.getDescription(),
                request.getCategory().trim(),
                request.getPrice()
        );

        Product savedProduct = productRepository.save(product);
        log.info("Created new product with SKU '{}' and ID {}", savedProduct.getSku(), savedProduct.getId());
        return ProductResponse.fromEntity(savedProduct);
    }

    @Transactional
    public ProductResponse updateProduct(Long id, ProductUpdateRequest request) {
        Product product = findEntityById(id);

        product.setName(request.getName().trim());
        product.setDescription(request.getDescription());
        product.setCategory(request.getCategory().trim());
        product.setPrice(request.getPrice());
        product.setActive(request.getActive());

        Product updatedProduct = productRepository.save(product);
        log.info("Updated product ID {} with SKU '{}'", updatedProduct.getId(), updatedProduct.getSku());
        return ProductResponse.fromEntity(updatedProduct);
    }

    @Transactional
    public void deleteProduct(Long id) {
        Product product = findEntityById(id);
        // Soft-deactivate to preserve historical integrity in inventory and order history
        product.setActive(false);
        productRepository.save(product);
        log.info("Deactivated product ID {} with SKU '{}'", product.getId(), product.getSku());
    }

    @Transactional(readOnly = true)
    public ProductResponse getProductById(Long id) {
        return ProductResponse.fromEntity(findEntityById(id));
    }

    @Transactional(readOnly = true)
    public ProductResponse getProductBySku(String sku) {
        Product product = productRepository.findBySku(sku.trim().toUpperCase())
                .orElseThrow(() -> new ResourceNotFoundException("Product", "SKU", sku));
        return ProductResponse.fromEntity(product);
    }

    @Transactional(readOnly = true)
    public PageResponse<ProductResponse> searchProducts(
            String search,
            String category,
            Boolean active,
            BigDecimal minPrice,
            BigDecimal maxPrice,
            Pageable pageable) {

        Specification<Product> spec = ProductSpecification.withFilters(search, category, active, minPrice, maxPrice);
        Page<Product> page = productRepository.findAll(spec, pageable);
        return PageResponse.of(page, page.getContent().stream().map(ProductResponse::fromEntity).toList());
    }

    @Transactional(readOnly = true)
    public Product findEntityById(Long id) {
        return productRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Product", "ID", id));
    }
}
