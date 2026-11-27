package com.oldbutgold.shop.modules.trustsafety.application;

import com.oldbutgold.shop.modules.commerce.api.VoucherDtos;
import com.oldbutgold.shop.modules.commerce.infrastructure.persistence.VoucherWalletRepository;
import com.oldbutgold.shop.modules.platform.api.AdminDashboardDtos;
import com.oldbutgold.shop.modules.platform.application.AdminDashboardService;
import com.oldbutgold.shop.modules.trustsafety.infrastructure.persistence.AccountTrustRepository;
import com.oldbutgold.shop.shared.PostgresTestSafetyGuard;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Real PostgreSQL integration tests for:
 * 1. UC64/UC65/UC76/UC49/UC53 SYSTEM_ISSUE lifecycle, V43 trigger override for REJECT_ISSUE -> REJECTED,
 *    PO-02 Option A designated Admin + handover, and UC70 compensation voucher + dedup.
 * 2. UC81 returns & system-issue report counts, multi-round DISTINCT(case_id), outcome bucketing, and CSV cutoffAt reuse.
 */
@org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Transactional
@EnabledIfEnvironmentVariable(named = "OGSHOP_TEST_DB_URL", matches = ".+")
class SystemIssueAndUc81PostgresIntegrationTest {

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        String url = PostgresTestSafetyGuard.resolveAndAssertSafeFromEnv("OGSHOP_TEST_DB_URL");
        registry.add("spring.datasource.url", () -> url);
        registry.add("spring.datasource.username", () -> System.getenv("OGSHOP_TEST_DB_USER"));
        registry.add("spring.datasource.password", () -> System.getenv("OGSHOP_TEST_DB_PASSWORD"));
        registry.add("spring.datasource.hikari.connection-init-sql", () -> "SET search_path TO public,og_compat");
        registry.add("spring.flyway.enabled", PostgresTestSafetyGuard::isFlywayOptInEnabled);
        registry.add("spring.flyway.schemas", () -> "public");
        registry.add("spring.flyway.default-schema", () -> "public");
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
    }

    @Autowired
    private SystemIssueGovernanceService governanceService;

    @Autowired
    private AccountTrustRepository accountTrustRepository;

    @Autowired
    private VoucherWalletRepository voucherWalletRepository;

    @Autowired
    private AdminDashboardService adminDashboardService;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private EntityManager entityManager;

    @Autowired private org.springframework.test.web.servlet.MockMvc mvc;
    @Autowired private com.fasterxml.jackson.databind.ObjectMapper json;

    private org.springframework.test.web.servlet.request.RequestPostProcessor identity(long id, String role) {
        return org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt()
                .jwt(j -> j.subject(Long.toString(id)))
                .authorities(new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_" + role));
    }

    @Test
    @Transactional(propagation=org.springframework.transaction.annotation.Propagation.NOT_SUPPORTED)
    void httpChecksOwnershipAndHandlesSupplementThroughDecision() throws Exception {
        var endpoint = "/api/v1/trustsafety/system-issues";
        var owner = identity(buyerId, "BUYER");
        var other = identity(createUser("second", "SELLER"), "SELLER");
        var staff = identity(ktvId, "KTV");
        var payload = json.writeValueAsString(new ReturnCaseDtos.CreateSystemIssueRequest(null,null,null,null,
                "Phí đăng tin bị ghi nhận hai lần", List.of(new ReturnCaseDtos.EvidenceItemDto("TEXT", null,
                "Biên nhận riêng", null,null,null)), UUID.randomUUID().toString()));
        for (String secret : List.of("mã OTP 123456", "mật khẩu: ExampleSecret", "password=ExampleSecret")) {
            mvc.perform(post(endpoint).with(owner).contentType("application/json")
                    .content(payload.replace("Biên nhận riêng",secret))).andExpect(status().isBadRequest());
        }
        var result = mvc.perform(post(endpoint).with(owner).contentType("application/json").content(payload))
                .andExpect(status().isOk()).andReturn();
        long id = json.readTree(result.getResponse().getContentAsByteArray()).get("caseId").asLong();
        mvc.perform(post(endpoint).with(owner).contentType("application/json").content(payload))
                .andExpect(status().isOk()).andExpect(jsonPath("$.caseId").value(id));
        mvc.perform(post(endpoint).with(other).contentType("application/json").content(payload))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.evidence").doesNotExist());
        mvc.perform(get("/api/v1/trustsafety/returns/"+id).with(other))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.evidence").doesNotExist());
        String route = "/api/v1/ktv/system-issues/" + id;
        mvc.perform(post(route+"/receive").with(other)).andExpect(status().isForbidden());
        mvc.perform(post(route+"/receive").with(staff)).andExpect(status().isOk())
                .andExpect(jsonPath("$.state").value("REVIEWING"));
        mvc.perform(post(route+"/information").with(staff).contentType("application/json").content("{\"note\":\"Gửi thêm mã biên nhận\"}"))
                .andExpect(status().isOk());
        var req = jdbc.queryForObject("SELECT event_id FROM og_compat.case_events WHERE case_id=? AND metadata->>'action'='INFORMATION_REQUEST'", UUID.class,id);
        var answer = json.writeValueAsString(java.util.Map.of("requestId",req,"note","Biên nhận đã bổ sung"));
        mvc.perform(post(endpoint+"/"+id+"/supplements").with(other).contentType("application/json").content(answer))
                .andExpect(status().isNotFound());
        mvc.perform(post(endpoint+"/"+id+"/supplements").with(owner).contentType("application/json").content(answer))
                .andExpect(status().isOk()).andExpect(jsonPath("$.evidence.length()").value(2));
        mvc.perform(post("/api/v1/ktv/cases/"+id+"/decide-system-issue").with(staff).contentType("application/json")
                .content("{\"resolution\":\"CONFIRM_ISSUE\",\"reason\":\"Đã kiểm tra biên nhận\"}")).andExpect(status().isOk());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM og_compat.notifications WHERE user_id=? AND type='UC53_SYSTEM_DECISION' AND reference_id=?",
                Integer.class,ktvId,id)).isEqualTo(1);
        mvc.perform(get(endpoint).with(owner)).andExpect(status().isOk()).andExpect(jsonPath("$[0].state").value("CLOSED"));
        mvc.perform(get(endpoint).with(other)).andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(0));
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings={"undated","overlapping","distinct"})
    @Transactional(propagation=org.springframework.transaction.annotation.Propagation.NOT_SUPPORTED)
    void concurrentCompensationAcrossCasesAndVouchers(String scenario) throws Exception {
        var cases = new long[2];
        var vouchers = new long[2];
        Instant now = Instant.now();
        for (int i=0; i<2; i++) {
            cases[i] = governanceService.createSystemIssue(buyerId, new ReturnCaseDtos.CreateSystemIssueRequest(
                    null,null,null,null,"Sự cố tài khoản cần xác minh",List.of(),UUID.randomUUID().toString())).caseId();
            Instant from = now.minus(3, ChronoUnit.DAYS).plus(i * ("distinct".equals(scenario) ? 4 : 1), ChronoUnit.HOURS);
            boolean dated = !"undated".equals(scenario);
            governanceService.decideSystemIssue(ktvId,false,cases[i],new ReturnCaseDtos.SystemIssueDecisionRequest(
                    "CONFIRM_ISSUE","Đã xác minh sự việc",dated ? from : null,
                    dated ? from.plus(2,ChronoUnit.HOURS) : null,UUID.randomUUID().toString()));
            vouchers[i] = voucherWalletRepository.save(admin1Id,null,new VoucherDtos.Save(UUID.randomUUID(),0L,
                    "COMP"+UUID.randomUUID().toString().substring(0,8).toUpperCase(),"Bồi thường","Bồi thường",
                    "ORDER_DISCOUNT","FIXED_AMOUNT",BigDecimal.TEN,BigDecimal.TEN,BigDecimal.ZERO,"CHECKOUT",
                    List.of(),100,now.minus(1,ChronoUnit.DAYS),now.plus(3,ChronoUnit.DAYS),true,false));
        }
        var ready = new java.util.concurrent.CyclicBarrier(2);
        try (var pool = java.util.concurrent.Executors.newFixedThreadPool(2)) {
            var futures = new java.util.ArrayList<java.util.concurrent.Future<String>>();
            for (int i=0; i<2; i++) {
                final int index=i;
                futures.add(pool.submit(() -> {
                    ready.await(10,java.util.concurrent.TimeUnit.SECONDS);
                    try {
                        governanceService.executeCompensation(ktvId,false,cases[index],new ReturnCaseDtos.CaseCompensationRequest(
                                "VOUCHER",buyerId,vouchers[index],null,null,null,"Bồi thường sự cố",UUID.randomUUID().toString()));
                        return "GRANTED";
                    } catch (TrustSafetyBusinessException ex) { return ex.getCode(); }
                }));
            }
            assertThat(List.of(futures.get(0).get(30,java.util.concurrent.TimeUnit.SECONDS),
                    futures.get(1).get(30,java.util.concurrent.TimeUnit.SECONDS)))
                    .containsExactlyInAnyOrder("GRANTED","distinct".equals(scenario) ? "GRANTED" : "DUPLICATE_COMPENSATION");
        }
        assertThat(voucherWalletRepository.owned(buyerId)).hasSize("distinct".equals(scenario) ? 2 : 1);
    }

    @org.springframework.boot.test.web.server.LocalServerPort private int port;
    @Autowired private com.oldbutgold.shop.modules.identity.application.TokenService tokens;
    @Autowired private com.oldbutgold.shop.modules.identity.infrastructure.persistence.UserRepository users;

    @Test
    @EnabledIfEnvironmentVariable(named="OGSHOP_TEST_UI",matches="true")
    @Transactional(propagation=org.springframework.transaction.annotation.Propagation.NOT_SUPPORTED)
    void renderedComplaintFlowAgainstLiveHttp() throws Exception {
        var frontend = java.nio.file.Path.of("..","frontend").toAbsolutePath().normalize();
        var process = new ProcessBuilder("node","node_modules/vitest/vitest.mjs","run","SystemComplaints.live.test.tsx")
                .directory(frontend.toFile()).redirectErrorStream(true);
        process.environment().put("VITE_API_BASE_URL","http://127.0.0.1:"+port+"/api/v1");
        process.environment().put("OG_UI_BUYER",tokens.issueAccessToken(users.findById(buyerId).orElseThrow()));
        process.environment().put("OG_UI_SELLER",tokens.issueAccessToken(users.findById(createUser("ui_seller","SELLER")).orElseThrow()));
        process.environment().put("OG_UI_KTV",tokens.issueAccessToken(users.findById(ktvId).orElseThrow()));
        String code="COMP"+UUID.randomUUID().toString().substring(0,8).toUpperCase();
        var incident=governanceService.createSystemIssue(buyerId,new ReturnCaseDtos.CreateSystemIssueRequest(
                null,null,null,null,"Sự cố đã xác minh tại quầy",List.of(),UUID.randomUUID().toString()));
        governanceService.decideSystemIssue(ktvId,false,incident.caseId(),new ReturnCaseDtos.SystemIssueDecisionRequest(
                "CONFIRM_ISSUE","Giao dịch đã được xác minh",null,null,UUID.randomUUID().toString()));
        Instant now=Instant.now();
        long campaign=voucherWalletRepository.save(admin1Id,null,new VoucherDtos.Save(UUID.randomUUID(),0L,code,
                "Bồi thường giao dịch","Bồi thường","ORDER_DISCOUNT","FIXED_AMOUNT",BigDecimal.TEN,BigDecimal.TEN,
                BigDecimal.ZERO,"CHECKOUT",List.of(),100,now.minus(1,ChronoUnit.DAYS),now.plus(3,ChronoUnit.DAYS),true,false));
        governanceService.executeCompensation(ktvId,false,incident.caseId(),new ReturnCaseDtos.CaseCompensationRequest(
                "VOUCHER",buyerId,campaign,null,null,null,"Bồi thường giao dịch",UUID.randomUUID().toString()));
        process.environment().put("OG_UI_VOUCHER_CODE",code);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM og_compat.notifications WHERE user_id=? AND type='UC55_VOUCHER_GRANTED' AND target_data->>'destination'='KTV_VOUCHERS'",Integer.class,ktvId)).isEqualTo(1);
        LiveUiTestRunner.run(process);
    }

    private Long buyerId;
    private Long ktvId;
    private Long admin1Id;
    private Long admin2Id;

    @BeforeEach
    void setUp() {
        buyerId = createUser("sys_buyer", "BUYER");
        ktvId = createUser("sys_ktv", "KTV");
        admin1Id = createUser("sys_admin1", "ADMIN");
        admin2Id = createUser("sys_admin2", "ADMIN");
    }

    private Long createUser(String prefix, String role) {
        Long userId = jdbc.queryForObject("""
                INSERT INTO og_compat.users(email, password_hash, full_name, status)
                VALUES (?, 'fixture_pass', ?, 'ACTIVE') RETURNING user_id
                """, Long.class, prefix + "_" + UUID.randomUUID() + "@example.test", "User " + prefix);
        jdbc.update("""
                INSERT INTO og_compat.user_roles(user_id, role_id)
                SELECT ?, role_id FROM og_compat.roles WHERE role_name = ?
                ON CONFLICT DO NOTHING
                """, userId, role);
        return userId;
    }

    @Test
    @DisplayName("SYSTEM_ISSUE REJECT_ISSUE preserves state=REJECTED, blocks confirmedSystemIssue & blocks compensation")
    void testSystemIssueRejectionPreservesRejectedAndBlocksCompensation() {
        var created = governanceService.createSystemIssue(buyerId, new ReturnCaseDtos.CreateSystemIssueRequest(
                null, null, null, null,
                "Lỗi hiển thị giỏ hàng khi nhấn thanh toán nhiều lần",
                List.of(), "CMD_SYS_REJ_" + UUID.randomUUID()
        ));

        var rejected = governanceService.decideSystemIssue(ktvId, false, created.caseId(),
                new ReturnCaseDtos.SystemIssueDecisionRequest(
                        "REJECT_ISSUE",
                        "Không ghi nhận bất thường trên hệ thống ở thời điểm giao dịch",
                        null, null, "CMD_DEC_REJ_" + UUID.randomUUID()
                ));

        assertThat(rejected.state()).isEqualTo("REJECTED");
        assertThat(accountTrustRepository.confirmedSystemIssue(buyerId, created.caseId())).isFalse();

        assertThatThrownBy(() -> governanceService.executeCompensation(
                ktvId, false, created.caseId(),
                new ReturnCaseDtos.CaseCompensationRequest(
                        "VOUCHER", buyerId, 1L, null, null, null,
                        "Thử cấp bù khi đã từ chối", "CMD_COMP_FAIL_" + UUID.randomUUID()
                )))
                .isInstanceOf(TrustSafetyBusinessException.class)
                .hasMessageContaining("SYSTEM_ISSUE");
    }

    @Test
    @DisplayName("PO-02 A: Escalate to Admin1 -> Admin2 blocked -> Handover to Admin2 -> Confirm -> UC70 compensation & dedup")
    void testDesignatedAdminHandoverAndUc70CompensationDedup() {
        var created = governanceService.createSystemIssue(buyerId, new ReturnCaseDtos.CreateSystemIssueRequest(
                null, null, null, null,
                "Hệ thống timeout khi xác nhận thanh toán đơn hàng lớn",
                List.of(), "CMD_SYS_ESC_" + UUID.randomUUID()
        ));

        var escalated = governanceService.ktvEscalateCase(ktvId, created.caseId(),
                new ReturnCaseDtos.KtvEscalateCaseRequest(
                        admin1Id, "Cần Admin1 duyệt chính sách bồi thường vượt hạn mức", "CMD_ESC_" + UUID.randomUUID()
                ));
        assertThat(escalated.assignedTo()).isEqualTo(admin1Id);

        // Admin2 is NOT designated yet -> blocked
        assertThatThrownBy(() -> governanceService.decideSystemIssue(admin2Id, true, created.caseId(),
                new ReturnCaseDtos.SystemIssueDecisionRequest(
                        "CONFIRM_ISSUE", "Admin2 tự quyết khi chưa được bàn giao",
                        null, null, "CMD_ADM2_BLK_" + UUID.randomUUID()
                )))
                .isInstanceOf(TrustSafetyBusinessException.class)
                .hasMessageContaining("Admin");

        // Admin1 hands over to Admin2 with recorded ASSIGNED event
        var handedOver = governanceService.adminHandoverCase(admin1Id, created.caseId(),
                new ReturnCaseDtos.AdminHandoverRequest(
                        admin2Id, "Admin1 nghỉ phép, bàn giao toàn quyền xử lý cho Admin2", "CMD_HO_" + UUID.randomUUID()
                ));
        assertThat(handedOver.assignedTo()).isEqualTo(admin2Id);

        // Now Admin1 is blocked, Admin2 decides CONFIRM_ISSUE
        assertThatThrownBy(() -> governanceService.decideSystemIssue(admin1Id, true, created.caseId(),
                new ReturnCaseDtos.SystemIssueDecisionRequest(
                        "CONFIRM_ISSUE", "Admin1 quyết định sau khi đã bàn giao",
                        null, null, "CMD_ADM1_BLK_" + UUID.randomUUID()
                )))
                .isInstanceOf(TrustSafetyBusinessException.class);

        Instant now = Instant.now().truncatedTo(ChronoUnit.SECONDS);
        var confirmed = governanceService.decideSystemIssue(admin2Id, true, created.caseId(),
                new ReturnCaseDtos.SystemIssueDecisionRequest(
                        "CONFIRM_ISSUE", "Xác nhận lỗi hệ thống thanh toán tại khung giờ sự cố",
                        now.minus(2, ChronoUnit.HOURS), now.minus(1, ChronoUnit.HOURS),
                        "CMD_CONF_" + UUID.randomUUID()
                ));
        assertThat(confirmed.state()).isEqualTo("CLOSED");
        assertThat(accountTrustRepository.confirmedSystemIssue(buyerId, created.caseId())).isTrue();

        // Create UC70 voucher campaign & grant compensation via executeCompensation
        long voucherId = voucherWalletRepository.save(admin2Id, null, new VoucherDtos.Save(
                UUID.randomUUID(), 0L,
                "COMP" + UUID.randomUUID().toString().replace("-", "").substring(0, 8).toUpperCase(),
                "Voucher bồi thường sự cố", "Bồi thường UC70",
                "ORDER_DISCOUNT", "FIXED_AMOUNT",
                new BigDecimal("30000"), new BigDecimal("30000"), new BigDecimal("0"),
                "CHECKOUT", List.of(), 100,
                now.minus(1, ChronoUnit.DAYS), now.plus(30, ChronoUnit.DAYS), true, false
        ));

        var compRes = governanceService.executeCompensation(admin2Id, true, created.caseId(),
                new ReturnCaseDtos.CaseCompensationRequest(
                        "VOUCHER", buyerId, voucherId, null, null, null,
                        "Cấp voucher bồi thường lỗi thanh toán", "CMD_COMP_1_" + UUID.randomUUID()
                ));
        assertThat(compRes.grantId()).isNotNull();
        assertThat(voucherWalletRepository.owned(buyerId)).hasSize(1);

        // Different commandKey on same (caseId, recipientId, revisionId) must be rejected by DB dedup index
        assertThatThrownBy(() -> governanceService.executeCompensation(admin2Id, true, created.caseId(),
                new ReturnCaseDtos.CaseCompensationRequest(
                        "VOUCHER", buyerId, voucherId, null, null, null,
                        "Thử cấp trùng cùng sự việc", "CMD_COMP_2_" + UUID.randomUUID()
                )))
                .isInstanceOf(TrustSafetyBusinessException.class)
                .hasMessageContaining("bồi thường");
    }

    @Test
    @DisplayName("UC81 classifies SYSTEM_ISSUE confirmed vs rejected properly and CSV reuses exact cutoffAt snapshot")
    void testUc81ClassificationAndCutoffSnapshotConsistency() {
        var sysConfirmed = governanceService.createSystemIssue(buyerId, new ReturnCaseDtos.CreateSystemIssueRequest(
                null, null, null, null, "Lỗi hệ thống ca được xác nhận để thống kê UC81",
                List.of(), "CMD_UC81_CONF_" + UUID.randomUUID()
        ));
        governanceService.decideSystemIssue(ktvId, false, sysConfirmed.caseId(),
                new ReturnCaseDtos.SystemIssueDecisionRequest(
                        "CONFIRM_ISSUE", "Xác nhận lỗi hệ thống hợp lệ",
                        null, null, "CMD_UC81_C_DEC_" + UUID.randomUUID()
                ));

        var sysRejected = governanceService.createSystemIssue(buyerId, new ReturnCaseDtos.CreateSystemIssueRequest(
                null, null, null, null, "Lỗi hệ thống ca bị từ chối để thống kê UC81",
                List.of(), "CMD_UC81_REJ_" + UUID.randomUUID()
        ));
        governanceService.decideSystemIssue(ktvId, false, sysRejected.caseId(),
                new ReturnCaseDtos.SystemIssueDecisionRequest(
                        "REJECT_ISSUE", "Từ chối vì thao tác sai từ phía thiết bị",
                        null, null, "CMD_UC81_R_DEC_" + UUID.randomUUID()
                ));

        entityManager.flush();

        ZoneId vn = ZoneId.of("Asia/Ho_Chi_Minh");
        LocalDate today = LocalDate.now(vn);
        Instant cutoffT1 = Instant.now().plusSeconds(1);

        AdminDashboardDtos.ReturnsReportResponse report = adminDashboardService.getReturnsReport(
                today, today, "Asia/Ho_Chi_Minh", "SYSTEM_ISSUE", null, cutoffT1
        );

        assertThat(report.cutoffAt()).isEqualTo(cutoffT1);
        assertThat(report.resolvedCases())
                .anySatisfy(r -> {
                    assertThat(r.caseId()).isEqualTo(sysConfirmed.caseId());
                    assertThat(r.outcomeBucket()).isEqualTo("SYSTEM_ISSUE_CONFIRMED");
                    assertThat(r.finalState()).isEqualTo("CLOSED");
                })
                .anySatisfy(r -> {
                    assertThat(r.caseId()).isEqualTo(sysRejected.caseId());
                    assertThat(r.outcomeBucket()).isEqualTo("SYSTEM_ISSUE_REJECTED");
                    assertThat(r.finalState()).isEqualTo("REJECTED");
                });
        assertThat(report.systemIssueConfirmedCount()).isGreaterThanOrEqualTo(1L);
        assertThat(report.systemIssueRejectedCount()).isGreaterThanOrEqualTo(1L);

        // Create a later resolved SYSTEM_ISSUE AFTER cutoffT1
        var laterSys = governanceService.createSystemIssue(buyerId, new ReturnCaseDtos.CreateSystemIssueRequest(
                null, null, null, null, "Ca mới đóng sau cutoffT1 không được lọt vào CSV",
                List.of(), "CMD_UC81_LATE_" + UUID.randomUUID()
        ));
        var laterClosed = governanceService.decideSystemIssue(ktvId, false, laterSys.caseId(),
                new ReturnCaseDtos.SystemIssueDecisionRequest(
                        "CONFIRM_ISSUE", "Đóng sau cutoffT1",
                        null, null, "CMD_UC81_L_DEC_" + UUID.randomUUID()
                ));
        jdbc.update("UPDATE og_compat.cases SET closed_at = ? WHERE case_id = ?",
                Timestamp.from(cutoffT1.plusSeconds(120)), laterClosed.caseId());

        // Export CSV with same cutoffT1 -> excludes laterClosed, includes sysConfirmed & sysRejected, and writes audit row
        byte[] csvBytes = adminDashboardService.exportReturnsReportCsv(
                admin1Id, today, today, "Asia/Ho_Chi_Minh", "SYSTEM_ISSUE", null, cutoffT1
        );
        String csv = new String(csvBytes, StandardCharsets.UTF_8);
        assertThat(csv).contains("\n" + sysConfirmed.caseId() + ",");
        assertThat(csv).contains("\n" + sysRejected.caseId() + ",");
        assertThat(csv).doesNotContain("\n" + laterClosed.caseId() + ",");

        Integer exportAuditCount = jdbc.queryForObject("""
                SELECT count(*) FROM og_compat.report_export_runs
                WHERE requested_by = ? AND metric = 'RETURN_CASES' AND cutoff_at = ?
                """, Integer.class, admin1Id, Timestamp.from(cutoffT1));
        assertThat(exportAuditCount).isEqualTo(1);
    }

    @Test
    @DisplayName("Commit 5: Unescalated compensation only allowed for assigned KTV (even on commandKey replay), savepoint isolates notif errors, and PRIVATE_SECRET blocks tokens/secrets")
    void testUnescalatedKtvAssignmentReplaySavepointAndPrivateSecrets() {
        Long otherKtvId = createUser("sys_ktv2", "KTV");
        var created = governanceService.createSystemIssue(buyerId, new ReturnCaseDtos.CreateSystemIssueRequest(
                null, null, null, null,
                "Lỗi hệ thống thanh toán đơn hàng cần KTV xác nhận",
                List.of(), "CMD_C5_NEW_" + UUID.randomUUID()
        ));
        governanceService.decideSystemIssue(ktvId, false, created.caseId(),
                new ReturnCaseDtos.SystemIssueDecisionRequest(
                        "CONFIRM_ISSUE", "KTV1 xác nhận lỗi hệ thống",
                        null, null, "CMD_C5_DEC_" + UUID.randomUUID()
                ));

        Instant now = Instant.now().truncatedTo(ChronoUnit.SECONDS);
        long voucherId = voucherWalletRepository.save(admin1Id, null, new VoucherDtos.Save(
                UUID.randomUUID(), 0L,
                "CMP5" + UUID.randomUUID().toString().replace("-", "").substring(0, 7).toUpperCase(),
                "Voucher bồi thường C5", "Bồi thường C5",
                "ORDER_DISCOUNT", "FIXED_AMOUNT",
                new BigDecimal("20000"), new BigDecimal("20000"), BigDecimal.ZERO,
                "CHECKOUT", List.of(), 100,
                now.minus(1, ChronoUnit.DAYS), now.plus(7, ChronoUnit.DAYS), true, false
        ));

        String sharedCmdKey = "CMD_C5_REPLAY_" + UUID.randomUUID();
        var compReq = new ReturnCaseDtos.CaseCompensationRequest(
                "VOUCHER", buyerId, voucherId, null, null, null,
                "Cấp voucher bồi thường hợp lệ", sharedCmdKey
        );

        // Non-assigned KTV & Admin cannot grant on unescalated confirmed issue
        assertThatThrownBy(() -> governanceService.executeCompensation(otherKtvId, false, created.caseId(), compReq))
                .isInstanceOf(TrustSafetyBusinessException.class)
                .hasMessageContaining("KTV");
        assertThatThrownBy(() -> governanceService.executeCompensation(admin1Id, true, created.caseId(), compReq))
                .isInstanceOf(TrustSafetyBusinessException.class);

        // Savepoint isolates Postgres notification error so compensation + voucher grant commit cleanly
        jdbc.execute("""
                CREATE OR REPLACE FUNCTION public.og_test_fail_comp_notif() RETURNS trigger LANGUAGE plpgsql AS $$
                BEGIN
                    IF NEW.type = 'UC55_COMPENSATION_VOUCHER' THEN
                        RAISE EXCEPTION 'Simulated voucher notification failure';
                    END IF;
                    RETURN NEW;
                END $$;
                CREATE TRIGGER trg_test_fail_comp_notif
                BEFORE INSERT ON public.notifications
                FOR EACH ROW EXECUTE FUNCTION public.og_test_fail_comp_notif();
                """);
        try {
            var granted = governanceService.executeCompensation(ktvId, false, created.caseId(), compReq);
            assertThat(granted.grantId()).isNotNull();
            assertThat(voucherWalletRepository.owned(buyerId)).hasSize(1);
        } finally {
            jdbc.execute("DROP TRIGGER IF EXISTS trg_test_fail_comp_notif ON public.notifications");
            jdbc.execute("DROP FUNCTION IF EXISTS public.og_test_fail_comp_notif()");
        }

        // Authorization is checked BEFORE idempotent commandKey replay: other KTV & Admin still 403 replaying sharedCmdKey
        assertThatThrownBy(() -> governanceService.executeCompensation(otherKtvId, false, created.caseId(), compReq))
                .isInstanceOf(TrustSafetyBusinessException.class);
        assertThatThrownBy(() -> governanceService.executeCompensation(admin1Id, true, created.caseId(), compReq))
                .isInstanceOf(TrustSafetyBusinessException.class);

        // Assigned KTV replaying same commandKey gets idempotent success
        var replay = governanceService.executeCompensation(ktvId, false, created.caseId(), compReq);
        assertThat(replay.message()).contains("idempotent");

        // PRIVATE_SECRET blocks token, secret, api_key, Bearer
        for (String leaky : List.of(
                "Lỗi với token: abc123secretvalue",
                "Gửi secret=myTopSecret999 trong payload",
                "Lộ api_key=sk_live_1234567890",
                "Authorization Header Bearer eyJhbGciOiJIUzI1NiJ9"
        )) {
            assertThatThrownBy(() -> governanceService.createSystemIssue(buyerId,
                    new ReturnCaseDtos.CreateSystemIssueRequest(null, null, null, null, leaky, List.of(), UUID.randomUUID().toString())))
                    .isInstanceOf(TrustSafetyBusinessException.class)
                    .satisfies(ex -> assertThat(((TrustSafetyBusinessException) ex).getCode()).isEqualTo("PRIVATE_SECRET"));
        }
    }
}

