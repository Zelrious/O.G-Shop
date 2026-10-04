package com.oldbutgold.shop.modules.catalog.application;

import com.oldbutgold.shop.modules.catalog.infrastructure.persistence.ProductEntity;
import com.oldbutgold.shop.modules.catalog.infrastructure.persistence.ProductMediaEntity;
import com.oldbutgold.shop.modules.catalog.infrastructure.persistence.ProductMediaRepository;
import com.oldbutgold.shop.modules.catalog.infrastructure.persistence.ProductRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Component
@Transactional
public class DefaultCatalogCommerceFacade implements CatalogCommerceFacade {
    private final ProductRepository productRepository;
    private final ProductMediaRepository productMediaRepository;

    public DefaultCatalogCommerceFacade(ProductRepository productRepository,
                                        ProductMediaRepository productMediaRepository) {
        this.productRepository = productRepository;
        this.productMediaRepository = productMediaRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<CommerceProductSummary> getActiveProduct(long productId) {
        return productRepository.findByIdAndStatusAndDeletedAtIsNull(productId, ProductEntity.STATUS_ACTIVE)
                .map(product -> {
                    String thumb = findThumbnail(productId);
                    return new CommerceProductSummary(
                            product.getId(),
                            product.getSellerId(),
                            product.getTitle(),
                            product.getListedPrice(),
                            product.getCondition(),
                            product.getStatus(),
                            thumb,
                            product.isRequiresBuyerEkyc()
                    );
                });
    }

    @Override
    public CommerceProductDetail getProductForCheckoutLock(long productId) {
        ProductEntity product = productRepository.findByIdForUpdate(productId)
                .orElseThrow(() -> new ProductNotFoundException("Sản phẩm không tồn tại: " + productId));

        if (!ProductEntity.STATUS_ACTIVE.equals(product.getStatus())) {
            throw new ProductStateConflictException(
                    "Sản phẩm đang ở trạng thái '" + product.getStatus() + "' và không thể đặt mua."
            );
        }

        String thumb = findThumbnail(productId);
        return new CommerceProductDetail(
                product.getId(),
                product.getSellerId(),
                product.getTitle(),
                product.getListedPrice(),
                product.getCondition(),
                product.getStatus(),
                thumb,
                product.isRequiresBuyerEkyc(),
                product.getVersion()
        );
    }

    @Override
    public void reserveProduct(long productId, long orderId, Instant reservedUntil, Instant now) {
        ProductEntity product = productRepository.findById(productId)
                .filter(p -> p.getDeletedAt() == null)
                .orElseThrow(() -> new ProductNotFoundException("Sản phẩm không tồn tại: " + productId));

        product.reserve(orderId, reservedUntil, now);
        productRepository.save(product);
    }

    @Override
    public void releaseProductReservation(long productId, long orderId, Instant now) {
        ProductEntity product = productRepository.findById(productId)
                .filter(p -> p.getDeletedAt() == null)
                .orElse(null);

        if (product != null) {
            product.releaseReservation(orderId, now);
            productRepository.save(product);
        }
    }

    @Override
    public void markProductSold(long productId, long orderId, Instant now) {
        ProductEntity product = productRepository.findById(productId)
                .filter(p -> p.getDeletedAt() == null)
                .orElse(null);

        if (product != null) {
            product.markSold(orderId, now);
            productRepository.save(product);
        }
    }

    private String findThumbnail(long productId) {
        List<ProductMediaEntity> mediaList = productMediaRepository.findByProductIdOrderByDisplayOrderAscIdAsc(productId);
        return mediaList.stream()
                .filter(m -> "IMAGE".equalsIgnoreCase(m.getMediaType()))
                .map(ProductMediaEntity::getMediaUrl)
                .findFirst()
                .orElse(null);
    }
}
