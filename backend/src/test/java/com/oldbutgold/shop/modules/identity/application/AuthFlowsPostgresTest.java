package com.oldbutgold.shop.modules.identity.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.oldbutgold.shop.modules.identity.api.ProfileDtos;
import com.oldbutgold.shop.modules.identity.infrastructure.persistence.AuthFlowRepository;
import com.oldbutgold.shop.modules.platform.infrastructure.LegacyModerationHistoryCallback;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.*;
import org.springframework.context.annotation.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.*;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.security.oauth2.jwt.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Real migration/JPA/JDBC/security/transactions. Only SMTP and Google provider are mocked. */
@SpringBootTest @AutoConfigureMockMvc @ActiveProfiles("test") @DirtiesContext
@EnabledIfEnvironmentVariable(named="OGSHOP_EXPANSION_TEST_DB_URL",matches=".+")
class AuthFlowsPostgresTest {
    private static String database;
    private static JdbcTemplate root;
    private static String url(String name) {
        String u=System.getenv("OGSHOP_EXPANSION_TEST_DB_URL");
        if(u==null || !u.matches("jdbc:postgresql://(127\\.0\\.0\\.1|localhost):[0-9]+/ogshop_[a-z0-9_]*test[a-z0-9_]*"))
            throw new IllegalStateException("Explicit isolated local test database required");
        return u.substring(0,u.lastIndexOf('/')+1)+name;
    }
    private static DriverManagerDataSource source(String name) { return new DriverManagerDataSource(url(name),System.getenv("OGSHOP_TEST_DB_USER"),System.getenv("OGSHOP_TEST_DB_PASSWORD")); }
    @DynamicPropertySource static void db(DynamicPropertyRegistry p) {
        database="ogshop_auth_test_"+UUID.randomUUID().toString().replace("-","");
        root=new JdbcTemplate(source("postgres"));root.execute("CREATE DATABASE "+database);
        Flyway.configure().dataSource(source(database)).schemas("public").defaultSchema("public")
                .callbacks(new LegacyModerationHistoryCallback()).target("25").load().migrate();
        new JdbcTemplate(source(database)).update("INSERT INTO og_compat.users(email,password_hash,full_name) VALUES('v25-preserved@example.invalid','{noop}legacy','Preserved')");
        p.add("spring.datasource.url",()->url(database)+"?currentSchema=og_compat,public");
        p.add("spring.datasource.username",()->System.getenv("OGSHOP_TEST_DB_USER"));p.add("spring.datasource.password",()->System.getenv("OGSHOP_TEST_DB_PASSWORD"));
        p.add("spring.jpa.hibernate.ddl-auto",()->"validate");p.add("spring.flyway.enabled",()->true);p.add("app.google.client-id",()->"test-client.apps.googleusercontent.com");
    }
    @AfterAll static void cleanup() { if(root!=null && database!=null && database.matches("ogshop_auth_test_[0-9a-f]{32}")) root.execute("DROP DATABASE "+database+" WITH (FORCE)"); }
    @TestConfiguration static class TimeConfig { @Bean @Primary TestClock authTestClock() { return new TestClock(); } }
    static class TestClock extends Clock {
        volatile Instant time=Instant.now();void reset() {time=Instant.now();}void advance(long s) {time=time.plusSeconds(s);}
        public Instant instant(){return time;}public ZoneId getZone(){return ZoneOffset.UTC;}public Clock withZone(ZoneId z){return this;}
    }
    @Autowired AuthFlowService flows; @Autowired AuthService auth; @Autowired EmailRegistrationService registration;
    @Autowired ProfileService profile; @Autowired AuthFlowRepository repository; @Autowired JdbcTemplate jdbc;
    @Autowired MockMvc http; @Autowired ObjectMapper json; @Autowired JwtDecoder decoder; @Autowired TestClock clock;
    @Autowired JwtEncoder encoder;
    @MockitoBean RegistrationEmailPort mail; @MockitoBean GoogleIdentityPort google;
    private final Map<String,String> codes=new ConcurrentHashMap<>();
    private String email,requester;private long id;
    @BeforeEach void fixture() {
        clock.reset();codes.clear();email=UUID.randomUUID()+"@example.invalid";requester=UUID.randomUUID().toString();
        doAnswer(c->{codes.put(c.getArgument(0),c.getArgument(1));return null;}).when(mail).sendCode(anyString(),anyString(),any());
        doAnswer(c->{codes.put(c.getArgument(0),c.getArgument(1));return null;}).when(mail).sendAuthCode(anyString(),anyString(),any(),anyString());
        var pending=registration.start(input(email),requester);id=registration.verify(pending.id(),codes.get(email)).session().user().getId();codes.clear();
    }
    private EmailRegistrationService.Input input(String email) { return new EmailRegistrationService.Input("Fixture",email,null,"Password123@",RegistrationDraft.Type.BUYER,null); }
    private void twoFactor() { jdbc.update("INSERT INTO og_compat.user_security_settings(user_id,email_2fa_enabled,verified_at) VALUES(?,true,now())",id); }
    private void fails(String code,Runnable action) { assertThatThrownBy(action::run).isInstanceOfSatisfying(RegistrationException.class,e->assertThat(e.code()).isEqualTo(code)); }
    private String wrong() {return "000000".equals(codes.get(email))?"000001":"000000";}
    private String loginBody(String password) {return "{\"email\":\""+email+"\",\"password\":\""+password+"\"}";}
    @Test void migrationPreservesV25AndCleanDatabaseReaches26With48Tables() {
        assertThat(jdbc.queryForObject("SELECT count(*) FROM public.users WHERE email='v25-preserved@example.invalid' AND credential_version=0",Integer.class)).isEqualTo(1);
        String clean="ogshop_auth_clean_test_"+UUID.randomUUID().toString().replace("-","");root.execute("CREATE DATABASE "+clean);
        try {
            Flyway.configure().dataSource(source(clean)).schemas("public").defaultSchema("public").callbacks(new LegacyModerationHistoryCallback()).load().migrate();
            assertThat(new JdbcTemplate(source(clean)).queryForObject("SELECT count(*) FROM information_schema.tables WHERE table_schema='public' AND table_type='BASE TABLE'",Integer.class)).isEqualTo(48);
        } finally {root.execute("DROP DATABASE "+clean+" WITH (FORCE)");}
    }
    @Test void password2faCannotIssueTokenCookieUntilOtpAndWrongAttemptsCommit() throws Exception {
        twoFactor();
        var response=http.perform(post("/api/v1/auth/login").header("Origin","http://localhost:5173").contentType("application/json").content(loginBody("Password123@")))
                .andExpect(status().isAccepted()).andExpect(header().doesNotExist("Set-Cookie")).andExpect(jsonPath("$.accessToken").doesNotExist()).andReturn();
        UUID challenge=UUID.fromString(json.readTree(response.getResponse().getContentAsString()).get("challengeId").asText());
        fails("OTP_INVALID",()->flows.verify(challenge,wrong(),"LOGIN_EMAIL_2FA"));
        assertThat(jdbc.queryForObject("SELECT attempt_count FROM public.auth_challenges WHERE challenge_id=?",Integer.class,challenge)).isEqualTo(1);
        fails("OTP_UNAVAILABLE",()->flows.verify(challenge,codes.get(email),"PASSWORD_RESET"));
        http.perform(post("/api/v1/auth/login/verify").header("Origin","http://localhost:5173").contentType("application/json")
                .content("{\"challengeId\":\""+challenge+"\",\"code\":\""+codes.get(email)+"\"}"))
                .andExpect(status().isOk()).andExpect(header().string("Set-Cookie",org.hamcrest.Matchers.containsString("og_access=")))
                .andExpect(jsonPath("$.accessToken").isString());
        fails("OTP_UNAVAILABLE",()->flows.verify(challenge,codes.get(email),"LOGIN_EMAIL_2FA"));
    }
    @Test void disabled2faLoginRemainsDirect() {
        var result=flows.login(email,"Password123@",requester);assertThat(result.pending()).isNull();
        assertThat(((Number)decoder.decode(result.session().accessToken()).getClaim("cv")).longValue()).isZero();verify(mail,never()).sendAuthCode(any(),any(),any(),any());
    }
    @Test void resetIsGenericAndRequiresVerifiedOtpThenRevokesEveryOldCredential() throws Exception {
        twoFactor();String old=auth.newSession(auth.currentUser(id)).accessToken();
        var known=flows.startReset(email,requester);var unknown=flows.startReset(UUID.randomUUID()+"@example.invalid",requester);
        assertThat(known.status()).isEqualTo(unknown.status());assertThat(known.purpose()).isEqualTo(unknown.purpose());assertThat(known.attemptsRemaining()).isEqualTo(unknown.attemptsRemaining());
        fails("OTP_UNAVAILABLE",()->flows.finishReset(known.challengeId(),"NewPassword123@","NewPassword123@"));
        org.awaitility.Awaitility.await().atMost(Duration.ofSeconds(3)).until(()->codes.containsKey(email));
        var grant=flows.verify(known.challengeId(),codes.get(email),"PASSWORD_RESET").pending();
        fails("PASSWORD_CONFIRMATION_MISMATCH",()->flows.finishReset(grant.challengeId(),"NewPassword123@","Mismatch123@"));
        fails("PASSWORD_SAME_AS_OLD",()->flows.finishReset(grant.challengeId(),"Password123@","Password123@"));
        assertThat(decoder.decode(old)).isNotNull();
        flows.finishReset(grant.challengeId(),"NewPassword123@","NewPassword123@");
        assertThatThrownBy(()->decoder.decode(old)).isInstanceOf(JwtException.class);
        http.perform(get("/api/v1/auth/me").header("Authorization","Bearer "+old)).andExpect(status().isUnauthorized());
        http.perform(post("/api/v1/auth/session").header("Origin","http://localhost:5173").cookie(new jakarta.servlet.http.Cookie("og_access",old))).andExpect(status().isUnauthorized());
        assertThat(repository.twoFactor(id)).isTrue();assertThat(auth.currentUser(id).getEmail()).isEqualTo(email);
        assertThat(flows.login(email,"NewPassword123@",requester).pending().purpose()).isEqualTo("LOGIN_EMAIL_2FA");
        assertThatThrownBy(()->flows.login(email,"Password123@",requester)).isInstanceOf(org.springframework.security.authentication.BadCredentialsException.class);
        fails("OTP_UNAVAILABLE",()->flows.finishReset(grant.challengeId(),"Another123@","Another123@"));
        verify(mail,never()).sendAuthCode(eq(unknown.email()),any(),any(),any());
    }
    @Test void passwordChangeRevokesOldBearerAndCookieOnlyOnSuccess() {
        String first=flows.login(email,"Password123@",requester).session().accessToken();
        String second=flows.login(email,"Password123@",requester).session().accessToken();
        assertThatThrownBy(()->profile.changePassword(id,new ProfileDtos.ChangePasswordRequest("wrong","NewPassword123@"))).isInstanceOf(org.springframework.security.authentication.BadCredentialsException.class);
        assertThatThrownBy(()->profile.changePassword(id,new ProfileDtos.ChangePasswordRequest("Password123@","Password123@"))).isInstanceOf(IllegalArgumentException.class);
        assertThat(decoder.decode(first)).isNotNull();
        profile.changePassword(id,new ProfileDtos.ChangePasswordRequest("Password123@","NewPassword123@"));
        assertThatThrownBy(()->decoder.decode(first)).isInstanceOf(JwtException.class);assertThatThrownBy(()->auth.restoreSession(second)).isInstanceOf(org.springframework.security.authentication.BadCredentialsException.class);
        assertThat(((Number)decoder.decode(flows.login(email,"NewPassword123@",requester).session().accessToken()).getClaim("cv")).longValue()).isEqualTo(1);
    }
    @Test void concurrentOtpCompletionHasExactlyOneWinner() throws Exception {
        twoFactor();var pending=flows.login(email,"Password123@",requester).pending();String code=codes.get(email);
        try(var pool=Executors.newFixedThreadPool(2)) {
            CountDownLatch start=new CountDownLatch(1);
            Callable<Boolean> verify=()->{start.await();try {flows.verify(pending.challengeId(),code,"LOGIN_EMAIL_2FA");return true;}catch(RegistrationException e){return false;}};
            var a=pool.submit(verify);var b=pool.submit(verify);start.countDown();
            assertThat(List.of(a.get(15,TimeUnit.SECONDS),b.get(15,TimeUnit.SECONDS))).containsExactlyInAnyOrder(true,false);
        }
    }
    @Test void exhaustionExpiryCooldownResendAndCancellationCannotBypassOtp() {
        twoFactor();var pending=flows.login(email,"Password123@",requester).pending();
        fails("OTP_RESEND_TOO_SOON",()->flows.resend(pending.challengeId(),requester));
        for(int n=0;n<5;n++) fails("OTP_INVALID",()->flows.verify(pending.challengeId(),wrong(),"LOGIN_EMAIL_2FA"));
        fails("OTP_ATTEMPTS_EXHAUSTED",()->flows.verify(pending.challengeId(),codes.get(email),"LOGIN_EMAIL_2FA"));
        clock.advance(61);var next=flows.resend(pending.challengeId(),requester);
        fails("OTP_UNAVAILABLE",()->flows.verify(pending.challengeId(),codes.get(email),"LOGIN_EMAIL_2FA"));
        clock.advance(301);fails("OTP_EXPIRED",()->flows.verify(next.challengeId(),codes.get(email),"LOGIN_EMAIL_2FA"));
        var third=flows.resend(next.challengeId(),requester);flows.cancel(third.challengeId());
        fails("OTP_UNAVAILABLE",()->flows.verify(third.challengeId(),codes.get(email),"LOGIN_EMAIL_2FA"));
    }
    @Test void googleLinkProvesOwnershipThenStillRequiresConfigured2faWithoutNewReward() {
        twoFactor();String subject=UUID.randomUUID().toString();var p=new AuthFlowService.GoogleProfile(subject,email,"Google Fixture");
        var result=flows.google(flows.googleNonce(requester),p,requester);var link=result.login().pending();
        assertThat(repository.googleUser(subject)).isEmpty();fails("OTP_INVALID",()->flows.verify(link.challengeId(),wrong(),"GOOGLE_LINK"));
        var linked=flows.verify(link.challengeId(),codes.get(email),"GOOGLE_LINK");assertThat(linked.session()).isNull();assertThat(linked.pending().purpose()).isEqualTo("LOGIN_EMAIL_2FA");
        assertThat(repository.googleUser(subject)).contains(id);flows.verify(linked.pending().challengeId(),codes.get(email),"LOGIN_EMAIL_2FA");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM public.reward_ledger WHERE user_id=? AND entry_type='CREDIT_REGISTRATION'",Integer.class,id)).isEqualTo(1);
        clock.advance(61);assertThat(flows.google(flows.googleNonce(requester),p,requester).login().pending().purpose()).isEqualTo("LOGIN_EMAIL_2FA");
    }
    @Test void googleNewRegistrationNeedsPasswordOtpAndSingleUseTicket() {
        String fresh=UUID.randomUUID()+"@example.invalid",subject=UUID.randomUUID().toString();
        var result=flows.google(flows.googleNonce(requester),new AuthFlowService.GoogleProfile(subject,fresh,"New Google"),requester);
        assertThat(repository.googleUser(subject)).isEmpty();
        var pending=registration.start(input(fresh),requester,result.registrationTicket());
        fails("OTP_UNAVAILABLE",()->registration.start(input(fresh),requester,result.registrationTicket()));
        var complete=registration.verify(pending.id(),codes.get(fresh));assertThat(complete.awardedCoins()).isEqualTo(3000);
        assertThat(repository.googleUser(subject)).contains(complete.session().user().getId());
        assertThat(flows.google(flows.googleNonce(requester),new AuthFlowService.GoogleProfile(subject,fresh,"New Google"),requester).login().session().user().getId()).isEqualTo(complete.session().user().getId());
    }
    @Test void lockedResponseRequiresCorrectCredentialAndCarriesStatusSupport() throws Exception {
        jdbc.update("UPDATE public.users SET status='LOCKED' WHERE user_id=? AND record_type='users'",id);
        http.perform(post("/api/v1/auth/login").header("Origin","http://localhost:5173").contentType("application/json").content(loginBody("wrong")))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.accountStatus").doesNotExist());
        http.perform(post("/api/v1/auth/login").header("Origin","http://localhost:5173").contentType("application/json").content(loginBody("Password123@")))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("ACCOUNT_RESTRICTED"))
                .andExpect(jsonPath("$.accountStatus").value("LOCKED")).andExpect(jsonPath("$.supportInstruction").isString()).andExpect(header().doesNotExist("Set-Cookie"));
    }
    @Test void resetSmtpFailureDoesNotDiscloseEmailEligibilityOrChangePassword() {
        doThrow(new RegistrationException("OTP_EMAIL_UNAVAILABLE","Unavailable",503)).when(mail).sendAuthCode(any(),any(),any(),eq("PASSWORD_RESET"));
        var accepted=flows.startReset(email,requester);assertThat(accepted.status()).isEqualTo("OTP_REQUIRED");
        assertThat(flows.login(email,"Password123@",requester).session()).isNotNull();
    }
    @Test void blockedSmtpDoesNotBlockGenericResetAcceptance() throws Exception {
        CountDownLatch sending=new CountDownLatch(1),release=new CountDownLatch(1),finished=new CountDownLatch(1);
        doAnswer(c->{sending.countDown();try {release.await(5,TimeUnit.SECONDS);}finally {finished.countDown();}return null;})
                .when(mail).sendAuthCode(any(),any(),any(),eq("PASSWORD_RESET"));
        try(var pool=Executors.newSingleThreadExecutor()) {
            var accepted=pool.submit(()->flows.startReset(email,requester));
            try {assertThat(sending.await(3,TimeUnit.SECONDS)).isTrue();assertThat(accepted.get(1,TimeUnit.SECONDS).status()).isEqualTo("OTP_REQUIRED");}
            finally {release.countDown();assertThat(finished.await(3,TimeUnit.SECONDS)).isTrue();}
        }
    }
    @Test void resetStorageFailureRollsBackHashVersionAndGrantConsumptionThenCanRetry() {
        var pending=flows.startReset(email,requester);
        org.awaitility.Awaitility.await().atMost(Duration.ofSeconds(3)).until(()->codes.containsKey(email));
        var grant=flows.verify(pending.challengeId(),codes.get(email),"PASSWORD_RESET").pending();
        String old=flows.login(email,"Password123@",requester).session().accessToken();
        jdbc.execute("CREATE FUNCTION public.task0086_reject_password() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN IF NEW.user_id="+id+" AND NEW.password_hash IS DISTINCT FROM OLD.password_hash THEN RAISE EXCEPTION 'test storage failure' USING ERRCODE='23514'; END IF; RETURN NEW; END; $$");
        jdbc.execute("CREATE TRIGGER task0086_reject_password BEFORE UPDATE ON public.users FOR EACH ROW EXECUTE FUNCTION public.task0086_reject_password()");
        try {
            assertThatThrownBy(()->flows.finishReset(grant.challengeId(),"NewPassword123@","NewPassword123@")).isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
            assertThat(decoder.decode(old)).isNotNull();assertThat(auth.currentUser(id).getCredentialVersion()).isZero();
            assertThat(repository.find(grant.challengeId(),false).orElseThrow().state()).isEqualTo("OPEN");
        } finally {jdbc.execute("DROP TRIGGER task0086_reject_password ON public.users");jdbc.execute("DROP FUNCTION public.task0086_reject_password()");}
        flows.finishReset(grant.challengeId(),"NewPassword123@","NewPassword123@");
        assertThatThrownBy(()->decoder.decode(old)).isInstanceOf(JwtException.class);
    }
    @Test void googleNonceReplayAndCancelledLinkCannotGrantLogin() {
        UUID nonce=flows.googleNonce(requester);var p=new AuthFlowService.GoogleProfile(UUID.randomUUID().toString(),email,"Fixture");
        var link=flows.google(nonce,p,requester).login().pending();
        fails("OTP_UNAVAILABLE",()->flows.google(nonce,p,requester));
        flows.cancel(link.challengeId());fails("OTP_UNAVAILABLE",()->flows.verify(link.challengeId(),codes.get(email),"GOOGLE_LINK"));
        assertThat(repository.googleUser(p.subject())).isEmpty();
    }
    @Test void googleSellerUsesPendingApprovalAndOneRewardRatherThanGrantingSellerRole() {
        String fresh=UUID.randomUUID()+"@example.invalid",subject=UUID.randomUUID().toString();
        var ticket=flows.google(flows.googleNonce(requester),new AuthFlowService.GoogleProfile(subject,fresh,"Seller"),requester).registrationTicket();
        var address=new RegistrationDraft.Address("P","D","W","A");
        var seller=new RegistrationDraft.Seller("Fixture store","0900000000","Bank","12345678","FIXTURE",address,address);
        var pending=registration.start(new EmailRegistrationService.Input("Seller",fresh,null,"Password123@",RegistrationDraft.Type.SELLER,seller),requester,ticket);
        var result=registration.verify(pending.id(),codes.get(fresh));
        assertThat(result.nextStep()).isEqualTo("SELLER_VERIFICATION");assertThat(result.awardedCoins()).isEqualTo(3000);
        assertThat(result.session().user().getRoles()).extracting(r->r.getRoleName()).containsExactly("BUYER");
        assertThat(jdbc.queryForObject("SELECT state FROM public.seller_profiles WHERE user_id=? AND record_type='seller_profiles'",String.class,result.session().user().getId())).isEqualTo("PENDING_EKYC");
    }
    @Test void oldTokensWithoutVersionClaimAreCompatibleOnlyBeforeAnyPasswordChange() {
        var claims=JwtClaimsSet.builder().issuer("og-shop").subject(Long.toString(id)).issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(60)).claim("roles",List.of("BUYER")).build();
        String old=encoder.encode(JwtEncoderParameters.from(JwsHeader.with(org.springframework.security.oauth2.jose.jws.MacAlgorithm.HS256).build(),claims)).getTokenValue();
        assertThat(decoder.decode(old)).isNotNull();
        profile.changePassword(id,new ProfileDtos.ChangePasswordRequest("Password123@","NewPassword123@"));
        assertThatThrownBy(()->decoder.decode(old)).isInstanceOf(JwtException.class);
    }
    @Test void staffCannotResetOrUseGoogleAndDoesNotGetCustomer2fa() {
        // Bootstrap a legacy administrator only in this disposable fixture database,
        // then supply its approval authority for the canonical UC83 staff grant.
        long administrator=jdbc.queryForObject("INSERT INTO og_compat.users(email,password_hash,full_name) VALUES(?,'{noop}unused','Fixture administrator') RETURNING user_id",
                Long.class,UUID.randomUUID()+"@example.invalid");
        jdbc.update("INSERT INTO og_compat.user_roles(user_id,role_id) SELECT ?,role_id FROM og_compat.roles WHERE role_name='ADMIN'",administrator);
        jdbc.update("INSERT INTO og_compat.user_roles(user_id,role_id,granted_by) SELECT ?,role_id,? FROM og_compat.roles WHERE role_name='KTV'",id,administrator);
        twoFactor();assertThat(flows.login(email,"Password123@",requester).session()).isNotNull();
        var reset=flows.startReset(email,requester);assertThat(reset.status()).isEqualTo("OTP_REQUIRED");
        assertThat(repository.find(reset.challengeId(),false).orElseThrow().data().userId()).isNull();
        fails("GOOGLE_NOT_ALLOWED",()->flows.google(flows.googleNonce(requester),new AuthFlowService.GoogleProfile(UUID.randomUUID().toString(),email,"KTV"),requester));
    }
}
