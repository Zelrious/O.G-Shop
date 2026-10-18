package com.oldbutgold.shop.modules.catalog.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.oldbutgold.shop.modules.catalog.api.CatalogDtos;
import com.oldbutgold.shop.modules.platform.application.NotificationService;
import com.oldbutgold.shop.modules.platform.application.PlatformNotificationFacade;
import com.oldbutgold.shop.modules.platform.infrastructure.persistence.AuditLogRepository;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.PlatformTransactionManager;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.*;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@EnabledIfEnvironmentVariable(named="OGSHOP_PHASE1_TEST_DB_URL",matches=".+")
class ListingWorkflowPostgresTest {
    @DynamicPropertySource static void database(DynamicPropertyRegistry p) {
        String url=System.getenv("OGSHOP_PHASE1_TEST_DB_URL");
        if(!url.matches("jdbc:postgresql://127\\.0\\.0\\.1:[0-9]+/ogshop_phase1_test")) throw new IllegalStateException("Dedicated phase1 test DB required");
        p.add("spring.datasource.url",()->url);p.add("spring.datasource.username",()->System.getenv("OGSHOP_TEST_DB_USER"));
        p.add("spring.datasource.password",()->System.getenv("OGSHOP_TEST_DB_PASSWORD"));
        p.add("spring.datasource.hikari.connection-init-sql",()->"SET search_path TO og_compat,public");
        p.add("spring.flyway.enabled",()->true);p.add("spring.jpa.hibernate.ddl-auto",()->"validate");
    }
    @Autowired SellerProductService listings;
    @Autowired ListingWorkflowService workflow;
    @Autowired NotificationService notifications;
    @Autowired PlatformNotificationFacade noticeFacade;
    @Autowired PlatformTransactionManager transactions;
    @Autowired JdbcTemplate jdbc;
    @Autowired MockMvc http;
    @Autowired ObjectMapper json;
    @MockitoSpyBean AuditLogRepository audit;
    long seller,ktv,other,category;
    String key() { return UUID.randomUUID().toString(); }
    long id(String sql,Object... args) { return jdbc.queryForObject(sql,Long.class,args); }
    long user(String role) {
        long value=id("INSERT INTO og_compat.users(email,password_hash,full_name) VALUES(?,'FAKE','Test account') RETURNING user_id","test-"+key()+"@example.invalid");
        jdbc.update("INSERT INTO og_compat.user_roles(user_id,role_id) SELECT ?,role_id FROM og_compat.roles WHERE role_name=?",value,role);
        return value;
    }
    @BeforeEach void setup() { reset(audit);seller=user("SELLER");ktv=user("KTV");other=user("SELLER");category=id("SELECT min(category_id) FROM og_compat.categories"); }
    @AfterEach void finish() { reset(audit); }
    CatalogDtos.CreateOrUpdateProductRequest content(int price,Long version) {
        return new CatalogDtos.CreateOrUpdateProductRequest(category,"Máy ảnh kiểm thử","Mô tả thật",BigDecimal.valueOf(price),"GOOD",
            "6 tháng","Xước ở góc","Đã thay pin","Dây đeo","TP. Hồ Chí Minh",false,version,List.of(category));
    }
    long pending(int price) {
        long p=listings.createProduct(seller,content(price,null)).productId();
        jdbc.update("INSERT INTO og_compat.product_media(product_id,media_type,media_url,display_order,file_size_bytes,duration_seconds) VALUES(?,'IMAGE','https://example.invalid/image.jpg',0,100,NULL),(?,'VIDEO','https://example.invalid/video.mp4',1,100,10)",p,p);
        listings.submitProduct(seller,p);return p;
    }
    long version(long p) { return id("SELECT version FROM og_compat.products WHERE product_id=?",p); }
    String state(long p) { return jdbc.queryForObject("SELECT status FROM og_compat.products WHERE product_id=?",String.class,p); }
    void approve(long p) { listings.approveProduct(ktv,p,version(p),key()); }
    void pay(long p) { var f=workflow.getFee(seller,p);workflow.payMock(seller,p,f.chargeId(),f.version(),key()); }

