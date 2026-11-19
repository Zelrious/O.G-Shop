package com.oldbutgold.shop.modules.trustsafety.api;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;
import java.util.List;

public class ReviewDtos {

    public record CreateReviewRequest(
            @NotNull Long orderId,
            @NotNull @Min(1) @Max(5) Integer rating,
            String comment
    ) {
        @JsonCreator
        public CreateReviewRequest(
                @JsonProperty("orderId") Long orderId,
                @JsonProperty("rating") Integer rating,
                @JsonProperty("comment") String comment
        ) {
            this.orderId = orderId;
            this.rating = rating;
            this.comment = comment;
        }
    }

    public record ReviewResponse(
            Long reviewId,
            Long orderId,
            Long reviewerId,
            String reviewerName,
            Long revieweeId,
            String revieweeName,
            Integer rating,
            String comment,
            Instant createdAt,
            Instant updatedAt
    ) {}

    public record OrderReviewsResponse(
            Long orderId,
            ReviewResponse buyerReview,
            ReviewResponse sellerReview
    ) {}

    public record UserReviewsSummaryResponse(
            Long userId,
            Double averageRating,
            Long totalReviews,
            List<ReviewResponse> recentReviews
    ) {}
}
