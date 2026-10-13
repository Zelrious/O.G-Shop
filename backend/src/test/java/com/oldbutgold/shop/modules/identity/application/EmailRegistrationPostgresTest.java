package com.oldbutgold.shop.modules.identity.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.oldbutgold.shop.modules.identity.infrastructure.persistence.RegistrationRepository;
import com.oldbutgold.shop.modules.platform.infrastructure.LegacyModerationHistoryCallback;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.*;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Real JPA, JDBC, Flyway and transactions; only the external email provider is replaced. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DirtiesContext
@EnabledIfEnvironmentVariable(named="OGSHOP_EXPANSION_TEST_DB_URL", matches=".+")
class EmailRegistrationPostgresTest {
    private static String database;
    private static JdbcTemplate root;
    private static long legacyId;
    private static String sourceUrl(String name) {
        String url=System.getenv("OGSHOP_EXPANSION_TEST_DB_URL");
        if (url==null || !url.matches("jdbc:postgresql://(127\\.0\\.0\\.1|localhost):[0-9]+/ogshop_[a-z0-9_]*test[a-z0-9_]*"))
            throw new IllegalStateException("Use an explicit local test database URL");
        return url.substring(0,url.lastIndexOf('/')+1)+name;
    }
    private static DriverManagerDataSource source(String name) {
        return new DriverManagerDataSource(sourceUrl(name), System.getenv("OGSHOP_TEST_DB_USER"),
                System.getenv("OGSHOP_TEST_DB_PASSWORD"));
    }
    @DynamicPropertySource static void database(DynamicPropertyRegistry properties) {
        database="ogshop_registration_"+UUID.randomUUID().toString().replace("-","");
        root=new JdbcTemplate(source("postgres"));
        root.execute("CREATE DATABASE "+database);
        var data=source(database);
        Flyway.configure().dataSource(data).defaultSchema("public").schemas("public")
                .callbacks(new LegacyModerationHistoryCallback()).target("24").load().migrate();
        // Shared V24 account is preserved when the application applies V25.
        legacyId=new JdbcTemplate(data).queryForObject("""
                INSERT INTO og_compat.users(email,password_hash,full_name)
                VALUES('legacy@example.invalid','{noop}short6','Legacy') RETURNING user_id
                """,Long.class);
        new JdbcTemplate(data).update("""
                INSERT INTO og_compat.user_roles(user_id,role_id)
                SELECT ?,role_id FROM og_compat.roles WHERE role_name='BUYER'
                """,legacyId);
        properties.add("spring.datasource.url",()->sourceUrl(database)+"?currentSchema=og_compat,public");
        properties.add("spring.datasource.username",()->System.getenv("OGSHOP_TEST_DB_USER"));
        properties.add("spring.datasource.password",()->System.getenv("OGSHOP_TEST_DB_PASSWORD"));
        properties.add("spring.jpa.hibernate.ddl-auto",()->"validate");
        properties.add("spring.flyway.enabled",()->true);
        properties.add("app.registration.ip-hourly-limit",()->20);
    }
    @AfterAll static void cleanup() {
        if(root!=null && database!=null && database.matches("ogshop_registration_[0-9a-f]{32}"))
            root.execute("DROP DATABASE "+database+" WITH (FORCE)");
    }
    @TestConfiguration static class TimeConfiguration {
        @Bean @Primary TestClock registrationTestClock() { return new TestClock(); }
    }
    static class TestClock extends Clock {
        private volatile Instant instant=Instant.now();
        void reset() { instant=Instant.now(); }
        void advance(long seconds) { instant=instant.plusSeconds(seconds); }
        @Override public Instant instant() { return instant; }
        @Override public ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(ZoneId zone) { return this; }
    }
    @Autowired EmailRegistrationService service;
    @Autowired AuthService auth;
    @Autowired SellerActivationService activation;
    @Autowired RegistrationRepository drafts;
    @Autowired TestClock clock;
    @Autowired JdbcTemplate jdbc;
    @Autowired MockMvc http;
    @Autowired ObjectMapper json;
    @MockitoBean RegistrationEmailPort mail;
    private final Map<String,String> codes=new ConcurrentHashMap<>();
    private final AtomicBoolean failMail=new AtomicBoolean();
    private String email;
    private String requester;
    @BeforeEach void prepare() {
        clock.reset(); codes.clear(); failMail.set(false);
        email=UUID.randomUUID()+"@example.invalid"; requester=UUID.randomUUID().toString();
        doAnswer(call->{
            if(failMail.get()) throw new RegistrationException("OTP_EMAIL_UNAVAILABLE","Email unavailable",503);
            codes.put(call.getArgument(0),call.getArgument(1)); return null;
        }).when(mail).sendCode(anyString(),anyString(),any());
    }
    private EmailRegistrationService.Input buyer() {
        return new EmailRegistrationService.Input("Test Buyer",email,null,"Password123@",
                RegistrationDraft.Type.BUYER,null);
    }
    private long count(String table) {
        return jdbc.queryForObject("SELECT count(*) FROM public."+table+" WHERE email=?",Long.class,email);
    }
    private void fails(String code,Runnable action) {
        assertThatThrownBy(action::run).isInstanceOfSatisfying(RegistrationException.class,
                error->assertThat(error.code()).isEqualTo(code));
    }
    @Test void pendingHasNoUserOrTokenAndWrongAttemptsCommitThenReplayIsRejected() throws Exception {
        var response=http.perform(post("/api/v1/auth/register").header("Origin","http://localhost:5173")
                .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(buyer())))
                .andExpect(status().isAccepted()).andExpect(header().doesNotExist("Set-Cookie"))
                .andExpect(jsonPath("$.accessToken").doesNotExist())
                .andExpect(jsonPath("$.user").doesNotExist()).andReturn();
        var id=UUID.fromString(json.readTree(response.getResponse().getContentAsString()).get("challengeId").asText());
        assertThat(count("users")).isZero();
        String stored=jdbc.queryForObject("SELECT registration_data::text FROM public.auth_challenges WHERE challenge_id=?",String.class,id);
        assertThat(stored).doesNotContain("Password123@").contains("{bcrypt}");
        String wrong=codes.get(email).equals("000000")?"000001":"000000";
        fails("OTP_INVALID",()->service.verify(id,wrong));
        assertThat(jdbc.queryForObject("SELECT attempt_count FROM public.auth_challenges WHERE challenge_id=?",Integer.class,id)).isEqualTo(1);
        assertThat(count("users")).isZero();
        var result=service.verify(id,codes.get(email));
        assertThat(result.awardedCoins()).isEqualTo(3000);
        assertThat(result.session().expiresIn()).isEqualTo(900);
        long user=result.session().user().getId();
        assertThat(jdbc.queryForObject("SELECT reward_balance FROM public.users WHERE user_id=?",Long.class,user)).isEqualTo(3000);
        assertThat(jdbc.queryForObject("SELECT registration_data IS NULL FROM public.auth_challenges WHERE challenge_id=?",Boolean.class,id)).isTrue();
        fails("OTP_UNAVAILABLE",()->service.verify(id,codes.get(email)));
        assertThat(count("users")).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM public.reward_ledger WHERE user_id=? AND entry_type='CREDIT_REGISTRATION'",Long.class,user)).isEqualTo(1);
    }
    @Test void attemptsCooldownResendExpiryAndCancellationHaveNoAccountSideEffects() {
        var first=service.start(buyer(),requester);
        fails("OTP_RESEND_TOO_SOON",()->service.resend(first.id(),requester));
        String wrong=codes.get(email).equals("000000")?"000001":"000000";
        for(int i=0;i<4;i++) fails("OTP_INVALID",()->service.verify(first.id(),wrong));
        fails("OTP_ATTEMPTS_EXHAUSTED",()->service.verify(first.id(),wrong));
        fails("OTP_ATTEMPTS_EXHAUSTED",()->service.verify(first.id(),codes.get(email)));
        clock.advance(301);
        fails("OTP_EXPIRED",()->service.verify(first.id(),codes.get(email)));
        var second=service.resend(first.id(),requester);
        assertThat(second.id()).isNotEqualTo(first.id());
        fails("OTP_UNAVAILABLE",()->service.verify(first.id(),codes.get(email)));
        service.cancel(second.id()); service.cancel(second.id());
        fails("OTP_UNAVAILABLE",()->service.verify(second.id(),codes.get(email)));
        assertThat(count("users")).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM public.auth_challenges WHERE subject_key=? AND registration_data IS NOT NULL",Long.class,email)).isZero();
    }
    @Test void transportFailureRollsBackAndResendPreservesPreviousChallenge() {
        failMail.set(true);
        fails("OTP_EMAIL_UNAVAILABLE",()->service.start(buyer(),requester));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM public.auth_challenges WHERE subject_key=?",Long.class,email)).isZero();
        failMail.set(false); var first=service.start(buyer(),requester);
        clock.advance(61); failMail.set(true);
        fails("OTP_EMAIL_UNAVAILABLE",()->service.resend(first.id(),requester));
        assertThat(jdbc.queryForObject("SELECT state FROM public.auth_challenges WHERE challenge_id=?",String.class,first.id())).isEqualTo("OPEN");
        assertThat(service.verify(first.id(),codes.get(email)).awardedCoins()).isEqualTo(3000);
    }
    @Test void rewardFailureRollsBackAccountAndConsumptionSoSameCodeCanRetry() {
        var pending=service.start(buyer(),requester);
        jdbc.execute("""
                CREATE FUNCTION public.fail_registration_reward_fixture() RETURNS trigger LANGUAGE plpgsql AS $$
                BEGIN IF NEW.entry_type='CREDIT_REGISTRATION' THEN RAISE EXCEPTION 'Fixture reward failure'; END IF;
                RETURN NEW; END; $$;
                """);
        jdbc.execute("CREATE TRIGGER fail_registration_reward_fixture BEFORE INSERT ON public.reward_ledger FOR EACH ROW EXECUTE FUNCTION public.fail_registration_reward_fixture()");
        try {
            assertThatThrownBy(()->service.verify(pending.id(),codes.get(email))).isInstanceOf(DataAccessException.class);
            assertThat(count("users")).isZero();
            assertThat(jdbc.queryForObject("SELECT attempt_count FROM public.auth_challenges WHERE challenge_id=?",Integer.class,pending.id())).isZero();
        } finally {
            jdbc.execute("DROP TRIGGER fail_registration_reward_fixture ON public.reward_ledger");
            jdbc.execute("DROP FUNCTION public.fail_registration_reward_fixture()");
        }
        assertThat(service.verify(pending.id(),codes.get(email)).awardedCoins()).isEqualTo(3000);
    }

    @Test void databaseRejectsDraftTamperingAndDuplicateOrIncorrectReward() {
        var pending=service.start(buyer(),requester);
        assertThatThrownBy(()->jdbc.update("UPDATE public.auth_challenges SET registration_data=jsonb_set(registration_data,'{passwordHash}','\"forged\"') WHERE challenge_id=?",pending.id())).isInstanceOf(DataAccessException.class);
        assertThatThrownBy(()->jdbc.update("UPDATE public.auth_challenges SET registration_expires_at=registration_expires_at+INTERVAL '1 hour' WHERE challenge_id=?",pending.id())).isInstanceOf(DataAccessException.class);
        long user=service.verify(pending.id(),codes.get(email)).session().user().getId();
        for(int points:new int[]{1000,3000}) {
            assertThatThrownBy(()->jdbc.update("""
                    INSERT INTO og_compat.reward_ledger(entry_id,user_id,entry_type,points_delta,reason,command_key)
                    VALUES(?,?,'CREDIT_REGISTRATION',?,'UC22: verified email registration',?)
                    """,UUID.randomUUID(),user,points,"REGISTER_EMAIL:"+user)).isInstanceOf(DataAccessException.class);
        }
        assertThat(jdbc.queryForObject("SELECT reward_balance FROM public.users WHERE user_id=?",Long.class,user)).isEqualTo(3000);
    }

    @Test void ipSendLimitAppliesAcrossDifferentEmailAddressesAndStoresOnlyDigest() {
        var first=service.start(buyer(),requester);
        String digest=jdbc.queryForObject("SELECT registration_requester_digest FROM public.auth_challenges WHERE challenge_id=?",String.class,first.id());
        assertThat(digest).matches("[0-9a-f]{64}").doesNotContain(requester);
        for(int i=1;i<20;i++) {
            var other=new EmailRegistrationService.Input("Test",UUID.randomUUID()+"@example.invalid",null,
                    "Password123@",RegistrationDraft.Type.BUYER,null);
            service.start(other,requester);
        }
        var last=new EmailRegistrationService.Input("Test",UUID.randomUUID()+"@example.invalid",null,
                "Password123@",RegistrationDraft.Type.BUYER,null);
        fails("OTP_RATE_LIMIT",()->service.start(last,requester));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM public.auth_challenges WHERE registration_requester_digest=?",Long.class,digest)).isEqualTo(20);
        assertThat(count("users")).isZero();
    }
    @Test void concurrentVerificationCreatesOneAccountAndOneReward() throws Exception {
        var pending=service.start(buyer(),requester); String code=codes.get(email);
        try(var workers=Executors.newFixedThreadPool(2)) {
            var gate=new CountDownLatch(1);
            Callable<Boolean> verify=()->{ gate.await(); try{service.verify(pending.id(),code);return true;}
                catch(RegistrationException error){assertThat(error.code()).isEqualTo("OTP_UNAVAILABLE");return false;} };
            var a=workers.submit(verify); var b=workers.submit(verify); gate.countDown();
            assertThat(a.get(30,TimeUnit.SECONDS)^b.get(30,TimeUnit.SECONDS)).isTrue();
        }
        assertThat(count("users")).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM public.reward_ledger r JOIN public.users u USING(user_id) WHERE u.email=? AND r.entry_type='CREDIT_REGISTRATION'",Long.class,email)).isEqualTo(1);
    }
    @Test void sellerDraftStoresOwnedAddressesAndBankWithoutGrantingSeller() {
        var address=new RegistrationDraft.Address("P","D","W","Pickup");
        var returns=new RegistrationDraft.Address("P","D","W","Return");
        var seller=new RegistrationDraft.Seller("Test Store","0900000000","Mock Bank","12345678","TEST SELLER",address,returns);
        var pending=service.start(new EmailRegistrationService.Input("Test Seller",email,null,"Password123@",
                RegistrationDraft.Type.SELLER,seller),requester);
        var completed=service.verify(pending.id(),codes.get(email)); long user=completed.session().user().getId();
        assertThat(completed.nextStep()).isEqualTo("SELLER_VERIFICATION");
        assertThat(completed.session().user().getRoles()).extracting(role->role.getRoleName()).containsExactly("BUYER");
        assertThat(jdbc.queryForObject("SELECT state FROM public.seller_profiles WHERE user_id=?",String.class,user)).isEqualTo("PENDING_EKYC");
        assertThat(jdbc.queryForObject("SELECT store_name FROM public.seller_profiles WHERE user_id=?",String.class,user)).isEqualTo("Test Store");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM public.addresses WHERE user_id=?",Long.class,user)).isEqualTo(2);
        assertThat(jdbc.queryForObject("SELECT bank_snapshot->>'account_number' FROM public.seller_profiles WHERE user_id=?",String.class,user)).isEqualTo("12345678");
        assertThatThrownBy(()->activation.activate(user)).isInstanceOf(SellerActivationDisabledException.class);
    }
    @Test void hardExpiryAndHourlyRateLimitCannotBeExtendedByResend() {
        var pending=service.start(buyer(),requester);
        for(int i=0;i<4;i++){clock.advance(61);pending=service.resend(pending.id(),requester);}
        clock.advance(61); var last=pending;
        fails("OTP_RATE_LIMIT",()->service.resend(last.id(),requester));
        clock.advance(1800);
        fails("OTP_UNAVAILABLE",()->service.verify(last.id(),codes.get(email)));
        assertThat(count("users")).isZero();
        assertThat(jdbc.queryForObject("SELECT registration_data IS NULL FROM public.auth_challenges WHERE challenge_id=?",Boolean.class,last.id())).isTrue();
    }

    @Test void expiredAbandonedDraftIsCleanedAndRegistrationCanStartAgain() {
        clock.advance(-1900);
        var old=service.start(buyer(),requester);
        clock.reset();
        drafts.expireDrafts();
        assertThat(jdbc.queryForObject("SELECT state FROM public.auth_challenges WHERE challenge_id=?",String.class,old.id())).isEqualTo("INVALIDATED");
        assertThat(jdbc.queryForObject("SELECT registration_data IS NULL FROM public.auth_challenges WHERE challenge_id=?",Boolean.class,old.id())).isTrue();
        assertThat(count("users")).isZero();
        var replacement=service.start(buyer(),requester);
        assertThat(service.verify(replacement.id(),codes.get(email)).awardedCoins()).isEqualTo(3000);
    }
    @Test void upgradePreservesLegacyAccountAndShortPasswordLogin() throws Exception {
        assertThat(auth.authenticatePassword("legacy@example.invalid","short6").getId()).isEqualTo(legacyId);
        http.perform(post("/api/v1/auth/login").header("Origin","http://localhost:5173")
                .contentType(MediaType.APPLICATION_JSON).content("{\"email\":\"legacy@example.invalid\",\"password\":\"short6\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.user.sellerApprovalRequired").value(false));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM public.reward_ledger WHERE user_id=?",Long.class,legacyId)).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM information_schema.tables WHERE table_schema='public' AND table_type='BASE TABLE' AND table_name<>'flyway_schema_history'",Integer.class)).isEqualTo(47);
    }
    @Test void maliciousOriginOrInvalidSellerCannotSendEmail() throws Exception {
        http.perform(post("/api/v1/auth/register").header("Origin","https://untrusted.example.invalid")
                .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(buyer()))).andExpect(status().isForbidden());
        http.perform(post("/api/v1/auth/register").header("Origin","http://localhost:5173")
                .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(
                        new EmailRegistrationService.Input("Test",email,null,"Password123@",RegistrationDraft.Type.SELLER,null))))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(mail);
        assertThat(count("users")).isZero();
    }
}