    @Test void reviewContractAndApprovalRequireFeeBeforePublicVisibility() throws Exception {
        long p=pending(80000);
        var reviewer=jwt().jwt(t->t.subject(Long.toString(ktv))).authorities(new SimpleGrantedAuthority("ROLE_KTV"));
        http.perform(get("/api/v1/moderation/products/"+p).with(reviewer)).andExpect(status().isOk())
            .andExpect(jsonPath("$.defects").value("Xước ở góc")).andExpect(jsonPath("$.repairHistory").value("Đã thay pin"))
            .andExpect(jsonPath("$.usageDuration").value("6 tháng")).andExpect(jsonPath("$.includedAccessories").value("Dây đeo"));
        approve(p);
        assertThat(state(p)).isEqualTo("APPROVED_AWAITING_FEE");
        assertThat(workflow.getFee(seller,p).amountDue()).isEqualByComparingTo("10000");
        http.perform(get("/api/v1/products/"+p)).andExpect(status().isNotFound());
        assertThat(id("SELECT count(*) FROM og_compat.reward_ledger WHERE user_id=?",seller)).isZero();
        assertThatThrownBy(()->listings.publishProduct(seller,p)).isInstanceOf(ProductStateConflictException.class);
        assertThatThrownBy(()->jdbc.update("UPDATE public.products SET status='ACTIVE',public_revision_id=current_revision_id WHERE product_id=?",p))
            .isInstanceOf(org.springframework.dao.DataAccessException.class);
        pay(p);assertThat(state(p)).isEqualTo("ACTIVE");
        http.perform(get("/api/v1/products/"+p)).andExpect(status().isOk());
    }

    @Test void paymentRetryIsIdempotentAndFirstRewardIsOncePerSeller() {
        long p=pending(80000);approve(p);var f=workflow.getFee(seller,p);String payment=key();
        workflow.payMock(seller,p,f.chargeId(),f.version(),payment);
        workflow.payMock(seller,p,f.chargeId(),f.version(),payment);
        assertThat(id("SELECT count(*) FROM og_compat.listing_fee_receipts WHERE charge_id=?",f.chargeId())).isEqualTo(1);
        long second=pending(80000);approve(second);pay(second);
        assertThat(id("SELECT count(*) FROM og_compat.reward_ledger WHERE user_id=? AND entry_type='CREDIT_FIRST_LISTING'",seller)).isEqualTo(1);
        assertThat(id("SELECT balance FROM og_compat.reward_accounts WHERE user_id=?",seller)).isEqualTo(10000);
        var f2=workflow.getFee(seller,second);
        assertThatThrownBy(()->workflow.payMock(seller,second,f2.chargeId(),f2.version(),payment)).isInstanceOf(CommandKeyConflictException.class);
    }

    @Test void increasedPriceChargesOnlyTheDifferenceAndDecreasesNeverRefund() {
        long p=pending(80000);approve(p);pay(p);
        listings.hideProduct(seller,p);listings.updateProduct(seller,p,content(200000,version(p)));listings.submitProduct(seller,p);approve(p);
        assertThat(workflow.getFee(seller,p).amountDue()).isEqualByComparingTo("10000");pay(p);
        listings.hideProduct(seller,p);listings.updateProduct(seller,p,content(150000,version(p)));listings.submitProduct(seller,p);approve(p);
        assertThat(state(p)).isEqualTo("ACTIVE");assertThat(workflow.getFee(seller,p).amountDue()).isZero();
        assertThat(workflow.getFee(seller,p).totalPaid()).isEqualByComparingTo("20000");
        assertThat(id("SELECT count(*) FROM og_compat.listing_fee_charges c JOIN og_compat.listing_fee_assessments a USING(assessment_id) WHERE a.product_id=?",p)).isEqualTo(2);
    }

    @Test void staleReviewAndStalePaymentCannotChangeTheDecision() {
        long p=pending(80000);
        assertThatThrownBy(()->listings.approveProduct(ktv,p,version(p)-1,key())).isInstanceOf(ProductVersionConflictException.class);
        approve(p);var f=workflow.getFee(seller,p);
        assertThatThrownBy(()->workflow.payMock(seller,p,f.chargeId(),f.version()-1,key())).isInstanceOf(ProductVersionConflictException.class);
        assertThat(state(p)).isEqualTo("APPROVED_AWAITING_FEE");
        assertThat(id("SELECT count(*) FROM og_compat.listing_fee_receipts WHERE charge_id=?",f.chargeId())).isZero();
    }

