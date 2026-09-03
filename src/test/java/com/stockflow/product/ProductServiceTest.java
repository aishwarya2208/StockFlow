package com.stockflow.product;

import com.stockflow.common.exception.DuplicateResourceException;
import com.stockflow.common.exception.ResourceNotFoundException;
import com.stockflow.product.dto.ProductCreateRequest;
import com.stockflow.product.dto.ProductResponse;
import com.stockflow.product.dto.ProductUpdateRequest;
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
class ProductServiceTest {

    @Mock
    private ProductRepository productRepository;

    @InjectMocks
    private ProductService productService;

    private Product sampleProduct;

    @BeforeEach
    void setUp() {
        sampleProduct = new Product(
                "SKU-TEST-01",
                "Mechanical Keyboard",
                "TKL RGB Keyboard",
                "Accessories",
                new BigDecimal("99.99")
        );
        sampleProduct.setId(10L);
    }

    @Test
    @DisplayName("createProduct: successfully creates new product with valid SKU")
    void createProduct_success() {
        ProductCreateRequest request = new ProductCreateRequest(
                "SKU-TEST-01",
                "Mechanical Keyboard",
                "TKL RGB Keyboard",
                "Accessories",
                new BigDecimal("99.99")
        );

        when(productRepository.existsBySku("SKU-TEST-01")).thenReturn(false);
        when(productRepository.save(any(Product.class))).thenReturn(sampleProduct);

        ProductResponse response = productService.createProduct(request);

        assertThat(response).isNotNull();
        assertThat(response.getSku()).isEqualTo("SKU-TEST-01");
        assertThat(response.getName()).isEqualTo("Mechanical Keyboard");
        assertThat(response.getPrice()).isEqualByComparingTo("99.99");
        verify(productRepository).save(any(Product.class));
    }

    @Test
    @DisplayName("createProduct: throws DuplicateResourceException when SKU already exists")
    void createProduct_duplicateSku() {
        ProductCreateRequest request = new ProductCreateRequest(
                "SKU-TEST-01",
                "Another Keyboard",
                null,
                "Accessories",
                new BigDecimal("129.99")
        );

        when(productRepository.existsBySku("SKU-TEST-01")).thenReturn(true);

        assertThatThrownBy(() -> productService.createProduct(request))
                .isInstanceOf(DuplicateResourceException.class)
                .hasMessageContaining("Product already exists with SKU: 'SKU-TEST-01'");
    }

    @Test
    @DisplayName("updateProduct: updates existing product fields successfully")
    void updateProduct_success() {
        ProductUpdateRequest request = new ProductUpdateRequest(
                "Mechanical Keyboard v2",
                "Updated description",
                "Peripherals",
                new BigDecimal("109.99"),
                true
        );

        when(productRepository.findById(10L)).thenReturn(Optional.of(sampleProduct));
        when(productRepository.save(any(Product.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ProductResponse response = productService.updateProduct(10L, request);

        assertThat(response.getName()).isEqualTo("Mechanical Keyboard v2");
        assertThat(response.getCategory()).isEqualTo("Peripherals");
        assertThat(response.getPrice()).isEqualByComparingTo("109.99");
    }

    @Test
    @DisplayName("deleteProduct: soft deactivates product")
    void deleteProduct_softDeactivates() {
        when(productRepository.findById(10L)).thenReturn(Optional.of(sampleProduct));

        productService.deleteProduct(10L);

        assertThat(sampleProduct.isActive()).isFalse();
        verify(productRepository).save(sampleProduct);
    }

    @Test
    @DisplayName("getProductById: throws ResourceNotFoundException when product does not exist")
    void getProductById_notFound() {
        when(productRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> productService.getProductById(999L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Product not found with ID: '999'");
    }
}
