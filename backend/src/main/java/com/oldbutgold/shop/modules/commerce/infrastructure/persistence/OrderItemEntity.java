package com.oldbutgold.shop.modules.commerce.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;

@Entity
@Table(name = "order_items", schema = "og_compat")
public class OrderItemEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "order_item_id")
    private Long id;

    @Column(name = "order_id", nullable = false)
    private Long orderId;

    @Column(name = "product_id", nullable = false)
    private Long productId;

    @Column(name = "product_title", nullable = false, length = 200)
    private String productTitle;

    @Column(nullable = false)
    private int quantity = 1;

    @Column(name = "listed_price", nullable = false, precision = 19, scale = 2)
    private BigDecimal listedPrice;

    @Column(name = "agreed_price", nullable = false, precision = 19, scale = 2)
    private BigDecimal agreedPrice;

    @Column(name = "buyer_system_fee", nullable = false, precision = 19, scale = 2)
    private BigDecimal buyerSystemFee = BigDecimal.ZERO;

    @Column(name = "seller_system_fee", nullable = false, precision = 19, scale = 2)
    private BigDecimal sellerSystemFee = BigDecimal.ZERO;

    @Column(name = "buyer_line_total", nullable = false, precision = 19, scale = 2)
    private BigDecimal buyerLineTotal;

    @Column(name = "seller_line_proceeds", nullable = false, precision = 19, scale = 2)
    private BigDecimal sellerLineProceeds;

    @Column(name = "fee_policy_id")
    private Long feePolicyId;

    @Column(name = "accepted_offer_id")
    private Long acceptedOfferId;

    @Column(name = "pricing_source", nullable = false, length = 20)
    private String pricingSource = "LIST_PRICE";

    protected OrderItemEntity() {}

    public OrderItemEntity(Long orderId, Long productId, String productTitle, int quantity,
                           BigDecimal listedPrice, BigDecimal agreedPrice, BigDecimal buyerSystemFee,
                           BigDecimal sellerSystemFee, BigDecimal buyerLineTotal, BigDecimal sellerLineProceeds,
                           Long feePolicyId, Long acceptedOfferId, String pricingSource) {
        this.orderId = orderId;
        this.productId = productId;
        this.productTitle = productTitle;
        this.quantity = quantity;
        this.listedPrice = listedPrice;
        this.agreedPrice = agreedPrice;
        this.buyerSystemFee = buyerSystemFee;
        this.sellerSystemFee = sellerSystemFee;
        this.buyerLineTotal = buyerLineTotal;
        this.sellerLineProceeds = sellerLineProceeds;
        this.feePolicyId = feePolicyId;
        this.acceptedOfferId = acceptedOfferId;
        this.pricingSource = pricingSource;
    }

    public Long getId() { return id; }
    public Long getOrderId() { return orderId; }
    public Long getProductId() { return productId; }
    public String getProductTitle() { return productTitle; }
    public int getQuantity() { return quantity; }
    public BigDecimal getListedPrice() { return listedPrice; }
    public BigDecimal getAgreedPrice() { return agreedPrice; }
    public BigDecimal getBuyerSystemFee() { return buyerSystemFee; }
    public BigDecimal getSellerSystemFee() { return sellerSystemFee; }
    public BigDecimal getBuyerLineTotal() { return buyerLineTotal; }
    public BigDecimal getSellerLineProceeds() { return sellerLineProceeds; }
    public Long getFeePolicyId() { return feePolicyId; }
    public Long getAcceptedOfferId() { return acceptedOfferId; }
    public String getPricingSource() { return pricingSource; }
}
