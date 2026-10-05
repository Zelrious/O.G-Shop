package com.oldbutgold.shop.modules.catalog.application;

import com.oldbutgold.shop.modules.catalog.api.CatalogDtos;
import com.oldbutgold.shop.modules.catalog.infrastructure.persistence.CategoryEntity;
import com.oldbutgold.shop.modules.catalog.infrastructure.persistence.CategoryRepository;
import com.oldbutgold.shop.modules.catalog.infrastructure.persistence.ProductEntity;
import com.oldbutgold.shop.modules.catalog.infrastructure.persistence.ProductMediaRepository;
import com.oldbutgold.shop.modules.catalog.infrastructure.persistence.ProductRepository;
import com.oldbutgold.shop.modules.identity.application.IdentityCatalogFacade;
import com.oldbutgold.shop.modules.catalog.infrastructure.persistence.ProductModerationDecisionEntity;
import com.oldbutgold.shop.modules.catalog.infrastructure.persistence.ProductModerationDecisionRepository;
import com.oldbutgold.shop.modules.platform.application.PlatformAuditFacade;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SellerProductServiceTest {
    private ProductRepository productRepository;
    private CategoryRepository categoryRepository;
    private ProductMediaRepository productMediaRepository;
    private IdentityCatalogFacade identityCatalogFacade;
    private MediaStorageService mediaStorageService;
    private ProductModerationDecisionRepository productModerationDecisionRepository;
    private PlatformAuditFacade platformAuditFacade;
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
        mediaStorageService = mock(MediaStorageService.class);
        productModerationDecisionRepository = mock(ProductModerationDecisionRepository.class);
        platformAuditFacade = mock(PlatformAuditFacade.class);
        fixedInstant = Instant.parse("2026-09-20T10:00:00Z");
        clock = Clock.fixed(fixedInstant, ZoneOffset.UTC);

        sellerProductService = new SellerProductService(
                productRepository, categoryRepository, productMediaRepository,
                identityCatalogFacade, mediaStorageService,
                productModerationDecisionRepository, platformAuditFacade, clock
        );

        category = new CategoryEntity("Điện tử", "electronics", "Thiết bị điện tử", true, 1, fixedInstant);
        setField(category, "id", 10L);
        when(categoryRepository.findForProduct(any())).thenReturn(List.of(category));

        when(identityCatalogFacade.isSellerActive(100L)).thenReturn(true);
        when(identityCatalogFacade.isSellerActive(200L)).thenReturn(false);
        when(identityCatalogFacade.isModeratorActive(999L)).thenReturn(true);
    }

    @Test
    void moderationRejectsInactiveOrNonModeratorBeforeLoadingProduct() {
        assertThatThrownBy(() -> sellerProductService.approveProduct(123L, 1L, 0L, "unauthorized"))
                .isInstanceOf(org.springframework.security.access.AccessDeniedException.class);
        verify(productRepository, never()).findById(any());
        verify(productModerationDecisionRepository, never()).findByCommandKey(any());
    }

    @Test
    void createProduct_rejectsNonSeller() {
        CatalogDtos.CreateOrUpdateProductRequest request = new CatalogDtos.CreateOrUpdateProductRequest(
                10L, "MacBook Pro", "M1 16GB", BigDecimal.valueOf(18000000), "GOOD",
                "2 năm", null, null, null, "HCM", null, null
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
                "2 năm", null, null, null, "HCM", null, null
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
                "2 năm", "Vết xước nhẹ đáy", null, "Sạc 67W", "TP. Hồ Chí Minh", null, null
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
                null, null, null, null, null, null, 1L
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
                null, null, null, null, null, null, null
        );

        assertThatThrownBy(() -> sellerProductService.updateProduct(100L, 700L, request))
                .isInstanceOf(ProductStateConflictException.class);
    }

    @Test
    void publishProduct_blocksDraftAndRequiresSubmit() throws Exception {
        ProductEntity draft = new ProductEntity(
                100L, 10L, "MacBook Pro", "M1 16GB", BigDecimal.valueOf(18000000),
                "GOOD", null, null, null, null, null, fixedInstant
        );
        setField(draft, "id", 700L);

        when(productRepository.findByIdAndSellerIdAndDeletedAtIsNull(700L, 100L))
                .thenReturn(Optional.of(draft));

        assertThatThrownBy(() -> sellerProductService.publishProduct(100L, 700L))
                .isInstanceOf(ProductStateConflictException.class)
                .hasMessageContaining("gửi duyệt");
        verify(productRepository, never()).save(any());
    }

    @Test
    void publishProduct_transitionsHiddenToActive() throws Exception {
        ProductEntity hidden = new ProductEntity(
                100L, 10L, "MacBook Pro", "M1 16GB", BigDecimal.valueOf(18000000),
                "GOOD", null, null, null, null, null, fixedInstant
        );
        setField(hidden, "id", 700L);
        setField(hidden, "status", ProductEntity.STATUS_HIDDEN);

        ProductModerationDecisionEntity approvedDecision = new ProductModerationDecisionEntity(
                700L, 200L, ProductModerationDecisionEntity.DECISION_APPROVED,
                null, hidden.getContentRevision(), "ck-app-700", fixedInstant, hidden.getContentRevision()
        );
        when(productModerationDecisionRepository.findFirstByProductIdOrderByCreatedAtDescIdDesc(700L))
                .thenReturn(Optional.of(approvedDecision));

        when(productRepository.findByIdAndSellerIdAndDeletedAtIsNull(700L, 100L))
                .thenReturn(Optional.of(hidden));
        when(categoryRepository.findById(10L)).thenReturn(Optional.of(category));
        when(productRepository.save(any())).thenReturn(hidden);

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
        setField(active, "status", ProductEntity.STATUS_ACTIVE);

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

    @Test
    void createProduct_withBuyerEkycRequired() throws Exception {
        CatalogDtos.CreateOrUpdateProductRequest request = new CatalogDtos.CreateOrUpdateProductRequest(
                10L, "iPhone 15 Pro", "Chi tiết", BigDecimal.valueOf(22000000), "GOOD",
                "1 năm", null, null, null, "Hà Nội", true, null
        );

        when(categoryRepository.findByIdAndActiveTrue(10L)).thenReturn(Optional.of(category));
        when(productRepository.save(any())).thenAnswer(invocation -> {
            ProductEntity saved = invocation.getArgument(0);
            setField(saved, "id", 750L);
            return saved;
        });

        CatalogDtos.SellerProductDetailResponse response = sellerProductService.createProduct(100L, request);
        assertThat(response.requiresBuyerEkyc()).isTrue();
    }

    @Test
    void uploadMedia_rejectsExceedingFiveImages() throws Exception {
        ProductEntity product = new ProductEntity(
                100L, 10L, "iPhone", "Desc", BigDecimal.valueOf(10000000),
                "GOOD", null, null, null, null, null, fixedInstant
        );
        setField(product, "id", 700L);

        when(productRepository.findByIdAndSellerIdAndDeletedAtIsNull(700L, 100L)).thenReturn(Optional.of(product));
        when(productMediaRepository.countByProductIdAndMediaType(700L, "IMAGE")).thenReturn(5L);

        org.springframework.web.multipart.MultipartFile mockFile = mock(org.springframework.web.multipart.MultipartFile.class);

        assertThatThrownBy(() -> sellerProductService.uploadMedia(100L, 700L, mockFile, "IMAGE"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("tối đa 5 hình ảnh");
    }

    @Test
    void uploadMedia_rejectsExceedingOneVideo() throws Exception {
        ProductEntity product = new ProductEntity(
                100L, 10L, "iPhone", "Desc", BigDecimal.valueOf(10000000),
                "GOOD", null, null, null, null, null, fixedInstant
        );
        setField(product, "id", 700L);

        when(productRepository.findByIdAndSellerIdAndDeletedAtIsNull(700L, 100L)).thenReturn(Optional.of(product));
        when(productMediaRepository.countByProductIdAndMediaType(700L, "VIDEO")).thenReturn(1L);

        org.springframework.web.multipart.MultipartFile mockFile = mock(org.springframework.web.multipart.MultipartFile.class);

        assertThatThrownBy(() -> sellerProductService.uploadMedia(100L, 700L, mockFile, "VIDEO"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("tối đa 1 video");
    }

    @Test
    void submitProduct_rejectsMissingImageOrVideo() throws Exception {
        ProductEntity product = new ProductEntity(
                100L, 10L, "iPhone", "Desc", BigDecimal.valueOf(10000000),
                "GOOD", null, null, null, null, null, fixedInstant
        );
        setField(product, "id", 700L);

        when(productRepository.findByIdAndSellerIdAndDeletedAtIsNull(700L, 100L)).thenReturn(Optional.of(product));
        when(categoryRepository.findById(10L)).thenReturn(Optional.of(category));

        // 0 images, 1 video
        when(productMediaRepository.countByProductIdAndMediaType(700L, "IMAGE")).thenReturn(0L);
        when(productMediaRepository.countByProductIdAndMediaType(700L, "VIDEO")).thenReturn(1L);

        assertThatThrownBy(() -> sellerProductService.submitProduct(100L, 700L))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("ít nhất 1 hình ảnh");

        // 1 image, 0 videos
        when(productMediaRepository.countByProductIdAndMediaType(700L, "IMAGE")).thenReturn(1L);
        when(productMediaRepository.countByProductIdAndMediaType(700L, "VIDEO")).thenReturn(0L);

        assertThatThrownBy(() -> sellerProductService.submitProduct(100L, 700L))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("ít nhất 1 video");
    }

    @Test
    void submitProduct_transitionsToPendingWhenValid() throws Exception {
        ProductEntity product = new ProductEntity(
                100L, 10L, "iPhone", "Desc", BigDecimal.valueOf(10000000),
                "GOOD", null, null, null, null, null, fixedInstant
        );
        setField(product, "id", 700L);

        when(productRepository.findByIdAndSellerIdAndDeletedAtIsNull(700L, 100L)).thenReturn(Optional.of(product));
        when(categoryRepository.findById(10L)).thenReturn(Optional.of(category));
        when(productMediaRepository.countByProductIdAndMediaType(700L, "IMAGE")).thenReturn(2L);
        when(productMediaRepository.countByProductIdAndMediaType(700L, "VIDEO")).thenReturn(1L);
        when(productRepository.save(any())).thenReturn(product);

        CatalogDtos.SellerProductDetailResponse response = sellerProductService.submitProduct(100L, 700L);
        assertThat(response.status()).isEqualTo("PENDING");
    }

    @Test
    void publishProduct_rejectsNonHiddenStatus() throws Exception {
        ProductEntity draft = new ProductEntity(
                100L, 10L, "iPhone", "Desc", BigDecimal.valueOf(10000000),
                "GOOD", null, null, null, null, null, fixedInstant
        );
        setField(draft, "id", 700L);
        setField(draft, "status", ProductEntity.STATUS_DRAFT);
        when(productRepository.findByIdAndSellerIdAndDeletedAtIsNull(700L, 100L)).thenReturn(Optional.of(draft));

        assertThatThrownBy(() -> sellerProductService.publishProduct(100L, 700L))
                .isInstanceOf(ProductStateConflictException.class)
                .hasMessageContaining("HIDDEN");
    }

    @Test
    void publishProduct_rejectsHiddenWithoutModerationDecision() throws Exception {
        ProductEntity hidden = new ProductEntity(
                100L, 10L, "iPhone", "Desc", BigDecimal.valueOf(10000000),
                "GOOD", null, null, null, null, null, fixedInstant
        );
        setField(hidden, "id", 700L);
        setField(hidden, "status", ProductEntity.STATUS_HIDDEN);
        when(productRepository.findByIdAndSellerIdAndDeletedAtIsNull(700L, 100L)).thenReturn(Optional.of(hidden));
        when(productModerationDecisionRepository.findFirstByProductIdOrderByCreatedAtDescIdDesc(700L))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> sellerProductService.publishProduct(100L, 700L))
                .isInstanceOf(ProductStateConflictException.class)
                .hasMessageContaining("chưa có quyết định kiểm duyệt");
    }

    @Test
    void publishProduct_rejectsHiddenWhenLatestDecisionNotApproved() throws Exception {
        ProductEntity hidden = new ProductEntity(
                100L, 10L, "iPhone", "Desc", BigDecimal.valueOf(10000000),
                "GOOD", null, null, null, null, null, fixedInstant
        );
        setField(hidden, "id", 700L);
        setField(hidden, "status", ProductEntity.STATUS_HIDDEN);
        when(productRepository.findByIdAndSellerIdAndDeletedAtIsNull(700L, 100L)).thenReturn(Optional.of(hidden));

        ProductModerationDecisionEntity rejectedDecision = new ProductModerationDecisionEntity(
                700L, 999L, "REJECTED", "Không đạt chuẩn", 1L, "key-rej", fixedInstant
        );
        when(productModerationDecisionRepository.findFirstByProductIdOrderByCreatedAtDescIdDesc(700L))
                .thenReturn(Optional.of(rejectedDecision));

        assertThatThrownBy(() -> sellerProductService.publishProduct(100L, 700L))
                .isInstanceOf(ProductStateConflictException.class)
                .hasMessageContaining("không phải là APPROVED");
    }

    @Test
    void publishProduct_rejectsHiddenWhenContentRevisionMismatch() throws Exception {
        ProductEntity hidden = new ProductEntity(
                100L, 10L, "iPhone", "Desc", BigDecimal.valueOf(10000000),
                "GOOD", null, null, null, null, null, fixedInstant
        );
        setField(hidden, "id", 700L);
        setField(hidden, "status", ProductEntity.STATUS_HIDDEN);
        setField(hidden, "contentRevision", 2L); // Content modified after approval
        when(productRepository.findByIdAndSellerIdAndDeletedAtIsNull(700L, 100L)).thenReturn(Optional.of(hidden));

        ProductModerationDecisionEntity approvedDecision = new ProductModerationDecisionEntity(
                700L, 999L, "APPROVED", null, 1L, "key-app", fixedInstant, 1L // approved at v1
        );
        when(productModerationDecisionRepository.findFirstByProductIdOrderByCreatedAtDescIdDesc(700L))
                .thenReturn(Optional.of(approvedDecision));

        assertThatThrownBy(() -> sellerProductService.publishProduct(100L, 700L))
                .isInstanceOf(ProductStateConflictException.class)
                .hasMessageContaining("Nội dung hoặc media của tin đăng đã thay đổi");
    }

    @Test
    void publishProduct_transitionsHiddenToActive_whenApprovedAndRevisionMatches() throws Exception {
        ProductEntity hidden = new ProductEntity(
                100L, 10L, "iPhone", "Desc", BigDecimal.valueOf(10000000),
                "GOOD", null, null, null, null, null, fixedInstant
        );
        setField(hidden, "id", 700L);
        setField(hidden, "status", ProductEntity.STATUS_HIDDEN);
        setField(hidden, "contentRevision", 2L);
        when(productRepository.findByIdAndSellerIdAndDeletedAtIsNull(700L, 100L)).thenReturn(Optional.of(hidden));
        when(categoryRepository.findForProduct(hidden)).thenReturn(List.of(category));
        when(productRepository.save(any())).thenReturn(hidden);

        ProductModerationDecisionEntity approvedDecision = new ProductModerationDecisionEntity(
                700L, 999L, "APPROVED", null, 2L, "key-app", fixedInstant, 2L // matches v2
        );
        when(productModerationDecisionRepository.findFirstByProductIdOrderByCreatedAtDescIdDesc(700L))
                .thenReturn(Optional.of(approvedDecision));

        CatalogDtos.SellerProductDetailResponse res = sellerProductService.publishProduct(100L, 700L);
        assertThat(res.status()).isEqualTo("ACTIVE");
    }

    @Test
    void approveProduct_transitionsPendingToActive_persistsDecisionAndAudit() throws Exception {
        ProductEntity pending = new ProductEntity(
                100L, 10L, "iPhone", "Desc", BigDecimal.valueOf(10000000),
                "GOOD", null, null, null, null, null, fixedInstant
        );
        setField(pending, "id", 700L);
        setField(pending, "status", ProductEntity.STATUS_PENDING);
        setField(pending, "contentRevision", 2L);
        setField(pending, "version", 2L);

        when(productRepository.findById(700L)).thenReturn(Optional.of(pending));
        when(productMediaRepository.countByProductIdAndMediaType(700L, "IMAGE")).thenReturn(2L);
        when(productMediaRepository.countByProductIdAndMediaType(700L, "VIDEO")).thenReturn(1L);
        when(productRepository.save(any())).thenReturn(pending);

        CatalogDtos.ActionResponse res = sellerProductService.approveProduct(999L, 700L, 2L, "cmd-approve-1");
        assertThat(res.status()).isEqualTo("ACTIVE");
        assertThat(res.message()).contains("Phê duyệt tin đăng thành công");

        verify(productModerationDecisionRepository).save(any(ProductModerationDecisionEntity.class));
        verify(platformAuditFacade).recordAudit(
                eq(999L), eq("APPROVE_PRODUCT"), eq("PRODUCT"), eq(700L),
                eq(Map.of("status", "PENDING", "version", 2L, "contentRevision", 2L)),
                any(), eq(null), eq(null)
        );
    }

    @Test
    void approveProduct_rejectsMissingMedia() throws Exception {
        ProductEntity pending = new ProductEntity(
                100L, 10L, "iPhone", "Desc", BigDecimal.valueOf(10000000),
                "GOOD", null, null, null, null, null, fixedInstant
        );
        setField(pending, "id", 700L);
        setField(pending, "status", ProductEntity.STATUS_PENDING);
        setField(pending, "contentRevision", 1L);
        setField(pending, "version", 1L);

        when(productRepository.findById(700L)).thenReturn(Optional.of(pending));
        when(productMediaRepository.countByProductIdAndMediaType(700L, "IMAGE")).thenReturn(1L);
        when(productMediaRepository.countByProductIdAndMediaType(700L, "VIDEO")).thenReturn(0L);

        assertThatThrownBy(() -> sellerProductService.approveProduct(999L, 700L, 1L, "cmd-approve-1"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("tối thiểu 1 ảnh và 1 video");
        verify(productRepository, never()).save(any());
    }

    @Test
    void approveProduct_rejectsNonPendingStatus() throws Exception {
        ProductEntity draft = new ProductEntity(
                100L, 10L, "iPhone", "Desc", BigDecimal.valueOf(10000000),
                "GOOD", null, null, null, null, null, fixedInstant
        );
        setField(draft, "id", 700L);
        setField(draft, "status", ProductEntity.STATUS_DRAFT);
        setField(draft, "contentRevision", 1L);
        setField(draft, "version", 1L);

        when(productRepository.findById(700L)).thenReturn(Optional.of(draft));

        assertThatThrownBy(() -> sellerProductService.approveProduct(999L, 700L, 1L, "cmd-approve-1"))
                .isInstanceOf(ProductStateConflictException.class)
                .hasMessageContaining("PENDING");
    }

    @Test
    void approveProduct_rejectsReservedAndSoldStatus() throws Exception {
        ProductEntity reserved = new ProductEntity(
                100L, 10L, "iPhone", "Desc", BigDecimal.valueOf(10000000),
                "GOOD", null, null, null, null, null, fixedInstant
        );
        setField(reserved, "id", 700L);
        setField(reserved, "status", ProductEntity.STATUS_RESERVED);
        setField(reserved, "contentRevision", 1L);
        setField(reserved, "version", 1L);
        when(productRepository.findById(700L)).thenReturn(Optional.of(reserved));

        assertThatThrownBy(() -> sellerProductService.approveProduct(999L, 700L, 1L, "cmd-approve-1"))
                .isInstanceOf(ProductStateConflictException.class)
                .hasMessageContaining("PENDING");

        ProductEntity sold = new ProductEntity(
                100L, 10L, "iPhone", "Desc", BigDecimal.valueOf(10000000),
                "GOOD", null, null, null, null, null, fixedInstant
        );
        setField(sold, "id", 701L);
        setField(sold, "status", ProductEntity.STATUS_SOLD);
        setField(sold, "contentRevision", 1L);
        setField(sold, "version", 1L);
        when(productRepository.findById(701L)).thenReturn(Optional.of(sold));

        assertThatThrownBy(() -> sellerProductService.approveProduct(999L, 701L, 1L, "cmd-approve-1"))
                .isInstanceOf(ProductStateConflictException.class)
                .hasMessageContaining("PENDING");
    }

    @Test
    void approveProduct_bubblesException_whenAuditFails() throws Exception {
        ProductEntity pending = new ProductEntity(
                100L, 10L, "iPhone", "Desc", BigDecimal.valueOf(10000000),
                "GOOD", null, null, null, null, null, fixedInstant
        );
        setField(pending, "id", 700L);
        setField(pending, "status", ProductEntity.STATUS_PENDING);
        setField(pending, "contentRevision", 1L);
        setField(pending, "version", 1L);

        when(productRepository.findById(700L)).thenReturn(Optional.of(pending));
        when(productRepository.save(any())).thenReturn(pending);
        when(productMediaRepository.countByProductIdAndMediaType(700L, "IMAGE")).thenReturn(1L);
        when(productMediaRepository.countByProductIdAndMediaType(700L, "VIDEO")).thenReturn(1L);

        doThrow(new RuntimeException("Audit service down"))
                .when(platformAuditFacade)
                .recordAudit(any(), any(), any(), any(), any(), any(), any(), any());

        assertThatThrownBy(() -> sellerProductService.approveProduct(999L, 700L, 1L, "cmd-approve-1"))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("Audit service down");
    }

    @Test
    void approveProduct_rejectsStaleVersion() throws Exception {
        ProductEntity pending = new ProductEntity(
                100L, 10L, "iPhone", "Desc", BigDecimal.valueOf(10000000),
                "GOOD", null, null, null, null, null, fixedInstant
        );
        setField(pending, "id", 700L);
        setField(pending, "status", ProductEntity.STATUS_PENDING);
        setField(pending, "contentRevision", 3L);
        setField(pending, "version", 3L);

        when(productRepository.findById(700L)).thenReturn(Optional.of(pending));

        assertThatThrownBy(() -> sellerProductService.approveProduct(999L, 700L, 2L, "cmd-approve-1"))
                .isInstanceOf(ProductVersionConflictException.class)
                .hasMessageContaining("Phiên bản tin đăng đã thay đổi");
    }

    @Test
    void approveProduct_handlesCommandKeyReplayAndConflict() throws Exception {
        ProductModerationDecisionEntity prevDecision = new ProductModerationDecisionEntity(
                700L, 999L, "APPROVED", null, 2L, "cmd-key-10", fixedInstant
        );
        when(productModerationDecisionRepository.findByCommandKey("cmd-key-10"))
                .thenReturn(Optional.of(prevDecision));

        CatalogDtos.ActionResponse replay = sellerProductService.approveProduct(999L, 700L, 2L, "cmd-key-10");
        assertThat(replay.status()).isEqualTo("ACTIVE");
        verify(productModerationDecisionRepository, never()).save(any());
        verify(platformAuditFacade, never()).recordAudit(any(), any(), any(), any(), any(), any(), any(), any());

        // Mismatched expectedVersion for same key -> conflict
        assertThatThrownBy(() -> sellerProductService.approveProduct(999L, 700L, 5L, "cmd-key-10"))
                .isInstanceOf(CommandKeyConflictException.class);
    }

    @Test
    void approveProduct_rejectsInvalidParameters() {
        assertThatThrownBy(() -> sellerProductService.approveProduct(0L, 700L, 1L, "key-1"))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("reviewerId");
        assertThatThrownBy(() -> sellerProductService.approveProduct(999L, 700L, null, "key-1"))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("expectedVersion");
        assertThatThrownBy(() -> sellerProductService.approveProduct(999L, 700L, -1L, "key-1"))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("expectedVersion");
        assertThatThrownBy(() -> sellerProductService.approveProduct(999L, 700L, 1L, null))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("commandKey");
        assertThatThrownBy(() -> sellerProductService.approveProduct(999L, 700L, 1L, "   "))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("commandKey");
        assertThatThrownBy(() -> sellerProductService.approveProduct(999L, 700L, 1L, "a".repeat(101)))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("commandKey");
    }

    @Test
    void rejectProduct_transitionsPendingToRejected_persistsDecisionAndAudit() throws Exception {
        ProductEntity pending = new ProductEntity(
                100L, 10L, "iPhone", "Desc", BigDecimal.valueOf(10000000),
                "GOOD", null, null, null, null, null, fixedInstant
        );
        setField(pending, "id", 701L);
        setField(pending, "status", ProductEntity.STATUS_PENDING);
        setField(pending, "contentRevision", 1L);
        setField(pending, "version", 1L);

        when(productRepository.findById(701L)).thenReturn(Optional.of(pending));
        when(productRepository.save(any())).thenReturn(pending);

        CatalogDtos.ActionResponse rejectRes = sellerProductService.rejectProduct(
                999L, 701L, "  Video bị rung mờ không rõ số seri  ", 1L, "cmd-reject-1"
        );
        assertThat(rejectRes.status()).isEqualTo("REJECTED");
        assertThat(rejectRes.message()).contains("Video bị rung mờ không rõ số seri");

        verify(productModerationDecisionRepository).save(any(ProductModerationDecisionEntity.class));
        verify(platformAuditFacade).recordAudit(
                eq(999L), eq("REJECT_PRODUCT"), eq("PRODUCT"), eq(701L),
                eq(Map.of("status", "PENDING", "version", 1L, "contentRevision", 1L)),
                eq(Map.of("status", "REJECTED", "reason", "Video bị rung mờ không rõ số seri", "version", 1L, "contentRevision", 1L)),
                eq(null), eq(null)
        );
    }

    @Test
    void rejectProduct_rejectsBlankOrTooLongReason() {
        assertThatThrownBy(() -> sellerProductService.rejectProduct(999L, 701L, "", 1L, "key-1"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> sellerProductService.rejectProduct(999L, 701L, "   ", 1L, "key-1"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> sellerProductService.rejectProduct(999L, 701L, null, 1L, "key-1"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> sellerProductService.rejectProduct(999L, 701L, "a".repeat(501), 1L, "key-1"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectProduct_rejectsReservedAndSoldStatus() throws Exception {
        ProductEntity reserved = new ProductEntity(
                100L, 10L, "iPhone", "Desc", BigDecimal.valueOf(10000000),
                "GOOD", null, null, null, null, null, fixedInstant
        );
        setField(reserved, "id", 700L);
        setField(reserved, "status", ProductEntity.STATUS_RESERVED);
        setField(reserved, "contentRevision", 1L);
        setField(reserved, "version", 1L);
        when(productRepository.findById(700L)).thenReturn(Optional.of(reserved));

        assertThatThrownBy(() -> sellerProductService.rejectProduct(999L, 700L, "Lý do từ chối", 1L, "key-1"))
                .isInstanceOf(ProductStateConflictException.class)
                .hasMessageContaining("PENDING");

        ProductEntity sold = new ProductEntity(
                100L, 10L, "iPhone", "Desc", BigDecimal.valueOf(10000000),
                "GOOD", null, null, null, null, null, fixedInstant
        );
        setField(sold, "id", 701L);
        setField(sold, "status", ProductEntity.STATUS_SOLD);
        setField(sold, "contentRevision", 1L);
        setField(sold, "version", 1L);
        when(productRepository.findById(701L)).thenReturn(Optional.of(sold));

        assertThatThrownBy(() -> sellerProductService.rejectProduct(999L, 701L, "Lý do từ chối", 1L, "key-1"))
                .isInstanceOf(ProductStateConflictException.class)
                .hasMessageContaining("PENDING");
    }

    @Test
    void rejectProduct_bubblesException_whenDecisionRepositoryFails() throws Exception {
        ProductEntity pending = new ProductEntity(
                100L, 10L, "iPhone", "Desc", BigDecimal.valueOf(10000000),
                "GOOD", null, null, null, null, null, fixedInstant
        );
        setField(pending, "id", 701L);
        setField(pending, "status", ProductEntity.STATUS_PENDING);
        setField(pending, "contentRevision", 1L);
        setField(pending, "version", 1L);

        when(productRepository.findById(701L)).thenReturn(Optional.of(pending));
        when(productRepository.save(any())).thenReturn(pending);

        doThrow(new RuntimeException("DB insert decision failed"))
                .when(productModerationDecisionRepository)
                .save(any());

        assertThatThrownBy(() -> sellerProductService.rejectProduct(999L, 701L, "Video không rõ nét", 1L, "key-1"))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("DB insert decision failed");
    }

    @Test
    void rejectProduct_rejectsInvalidParameters() {
        assertThatThrownBy(() -> sellerProductService.rejectProduct(0L, 701L, "Reason", 1L, "key-1"))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("reviewerId");
        assertThatThrownBy(() -> sellerProductService.rejectProduct(999L, 701L, "Reason", null, "key-1"))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("expectedVersion");
        assertThatThrownBy(() -> sellerProductService.rejectProduct(999L, 701L, "Reason", -1L, "key-1"))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("expectedVersion");
        assertThatThrownBy(() -> sellerProductService.rejectProduct(999L, 701L, "Reason", 1L, null))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("commandKey");
        assertThatThrownBy(() -> sellerProductService.rejectProduct(999L, 701L, "Reason", 1L, "   "))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("commandKey");
        assertThatThrownBy(() -> sellerProductService.rejectProduct(999L, 701L, "Reason", 1L, "a".repeat(101)))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("commandKey");
    }

    @Test
    void getSellerProduct_attachesRejectionReason() throws Exception {
        ProductEntity rejected = new ProductEntity(
                100L, 10L, "iPhone", "Desc", BigDecimal.valueOf(10000000),
                "GOOD", null, null, null, null, null, fixedInstant
        );
        setField(rejected, "id", 702L);
        setField(rejected, "status", ProductEntity.STATUS_REJECTED);

        when(productRepository.findByIdAndSellerIdAndDeletedAtIsNull(702L, 100L))
                .thenReturn(Optional.of(rejected));
        when(categoryRepository.findForProduct(rejected)).thenReturn(List.of(category));

        ProductModerationDecisionEntity decision = new ProductModerationDecisionEntity(
                702L, 999L, "REJECTED", "Hình ảnh không khớp với mô tả", 1L, null, fixedInstant
        );
        when(productModerationDecisionRepository.findFirstByProductIdOrderByCreatedAtDescIdDesc(702L))
                .thenReturn(Optional.of(decision));

        CatalogDtos.SellerProductDetailResponse detail = sellerProductService.getSellerProduct(100L, 702L);
        assertThat(detail.status()).isEqualTo("REJECTED");
        assertThat(detail.rejectionReason()).isEqualTo("Hình ảnh không khớp với mô tả");
        assertThat(detail.reviewedAt()).isEqualTo(fixedInstant);
    }

    private static void setField(Object target, String fieldName, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(target, value);
    }

    @Test
    void createProduct_savesEveryCategoryAndDeduplicatesIds() throws Exception {
        CategoryEntity other = new CategoryEntity("Khác", "other", "", true, 2, fixedInstant);
        setField(other, "id", 20L);
        when(categoryRepository.findByIdAndActiveTrue(10L)).thenReturn(Optional.of(category));
        when(categoryRepository.findByIdAndActiveTrue(20L)).thenReturn(Optional.of(other));
        when(productRepository.save(any())).thenAnswer(invocation -> {
            ProductEntity saved = invocation.getArgument(0);
            setField(saved, "id", 750L);
            assertThat(saved.getCategoryIds()).containsExactly(10L, 20L);
            return saved;
        });
        var request = new CatalogDtos.CreateOrUpdateProductRequest(null, "Thiết bị", "Mô tả", BigDecimal.valueOf(250000),
                "GOOD", null, null, null, null, null, false, null, List.of(10L, 20L, 10L));
        var response = sellerProductService.createProduct(100L, request);
        assertThat(response.categories()).extracting(CatalogDtos.CategoryResponse::categoryId).containsExactly(10L, 20L);
        assertThat(response.category().categoryId()).isEqualTo(10L);
        verify(categoryRepository).findByIdAndActiveTrue(10L);
    }

    @Test
    void createProduct_rejectsEmptySelectionAndInvalidAdditionalCategory() {
        var empty = new CatalogDtos.CreateOrUpdateProductRequest(10L, "Thiết bị", "Mô tả", BigDecimal.valueOf(250000),
                "GOOD", null, null, null, null, null, false, null, List.of());
        assertThatThrownBy(() -> sellerProductService.createProduct(100L, empty)).isInstanceOf(IllegalArgumentException.class);
        when(categoryRepository.findByIdAndActiveTrue(10L)).thenReturn(Optional.of(category));
        var invalid = new CatalogDtos.CreateOrUpdateProductRequest(null, "Thiết bị", "Mô tả", BigDecimal.valueOf(250000),
                "GOOD", null, null, null, null, null, false, null, List.of(10L, 99L));
        assertThatThrownBy(() -> sellerProductService.createProduct(100L, invalid)).isInstanceOf(CategoryNotFoundException.class);
        verify(productRepository, never()).save(any());
    }
}
