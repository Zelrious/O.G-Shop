package com.oldbutgold.shop.modules.commerce.application;

import java.util.Objects;
import java.util.concurrent.Callable;

/**
 * TEST-ONLY FIXTURE: Simulated PO Approval Gate Transition for DEC-10.7A / GAP-1.
 * <p>
 * <b>CRITICAL ARCHITECTURAL CONSTRAINTS:</b>
 * <ul>
 *   <li>DEC-10.7A remains <b>OPEN</b>. No Coin Redemption Cap percentage has been approved by the PO.</li>
 *   <li>In production, {@code capPoApproved} defaults to {@code false} and must NEVER be changed.</li>
 *   <li>This fixture exists <i>strictly in test source</i> to simulate PO approval for downstream
 *       business logic tests (e.g. order creation, coin debit ledger records, IPN concurrency,
 *       and atomic transaction rollback).</li>
 *   <li>Any technical cap value configured in tests (e.g., "50") is for test execution only and
 *       does NOT constitute a business approval or approved ratio.</li>
 *   <li>The fixture guarantees automatic cleanup via {@link AutoCloseable} or try-finally to prevent
 *       state leakage between tests on shared Spring singleton beans.</li>
 * </ul>
 */
public final class SimulatedPoApprovalTestFixture {

    private SimulatedPoApprovalTestFixture() {
        // Test utility class - no instantiation
    }

    /**
     * AutoCloseable scope interface for try-with-resources blocks.
     */
    @FunctionalInterface
    public interface Scope extends AutoCloseable {
        @Override
        void close();
    }

    /**
     * Opens an AutoCloseable simulation scope for {@link CheckoutService}.
     * When closed, {@code capPoApproved} is guaranteed to reset to {@code false}.
     *
     * @param checkoutService the checkout service singleton under test
     * @return a {@link Scope} that resets approval state upon {@link Scope#close()}
     */
    public static Scope openScope(CheckoutService checkoutService) {
        Objects.requireNonNull(checkoutService, "checkoutService must not be null");
        synchronized (checkoutService) {
            checkoutService.setCapPoApproved(true);
        }
        return () -> {
            synchronized (checkoutService) {
                checkoutService.setCapPoApproved(false);
            }
        };
    }

    /**
     * Opens an AutoCloseable simulation scope for {@link GroupCheckoutService}.
     * When closed, {@code capPoApproved} is guaranteed to reset to {@code false}.
     *
     * @param groupCheckoutService the group checkout service singleton under test
     * @return a {@link Scope} that resets approval state upon {@link Scope#close()}
     */
    public static Scope openScope(GroupCheckoutService groupCheckoutService) {
        Objects.requireNonNull(groupCheckoutService, "groupCheckoutService must not be null");
        synchronized (groupCheckoutService) {
            groupCheckoutService.setCapPoApproved(true);
        }
        return () -> {
            synchronized (groupCheckoutService) {
                groupCheckoutService.setCapPoApproved(false);
            }
        };
    }

    /**
     * Executes a runnable within a simulated PO approval scope for {@link CheckoutService}.
     *
     * @param checkoutService the service under test
     * @param action the test action to execute
     */
    public static void runWithPoApproval(CheckoutService checkoutService, Runnable action) {
        Objects.requireNonNull(action, "action must not be null");
        try (Scope ignored = openScope(checkoutService)) {
            action.run();
        }
    }

    /**
     * Executes a callable within a simulated PO approval scope for {@link CheckoutService}.
     *
     * @param checkoutService the service under test
     * @param callable the test action returning a value
     * @param <T> the return type
     * @return the result of callable
     * @throws Exception if callable throws
     */
    public static <T> T callWithPoApproval(CheckoutService checkoutService, Callable<T> callable) throws Exception {
        Objects.requireNonNull(callable, "callable must not be null");
        try (Scope ignored = openScope(checkoutService)) {
            return callable.call();
        }
    }

    /**
     * Executes a runnable within a simulated PO approval scope for {@link GroupCheckoutService}.
     *
     * @param groupCheckoutService the service under test
     * @param action the test action to execute
     */
    public static void runWithPoApproval(GroupCheckoutService groupCheckoutService, Runnable action) {
        Objects.requireNonNull(action, "action must not be null");
        try (Scope ignored = openScope(groupCheckoutService)) {
            action.run();
        }
    }

    /**
     * Executes a callable within a simulated PO approval scope for {@link GroupCheckoutService}.
     *
     * @param groupCheckoutService the service under test
     * @param callable the test action returning a value
     * @param <T> the return type
     * @return the result of callable
     * @throws Exception if callable throws
     */
    public static <T> T callWithPoApproval(GroupCheckoutService groupCheckoutService, Callable<T> callable) throws Exception {
        Objects.requireNonNull(callable, "callable must not be null");
        try (Scope ignored = openScope(groupCheckoutService)) {
            return callable.call();
        }
    }

    /**
     * Explicitly enables simulated PO approval for {@link CheckoutService}.
     * Prefer {@link #openScope(CheckoutService)} with try-with-resources to ensure cleanup.
     * If used, caller MUST call {@link #resetPoApprovalAfterTest(CheckoutService)} in a {@code finally} block.
     */
    public static void enablePoApprovalForTest(CheckoutService checkoutService) {
        if (checkoutService != null) {
            synchronized (checkoutService) {
                checkoutService.setCapPoApproved(true);
            }
        }
    }

    /**
     * Explicitly enables simulated PO approval for {@link GroupCheckoutService}.
     * Prefer {@link #openScope(GroupCheckoutService)} with try-with-resources to ensure cleanup.
     * If used, caller MUST call {@link #resetPoApprovalAfterTest(GroupCheckoutService)} in a {@code finally} block.
     */
    public static void enablePoApprovalForTest(GroupCheckoutService groupCheckoutService) {
        if (groupCheckoutService != null) {
            synchronized (groupCheckoutService) {
                groupCheckoutService.setCapPoApproved(true);
            }
        }
    }

    /**
     * Resets simulated PO approval to {@code false} (default fail-closed state) for {@link CheckoutService}.
     */
    public static void resetPoApprovalAfterTest(CheckoutService checkoutService) {
        if (checkoutService != null) {
            synchronized (checkoutService) {
                checkoutService.setCapPoApproved(false);
            }
        }
    }

    /**
     * Resets simulated PO approval to {@code false} (default fail-closed state) for {@link GroupCheckoutService}.
     */
    public static void resetPoApprovalAfterTest(GroupCheckoutService groupCheckoutService) {
        if (groupCheckoutService != null) {
            synchronized (groupCheckoutService) {
                groupCheckoutService.setCapPoApproved(false);
            }
        }
    }
}

