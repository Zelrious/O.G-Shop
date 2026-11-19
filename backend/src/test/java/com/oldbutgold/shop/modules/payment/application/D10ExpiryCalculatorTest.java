
package com.oldbutgold.shop.modules.payment.application;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("D10ExpiryCalculatorTest - Verification of D-10 Option B Month-End Expiry Boundary Policy")
class D10ExpiryCalculatorTest {

    private static final ZoneId ASIA_HO_CHI_MINH = ZoneId.of("Asia/Ho_Chi_Minh");

    @Test
    @DisplayName("Boundary Test 1: Confirmation date 2026-11-15 (15 days remaining) -> expires 2026-12-01 00:00:00 Asia/Ho_Chi_Minh")
    void calculateExpiry_november15_expiresCurrentMonthEnd() {
        // Confirmation on 2026-11-15 at 10:00:00 UTC = 17:00:00 Asia/Ho_Chi_Minh
        Instant confirmed = ZonedDateTime.of(2026, 11, 15, 17, 0, 0, 0, ASIA_HO_CHI_MINH).toInstant();

        Instant expiry = D10ExpiryCalculator.calculateExpiry(confirmed);

        // Expected boundary: 2026-12-01 00:00:00 Asia/Ho_Chi_Minh
        ZonedDateTime expectedZdt = ZonedDateTime.of(2026, 12, 1, 0, 0, 0, 0, ASIA_HO_CHI_MINH);
        assertThat(expiry).isEqualTo(expectedZdt.toInstant());
        assertThat(expiry.atZone(ASIA_HO_CHI_MINH)).isEqualTo(expectedZdt);
    }

    @Test
    @DisplayName("Boundary Test 2: Confirmation date 2026-11-16 (14 days remaining) -> expires 2027-01-01 00:00:00 Asia/Ho_Chi_Minh")
    void calculateExpiry_november16_expiresNextMonthEnd() {
        // Confirmation on 2026-11-16 at 09:30:00 UTC = 16:30:00 Asia/Ho_Chi_Minh
        Instant confirmed = ZonedDateTime.of(2026, 11, 16, 16, 30, 0, 0, ASIA_HO_CHI_MINH).toInstant();

        Instant expiry = D10ExpiryCalculator.calculateExpiry(confirmed);

        // Expected boundary: 2027-01-01 00:00:00 Asia/Ho_Chi_Minh
        ZonedDateTime expectedZdt = ZonedDateTime.of(2027, 1, 1, 0, 0, 0, 0, ASIA_HO_CHI_MINH);
        assertThat(expiry).isEqualTo(expectedZdt.toInstant());
        assertThat(expiry.atZone(ASIA_HO_CHI_MINH)).isEqualTo(expectedZdt);
    }

    @Test
    @DisplayName("Timezone conversion: Input in UTC parses correctly to Asia/Ho_Chi_Minh calendar day")
    void calculateExpiry_utcInputRespectsHoChiMinhDay() {
        // 2026-11-15 17:30:00 UTC is 2026-11-16 00:30:00 in Asia/Ho_Chi_Minh (UTC+7)
        Instant lateNightUtc = Instant.parse("2026-11-15T17:30:00Z");

        Instant expiry = D10ExpiryCalculator.calculateExpiry(lateNightUtc);

        // In Ho Chi Minh, it is Nov 16 (14 days remaining in 30-day month) -> next month-end (2027-01-01)
        ZonedDateTime expectedZdt = ZonedDateTime.of(2027, 1, 1, 0, 0, 0, 0, ASIA_HO_CHI_MINH);
        assertThat(expiry).isEqualTo(expectedZdt.toInstant());
    }

    @Test
    @DisplayName("Exact exclusive expiry boundary: Expiry timestamp is at exact 00:00:00.000")
    void calculateExpiry_exclusiveBoundaryPrecision() {
        Instant confirmed = Instant.parse("2026-11-10T08:00:00Z");
        Instant expiry = D10ExpiryCalculator.calculateExpiry(confirmed);

        ZonedDateTime zdt = expiry.atZone(ASIA_HO_CHI_MINH);
        assertThat(zdt.getHour()).isEqualTo(0);
        assertThat(zdt.getMinute()).isEqualTo(0);
        assertThat(zdt.getSecond()).isEqualTo(0);
        assertThat(zdt.getNano()).isEqualTo(0);
        assertThat(zdt.getDayOfMonth()).isEqualTo(1);
    }

    @Test
    @DisplayName("Two refund rounds with different confirmation dates receive independent expiry timestamps")
    void calculateExpiry_twoRoundsDifferentDatesIndependentExpiries() {
        Instant round1Confirmed = ZonedDateTime.of(2026, 11, 10, 14, 0, 0, 0, ASIA_HO_CHI_MINH).toInstant();
        Instant round2Confirmed = ZonedDateTime.of(2026, 11, 20, 14, 0, 0, 0, ASIA_HO_CHI_MINH).toInstant();

        Instant round1Expiry = D10ExpiryCalculator.calculateExpiry(round1Confirmed);
        Instant round2Expiry = D10ExpiryCalculator.calculateExpiry(round2Confirmed);

        // Round 1 (Nov 10, 20 days remaining) -> expires end of November (2026-12-01)
        assertThat(round1Expiry).isEqualTo(ZonedDateTime.of(2026, 12, 1, 0, 0, 0, 0, ASIA_HO_CHI_MINH).toInstant());

        // Round 2 (Nov 20, 10 days remaining) -> expires end of December (2027-01-01)
        assertThat(round2Expiry).isEqualTo(ZonedDateTime.of(2027, 1, 1, 0, 0, 0, 0, ASIA_HO_CHI_MINH).toInstant());

        assertThat(round1Expiry).isBefore(round2Expiry);
    }

    @Test
    @DisplayName("31-day month: December 16 has 15 days remaining -> expires end of December (2027-01-01)")
    void calculateExpiry_31DayMonth_boundary15Days() {
        Instant dec16 = ZonedDateTime.of(2026, 12, 16, 10, 0, 0, 0, ASIA_HO_CHI_MINH).toInstant();
        Instant expiry16 = D10ExpiryCalculator.calculateExpiry(dec16);
        assertThat(expiry16).isEqualTo(ZonedDateTime.of(2027, 1, 1, 0, 0, 0, 0, ASIA_HO_CHI_MINH).toInstant());

        Instant dec17 = ZonedDateTime.of(2026, 12, 17, 10, 0, 0, 0, ASIA_HO_CHI_MINH).toInstant();
        Instant expiry17 = D10ExpiryCalculator.calculateExpiry(dec17);
        assertThat(expiry17).isEqualTo(ZonedDateTime.of(2027, 2, 1, 0, 0, 0, 0, ASIA_HO_CHI_MINH).toInstant());
    }

    @Test
    @DisplayName("Null input throws IllegalArgumentException")
    void calculateExpiry_nullThrowsException() {
        assertThatThrownBy(() -> D10ExpiryCalculator.calculateExpiry(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("confirmedInstant must not be null");
    }
}
