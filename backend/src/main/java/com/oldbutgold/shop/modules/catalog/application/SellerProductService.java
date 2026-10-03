package com.oldbutgold.shop.modules.catalog.application;

import com.oldbutgold.shop.modules.catalog.api.CatalogDtos;
import com.oldbutgold.shop.modules.catalog.infrastructure.persistence.CategoryEntity;
import com.oldbutgold.shop.modules.catalog.infrastructure.persistence.CategoryRepository;
import com.oldbutgold.shop.modules.catalog.infrastructure.persistence.ProductEntity;
import com.oldbutgold.shop.modules.catalog.infrastructure.persistence.ProductMediaEntity;
import com.oldbutgold.shop.modules.catalog.infrastructure.persistence.ProductMediaRepository;
import com.oldbutgold.shop.modules.catalog.infrastructure.persistence.ProductRepository;
import com.oldbutgold.shop.modules.identity.application.IdentityCatalogFacade;
import com.oldbutgold.shop.modules.catalog.infrastructure.persistence.ProductModerationDecisionEntity;
import com.oldbutgold.shop.modules.catalog.infrastructure.persistence.ProductModerationDecisionRepository;
import com.oldbutgold.shop.modules.platform.application.PlatformAuditFacade;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@Transactional
public class SellerProductService {
    private final ProductRepository productRepository;
    private final CategoryRepository categoryRepository;
    private final ProductMediaRepository productMediaRepository;
    private final IdentityCatalogFacade identityCatalogFacade;
    private final MediaStorageService mediaStorageService;
    private final ProductModerationDecisionRepository productModerationDecisionRepository;
    private final PlatformAuditFacade platformAuditFacade;
    private final Clock clock;

