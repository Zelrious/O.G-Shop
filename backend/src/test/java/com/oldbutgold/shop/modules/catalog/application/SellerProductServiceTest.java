package com.oldbutgold.shop.modules.catalog.application;

import com.oldbutgold.shop.modules.catalog.api.CatalogDtos;
import com.oldbutgold.shop.modules.catalog.infrastructure.persistence.CategoryEntity;
import com.oldbutgold.shop.modules.catalog.infrastructure.persistence.CategoryRepository;
import com.oldbutgold.shop.modules.catalog.infrastructure.persistence.ProductEntity;
import com.oldbutgold.shop.modules.catalog.infrastructure.persistence.ProductMediaRepository;
import com.oldbutgold.shop.modules.catalog.infrastructure.persistence.ProductRepository;
import com.oldbutgold.shop.modules.identity.application.IdentityCatalogFacade;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SellerProductServiceTest {
    private ProductRepository productRepository;
    private CategoryRepository categoryRepository;
    private ProductMediaRepository productMediaRepository;
    private IdentityCatalogFacade identityCatalogFacade;
    private Clock clock;
    private SellerProductService sellerProductService;

    private CategoryEntity category;
    private Instant fixedInstant;

    @BeforeEach
    void setUp() throws Exception {
        productRepository = mock(ProductRepository.class);
        categoryRepository = mock(CategoryRepository.class);
        productMediaRepository = mock(ProductMediaRepository.class);
        identityCatalogFacade = mock(IdentityCatalogFacade.class);
        fixedInstant = Instant.parse("2026-09-20T10:00:00Z");
        clock = Clock.fixed(fixedInstant, ZoneOffset.UTC);

        sellerProductService = new SellerProductService(
                productRepository, categoryRepository, productMediaRepository, identityCatalogFacade, clock
        );

        category = new CategoryEntity("Điện tử", "electronics", "Thiết bị điện tử", true, 1, fixedInstant);
        setField(category, "id", 10L);

        when(identityCatalogFacade.isSellerActive(100L)).thenReturn(true);
        when(identityCatalogFacade.isSellerActive(200L)).thenReturn(false);
    }

    @Test
    void createProduct_rejectsNonSeller() {
        CatalogDtos.CreateOrUpdateProductRequest request = new CatalogDtos.CreateOrUpdateProductRequest(
                10L, "MacBook Pro", "M1 16GB", BigDecimal.valueOf(18000000), "GOOD",
                "2 năm", null, null, null, "HCM", null
        );

        assertThatThrownBy(() -> sellerProductService.createProduct(200L, request))
                .isInstanceOf(SellerRequiredException.class)
                .hasMessageContaining("Người bán");
    }

    @Test
    void createProduct_rejectsInactiveCategory() {
        when(categoryRepository.findByIdAndActiveTrue(99L)).thenReturn(Optional.empty());

        CatalogDtos.CreateOrUpdateProductRequest request = new CatalogDtos.CreateOrUpdateProductRequest(
                99L, "MacBook Pro", "M1 16GB", BigDecimal.valueOf(18000000), "GOOD",
                "2 năm", null, null, null, "HCM", null
        );

        assertThatThrownBy(() -> sellerProductService.createProduct(100L, request))
                .isInstanceOf(CategoryNotFoundException.class);
    }

    @Test
    void createProduct_createsProductInDraftStatus() {
        when(categoryRepository.findByIdAndActiveTrue(10L)).thenReturn(Optional.of(category));
        when(productRepository.save(any(ProductEntity.class))).thenAnswer(invocation -> {
            ProductEntity entity = invocation.getArgument(0);
            setField(entity, "id", 700L);
            return entity;
        });

        CatalogDtos.CreateOrUpdateProductRequest request = new CatalogDtos.CreateOrUpdateProductRequest(
                10L, "  MacBook Pro M1  ", "  M1 16GB 512GB  ", BigDecimal.valueOf(18000000), "GOOD",
                "2 năm", "Vết xước nhẹ đáy", null, "Sạc 67W", "TP. Hồ Chí Minh", null
        );

        CatalogDtos.SellerProductDetailResponse response = sellerProductService.createProduct(100L, request);

        assertThat(response.productId()).isEqualTo(700L);
        assertThat(response.status()).isEqualTo("DRAFT");
        assertThat(response.title()).isEqualTo("MacBook Pro M1");
        assertThat(response.currency()).isEqualTo("VND");
    }

    @Test
    void updateProduct_checksOptimisticLockVersion() throws Exception {
        ProductEntity existing = new ProductEntity(
                100L, 10L, "MacBook Pro", "M1 16GB", BigDecimal.valueOf(18000000),
                "GOOD", null, null, null, null, null, fixedInstant
        );
        setField(existing, "id", 700L);
        setField(existing, "version", 2L);

        when(productRepository.findByIdAndSellerIdAndDeletedAtIsNull(700L, 100L))
                .thenReturn(Optional.of(existing));
        when(categoryRepository.findByIdAndActiveTrue(10L)).thenReturn(Optional.of(category));

        CatalogDtos.CreateOrUpdateProductRequest requestWithOutdatedVersion = new CatalogDtos.CreateOrUpdateProductRequest(
                10L, "MacBook Pro updated", "M1 16GB updated", BigDecimal.valueOf(17500000), "GOOD",
                null, null, null, null, null, 1L
        );

        assertThatThrownBy(() -> sellerProductService.updateProduct(100L, 700L, requestWithOutdatedVersion))
                .isInstanceOf(ProductVersionConflictException.class);
    }

    @Test
    void updateProduct_rejectsUpdateWhenReservedOrSold() throws Exception {
        ProductEntity reservedProduct = new ProductEntity(
                100L, 10L, "MacBook Pro", "M1 16GB", BigDecimal.valueOf(18000000),
                "GOOD", null, null, null, null, null, fixedInstant
        );
        setField(reservedProduct, "id", 700L);
        setField(reservedProduct, "status", "RESERVED");

        when(productRepository.findByIdAndSellerIdAndDeletedAtIsNull(700L, 100L))
                .thenReturn(Optional.of(reservedProduct));
        when(categoryRepository.findByIdAndActiveTrue(10L)).thenReturn(Optional.of(category));

        CatalogDtos.CreateOrUpdateProductRequest request = new CatalogDtos.CreateOrUpdateProductRequest(
                10L, "MacBook Pro updated", "M1 16GB updated", BigDecimal.valueOf(17500000), "GOOD",
                null, null, null, null, null, null
        );

        assertThatThrownBy(() -> sellerProductService.updateProduct(100L, 700L, request))
                .isInstanceOf(ProductStateConflictException.class);
    }

    @Test
    void publishProduct_transitionsDraftToActive() throws Exception {
        ProductEntity draft = new ProductEntity(
                100L, 10L, "MacBook Pro", "M1 16GB", BigDecimal.valueOf(18000000),
                "GOOD", null, null, null, null, null, fixedInstant
        );
        setField(draft, "id", 700L);

        when(productRepository.findByIdAndSellerIdAndDeletedAtIsNull(700L, 100L))
                .thenReturn(Optional.of(draft));
        when(categoryRepository.findById(10L)).thenReturn(Optional.of(category));
        when(productRepository.save(any())).thenReturn(draft);

        CatalogDtos.SellerProductDetailResponse response = sellerProductService.publishProduct(100L, 700L);
        assertThat(response.status()).isEqualTo("ACTIVE");
    }

    @Test
    void hideProduct_transitionsActiveToHidden() throws Exception {
        ProductEntity active = new ProductEntity(
                100L, 10L, "MacBook Pro", "M1 16GB", BigDecimal.valueOf(18000000),
                "GOOD", null, null, null, null, null, fixedInstant
        );
        setField(active, "id", 700L);
        active.publish(fixedInstant);

        when(productRepository.findByIdAndSellerIdAndDeletedAtIsNull(700L, 100L))
                .thenReturn(Optional.of(active));
        when(categoryRepository.findById(10L)).thenReturn(Optional.of(category));
        when(productRepository.save(any())).thenReturn(active);

        CatalogDtos.SellerProductDetailResponse response = sellerProductService.hideProduct(100L, 700L);
        assertThat(response.status()).isEqualTo("HIDDEN");
    }

    @Test
    void hideProduct_rejectsHidingDraft() throws Exception {
        ProductEntity draft = new ProductEntity(
                100L, 10L, "MacBook Pro", "M1 16GB", BigDecimal.valueOf(18000000),
                "GOOD", null, null, null, null, null, fixedInstant
        );
        setField(draft, "id", 700L);

        when(productRepository.findByIdAndSellerIdAndDeletedAtIsNull(700L, 100L))
                .thenReturn(Optional.of(draft));
        when(categoryRepository.findById(10L)).thenReturn(Optional.of(category));

        assertThatThrownBy(() -> sellerProductService.hideProduct(100L, 700L))
                .isInstanceOf(ProductStateConflictException.class);
    }

    @Test
    void ownershipIsolation_returnsNotFoundForAnotherSellersProduct() {
        when(productRepository.findByIdAndSellerIdAndDeletedAtIsNull(700L, 100L))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> sellerProductService.getSellerProduct(100L, 700L))
                .isInstanceOf(ProductNotFoundException.class);
    }

    private static void setField(Object target, String fieldName, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(target, value);
    }
}
