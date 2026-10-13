package com.oldbutgold.shop.modules.identity.application;

import com.oldbutgold.shop.modules.identity.api.AccountManagementDtos.*;
import com.oldbutgold.shop.modules.identity.infrastructure.persistence.UserRepository;
import com.oldbutgold.shop.modules.platform.application.NotificationService;
import com.oldbutgold.shop.modules.platform.infrastructure.persistence.NotificationRepository;
import com.oldbutgold.shop.modules.trustsafety.application.AccountTrustFacade;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.*;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest @AutoConfigureMockMvc @ActiveProfiles("test")
@EnabledIfEnvironmentVariable(named="OGSHOP_PHASE2_TEST_DB_URL",matches=".+")
class AccountGovernancePostgresTest {
    @DynamicPropertySource static void database(DynamicPropertyRegistry p) {
        String url=System.getenv("OGSHOP_PHASE2_TEST_DB_URL");
        if(!url.matches("jdbc:postgresql://127\\.0\\.0\\.1:[0-9]+/ogshop_phase2_test")) throw new IllegalStateException("Dedicated phase2 test DB required");
        p.add("spring.datasource.url",()->url);p.add("spring.datasource.username",()->System.getenv("OGSHOP_TEST_DB_USER"));
        p.add("spring.datasource.password",()->System.getenv("OGSHOP_TEST_DB_PASSWORD"));p.add("spring.datasource.hikari.connection-init-sql",()->"SET search_path TO og_compat,public");
        p.add("spring.flyway.enabled",()->true);p.add("spring.jpa.hibernate.ddl-auto",()->"validate");
    }
    @Autowired AccountManagementService service; @Autowired AccountAccessService access;
    @Autowired AccountTrustFacade trust; @Autowired JdbcTemplate jdbc; @Autowired MockMvc mvc;
    @Autowired TokenService tokens; @Autowired JwtDecoder decoder; @Autowired UserRepository users;
    @Autowired AuthFlowService login; @Autowired PlatformTransactionManager manager;
    @Autowired IdentityCommerceFacade commerceIdentity;
    @Autowired com.oldbutgold.shop.modules.catalog.application.SellerProductService listings;
    @Autowired NotificationService notifications;
    @MockitoSpyBean NotificationRepository outbox;
    long ktv,buyer,seller,admin;
    String key() { return UUID.randomUUID().toString(); }
    long id(String sql,Object... args) { return jdbc.queryForObject(sql,Long.class,args); }
    long user(String... roles) {
        long id=id("INSERT INTO og_compat.users(email,password_hash,full_name) VALUES(?,'{noop}test-account-1234','User test') RETURNING user_id","u-"+key()+"@example.invalid");
        for(String role:roles) jdbc.update("INSERT INTO og_compat.user_roles(user_id,role_id) SELECT ?,role_id FROM og_compat.roles WHERE role_name=?",id,role);
        return id;
    }
    @BeforeEach void setup() { reset(outbox);ktv=user("KTV");buyer=user("BUYER");seller=user("BUYER","SELLER");admin=user("ADMIN","BUYER"); }
    @AfterEach void finish() { reset(outbox); }
    Command command(Action action,long version,String key,Scope scope) { return new Command(action,version,key,"Quyết định kiểm thử có căn cứ",scope,null,null,null,null,null,scope==null?null:2,null); }
    long version(long user) { return id("SELECT governance_version FROM public.users WHERE user_id=?",user); }
    String bearer(long user) { return tokens.issueAccessToken(users.findById(user).orElseThrow()); }