    public SellerProductService(ProductRepository productRepository,
                                CategoryRepository categoryRepository,
                                ProductMediaRepository productMediaRepository,
                                IdentityCatalogFacade identityCatalogFacade,
                                MediaStorageService mediaStorageService,
                                ProductModerationDecisionRepository productModerationDecisionRepository,
                                PlatformAuditFacade platformAuditFacade,
                                Clock clock) {
        this.productRepository = productRepository;
        this.categoryRepository = categoryRepository;
        this.productMediaRepository = productMediaRepository;
        this.identityCatalogFacade = identityCatalogFacade;
        this.mediaStorageService = mediaStorageService;
        this.productModerationDecisionRepository = productModerationDecisionRepository;
        this.platformAuditFacade = platformAuditFacade;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public CatalogDtos.PageResponse<CatalogDtos.SellerProductSummaryResponse> getSellerProducts(
            long sellerId, int page, int size
    ) {
        ensureSellerActive(sellerId);

        int clampedPage = Math.max(page, 0);
        int clampedSize = Math.min(Math.max(size, 1), 48);
        Pageable pageable = PageRequest.of(clampedPage, clampedSize, Sort.by(Sort.Direction.DESC, "createdAt").and(Sort.by(Sort.Direction.DESC, "id")));

        Page<ProductEntity> productPage = productRepository.findBySellerIdAndDeletedAtIsNull(sellerId, pageable);
        List<ProductEntity> products = productPage.getContent();

        if (products.isEmpty()) {
            return new CatalogDtos.PageResponse<>(
                    List.of(), productPage.getNumber(), productPage.getSize(),
                    productPage.getTotalElements(), productPage.getTotalPages(), productPage.hasNext()
            );
        }

        Set<Long> categoryIds = products.stream().flatMap(p -> p.getCategoryIds().stream()).collect(Collectors.toSet());
        Map<Long, CategoryEntity> categoryMap = categoryRepository.findAllById(categoryIds).stream()
                .collect(Collectors.toMap(CategoryEntity::getId, c -> c));

        Set<Long> productIds = products.stream().map(ProductEntity::getId).collect(Collectors.toSet());
        List<ProductMediaEntity> allMedia = productMediaRepository.findByProductIdInOrderByDisplayOrderAscIdAsc(productIds);
        Map<Long, String> thumbnailMap = allMedia.stream()
                .filter(m -> "IMAGE".equalsIgnoreCase(m.getMediaType()))
                .collect(Collectors.toMap(
                        ProductMediaEntity::getProductId,
                        ProductMediaEntity::getMediaUrl,
                        (first, replacement) -> first
                ));

        Map<Long, ProductModerationDecisionEntity> latestDecisionMap = productModerationDecisionRepository
                .findByProductIdInOrderByCreatedAtDesc(productIds)
                .stream()
                .collect(Collectors.toMap(
                        ProductModerationDecisionEntity::getProductId,
                        d -> d,
                        (first, replacement) -> first // Keep newest by created_at DESC
                ));

        List<CatalogDtos.SellerProductSummaryResponse> items = products.stream().map(p -> {
            String thumb = thumbnailMap.get(p.getId());
            ProductModerationDecisionEntity latest = latestDecisionMap.get(p.getId());
            String rejectionReason = (latest != null && ProductModerationDecisionEntity.DECISION_REJECTED.equals(latest.getDecision()))
                    ? latest.getReason() : null;
            Instant reviewedAt = latest != null ? latest.getCreatedAt() : null;
            return CatalogDtos.SellerProductSummaryResponse.from(
                    p, CategoryRepository.orderedForProduct(p, categoryMap), thumb, rejectionReason, reviewedAt
            );
        }).toList();

        return new CatalogDtos.PageResponse<>(
                items,
                productPage.getNumber(),
                productPage.getSize(),
                productPage.getTotalElements(),
                productPage.getTotalPages(),
                productPage.hasNext()
        );
    }

    @Transactional(readOnly = true)
    public CatalogDtos.SellerProductDetailResponse getSellerProduct(long sellerId, long productId) {
        ensureSellerActive(sellerId);
        ProductEntity product = productRepository.findByIdAndSellerIdAndDeletedAtIsNull(productId, sellerId)
                .orElseThrow(ProductNotFoundException::new);

        List<CategoryEntity> categories = categoryRepository.findForProduct(product);

        List<ProductMediaEntity> mediaList = productMediaRepository.findByProductIdOrderByDisplayOrderAscIdAsc(productId);

        Optional<ProductModerationDecisionEntity> latestDecision = productModerationDecisionRepository.findFirstByProductIdOrderByCreatedAtDesc(productId);
        String rejectionReason = latestDecision.filter(d -> ProductModerationDecisionEntity.DECISION_REJECTED.equals(d.getDecision()))
                .map(ProductModerationDecisionEntity::getReason)
                .orElse(null);
        Instant reviewedAt = latestDecision.map(ProductModerationDecisionEntity::getCreatedAt).orElse(null);

        return CatalogDtos.SellerProductDetailResponse.from(product, categories, mediaList, rejectionReason, reviewedAt);
    }

    public CatalogDtos.SellerProductDetailResponse createProduct(
            long sellerId, CatalogDtos.CreateOrUpdateProductRequest request
    ) {
        ensureSellerActive(sellerId);
        List<CategoryEntity> categories = resolveSelectedCategories(request);

        Instant now = clock.instant();
        boolean requiresBuyerEkyc = Boolean.TRUE.equals(request.requiresBuyerEkyc());
        ProductEntity product = new ProductEntity(
                sellerId,
                categories.getFirst().getId(),
                request.title().trim(),
                request.description().trim(),
                request.listedPrice(),
                request.condition().trim(),
                request.usageDuration() != null ? request.usageDuration().trim() : null,
                request.defects() != null ? request.defects().trim() : null,
                request.repairHistory() != null ? request.repairHistory().trim() : null,
                request.includedAccessories() != null ? request.includedAccessories().trim() : null,
                request.location() != null ? request.location().trim() : null,
                requiresBuyerEkyc,
                now
        );

        product.replaceCategories(categories.stream().map(CategoryEntity::getId).toList());
        ProductEntity saved = productRepository.save(product);
        productRepository.flush();
        return CatalogDtos.SellerProductDetailResponse.from(saved, categories, List.of());
    }

    public CatalogDtos.SellerProductDetailResponse updateProduct(
            long sellerId, long productId, CatalogDtos.CreateOrUpdateProductRequest request
    ) {
        ensureSellerActive(sellerId);
        ProductEntity product = productRepository.findByIdAndSellerIdAndDeletedAtIsNull(productId, sellerId)
                .orElseThrow(ProductNotFoundException::new);

        List<CategoryEntity> categories = resolveSelectedCategories(request);

        if (request.version() != null && !request.version().equals(product.getVersion())) {
            throw new ProductVersionConflictException();
        }

        Instant now = clock.instant();
        boolean requiresBuyerEkyc = request.requiresBuyerEkyc() != null
                ? request.requiresBuyerEkyc()
                : product.isRequiresBuyerEkyc();

        product.updateDetails(
                categories.getFirst().getId(),
                request.title().trim(),
                request.description().trim(),
                request.listedPrice(),
                request.condition().trim(),
                request.usageDuration() != null ? request.usageDuration().trim() : null,
                request.defects() != null ? request.defects().trim() : null,
                request.repairHistory() != null ? request.repairHistory().trim() : null,
                request.includedAccessories() != null ? request.includedAccessories().trim() : null,
                request.location() != null ? request.location().trim() : null,
                requiresBuyerEkyc,
                now
        );

        product.replaceCategories(categories.stream().map(CategoryEntity::getId).toList());
        ProductEntity saved = productRepository.save(product);
        productRepository.flush();
        List<ProductMediaEntity> mediaList = productMediaRepository.findByProductIdOrderByDisplayOrderAscIdAsc(productId);
        return CatalogDtos.SellerProductDetailResponse.from(saved, categories, mediaList);
    }

    public CatalogDtos.MediaUploadResponse uploadMedia(
            long sellerId, long productId, org.springframework.web.multipart.MultipartFile file, String mediaType
    ) {
        ensureSellerActive(sellerId);
        ProductEntity product = productRepository.findByIdAndSellerIdAndDeletedAtIsNull(productId, sellerId)
                .orElseThrow(ProductNotFoundException::new);

        String upperType = mediaType != null ? mediaType.toUpperCase().trim() : "IMAGE";
        if (!"IMAGE".equals(upperType) && !"VIDEO".equals(upperType)) {
            throw new IllegalArgumentException("Loại media chỉ chấp nhận IMAGE hoặc VIDEO.");
        }

        if ("IMAGE".equals(upperType)) {
            long imgCount = productMediaRepository.countByProductIdAndMediaType(productId, "IMAGE");
            if (imgCount >= 5) {
                throw new IllegalStateException("Mỗi sản phẩm chỉ được tải lên tối đa 5 hình ảnh (Rule V6).");
            }
        } else {
            long videoCount = productMediaRepository.countByProductIdAndMediaType(productId, "VIDEO");
            if (videoCount >= 1) {
                throw new IllegalStateException("Mỗi sản phẩm chỉ được tải lên tối đa 1 video cận cảnh (Rule V6).");
            }
        }

        MediaStorageService.StoredMedia stored = mediaStorageService.store(file, upperType);
        int nextOrder = (int) productMediaRepository.countByProductId(productId) + 1;

        ProductMediaEntity entity = new ProductMediaEntity(
                productId,
                upperType,
                stored.mediaUrl(),
                nextOrder,
                stored.thumbnailUrl(),
                stored.durationSeconds(),
                stored.fileSizeBytes(),
                stored.mimeType(),
                clock.instant()
        );

        ProductMediaEntity saved = productMediaRepository.save(entity);
        product.bumpContentRevision();
        product.touch(clock.instant());
        productRepository.save(product);
        return CatalogDtos.MediaUploadResponse.from(saved);
    }

    public void deleteMedia(long sellerId, long productId, long mediaId) {
        ensureSellerActive(sellerId);
        ProductEntity product = productRepository.findByIdAndSellerIdAndDeletedAtIsNull(productId, sellerId)
                .orElseThrow(ProductNotFoundException::new);

        ProductMediaEntity media = productMediaRepository.findByIdAndProductId(mediaId, productId)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy media ID: " + mediaId + " cho sản phẩm: " + productId));

        productMediaRepository.delete(media);
        product.bumpContentRevision();
        product.touch(clock.instant());
        productRepository.save(product);
    }

    public CatalogDtos.SellerProductDetailResponse submitProduct(long sellerId, long productId) {
        ensureSellerActive(sellerId);
        ProductEntity product = productRepository.findByIdAndSellerIdAndDeletedAtIsNull(productId, sellerId)
                .orElseThrow(ProductNotFoundException::new);

        List<CategoryEntity> categories = categoryRepository.findForProduct(product);

        long imgCount = productMediaRepository.countByProductIdAndMediaType(productId, "IMAGE");
        long videoCount = productMediaRepository.countByProductIdAndMediaType(productId, "VIDEO");

        if (imgCount < 1) {
            throw new IllegalStateException("Tin đăng phải có ít nhất 1 hình ảnh sản phẩm trước khi gửi duyệt.");
        }
        if (videoCount < 1) {
            throw new IllegalStateException("Tin đăng đồ cũ phải có ít nhất 1 video quay cận cảnh sản phẩm trước khi gửi duyệt.");
        }

        product.submitForReview(clock.instant());
        ProductEntity saved = productRepository.save(product);
        List<ProductMediaEntity> mediaList = productMediaRepository.findByProductIdOrderByDisplayOrderAscIdAsc(productId);
        return CatalogDtos.SellerProductDetailResponse.from(saved, categories, mediaList);
    }

    public CatalogDtos.SellerProductDetailResponse publishProduct(long sellerId, long productId) {
        ensureSellerActive(sellerId);
        ProductEntity product = productRepository.findByIdAndSellerIdAndDeletedAtIsNull(productId, sellerId)
                .orElseThrow(ProductNotFoundException::new);

        if (!ProductEntity.STATUS_HIDDEN.equals(product.getStatus())) {
            throw new ProductStateConflictException(
                    "Chỉ có thể đăng bán lại sản phẩm đang ở trạng thái HIDDEN. Trạng thái hiện tại: " + product.getStatus() +
                    ". Tin ở trạng thái DRAFT hoặc REJECTED cần được gửi duyệt (submit) để KTV kiểm tra trước khi công khai."
            );
        }

        ProductModerationDecisionEntity latestDecision = productModerationDecisionRepository
                .findFirstByProductIdOrderByCreatedAtDesc(productId)
                .orElseThrow(() -> new ProductStateConflictException(
                        "Tin đăng chưa có quyết định kiểm duyệt hợp lệ từ KTV. Vui lòng gửi duyệt lại trước khi đăng bán."
                ));

        if (!ProductModerationDecisionEntity.DECISION_APPROVED.equals(latestDecision.getDecision())) {
            throw new ProductStateConflictException(
                    "Tin đăng không ở trạng thái được phê duyệt kiểm duyệt. Quyết định gần nhất không phải là APPROVED."
            );
        }

        if (!latestDecision.getProductVersion().equals(product.getContentRevision())) {
            throw new ProductStateConflictException(
                    "Nội dung hoặc media của tin đăng đã thay đổi so với phiên bản được KTV phê duyệt (phiên bản hiện tại: v" +
                    product.getContentRevision() + ", phiên bản duyệt: v" + latestDecision.getProductVersion() +
                    "). Vui lòng gửi duyệt lại để KTV kiểm tra nội dung mới."
            );
        }

        List<CategoryEntity> categories = categoryRepository.findForProduct(product);

        product.publish(clock.instant());
        ProductEntity saved = productRepository.save(product);
        List<ProductMediaEntity> mediaList = productMediaRepository.findByProductIdOrderByDisplayOrderAscIdAsc(productId);
        return CatalogDtos.SellerProductDetailResponse.from(saved, categories, mediaList);
    }

    public CatalogDtos.SellerProductDetailResponse hideProduct(long sellerId, long productId) {
        ensureSellerActive(sellerId);
        ProductEntity product = productRepository.findByIdAndSellerIdAndDeletedAtIsNull(productId, sellerId)
                .orElseThrow(ProductNotFoundException::new);

        List<CategoryEntity> categories = categoryRepository.findForProduct(product);

        product.hide(clock.instant());
        ProductEntity saved = productRepository.save(product);
        List<ProductMediaEntity> mediaList = productMediaRepository.findByProductIdOrderByDisplayOrderAscIdAsc(productId);
        return CatalogDtos.SellerProductDetailResponse.from(saved, categories, mediaList);
    }

    @Transactional(readOnly = true)
    public CatalogDtos.PageResponse<CatalogDtos.ModerationProductResponse> getPendingProducts(int page, int size) {
        int clampedPage = Math.max(page, 0);
        int clampedSize = Math.min(Math.max(size, 1), 50);
        Pageable pageable = PageRequest.of(clampedPage, clampedSize, Sort.by(Sort.Direction.ASC, "createdAt"));

        Page<ProductEntity> pendingPage = productRepository.findByStatusAndDeletedAtIsNull(ProductEntity.STATUS_PENDING, pageable);
        List<ProductEntity> products = pendingPage.getContent();
        if (products.isEmpty()) {
            return new CatalogDtos.PageResponse<>(
                    List.of(), pendingPage.getNumber(), pendingPage.getSize(),
                    pendingPage.getTotalElements(), pendingPage.getTotalPages(), pendingPage.hasNext()
            );
        }

        Set<Long> categoryIds = products.stream().flatMap(p -> p.getCategoryIds().stream()).collect(Collectors.toSet());
        Map<Long, CategoryEntity> categoryMap = categoryRepository.findAllById(categoryIds).stream()
                .collect(Collectors.toMap(CategoryEntity::getId, c -> c));

        Set<Long> productIds = products.stream().map(ProductEntity::getId).collect(Collectors.toSet());
        Map<Long, List<ProductMediaEntity>> mediaMap = productMediaRepository.findByProductIdInOrderByDisplayOrderAscIdAsc(productIds).stream()
                .collect(Collectors.groupingBy(ProductMediaEntity::getProductId));

        List<CatalogDtos.ModerationProductResponse> items = products.stream().map(p -> {
            CategoryEntity cat = categoryMap.get(p.getCategoryId());
            List<ProductMediaEntity> media = mediaMap.getOrDefault(p.getId(), List.of());
            return new CatalogDtos.ModerationProductResponse(
                    p.getId(),
                    p.getTitle(),
                    p.getDescription(),
                    p.getListedPrice(),
                    p.getCondition(),
                    p.getStatus(),
                    p.getSellerId(),
                    cat != null ? CatalogDtos.CategoryResponse.from(cat) : null,
                    media.stream().map(CatalogDtos.ProductMediaResponse::from).toList(),
                    p.isRequiresBuyerEkyc(),
                    p.getCreatedAt(),
                    CategoryRepository.orderedForProduct(p, categoryMap).stream().map(CatalogDtos.CategoryResponse::from).toList(),
                    p.getContentRevision(),
                    p.getUpdatedAt()
            );
        }).toList();

        return new CatalogDtos.PageResponse<>(
                items,
                pendingPage.getNumber(),
                pendingPage.getSize(),
                pendingPage.getTotalElements(),
                pendingPage.getTotalPages(),
                pendingPage.hasNext()
        );
    }

    @Transactional(readOnly = true)
    public CatalogDtos.ModerationProductResponse getModerationDetail(long productId) {
        ProductEntity product = productRepository.findById(productId)
                .filter(p -> p.getDeletedAt() == null)
                .orElseThrow(ProductNotFoundException::new);

        List<CategoryEntity> categories = categoryRepository.findForProduct(product);

        List<ProductMediaEntity> media = productMediaRepository.findByProductIdOrderByDisplayOrderAscIdAsc(productId);

        return new CatalogDtos.ModerationProductResponse(
                product.getId(),
                product.getTitle(),
                product.getDescription(),
                product.getListedPrice(),
                product.getCondition(),
                product.getStatus(),
                product.getSellerId(),
                CatalogDtos.CategoryResponse.from(categories.getFirst()),
                media.stream().map(CatalogDtos.ProductMediaResponse::from).toList(),
                product.isRequiresBuyerEkyc(),
                product.getCreatedAt(),
                categories.stream().map(CatalogDtos.CategoryResponse::from).toList(),
                product.getContentRevision(),
                product.getUpdatedAt()
        );
    }

    public CatalogDtos.ActionResponse approveProduct(long reviewerId, long productId, Long expectedVersion, String commandKey) {
        if (reviewerId <= 0) {
            throw new IllegalArgumentException("reviewerId không hợp lệ: " + reviewerId);
        }
        if (expectedVersion == null || expectedVersion < 0) {
            throw new IllegalArgumentException("expectedVersion là bắt buộc và phải >= 0");
        }
        if (commandKey == null || commandKey.trim().isEmpty() || commandKey.trim().length() > 100) {
            throw new IllegalArgumentException("commandKey là bắt buộc và tối đa 100 ký tự");
        }
        String trimmedKey = commandKey.trim();

        Optional<ProductModerationDecisionEntity> existingDecision = productModerationDecisionRepository.findByCommandKey(trimmedKey);
        if (existingDecision.isPresent()) {
            ProductModerationDecisionEntity prev = existingDecision.get();
            boolean sameReviewer = prev.getReviewerId().equals(reviewerId);
            boolean sameProduct = prev.getProductId().equals(productId);
            boolean sameDecision = ProductModerationDecisionEntity.DECISION_APPROVED.equals(prev.getDecision());
            boolean sameReason = prev.getReason() == null;
            boolean sameVersion = prev.getProductVersion().equals(expectedVersion);

            if (sameReviewer && sameProduct && sameDecision && sameReason && sameVersion) {
                return new CatalogDtos.ActionResponse(productId, ProductEntity.STATUS_ACTIVE, "Phê duyệt tin đăng thành công.");
            } else {
                throw new CommandKeyConflictException("Command key '" + trimmedKey + "' đã được sử dụng với thông tin khác.");
            }
        }

        ProductEntity product = productRepository.findById(productId)
                .filter(p -> p.getDeletedAt() == null)
                .orElseThrow(ProductNotFoundException::new);

        if (!ProductEntity.STATUS_PENDING.equals(product.getStatus())) {
            throw new ProductStateConflictException(
                    "Chỉ có thể phê duyệt tin ở trạng thái PENDING. Trạng thái hiện tại: " + product.getStatus()
            );
        }

        if (!expectedVersion.equals(product.getContentRevision())) {
            throw new ProductVersionConflictException(
                    "Phiên bản nội dung tin đăng đã thay đổi (hiện tại: v" + product.getContentRevision() + ", kỳ vọng: v" + expectedVersion + "). Vui lòng tải lại trang để xem nội dung mới nhất."
            );
        }

        long imgCount = productMediaRepository.countByProductIdAndMediaType(productId, "IMAGE");
        long videoCount = productMediaRepository.countByProductIdAndMediaType(productId, "VIDEO");
        if (imgCount < 1 || videoCount < 1) {
            throw new IllegalStateException("Không thể duyệt tin khi chưa đủ tối thiểu 1 ảnh và 1 video.");
        }

        long oldVersion = product.getVersion();
        product.approve(clock.instant());
        ProductEntity saved = productRepository.save(product);

        ProductModerationDecisionEntity decision = new ProductModerationDecisionEntity(
                productId, reviewerId, ProductModerationDecisionEntity.DECISION_APPROVED,
                null, expectedVersion, trimmedKey, clock.instant()
        );
        productModerationDecisionRepository.save(decision);

        platformAuditFacade.recordAudit(
                reviewerId, "APPROVE_PRODUCT", "PRODUCT", productId,
                Map.of("status", ProductEntity.STATUS_PENDING, "version", oldVersion, "contentRevision", expectedVersion),
                Map.of("status", saved.getStatus(), "version", saved.getVersion(), "contentRevision", saved.getContentRevision()),
                null, null
        );

        return new CatalogDtos.ActionResponse(productId, saved.getStatus(), "Phê duyệt tin đăng thành công.");
    }

    public CatalogDtos.ActionResponse rejectProduct(long reviewerId, long productId, String reason, Long expectedVersion, String commandKey) {
        if (reason == null || reason.trim().isEmpty()) {
            throw new IllegalArgumentException("Lý do từ chối không được để trống");
        }
        String trimmedReason = reason.trim();
        if (trimmedReason.length() > 500) {
            throw new IllegalArgumentException("Lý do tối đa 500 ký tự");
        }
        if (reviewerId <= 0) {
            throw new IllegalArgumentException("reviewerId không hợp lệ: " + reviewerId);
        }
        if (expectedVersion == null || expectedVersion < 0) {
            throw new IllegalArgumentException("expectedVersion là bắt buộc và phải >= 0");
        }
        if (commandKey == null || commandKey.trim().isEmpty() || commandKey.trim().length() > 100) {
            throw new IllegalArgumentException("commandKey là bắt buộc và tối đa 100 ký tự");
        }
        String trimmedKey = commandKey.trim();

        Optional<ProductModerationDecisionEntity> existingDecision = productModerationDecisionRepository.findByCommandKey(trimmedKey);
        if (existingDecision.isPresent()) {
            ProductModerationDecisionEntity prev = existingDecision.get();
            boolean sameReviewer = prev.getReviewerId().equals(reviewerId);
            boolean sameProduct = prev.getProductId().equals(productId);
            boolean sameDecision = ProductModerationDecisionEntity.DECISION_REJECTED.equals(prev.getDecision());
            boolean sameReason = trimmedReason.equals(prev.getReason());
            boolean sameVersion = prev.getProductVersion().equals(expectedVersion);

            if (sameReviewer && sameProduct && sameDecision && sameReason && sameVersion) {
                return new CatalogDtos.ActionResponse(productId, ProductEntity.STATUS_REJECTED, "Đã từ chối tin đăng: " + prev.getReason());
            } else {
                throw new CommandKeyConflictException("Command key '" + trimmedKey + "' đã được sử dụng với thông tin khác.");
            }
        }

        ProductEntity product = productRepository.findById(productId)
                .filter(p -> p.getDeletedAt() == null)
                .orElseThrow(ProductNotFoundException::new);

        if (!ProductEntity.STATUS_PENDING.equals(product.getStatus())) {
            throw new ProductStateConflictException(
                    "Chỉ có thể từ chối tin ở trạng thái PENDING. Trạng thái hiện tại: " + product.getStatus()
            );
        }

        if (!expectedVersion.equals(product.getContentRevision())) {
            throw new ProductVersionConflictException(
                    "Phiên bản nội dung tin đăng đã thay đổi (hiện tại: v" + product.getContentRevision() + ", kỳ vọng: v" + expectedVersion + "). Vui lòng tải lại trang để xem nội dung mới nhất."
            );
        }

        long oldVersion = product.getVersion();
        product.reject(clock.instant());
        ProductEntity saved = productRepository.save(product);

        ProductModerationDecisionEntity decision = new ProductModerationDecisionEntity(
                productId, reviewerId, ProductModerationDecisionEntity.DECISION_REJECTED,
                trimmedReason, expectedVersion, trimmedKey, clock.instant()
        );
        productModerationDecisionRepository.save(decision);

        platformAuditFacade.recordAudit(
                reviewerId, "REJECT_PRODUCT", "PRODUCT", productId,
                Map.of("status", ProductEntity.STATUS_PENDING, "version", oldVersion, "contentRevision", expectedVersion),
                Map.of("status", saved.getStatus(), "reason", trimmedReason, "version", saved.getVersion(), "contentRevision", saved.getContentRevision()),
                null, null
        );

        return new CatalogDtos.ActionResponse(productId, saved.getStatus(), "Đã từ chối tin đăng: " + trimmedReason);
    }

    private void ensureSellerActive(long sellerId) {
        if (!identityCatalogFacade.isSellerActive(sellerId)) {
            throw new SellerRequiredException("Bạn cần kích hoạt quyền Người bán để quản lý tin đăng.");
        }
    }

    private List<CategoryEntity> resolveSelectedCategories(CatalogDtos.CreateOrUpdateProductRequest request) {
        List<Long> ids = request.categoryIds() != null ? request.categoryIds()
                : request.categoryId() != null ? List.of(request.categoryId()) : List.of();
        if (ids.isEmpty() || ids.stream().anyMatch(id -> id == null || id <= 0)) {
            throw new IllegalArgumentException("Vui lòng chọn ít nhất một danh mục hợp lệ.");
        }
        return new LinkedHashSet<>(ids).stream().map(id -> categoryRepository.findByIdAndActiveTrue(id)
                .orElseThrow(() -> new CategoryNotFoundException("Danh mục không tồn tại hoặc đã bị vô hiệu hóa."))).toList();
    }
}