    @Test void listingFeeUsesApprovedQuantityAndTheThresholdBoundary() {
        long p=pending(80000);
        jdbc.update("UPDATE og_compat.products SET quantity_total=5 WHERE product_id=?",p);
        approve(p);
        assertThat(workflow.getFee(seller,p).listedTotal()).isEqualByComparingTo("400000");
        assertThat(workflow.getFee(seller,p).amountDue()).isEqualByComparingTo("40000");
        long boundary=pending(100000);approve(boundary);
        assertThat(workflow.getFee(seller,boundary).amountDue()).isEqualByComparingTo("10000");
    }

    @Test void concurrentPaymentHasOneReceiptAndOneReward() throws Exception {
        long p=pending(80000);approve(p);var f=workflow.getFee(seller,p);String payment=key();
        var pool=Executors.newFixedThreadPool(2);CountDownLatch start=new CountDownLatch(1);
        try {
            Callable<String> action=()->{start.await();return workflow.payMock(seller,p,f.chargeId(),f.version(),payment).status();};
            var first=pool.submit(action);var second=pool.submit(action);start.countDown();
            assertThat(first.get(30,TimeUnit.SECONDS)).isEqualTo("ACTIVE");assertThat(second.get(30,TimeUnit.SECONDS)).isEqualTo("ACTIVE");
        } finally {pool.shutdownNow();}
        assertThat(id("SELECT count(*) FROM og_compat.listing_fee_receipts WHERE charge_id=?",f.chargeId())).isEqualTo(1);
        assertThat(id("SELECT count(*) FROM og_compat.reward_ledger WHERE user_id=? AND entry_type='CREDIT_FIRST_LISTING'",seller)).isEqualTo(1);
    }

    @Test void editingAnOpenListingHidesItUntilItsNewRevisionIsApproved() {
        long p=pending(80000);approve(p);pay(p);
        listings.updateProduct(seller,p,content(200000,version(p)));
        assertThat(state(p)).isEqualTo("HIDDEN");
        assertThatThrownBy(()->listings.publishProduct(seller,p)).isInstanceOf(ProductStateConflictException.class);
        listings.submitProduct(seller,p);approve(p);
        assertThat(state(p)).isEqualTo("APPROVED_AWAITING_FEE");
    }

    @Test void competingModeratorsProduceOneDecisionAndOneAssessment() throws Exception {
        long p=pending(80000),v=version(p);var pool=Executors.newFixedThreadPool(2);CountDownLatch start=new CountDownLatch(1);
        try {
            Callable<Boolean> action=()->{start.await();try {listings.approveProduct(ktv,p,v,key());return true;}catch(ProductStateConflictException|ProductVersionConflictException e){return false;}};
            var first=pool.submit(action);var second=pool.submit(action);start.countDown();
            assertThat((first.get(30,TimeUnit.SECONDS)?1:0)+(second.get(30,TimeUnit.SECONDS)?1:0)).isEqualTo(1);
        } finally {pool.shutdownNow();}
        assertThat(id("SELECT count(*) FROM og_compat.product_moderation_decisions WHERE product_id=?",p)).isEqualTo(1);
        assertThat(id("SELECT count(*) FROM og_compat.listing_fee_assessments WHERE product_id=?",p)).isEqualTo(1);
    }

    @Test void auditFailureRollsBackDecisionFeeSnapshotAndOutbox() {
        long p=pending(80000);
        doThrow(new IllegalStateException("Test audit unavailable")).when(audit).save(any());
        assertThatThrownBy(()->approve(p)).isInstanceOf(org.springframework.dao.DataAccessException.class)
            .hasMessageContaining("Test audit unavailable");
        assertThat(state(p)).isEqualTo("PENDING");
        assertThat(id("SELECT count(*) FROM og_compat.product_revisions WHERE product_id=? AND provenance='MANUAL_V27'",p)).isZero();
        assertThat(id("SELECT count(*) FROM og_compat.listing_fee_assessments WHERE product_id=?",p)).isZero();
        assertThat(id("SELECT count(*) FROM og_compat.product_moderation_decisions WHERE product_id=?",p)).isZero();
    }

    @Test void failedPaymentAuditRollsBackReceiptOpeningAndReward() {
        long p=pending(80000);approve(p);var f=workflow.getFee(seller,p);
        doThrow(new IllegalStateException("Test payment audit unavailable")).when(audit).save(any());
        assertThatThrownBy(()->workflow.payMock(seller,p,f.chargeId(),f.version(),key()))
            .isInstanceOf(org.springframework.dao.DataAccessException.class);
        assertThat(state(p)).isEqualTo("APPROVED_AWAITING_FEE");
        assertThat(id("SELECT count(*) FROM og_compat.listing_fee_receipts WHERE charge_id=?",f.chargeId())).isZero();
        assertThat(workflow.getFee(seller,p).paymentState()).isEqualTo("PENDING");
        assertThat(id("SELECT count(*) FROM og_compat.reward_ledger WHERE user_id=?",seller)).isZero();
    }

