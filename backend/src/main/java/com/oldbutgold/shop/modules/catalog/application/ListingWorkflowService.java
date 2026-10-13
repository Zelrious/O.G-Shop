package com.oldbutgold.shop.modules.catalog.application;

import com.oldbutgold.shop.modules.catalog.api.CatalogDtos;
import com.oldbutgold.shop.modules.catalog.infrastructure.persistence.*;
import com.oldbutgold.shop.modules.commerce.application.ListingRewardFacade;
import com.oldbutgold.shop.modules.identity.application.IdentityCatalogFacade;
import com.oldbutgold.shop.modules.payment.application.ListingFeePaymentFacade;
import com.oldbutgold.shop.modules.platform.application.PlatformAuditFacade;
import com.oldbutgold.shop.modules.platform.application.PlatformNotificationFacade;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static com.oldbutgold.shop.modules.platform.application.PlatformNotificationFacade.Destination.*;

@Service
@Transactional
public class ListingWorkflowService {
    private final ListingFeeRepository fees;
    private final ProductRepository products;
    private final IdentityCatalogFacade identity;
    private final ListingFeePaymentFacade payments;
    private final ListingRewardFacade rewards;
    private final PlatformNotificationFacade notifications;
    private final PlatformAuditFacade audit;
    private final Clock clock;
    public ListingWorkflowService(ListingFeeRepository fees,ProductRepository products,IdentityCatalogFacade identity,
        ListingFeePaymentFacade payments,ListingRewardFacade rewards,PlatformNotificationFacade notifications,
        PlatformAuditFacade audit,Clock clock) {
        this.fees=fees;this.products=products;this.identity=identity;this.payments=payments;
        this.rewards=rewards;this.notifications=notifications;this.audit=audit;this.clock=clock;
    }
    public long captureReview(ProductEntity product,List<CatalogDtos.ProductMediaResponse> media) {
        fees.enable(product.getId());
        long revision=fees.snapshot(product,media);
        product.referenceReviewRevision(revision);
        products.flush();
        return revision;
    }
    public void submitted(ProductEntity product) {
        fees.enable(product.getId());
        var notices=new java.util.ArrayList<PlatformNotificationFacade.Notice>();
        notices.add(new PlatformNotificationFacade.Notice(product.getSellerId(),"LISTING_SUBMITTED","Tin đăng đang chờ duyệt",
            product.getTitle()+" đã được gửi cho KTV xét.",SELLER_LISTING,product.getId(),product.getContentRevision()));
        notices.addAll(identity.getActiveKtvIds().stream().map(id->new PlatformNotificationFacade.Notice(id,"LISTING_PENDING",
            "Tin đăng cần kiểm duyệt",product.getTitle()+" đang chờ xét.",KTV_MODERATION,product.getId(),product.getContentRevision())).toList());
        notifications.enqueue("SUBMIT:"+product.getId()+":"+product.getVersion(),product.getId(),notices);
    }
    public CatalogDtos.ActionResponse approved(ProductEntity product,ProductModerationDecisionEntity decision) {
        identity.requireListingAllowed(product.getSellerId());
        if (!identity.isSellerActive(product.getSellerId()))
            throw new ProductStateConflictException("Người bán chưa có quyền bán đang hoạt động. Chưa ghi nhận quyết định duyệt.");
        var fee=fees.assess(product,decision.getId(),decision.getCommandKey());
        if(fee.paid()) product.activatePaidListing(clock.instant());
        else product.approveAwaitingFee(clock.instant());
        products.flush();
        boolean rewarded=fee.paid()&&rewards.grantFirstListing(product.getSellerId(),product.getId());
        String sellerMessage=fee.paid()?"Tin đã được duyệt và mở bán.":"Tin đã được duyệt. Phí còn phải trả: "+money(fee.outstanding())+". Thanh toán để mở bán.";
        if(rewarded) sellerMessage+=" Bạn nhận 10.000 xu cho tin đầu tiên mở bán.";
        decisionNotices(product,decision,sellerMessage);
        return approvalResult(product.getId(),fee.assessedAmount());
    }
    public CatalogDtos.ActionResponse replayApproval(ProductModerationDecisionEntity decision) {
        var fee=fees.byDecision(decision.getId());
        // Only historical pre-V27 decisions have no assessment.
        return fee==null?new CatalogDtos.ActionResponse(decision.getProductId(),"ACTIVE","Quyết định duyệt trước V27 đã được ghi nhận.")
            :approvalResult(decision.getProductId(),fee.assessedAmount());
    }
    private CatalogDtos.ActionResponse approvalResult(long id,BigDecimal due) {
        return new CatalogDtos.ActionResponse(id,due.signum()>0?"APPROVED_AWAITING_FEE":"ACTIVE",
            due.signum()>0?"Đã duyệt tin. Người bán cần trả "+money(due)+" để mở bán.":"Đã duyệt và mở bán tin đăng.");
    }
    public void rejected(ProductEntity product,ProductModerationDecisionEntity decision) {
        decisionNotices(product,decision,"Tin bị từ chối: "+decision.getReason()+". Sửa nội dung rồi gửi duyệt lại.");
    }
    private void decisionNotices(ProductEntity product,ProductModerationDecisionEntity decision,String sellerMessage) {
        notifications.enqueue("DECISION:"+decision.getId(),product.getId(),List.of(
            new PlatformNotificationFacade.Notice(product.getSellerId(),"LISTING_REVIEWED","Kết quả kiểm duyệt tin đăng",sellerMessage,
                SELLER_LISTING,product.getId(),product.getContentRevision()),
            new PlatformNotificationFacade.Notice(decision.getReviewerId(),"MODERATION_CONFIRMED","Đã ghi nhận quyết định",
                "Tin #"+product.getId()+": "+("APPROVED".equals(decision.getDecision())?"Đã phê duyệt":"Đã từ chối")+". "+sellerMessage,
                KTV_MODERATION,product.getId(),product.getContentRevision())));
    }
    public void ensurePublishable(ProductEntity product) {
        if(!fees.isManaged(product.getId())) return;
        var fee=fees.latest(product.getId());
        if(fee==null||!fee.paid()||!Long.valueOf(fee.revisionId()).equals(product.getCurrentRevisionId()))
            throw new ProductStateConflictException("Tin chưa đủ phí hoặc chưa có quyết định cho bản hiện tại.");
    }
    @Transactional(readOnly=true)
    public FeeSummary getFee(long sellerId,long productId) {
        ProductEntity product=owned(sellerId,productId,false);
        return summary(product,fees.latest(productId));
    }
    public FeeSummary payMock(long sellerId,long productId,UUID chargeId,long expectedVersion,String key) {
        identity.requireListingAllowed(sellerId);
        ProductEntity product=owned(sellerId,productId,true);
        var fee=fees.latest(productId);
        if(fee==null||fee.chargeId()==null||!fee.chargeId().equals(chargeId))
            throw new ProductStateConflictException("Khoản phí không còn là khoản hiện tại của tin đăng.");
        if(payments.isReplay(chargeId,fee.assessedAmount(),key)) return summary(product,fees.latest(productId));
        if(fee.paid()||!"PENDING".equals(fee.chargeState())||!"APPROVED_AWAITING_FEE".equals(product.getStatus()))
            throw new ProductStateConflictException("Khoản phí đã được xử lý hoặc tin không còn chờ thanh toán.");
        if(product.getVersion()!=expectedVersion) throw new ProductVersionConflictException("Tin đã thay đổi. Vui lòng tải lại khoản phí.");
        payments.confirmMock(chargeId,fee.assessedAmount(),key);
        product.activatePaidListing(clock.instant());
        products.flush();
        boolean rewarded=rewards.grantFirstListing(sellerId,productId);
        audit.recordAudit(sellerId,"PAY_LISTING_FEE_MOCK","PRODUCT",productId,
            Map.of("status","APPROVED_AWAITING_FEE"),Map.of("status","ACTIVE","chargeId",chargeId,"amount",fee.assessedAmount()),null,null);
        notifications.enqueue("OPEN:"+fee.assessmentId(),productId,List.of(new PlatformNotificationFacade.Notice(sellerId,
            "LISTING_OPENED","Tin đăng đã mở bán","Đã xác nhận phí mô phỏng "+money(fee.assessedAmount())+". "+
                (rewarded?"Bạn nhận 10.000 xu cho tin đầu tiên mở bán.":"Tin hiện có trên chợ."),SELLER_LISTING,productId,product.getContentRevision())));
        return summary(product,fees.latest(productId));
    }
    private ProductEntity owned(long sellerId,long productId,boolean lock) {
        if(!identity.isSellerActive(sellerId)) throw new SellerRequiredException("Bạn cần quyền Người bán đang hoạt động.");
        return (lock?products.findByIdForUpdate(productId):products.findByIdAndSellerIdAndDeletedAtIsNull(productId,sellerId))
            .filter(p->p.getSellerId()==sellerId).orElseThrow(ProductNotFoundException::new);
    }
    private FeeSummary summary(ProductEntity p,ListingFeeRepository.Fee f) {
        return new FeeSummary(p.getId(),p.getTitle(),p.getStatus(),p.getVersion(),p.getQuantityTotal(),
            f==null?null:f.chargeId(),f==null?null:f.policyCode(),f==null?null:f.listedTotal(),
            f==null?BigDecimal.ZERO:f.computedFee(),f==null?BigDecimal.ZERO:f.totalPaid(),
            f==null?BigDecimal.ZERO:f.outstanding(),f==null?"NOT_ASSESSED":f.paid()?"CONFIRMED":f.chargeState());
    }
    private static String money(BigDecimal value) { return java.text.NumberFormat.getNumberInstance(java.util.Locale.forLanguageTag("vi-VN")).format(value)+" đồng"; }
    public record FeeSummary(long productId,String title,String status,long version,int quantity,UUID chargeId,
        String policyCode,BigDecimal listedTotal,BigDecimal computedFee,BigDecimal totalPaid,BigDecimal amountDue,String paymentState) {}
}
