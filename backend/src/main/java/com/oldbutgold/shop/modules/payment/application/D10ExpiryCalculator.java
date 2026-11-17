package com.oldbutgold.shop.modules.payment.application;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;

/**
 * Calculator implementing the approved D-10 Option B month-end expiry policy (Task 4.1b).
 *
 * <p>Approved policy rules:
 * <ul>
 *   <li><b>Timezone:</b> Strict {@code Asia/Ho_Chi_Minh} (UTC+7). Never use server-default timezone.</li>
 *   <li><b>Calendar Counting:</b> Do not count the confirmation date. The next calendar day is day 1.</li>
 *   <li><b>Threshold Rule:</b>
 *     <ul>
 *       <li>If at least 15 calendar days remain until month-end, expire at the <b>current month's end</b>.</li>
 *       <li>If fewer than 15 calendar days remain, expire at the <b>next month's end</b>.</li>
 *     </ul>
 *   </li>
 *   <li><b>Exclusive Boundary:</b> Expiry is strictly exclusive at {@code 00:00:00} on the first day of the following month in {@code Asia/Ho_Chi_Minh}.</li>
 *   <li><b>Independence:</b> Each {@code CREDIT_REFUND} ledger row receives its own {@code expires_at} based on its settlement confirmation timestamp.</li>
 * </ul>
 */
public final class D10ExpiryCalculator {

    public static final ZoneId ASIA_HO_CHI_MINH = ZoneId.of("Asia/Ho_Chi_Minh");

    private D10ExpiryCalculator() {}

    /**
     * Calculates the exclusive expiration timestamp for refunded Buyer Coins per D-10 Option B.
     *
     * @param confirmedInstant the confirmation timestamp of the settlement operation
     * @return the exclusive expiration Instant at 00:00:00 of the 1st day of the following month
     */
    public static Instant calculateExpiry(Instant confirmedInstant) {
        if (confirmedInstant == null) {
            throw new IllegalArgumentException("confirmedInstant must not be null");
        }

        ZonedDateTime confirmedZdt = confirmedInstant.atZone(ASIA_HO_CHI_MINH);
        LocalDate confirmDate = confirmedZdt.toLocalDate();
        int lastDayOfMonth = confirmDate.lengthOfMonth();
        // Day count excludes the confirmation date; remaining days = lastDayOfMonth - confirmDate.getDayOfMonth()
        int remainingDays = lastDayOfMonth - confirmDate.getDayOfMonth();

        LocalDate expiryFirstDayOfNextMonth;
        if (remainingDays >= 15) {
            // Expires at end of current month -> boundary is 00:00:00 on 1st day of next month
            expiryFirstDayOfNextMonth = confirmDate.withDayOfMonth(1).plusMonths(1);
        } else {
            // Expires at end of next month -> boundary is 00:00:00 on 1st day of month + 2
            expiryFirstDayOfNextMonth = confirmDate.withDayOfMonth(1).plusMonths(2);
        }

        return expiryFirstDayOfNextMonth.atStartOfDay(ASIA_HO_CHI_MINH).toInstant();
    }
}