    @Test void ktvOnlyCustomerOnlyAndSelfPrivacy()throws Exception {
        mvc.perform(get("/api/v1/ktv/users").with(jwt().jwt(j->j.subject(Long.toString(buyer))).authorities(new SimpleGrantedAuthority("ROLE_BUYER")))).andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/ktv/users").with(jwt().jwt(j->j.subject(Long.toString(admin))).authorities(new SimpleGrantedAuthority("ROLE_ADMIN")))).andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/ktv/users").with(jwt().jwt(j->j.subject(Long.toString(buyer))).authorities(new SimpleGrantedAuthority("ROLE_KTV")))).andExpect(status().isForbidden());
        var page=service.list(ktv,"","ALL","ALL",0,50);
        assertThat(page.items()).extracting(Customer::userId).doesNotContain(admin,ktv);
        assertThatThrownBy(()->service.detail(ktv,admin)).isInstanceOf(RegistrationException.class);
        assertThat(service.self(buyer).user().userId()).isEqualTo(buyer);
        assertThatThrownBy(()->service.history(buyer,seller,0,20,true)).isInstanceOf(org.springframework.security.access.AccessDeniedException.class);
    }
    @Test void restrictionIsScopedAndRetryIsStable() {
        String key=key();var c=command(Action.RESTRICT,0,key,Scope.BUY_EKYC);var result=service.command(ktv,buyer,c);
        assertThat(service.command(ktv,buyer,c)).isEqualTo(result);
        assertThat(access.restricted(buyer,"BUY_EKYC")).isTrue();assertThat(access.restricted(buyer,"NEW_TRANSACTIONS")).isFalse();
        assertThat(access.restricted(buyer,"CHAT")).isFalse();
        assertThat(service.self(buyer).restrictions()).hasSize(1);
        assertThat(id("SELECT count(*) FROM public.audit_logs WHERE command_key=?",key)).isEqualTo(1);
        assertThatThrownBy(()->service.command(ktv,seller,c)).isInstanceOf(RegistrationException.class).hasMessageContaining("Mã thao tác");
    }
    @Test void validatesPolicyReasonScopeVersionAndNoAutomaticPunishment()throws Exception {
        assertThatThrownBy(()->service.command(ktv,buyer,command(Action.RESTRICT,0,key(),Scope.NEW_LISTINGS))).isInstanceOf(RegistrationException.class);
        assertThatThrownBy(()->service.command(ktv,buyer,command(Action.LOCK,5,key(),null))).isInstanceOf(RegistrationException.class).hasMessageContaining("đã thay đổi");
        var invalid=new Command(Action.RESTRICT,0L,key(),"Test",Scope.CHAT,null,null,null,null,null,0,null);
        assertThatThrownBy(()->service.command(ktv,buyer,invalid)).isInstanceOf(RegistrationException.class);
        mvc.perform(post("/api/v1/ktv/users/"+buyer+"/commands").with(jwt().jwt(j->j.subject(Long.toString(ktv))).authorities(new SimpleGrantedAuthority("ROLE_KTV")))
            .contentType("application/json").content("{\"action\":\"LOCK\",\"expectedVersion\":0,\"commandKey\":\"test\",\"reason\":\"  \"}")).andExpect(status().isBadRequest());
        assertThat(service.self(buyer).reputation().score()).isEqualTo(100);
        assertThat(id("SELECT count(*) FROM og_compat.penalty_ledger WHERE user_id=?",buyer)).isZero();
    }
    @Test void scopedListingRestrictionBlocksServiceAndSqlSubmissionButKeepsOwnerRead() {
        long category=id("SELECT min(category_id) FROM og_compat.categories");
        long product=new TransactionTemplate(manager).execute(s->{
            long p=id("INSERT INTO og_compat.products(seller_id,category_id,title,description,listed_price,condition) VALUES(?,?,'Fixture','Fixture',80000,'GOOD') RETURNING product_id",seller,category);
            jdbc.update("INSERT INTO og_compat.product_categories(product_id,category_id) VALUES(?,?)",p,category);
            jdbc.update("INSERT INTO og_compat.product_media(product_id,media_type,media_url,display_order,file_size_bytes,duration_seconds) VALUES(?,'IMAGE','https://example.invalid/image',0,100,NULL),(?,'VIDEO','https://example.invalid/video',1,100,10)",p,p);
            return p;
        });
        service.command(ktv,seller,command(Action.RESTRICT,0,key(),Scope.NEW_LISTINGS));
        assertThatThrownBy(()->listings.submitProduct(seller,product)).isInstanceOf(RegistrationException.class);
        assertThatThrownBy(()->jdbc.update("UPDATE og_compat.products SET status='PENDING' WHERE product_id=?",product)).isInstanceOf(org.springframework.dao.DataAccessException.class).rootCause().hasMessageContaining("Account cannot submit");
        assertThat(listings.getSellerProduct(seller,product).productId()).isEqualTo(product);
        assertThat(access.restricted(seller,"CHAT")).isFalse();
    }
    @Test void ekycPurchaseRestrictionDoesNotBlockOtherPurchases() {
        service.command(ktv,buyer,command(Action.RESTRICT,0,key(),Scope.BUY_EKYC));
        commerceIdentity.requirePurchaseAllowed(buyer,seller,false);
        assertThatThrownBy(()->commerceIdentity.requirePurchaseAllowed(buyer,seller,true)).isInstanceOf(RegistrationException.class);
    }
    @Test void purchaseOnlyRestrictionKeepsListingChatAndSellingRights() {
        service.command(ktv,seller,command(Action.RESTRICT,0,key(),Scope.BUY_ALL));
        assertThat(access.restricted(seller,"NEW_LISTINGS")).isFalse();assertThat(access.restricted(seller,"CHAT")).isFalse();
        assertThatThrownBy(()->commerceIdentity.requirePurchaseAllowed(seller,buyer,false)).isInstanceOf(RegistrationException.class);
        commerceIdentity.requirePurchaseAllowed(buyer,seller,false);
        long otherSeller=user("BUYER","SELLER");
        assertThatThrownBy(()->order(seller,otherSeller)).isInstanceOf(org.springframework.dao.DataAccessException.class).rootCause().hasMessageContaining("Account cannot create");
    }
    @Test void lockRevokesOldTokenAndPreservesNewLoginAndOldOrderAccess()throws Exception {
        long order=order(buyer,seller);String old=bearer(buyer);
        service.command(ktv,buyer,command(Action.LOCK,0,key(),null));
        assertThatThrownBy(()->decoder.decode(old)).isInstanceOf(org.springframework.security.oauth2.jwt.JwtException.class);
        var user=users.findById(buyer).orElseThrow();var logged=login.login(user.getEmail(),"test-account-1234","phase2-test");
        assertThat(logged.session()).isNotNull();String current=logged.session().accessToken();
        mvc.perform(get("/api/v1/account").header("Authorization","Bearer "+current)).andExpect(status().isOk()).andExpect(jsonPath("$.user.status").value("LOCKED"));
        mvc.perform(get("/api/v1/commerce/orders/"+order).header("Authorization","Bearer "+current)).andExpect(status().isOk());
        mvc.perform(post("/api/v1/commerce/checkout/buy-now").header("Authorization","Bearer "+current).contentType("application/json").content("{\"productId\":999999}")).andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("ACCOUNT_ACTION_RESTRICTED"));
        assertThat(jdbc.queryForObject("SELECT status FROM og_compat.orders WHERE order_id=?",String.class,order)).isEqualTo("PAYMENT_PENDING");
    }
    @Test void softDeactivationKeepsHistoryAndCanReactivate() {
        service.command(ktv,buyer,command(Action.DEACTIVATE,0,key(),null));
        assertThat(service.self(buyer).user().status()).isEqualTo("DEACTIVATED");
        assertThat(access.restricted(buyer,"NEW_TRANSACTIONS")).isTrue();
        service.command(ktv,buyer,command(Action.ACTIVATE,1,key(),null));
        assertThat(access.restricted(buyer,"NEW_TRANSACTIONS")).isFalse();
        assertThat(service.self(buyer).historyTotal()).isEqualTo(2);
        assertThat(id("SELECT count(*) FROM public.users WHERE user_id=? AND deleted_at IS NULL",buyer)).isEqualTo(1);
    }
    @Test void liftingOneScopeDoesNotRemoveOthers() {
        service.command(ktv,seller,command(Action.RESTRICT,0,key(),Scope.NEW_LISTINGS));
        service.command(ktv,seller,command(Action.RESTRICT,1,key(),Scope.CHAT));
        UUID restriction=service.self(seller).restrictions().stream().filter(r->r.scope().equals("CHAT")).findFirst().orElseThrow().id();
        service.command(ktv,seller,new Command(Action.LIFT,2L,key(),"Đã kiểm tra và gỡ",null,restriction,null,null,null,null,null,null));
        assertThat(access.restricted(seller,"CHAT")).isFalse();assertThat(access.restricted(seller,"NEW_LISTINGS")).isTrue();
        assertThat(service.self(seller).restrictions()).hasSize(2);
    }
    @Test void expirationRestoresPermissionsWithoutRevivingOldSessions()throws Exception {
        String old=bearer(buyer);Instant end=Instant.now().plusSeconds(3);
        service.command(ktv,buyer,new Command(Action.LOCK,0L,key(),"Khóa tạm để kiểm hết hạn",null,null,end,null,null,null,null,null));
        assertThat(access.restricted(buyer,"NEW_TRANSACTIONS")).isTrue();Thread.sleep(3200);
        assertThat(access.restricted(buyer,"NEW_TRANSACTIONS")).isFalse();
        assertThatThrownBy(()->decoder.decode(old)).isInstanceOf(org.springframework.security.oauth2.jwt.JwtException.class);
        assertThat(service.self(buyer).user().status()).isEqualTo("ACTIVE");
    }
    @Test void notificationFailureRollsBackDecisionAndAuditAndRevocation() {
        String key=key();doThrow(new IllegalStateException("test outbox failure")).when(outbox).enqueue(anyString(),anyLong(),anyList());
        assertThatThrownBy(()->service.command(ktv,buyer,command(Action.LOCK,0,key,null))).rootCause().hasMessage("test outbox failure");
        assertThat(version(buyer)).isZero();assertThat(id("SELECT credential_version FROM public.users WHERE user_id=?",buyer)).isZero();
        assertThat(id("SELECT count(*) FROM public.audit_logs WHERE command_key=?",key)).isZero();
    }
    @Test void concurrentDifferentDecisionsHaveOneWinner()throws Exception {
        var start=new CountDownLatch(1);var pool=Executors.newFixedThreadPool(2);
        try {
            Callable<Boolean> run=()->{ start.await();try { service.command(ktv,buyer,command(Action.LOCK,0,key(),null));return true; }catch(RegistrationException e){return false;} };
            var a=pool.submit(run);var b=pool.submit(run);start.countDown();
            assertThat((a.get(20,TimeUnit.SECONDS)?1:0)+(b.get(20,TimeUnit.SECONDS)?1:0)).isEqualTo(1);assertThat(version(buyer)).isEqualTo(1);
        }finally { pool.shutdownNow(); }
    }
    @Test void databaseRejectsUnauditedStateAndNewOrdersAfterLock() {
        assertThatThrownBy(()->jdbc.update("UPDATE public.users SET governance_state='LOCKED',governance_reason='Bypass',governance_version=1 WHERE user_id=?",buyer)).isInstanceOf(org.springframework.dao.DataAccessException.class);
        service.command(ktv,buyer,command(Action.LOCK,0,key(),null));
        assertThatThrownBy(()->order(buyer,seller)).isInstanceOf(org.springframework.dao.DataAccessException.class);
    }
    @Test void accountNotificationsArePrivateAndKeepUserTargets()throws Exception {
        service.command(ktv,buyer,command(Action.LOCK,0,key(),null));
        while(notifications.dispatchBatch()>0) { }
        var own=notifications.list(buyer,0,20);
        assertThat(own.items()).hasSize(1);assertThat(own.items().getFirst().destination()).isEqualTo("ACCOUNT_SELF");
        assertThat(own.items().getFirst().userId()).isEqualTo(buyer);assertThat(own.items().getFirst().productId()).isNull();
        assertThat(notifications.list(ktv,0,20).items()).hasSize(1);assertThat(notifications.list(seller,0,20).items()).isEmpty();
    }
    @Test void limitedUserCanRespondToOwnedCaseAndCannotReadAnotherCase()throws Exception {
        long caseId=id("INSERT INTO og_compat.cases(case_type,target_user_id,created_by,description,command_key) VALUES('SYSTEM_ISSUE',?,?, 'Test issue',?) RETURNING case_id",seller,buyer,key());
        service.command(ktv,buyer,command(Action.LOCK,0,key(),null));
        String key=key();UUID first=trust.respond(buyer,caseId,"Phản hồi trên hồ sơ cũ",key);
        assertThat(trust.respond(buyer,caseId,"Phản hồi trên hồ sơ cũ",key)).isEqualTo(first);
        assertThat(trust.responses(buyer,caseId)).hasSize(1);
        assertThatThrownBy(()->trust.responses(seller,caseId)).isInstanceOf(org.springframework.web.server.ResponseStatusException.class);
        assertThatThrownBy(()->trust.respond(seller,caseId,"Không phải bên của hồ sơ",key())).isInstanceOf(org.springframework.web.server.ResponseStatusException.class);
        assertThatThrownBy(()->jdbc.update("INSERT INTO og_compat.case_events(event_id,case_id,actor_id,event_type,reason,metadata,command_key) VALUES(?,?,?,'SUPPLEMENTED','Bypass','{\"partyResponse\":true}',?)",UUID.randomUUID(),caseId,seller,key())).isInstanceOf(org.springframework.dao.DataAccessException.class);
    }
    @Test void reputationUsesConfirmedLedgerAndDoesNotAutomaticallyLock() {
        long policy=id("INSERT INTO og_compat.penalty_policies(policy_code,thresholds,created_by) VALUES(?,'[]',?) RETURNING policy_id",key(),admin);
        long report=id("INSERT INTO og_compat.reports(reporter_id,reported_user_id,reason,status,resolved_by,resolved_at) VALUES(?,?,'OTHER','RESOLVED',?,CURRENT_TIMESTAMP) RETURNING report_id",buyer,seller,ktv);
        jdbc.update("INSERT INTO og_compat.penalty_ledger(entry_id,user_id,report_id,policy_id,points_delta,decided_by,reason,command_key) VALUES(?,?,?,?,15,?,'Quyết định xử phạt fixture',?)",UUID.randomUUID(),seller,report,policy,ktv,key());
        assertThat(service.self(seller).reputation().score()).isEqualTo(85);assertThat(service.self(seller).user().status()).isEqualTo("ACTIVE");
        assertThat(access.restricted(seller,"NEW_TRANSACTIONS")).isFalse();
        assertThat(service.self(buyer).reputation().events()).isEmpty();
    }
    @Test void compensationRequiresConfirmedExactOutageAndExtendsOnlyCaseDeadline() {
        long source=id("INSERT INTO og_compat.cases(case_type,target_user_id,created_by,description,command_key) VALUES('SYSTEM_ISSUE',?,?,'Confirmed outage fixture',?) RETURNING case_id",buyer,seller,key());
        Instant from=Instant.now().minusSeconds(7200).truncatedTo(java.time.temporal.ChronoUnit.SECONDS),until=from.plusSeconds(3600);
        Command c=new Command(Action.COMPENSATE,0L,key(),"Chưa có xác nhận gián đoạn",null,null,null,source,from,until,null,source);
        assertThatThrownBy(()->service.command(ktv,seller,c)).isInstanceOf(RegistrationException.class).hasMessageContaining("đã xác nhận");
        assertThat(version(seller)).isZero();
        assertThat(service.self(seller).user().status()).isEqualTo("ACTIVE");assertThat(service.self(seller).restrictions()).isEmpty();
    }
    long order(long buyer,long seller) {
        return id("""
            INSERT INTO og_compat.orders(checkout_group_id,buyer_id,seller_id,shipping_recipient_name,shipping_phone_number,
              shipping_province,shipping_district,shipping_ward,shipping_detail_address,subtotal,shipping_fee,total_amount,buyer_system_fee,seller_system_fee,seller_proceeds,payment_due_at)
            VALUES(?,?,?,'Test','0900000000','Test','Test','Test','Test',50000,0,50000,0,0,50000,CURRENT_TIMESTAMP+INTERVAL '1 hour') RETURNING order_id
            """,UUID.randomUUID(),buyer,seller);
    }
}
