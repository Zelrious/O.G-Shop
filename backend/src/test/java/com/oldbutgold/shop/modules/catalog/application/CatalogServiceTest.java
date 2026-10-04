package com.oldbutgold.shop.modules.catalog.application;

import com.oldbutgold.shop.modules.catalog.api.CatalogDtos;
import com.oldbutgold.shop.modules.catalog.infrastructure.persistence.CategoryEntity;
import com.oldbutgold.shop.modules.catalog.infrastructure.persistence.CategoryRepository;
import com.oldbutgold.shop.modules.catalog.infrastructure.persistence.ProductEntity;
import com.oldbutgold.shop.modules.catalog.infrastructure.persistence.ProductMediaEntity;
import com.oldbutgold.shop.modules.catalog.infrastructure.persistence.ProductMediaRepository;
import com.oldbutgold.shop.modules.catalog.infrastructure.persistence.ProductRepository;
import com.oldbutgold.shop.modules.identity.application.IdentityCatalogFacade;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;

import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CatalogServiceTest {
    private CategoryRepository categoryRepository;
    private ProductRepository productRepository;
    private ProductMediaRepository productMediaRepository;
    private IdentityCatalogFacade identityCatalogFacade;
    private CatalogService catalogService;

    private CategoryEntity category;
    private ProductEntity product;
    private Instant now;

    @BeforeEach
    void setUp() throws Exception {
        categoryRepository = mock(CategoryRepository.class);
        productRepository = mock(ProductRepository.class);
        productMediaRepository = mock(ProductMediaRepository.class);
        identityCatalogFacade = mock(IdentityCatalogFacade.class);

        catalogService = new CatalogService(categoryRepository, productRepository, productMediaRepository, identityCatalogFacade);

        now = Instant.parse("2026-09-20T12:00:00Z");

        category = new CategoryEntity("Điện tử", "electronics", "Thiết bị điện tử", true, 1, now);
        setField(category, "id", 10L);
        when(categoryRepository.findForProduct(any())).thenReturn(List.of(category));

        product = new ProductEntity(
                100L, 10L, "iPhone 13 128GB", "Máy đẹp pin 88%",
                BigDecimal.valueOf(9500000), "GOOD", "1 năm",
                null, null, "Hộp, sạc", "Hà Nội", now
        );
        setField(product, "id", 500L);
        setField(product, "status", ProductEntity.STATUS_ACTIVE);
    }

    @Test
    void getCategories_returnsActiveCategoriesOrdered() {
        when(categoryRepository.findByActiveTrueOrderByDisplayOrderAscCategoryNameAsc())
                .thenReturn(List.of(category));

        List<CatalogDtos.CategoryResponse> categories = catalogService.getCategories();
        assertThat(categories).hasSize(1);
        assertThat(categories.get(0).slug()).isEqualTo("electronics");
        assertThat(categories.get(0).categoryName()).isEqualTo("Điện tử");
    }

    @Test
    void searchProducts_validatesPriceRange() {
        assertThatThrownBy(() -> catalogService.searchProducts(
                null, null, null, BigDecimal.valueOf(500), BigDecimal.valueOf(100), 0, 10, "newest"
        )).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("minPrice");
    }

    @Test
    void searchProducts_validatesCondition() {
        assertThatThrownBy(() -> catalogService.searchProducts(
                null, null, "BRAND_NEW", null, null, 0, 10, "newest"
        )).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Tình trạng sản phẩm không hợp lệ");
    }

    @Test
    void searchProducts_validatesSort() {
        assertThatThrownBy(() -> catalogService.searchProducts(
                null, null, null, null, null, 0, 10, "sql_injection_attempt"
        )).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Tùy chọn sắp xếp không hợp lệ");
    }

    @Test
    void searchProducts_returnsPopulatedPageWithSellerSummaryAndThumbnail() throws Exception {
        when(productRepository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(product)));

        when(categoryRepository.findAllById(any())).thenReturn(List.of(category));
        when(identityCatalogFacade.getSellerSummaries(any())).thenReturn(Map.of(
                100L, new IdentityCatalogFacade.SellerPublicSummary(100L, "Người bán A", "Người bán MVP")
        ));

        ProductMediaEntity media = new ProductMediaEntity(500L, "IMAGE", "https://cdn.example.com/p1.jpg", 1, now);
        setField(media, "id", 1L);
        when(productMediaRepository.findByProductIdInOrderByDisplayOrderAscIdAsc(any()))
                .thenReturn(List.of(media));

        CatalogDtos.PageResponse<CatalogDtos.ProductSummaryResponse> response =
                catalogService.searchProducts("iPhone", 10L, "GOOD", BigDecimal.valueOf(1000000), BigDecimal.valueOf(15000000), 0, 12, "newest");

        assertThat(response.items()).hasSize(1);
        CatalogDtos.ProductSummaryResponse item = response.items().get(0);
        assertThat(item.productId()).isEqualTo(500L);
        assertThat(item.title()).isEqualTo("iPhone 13 128GB");
        assertThat(item.thumbnailUrl()).isEqualTo("https://cdn.example.com/p1.jpg");
        assertThat(item.seller().displayName()).isEqualTo("Người bán A");
        assertThat(item.seller().trustLabel()).isEqualTo("Người bán MVP");
        assertThat(item.category().slug()).isEqualTo("electronics");
    }

    @Test
    void getProductDetail_returnsFullProductWithMedia() throws Exception {
        when(productRepository.findByIdAndStatusAndDeletedAtIsNull(500L, "ACTIVE"))
                .thenReturn(Optional.of(product));
        when(categoryRepository.findById(10L)).thenReturn(Optional.of(category));
        when(identityCatalogFacade.getSellerSummary(100L)).thenReturn(Optional.of(
                new IdentityCatalogFacade.SellerPublicSummary(100L, "Người bán A", "Người bán MVP")
        ));

        ProductMediaEntity media = new ProductMediaEntity(500L, "IMAGE", "https://cdn.example.com/p1.jpg", 1, now);
        setField(media, "id", 1L);
        when(productMediaRepository.findByProductIdOrderByDisplayOrderAscIdAsc(500L))
                .thenReturn(List.of(media));

        CatalogDtos.ProductDetailResponse detail = catalogService.getProductDetail(500L);
        assertThat(detail.productId()).isEqualTo(500L);
        assertThat(detail.title()).isEqualTo("iPhone 13 128GB");
        assertThat(detail.description()).isEqualTo("Máy đẹp pin 88%");
        assertThat(detail.media()).hasSize(1);
        assertThat(detail.seller().displayName()).isEqualTo("Người bán A");
    }

    @Test
    void getProductDetail_throwsNotFoundWhenInactiveOrMissing() {
        when(productRepository.findByIdAndStatusAndDeletedAtIsNull(999L, "ACTIVE"))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> catalogService.getProductDetail(999L))
                .isInstanceOf(ProductNotFoundException.class);
    }

    private static void setField(Object target, String fieldName, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(target, value);
    }
}