    @Test void approvalReplayAfterPaymentKeepsItsOriginalDecisionResult() {
        long p=pending(80000),v=version(p);String command=key();
        var original=listings.approveProduct(ktv,p,v,command);pay(p);
        assertThat(state(p)).isEqualTo("ACTIVE");
        assertThat(listings.approveProduct(ktv,p,v,command)).isEqualTo(original);
        assertThat(id("SELECT count(*) FROM og_compat.listing_fee_assessments WHERE product_id=?",p)).isEqualTo(1);
    }

    @Test void rejectionHistoryAndNotificationsArePrivateAndDeliveryCanRetry() throws Exception {
        long p=pending(80000);listings.rejectProduct(ktv,p,"Quay rõ góc bị xước",version(p),key());
        assertThat(listings.getSellerProduct(seller,p).rejectionReason()).isEqualTo("Quay rõ góc bị xước");
        drainNotifications();
        assertThat(id("SELECT count(*) FROM public.notifications WHERE user_id=? AND reference_id=? AND type='LISTING_REVIEWED'",seller,p)).isEqualTo(1);
        long notification=id("SELECT notification_id FROM public.notifications WHERE user_id=? AND reference_id=? AND type='LISTING_REVIEWED'",seller,p);
        var owner=jwt().jwt(t->t.subject(Long.toString(seller))).authorities(new SimpleGrantedAuthority("ROLE_SELLER"));
        var intruder=jwt().jwt(t->t.subject(Long.toString(other))).authorities(new SimpleGrantedAuthority("ROLE_SELLER"));
        http.perform(get("/api/v1/seller/products/"+p+"/listing-fee").with(intruder)).andExpect(status().isNotFound());
        http.perform(post("/api/v1/notifications/"+notification+"/read").with(intruder)).andExpect(status().isNotFound());
        http.perform(post("/api/v1/notifications/"+notification+"/read").with(owner)).andExpect(status().isNoContent());
        assertThat(jdbc.queryForObject("SELECT is_read FROM public.notifications WHERE notification_id=?",Boolean.class,notification)).isTrue();
        http.perform(get("/api/v1/moderation/products/"+p).with(owner)).andExpect(status().isForbidden());
    }

    @Test void notificationFailureDoesNotRollBackBusinessAndDeliveryRollsBackAtomically() {
        long p=pending(80000);approve(p);String event="TEST_BAD:"+key();
        drainNotifications();
        new TransactionTemplate(transactions).executeWithoutResult(tx->noticeFacade.enqueue(event,p,List.of(
            new PlatformNotificationFacade.Notice(seller,"TEST","Test","Test",PlatformNotificationFacade.Destination.SELLER_LISTING,p,1),
            new PlatformNotificationFacade.Notice(Long.MAX_VALUE,"TEST","Test","Test",PlatformNotificationFacade.Destination.SELLER_LISTING,p,1))));
        UUID eventId=UUID.nameUUIDFromBytes(("og-listing:"+event).getBytes(java.nio.charset.StandardCharsets.UTF_8));
        try {
            assertThatThrownBy(()->notifications.dispatchBatch()).isInstanceOf(org.springframework.dao.DataAccessException.class);
            assertThat(state(p)).isEqualTo("APPROVED_AWAITING_FEE");
            assertThat(id("SELECT count(*) FROM public.notifications WHERE reference_id=? AND type='TEST'",p)).isZero();
            assertThat(jdbc.queryForObject("SELECT published_at FROM public.outbox_events WHERE event_id=?",Object.class,eventId)).isNull();
        } finally { jdbc.update("DELETE FROM public.outbox_events WHERE event_id=?",eventId); }
        drainNotifications();
        assertThat(id("SELECT count(*) FROM public.notifications WHERE user_id=? AND reference_id=? AND type='LISTING_REVIEWED'",seller,p)).isEqualTo(1);
    }
    private void drainNotifications() {
        for(int attempt=0;attempt<100;attempt++) if(notifications.dispatchBatch()==0) return;
        throw new AssertionError("Test notification queue did not drain");
    }
}
