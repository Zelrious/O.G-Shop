package com.oldbutgold.shop.modules.commerce.api;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;

public final class VoucherDtos {
    private VoucherDtos(){}
    public record Campaign(long id,long revisionId,long version,String code,String title,String description,
       String voucherType,String discountType,BigDecimal discountValue,BigDecimal maxDiscountAmount,
       BigDecimal minOrderAmount,String scope,List<Long> productIds,int quantity,Instant startTime,Instant endTime,
       boolean active,boolean selfClaim,long issued,long used) {}
    public record Save(@NotNull UUID commandId,@NotNull Long version,
       @NotBlank @Pattern(regexp="[A-Za-z0-9_-]{3,50}") String code,@NotBlank @Size(max=200) String title,
       @Size(max=2000) String description,@Pattern(regexp="ORDER_DISCOUNT|SHIPPING_DISCOUNT") @NotNull String voucherType,
       @Pattern(regexp="FIXED_AMOUNT|PERCENTAGE") @NotNull String discountType,@NotNull @DecimalMin("1") BigDecimal discountValue,
       @DecimalMin("1") BigDecimal maxDiscountAmount,@NotNull @DecimalMin("0") BigDecimal minOrderAmount,
       @Pattern(regexp="CHECKOUT|PRODUCTS") @NotNull String scope,@NotNull @Size(max=100) List<@Positive Long> productIds,
       @Min(1) @Max(1000000) int quantity,@NotNull Instant startTime,@NotNull Instant endTime,boolean active,boolean selfClaim) {}
    public record Grant(UUID id,long userId,String userName,String state,Instant grantedAt,Instant expiresAt,Campaign campaign) {}
    public record Wallet(List<Grant> grants,List<Campaign> receivable,long points,List<Point> pointsHistory) {}
    public record Point(long delta,String type,String reason,Instant createdAt) {}
    public record Claim(@NotNull UUID commandId,@NotNull Long version) {}
    public record Bulk(@NotNull UUID commandId,@NotNull Long version,@Pattern(regexp="GRANT|REVOKE") @NotNull String action,
       @NotNull @Size(min=1,max=100) List<@NotBlank String> ids,@NotBlank @Size(max=1000) String reason) {}
    public record Result(String id,long userId,String userName,boolean eligible,String status,String reason,UUID grantId) {}
    public record Batch(List<Result> items,long successful,long skipped) {}
    public record Targets(List<Result> items,long total,int page,int size,Campaign campaign) {}
}
