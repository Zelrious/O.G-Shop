package com.oldbutgold.shop.modules.trustsafety.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.oldbutgold.shop.modules.trustsafety.infrastructure.persistence.ReturnReasonCode;
import com.oldbutgold.shop.shared.PostgresTestSafetyGuard;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@EnabledIfEnvironmentVariable(named = "OGSHOP_TEST_DB_URL", matches = ".+")
@DisplayName("Commit 3 Postgres + HTTP Integration Test: Private Evidence Storage & V63 Append-Only Migration")
class PrivateEvidenceStoragePostgresIntegrationTest {

    @TempDir
    static Path tempRoot;

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
        registry.add("app.trustsafety.evidence-storage-root", () -> tempRoot.resolve("storage-root").toAbsolutePath().toString());
        registry.add("app.media.upload-dir", () -> tempRoot.resolve("public-media").toAbsolutePath().toString());
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Autowired
    private EvidenceStorageService evidenceStorageService;

    @Autowired
    private ReturnCaseService returnCaseService;

    @Autowired
    private CaseEvidenceRequestService caseEvidenceRequestService;

    private Long ktvId;
    private Long buyerId;
    private Long sellerId;
    private Long unrelatedUserId;

    @BeforeEach
    void setUpBaseUsers() {
        TransactionTemplate tx = new TransactionTemplate(transactionManager);
        tx.executeWithoutResult(status -> {
            ktvId = createUser("ktv");
            buyerId = createUser("buyer");
            sellerId = createUser("seller");
            unrelatedUserId = createUser("other");
            grantRole(ktvId, "KTV");
            grantRole(buyerId, "BUYER");
            grantRole(sellerId, "SELLER");
            grantRole(unrelatedUserId, "BUYER");
        });
    }

    @Test
    @DisplayName("1. Upload validation, private draft isolation, and attachment ownership across UC83, legacy disputes, and Seller R2")
    void uploadDraftIsolationAndAllAttachmentPathsWorkEndToEnd() throws Exception {
        byte[] validPng = minimalPngBytes();
        byte[] validMp4 = buildMp4Bytes(1000, 15_000L);
        byte[] overLimitMp4 = buildMp4Bytes(1000, 65_000L);
        byte[] noMetaMp4 = new byte[]{0, 0, 0, 12, 'f', 't', 'y', 'p', 'i', 's', 'o', 'm'};

        // 1a. Reject forged magic bytes, video > 60s, and video missing metadata (even with client hint)
        mockMvc.perform(multipart("/api/v1/trustsafety/returns/evidence/upload")
                        .file(new MockMultipartFile("file", "forged.png", "image/png", "NOT_A_PNG".getBytes(StandardCharsets.UTF_8)))
                        .param("mediaType", "IMAGE")
                        .with(userJwt(buyerId, "ROLE_BUYER")))
                .andExpect(status().isBadRequest());

        mockMvc.perform(multipart("/api/v1/trustsafety/returns/evidence/upload")
                        .file(new MockMultipartFile("file", "long.mp4", "video/mp4", overLimitMp4))
                        .param("mediaType", "VIDEO")
                        .param("durationSeconds", "10")
                        .with(userJwt(buyerId, "ROLE_BUYER")))
                .andExpect(status().isBadRequest());

        mockMvc.perform(multipart("/api/v1/trustsafety/returns/evidence/upload")
                        .file(new MockMultipartFile("file", "nometa.mp4", "video/mp4", noMetaMp4))
                        .param("mediaType", "VIDEO")
                        .param("durationSeconds", "30")
                        .with(userJwt(buyerId, "ROLE_BUYER")))
                .andExpect(status().isBadRequest());

        // 1b. Valid PNG + valid MP4 upload to private storage (NOT served on /api/v1/media/**)
        MvcResult buyerImgRes = mockMvc.perform(multipart("/api/v1/trustsafety/returns/evidence/upload")
                        .file(new MockMultipartFile("file", "unbox.png", "image/png", validPng))
                        .param("mediaType", "IMAGE")
                        .with(userJwt(buyerId, "ROLE_BUYER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.objectKey").isString())
                .andReturn();
        String buyerImgKey = objectMapper.readTree(buyerImgRes.getResponse().getContentAsString()).get("objectKey").asText();
        assertThat(buyerImgKey).startsWith("/api/v1/trustsafety/returns/evidence/");
        String buyerImgFile = buyerImgKey.substring(EvidenceStorageService.PRIVATE_EVIDENCE_PREFIX.length());

        MvcResult buyerVidRes = mockMvc.perform(multipart("/api/v1/trustsafety/returns/evidence/upload")
                        .file(new MockMultipartFile("file", "unbox.mp4", "video/mp4", validMp4))
                        .param("mediaType", "VIDEO")
                        .param("durationSeconds", "999")
                        .with(userJwt(buyerId, "ROLE_BUYER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.durationSeconds").value(15))
                .andReturn();
        String buyerVidKey = objectMapper.readTree(buyerVidRes.getResponse().getContentAsString()).get("objectKey").asText();

        MvcResult otherUploadRes = mockMvc.perform(multipart("/api/v1/trustsafety/returns/evidence/upload")
                        .file(new MockMultipartFile("file", "stolen.png", "image/png", validPng))
                        .param("mediaType", "IMAGE")
                        .with(userJwt(unrelatedUserId, "ROLE_BUYER")))
                .andExpect(status().isOk())
                .andReturn();
        String otherKey = objectMapper.readTree(otherUploadRes.getResponse().getContentAsString()).get("objectKey").asText();

        MvcResult sellerUploadRes = mockMvc.perform(multipart("/api/v1/trustsafety/returns/evidence/upload")
                        .file(new MockMultipartFile("file", "seller-r2.png", "image/png", validPng))
                        .param("mediaType", "IMAGE")
                        .with(userJwt(sellerId, "ROLE_SELLER")))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode sellerUploadJson = objectMapper.readTree(sellerUploadRes.getResponse().getContentAsString());
        String sellerKey = sellerUploadJson.get("objectKey").asText();
        String sellerSha = sellerUploadJson.get("contentDigest").asText();

        // 1c. Draft isolation: unauthenticated 401, other user 403, owner 200 (private, no-store), KTV 200
        mockMvc.perform(get(buyerImgKey))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get(buyerImgKey).with(userJwt(unrelatedUserId, "ROLE_BUYER")))
                .andExpect(status().isForbidden());
        mockMvc.perform(get(buyerImgKey).with(userJwt(buyerId, "ROLE_BUYER")))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "private, no-store"));
        mockMvc.perform(get(buyerImgKey).with(userJwt(ktvId, "ROLE_KTV")))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "private, no-store"));
        // Even direct public media lookup with private filename is 404
        mockMvc.perform(get("/api/v1/media/" + buyerImgFile))
                .andExpect(status().isNotFound());

        // 1d. UC83 attachment ownership: other user's file -> 403, invalid key -> 400, own files -> 200
        Long uc83OrderId = createDeliveredUc83OrderInTx(buyerId, sellerId);
        mockMvc.perform(post("/api/v1/trustsafety/returns/" + uc83OrderId)
                        .with(userJwt(buyerId, "ROLE_BUYER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new ReturnCaseDtos.CreateReturnRequest(
                                ReturnReasonCode.DAMAGED_OR_DEFECTIVE,
                                "Thử gắn file người khác",
                                List.of(new ReturnCaseDtos.EvidenceItemDto("IMAGE", otherKey, null, null, (long) validPng.length, null)),
                                "CMD_BAD_OWN_" + UUID.randomUUID()))))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/v1/trustsafety/returns/" + uc83OrderId)
                        .with(userJwt(buyerId, "ROLE_BUYER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new ReturnCaseDtos.CreateReturnRequest(
                                ReturnReasonCode.DAMAGED_OR_DEFECTIVE,
                                "Thử khóa không tồn tại",
                                List.of(new ReturnCaseDtos.EvidenceItemDto("IMAGE",
                                        "/api/v1/trustsafety/returns/evidence/" + UUID.randomUUID() + ".png",
                                        null, null, 100L, null)),
                                "CMD_BAD_KEY_" + UUID.randomUUID()))))
                .andExpect(status().isBadRequest());

        MvcResult createdUc83 = mockMvc.perform(post("/api/v1/trustsafety/returns/" + uc83OrderId)
                        .with(userJwt(buyerId, "ROLE_BUYER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new ReturnCaseDtos.CreateReturnRequest(
                                ReturnReasonCode.DAMAGED_OR_DEFECTIVE,
                                "Hàng bị vỡ góc",
                                List.of(
                                        new ReturnCaseDtos.EvidenceItemDto("IMAGE", buyerImgKey, null, sha256Hex(validPng), (long) validPng.length, null),
                                        new ReturnCaseDtos.EvidenceItemDto("VIDEO", buyerVidKey, null, sha256Hex(validMp4), (long) validMp4.length, 15)
                                ),
                                "CMD_OK_UC83_" + UUID.randomUUID()))))
                .andExpect(status().isOk())
                .andReturn();
        long uc83CaseId = objectMapper.readTree(createdUc83.getResponse().getContentAsString()).get("caseId").asLong();

        // Once attached to case, Seller on that case can read it, unrelatedUser still 403
        mockMvc.perform(get(buyerImgKey).with(userJwt(sellerId, "ROLE_SELLER")))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "private, no-store"));
        mockMvc.perform(get(buyerImgKey).with(userJwt(unrelatedUserId, "ROLE_BUYER")))
                .andExpect(status().isForbidden());

        // 1e. Seller R2 resubmission ownership + private hash resolution
        UUID r2RequestId = prepareSellerR2ResubmissionRequest(uc83CaseId, buyerId, sellerId, ktvId);
        // Reject seller attaching other user's upload key
        mockMvc.perform(post("/api/v1/trustsafety/returns/" + uc83CaseId + "/seller/damage-request")
                        .with(userJwt(sellerId, "ROLE_SELLER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new ReturnCaseDtos.SellerDamageExemptionRequest(
                                "Bổ sung thử key người khác",
                                List.of(new ReturnCaseDtos.EvidenceItemDto("IMAGE", otherKey, null, null, (long) validPng.length, null)),
                                "CMD_R2_BAD_" + UUID.randomUUID(),
                                r2RequestId))))
                .andExpect(status().isForbidden());

        // Succeed with seller's own uploaded private key (hash verified against private storage without client sending contentDigest)
        mockMvc.perform(post("/api/v1/trustsafety/returns/" + uc83CaseId + "/seller/damage-request")
                        .with(userJwt(sellerId, "ROLE_SELLER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new ReturnCaseDtos.SellerDamageExemptionRequest(
                                "Bằng chứng đóng gói hợp lệ vòng 2",
                                List.of(new ReturnCaseDtos.EvidenceItemDto("IMAGE", sellerKey, null, null, (long) validPng.length, null)),
                                "CMD_R2_OK_" + UUID.randomUUID(),
                                r2RequestId))))
                .andExpect(status().isOk());

        String savedDigest = jdbcTemplate.queryForObject(
                "SELECT content_digest FROM og_compat.case_evidence WHERE request_id = ? LIMIT 1",
                String.class, r2RequestId);
        assertThat(savedDigest).isEqualTo(sellerSha);

        // 1f. Legacy dispute create + seller response ownership
        Long legacyOrderId = createDeliveredUc83OrderInTx(buyerId, sellerId);
        MvcResult legBuyerUpload = mockMvc.perform(multipart("/api/v1/trustsafety/returns/evidence/upload")
                        .file(new MockMultipartFile("file", "leg-b.png", "image/png", validPng))
                        .param("mediaType", "IMAGE")
                        .with(userJwt(buyerId, "ROLE_BUYER")))
                .andExpect(status().isOk()).andReturn();
        String legBuyerKey = objectMapper.readTree(legBuyerUpload.getResponse().getContentAsString()).get("objectKey").asText();

        mockMvc.perform(post("/api/v1/commerce/orders/" + legacyOrderId + "/dispute")
                        .with(userJwt(buyerId, "ROLE_BUYER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"orderId\":" + legacyOrderId + ",\"reason\":\"Hư hỏng\",\"description\":\"Chi tiết\",\"buyerEvidenceUrls\":[\"" + otherKey + "\"]}"))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/v1/commerce/orders/" + legacyOrderId + "/dispute")
                        .with(userJwt(buyerId, "ROLE_BUYER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"orderId\":" + legacyOrderId + ",\"reason\":\"Hư hỏng\",\"description\":\"Chi tiết\",\"buyerEvidenceUrls\":[\"" + legBuyerKey + "\"]}"))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/commerce/orders/" + legacyOrderId + "/dispute/seller-response")
                        .with(userJwt(sellerId, "ROLE_SELLER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sellerResponse\":\"Giải trình\",\"sellerEvidenceUrls\":[\"" + otherKey + "\"]}"))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/v1/commerce/orders/" + legacyOrderId + "/dispute/seller-response")
                        .with(userJwt(sellerId, "ROLE_SELLER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sellerResponse\":\"Giải trình hợp lệ\",\"sellerEvidenceUrls\":[\"" + sellerKey + "\"]}"))
                .andExpect(status().isOk());

        // Legacy counterparties can read each other's attached evidence; unrelatedUser still blocked
        mockMvc.perform(get(legBuyerKey).with(userJwt(sellerId, "ROLE_SELLER")))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "private, no-store"));
        mockMvc.perform(get(legBuyerKey).with(userJwt(unrelatedUserId, "ROLE_BUYER")))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("2. Lock-first legacy migration: keeps case_evidence append-only, handles shared files, cleans interrupted leftovers, blocks public URLs while keeping product media 200")
    void lockFirstLegacyMigrationPreservesAppendOnlyAndSharedAccess() throws Exception {
        Set<String> preBlockedKeys = new HashSet<>(jdbcTemplate.queryForList(
                "SELECT legacy_key FROM public.evidence_blocked_public_keys", String.class));
        Set<String> preMappedKeys = new HashSet<>(jdbcTemplate.queryForList(
                "SELECT legacy_key FROM public.evidence_storage_map", String.class));
        try {
            byte[] sharedBytes = minimalPngBytes();
            byte[] interruptedBytes = minimalPngBytes();
            byte[] productBytes = minimalPngBytes();

            Path publicMediaDir = tempRoot.resolve("public-media");
            Path privateEvidenceDir = tempRoot.resolve("storage-root").resolve("evidence");
            Files.createDirectories(publicMediaDir);
            Files.createDirectories(privateEvidenceDir);

            String sharedLegacyName = "legacy-shared-" + UUID.randomUUID() + ".png";
            String sharedLegacyKey = "/api/v1/media/" + sharedLegacyName;
            Files.write(publicMediaDir.resolve(sharedLegacyName), sharedBytes);

            String interruptedLegacyName = "legacy-interrupted-" + UUID.randomUUID() + ".png";
            String interruptedLegacyKey = "/api/v1/media/" + interruptedLegacyName;
            String interruptedPrivateName = UUID.randomUUID() + ".png";
            // Simulate interrupted run: private copy & mapping already exist, AND leftover public file is still on disk
            Files.write(publicMediaDir.resolve(interruptedLegacyName), interruptedBytes);
            Files.write(privateEvidenceDir.resolve(interruptedPrivateName), interruptedBytes);
            jdbcTemplate.update(
                    "INSERT INTO public.evidence_blocked_public_keys(legacy_key) VALUES (?) ON CONFLICT (legacy_key) DO NOTHING",
                    interruptedLegacyKey);
            jdbcTemplate.update(
                    "INSERT INTO public.evidence_storage_map(legacy_key, private_filename, sha256, size_bytes) VALUES (?, ?, ?, ?) ON CONFLICT (legacy_key) DO NOTHING",
                    interruptedLegacyKey, interruptedPrivateName, sha256Hex(interruptedBytes), interruptedBytes.length);

            String productPublicName = UUID.randomUUID() + ".png";
            Files.write(publicMediaDir.resolve(productPublicName), productBytes);

            // Two distinct cases (A & B) referencing the SAME shared legacy evidence key
            Long buyerA = createUserInNewTx("buyerA");
            Long sellerA = createUserInNewTx("sellerA");
            Long buyerB = createUserInNewTx("buyerB");
            Long sellerB = createUserInNewTx("sellerB");

            Long caseA = createCaseWithDirectLegacyEvidence(buyerA, sellerA, List.of(sharedLegacyKey, interruptedLegacyKey));
            Long caseB = createCaseWithDirectLegacyEvidence(buyerB, sellerB, List.of(sharedLegacyKey));

            // Lock-first test: even BEFORE migrate moves sharedLegacyName, blocking its key makes /api/v1/media/<name> return 404 while copy is still on disk
            jdbcTemplate.update(
                    "INSERT INTO public.evidence_blocked_public_keys(legacy_key) VALUES (?) ON CONFLICT (legacy_key) DO NOTHING",
                    sharedLegacyKey);
            assertThat(Files.exists(publicMediaDir.resolve(sharedLegacyName))).isTrue();
            mockMvc.perform(get(sharedLegacyKey))
                    .andExpect(status().isNotFound());

            // Execute migration (handles sharedLegacyName move + cleans leftover interruptedLegacyName)
            EvidenceStorageService.LegacyMigrationReport report = evidenceStorageService.migrateLegacyEvidenceFiles();
            assertThat(report.migratedCount()).isGreaterThanOrEqualTo(1);
            assertThat(report.cleanedDuplicateCount()).isGreaterThanOrEqualTo(1);

            // Leftover and migrated public files removed
            assertThat(Files.exists(publicMediaDir.resolve(sharedLegacyName))).isFalse();
            assertThat(Files.exists(publicMediaDir.resolve(interruptedLegacyName))).isFalse();

            // Public product image STILL 200 OK
            mockMvc.perform(get("/api/v1/media/" + productPublicName))
                    .andExpect(status().isOk());

            // Even if a stale copy is restored to publicMediaDir (or exists in CDN/disk), public /api/v1/media/** remains 404
            Files.write(publicMediaDir.resolve(sharedLegacyName), sharedBytes);
            mockMvc.perform(get(sharedLegacyKey))
                    .andExpect(status().isNotFound());

            // og_compat.case_evidence was NEVER mutated (V22 append-only preserved)
            Integer legacyCount = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM og_compat.case_evidence WHERE object_key = ?",
                    Integer.class, sharedLegacyKey);
            assertThat(legacyCount).isEqualTo(2);

            // Read-time translation in case detail returns private /api/v1/trustsafety/returns/evidence/... URL
            ReturnCaseDtos.ReturnCaseDetailResponse detailA = returnCaseService.getReturnCaseById(buyerA, caseA);
            String translatedSharedUrl = detailA.evidence().stream()
                    .map(ReturnCaseDtos.CaseEvidenceDto::objectKey)
                    .filter(k -> k != null && k.startsWith(EvidenceStorageService.PRIVATE_EVIDENCE_PREFIX) && !k.endsWith(interruptedPrivateName))
                    .findFirst()
                    .orElseThrow();

            ReturnCaseDtos.ReturnCaseDetailResponse detailB = returnCaseService.getReturnCaseById(buyerB, caseB);
            assertThat(detailB.evidence()).anyMatch(e -> translatedSharedUrl.equals(e.objectKey()));

            // All authorized parties from BOTH case A and case B can fetch the shared private evidence; unrelated user is 403
            mockMvc.perform(get(translatedSharedUrl).with(userJwt(buyerA, "ROLE_BUYER")))
                    .andExpect(status().isOk())
                    .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "private, no-store"));
            mockMvc.perform(get(translatedSharedUrl).with(userJwt(sellerA, "ROLE_SELLER")))
                    .andExpect(status().isOk());
            mockMvc.perform(get(translatedSharedUrl).with(userJwt(buyerB, "ROLE_BUYER")))
                    .andExpect(status().isOk());
            mockMvc.perform(get(translatedSharedUrl).with(userJwt(sellerB, "ROLE_SELLER")))
                    .andExpect(status().isOk());
            mockMvc.perform(get(translatedSharedUrl).with(userJwt(unrelatedUserId, "ROLE_BUYER")))
                    .andExpect(status().isForbidden());
        } finally {
            List<String> afterBlockedKeys = jdbcTemplate.queryForList(
                    "SELECT legacy_key FROM public.evidence_blocked_public_keys", String.class);
            for (String key : afterBlockedKeys) {
                if (!preBlockedKeys.contains(key)) {
                    jdbcTemplate.update("DELETE FROM public.evidence_blocked_public_keys WHERE legacy_key = ?", key);
                }
            }
            List<String> afterMappedKeys = jdbcTemplate.queryForList(
                    "SELECT legacy_key FROM public.evidence_storage_map", String.class);
            for (String key : afterMappedKeys) {
                if (!preMappedKeys.contains(key)) {
                    jdbcTemplate.update("DELETE FROM public.evidence_storage_map WHERE legacy_key = ?", key);
                }
            }
        }
    }

    private org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor userJwt(
            Long userId, String role
    ) {
        return jwt()
                .jwt(t -> t.subject(String.valueOf(userId)))
                .authorities(new SimpleGrantedAuthority(role));
    }

    private Long createUserInNewTx(String prefix) {
        TransactionTemplate tx = new TransactionTemplate(transactionManager);
        tx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        return tx.execute(s -> {
            Long id = createUser(prefix);
            grantRole(id, "BUYER");
            grantRole(id, "SELLER");
            return id;
        });
    }

    private Long createCaseWithDirectLegacyEvidence(Long fBuyerId, Long fSellerId, List<String> legacyKeys) {
        Long oid = createDeliveredUc83OrderInTx(fBuyerId, fSellerId);
        ReturnCaseDtos.ReturnCaseDetailResponse created = returnCaseService.createReturnRequest(
                fBuyerId, oid,
                new ReturnCaseDtos.CreateReturnRequest(ReturnReasonCode.DAMAGED_OR_DEFECTIVE, "Legacy", List.of(), "CMD_LEG_" + UUID.randomUUID()));
        TransactionTemplate tx = new TransactionTemplate(transactionManager);
        tx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        tx.executeWithoutResult(status -> {
            Long roundId = jdbcTemplate.queryForObject(
                    "SELECT round_id FROM og_compat.case_rounds WHERE case_id = ? AND round_no = 1",
                    Long.class, created.caseId());
            int seq = 0;
            for (String key : legacyKeys) {
                seq++;
                jdbcTemplate.update("""
                        INSERT INTO og_compat.case_evidence(evidence_id, round_id, submitted_by, submission_no, media_type, object_key, content_digest, file_size_bytes)
                        VALUES (?, ?, ?, ?, 'IMAGE', ?, ?, 128)
                        """, UUID.randomUUID(), roundId, fBuyerId, seq, key, repeatHex('a'));
            }
        });
        return created.caseId();
    }

    private UUID prepareSellerR2ResubmissionRequest(Long caseId, Long fBuyerId, Long fSellerId, Long fKtvId) {
        TransactionTemplate tx = new TransactionTemplate(transactionManager);
        tx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        tx.executeWithoutResult(status -> returnCaseService.sellerRespondRound1(
                fSellerId, caseId,
                new ReturnCaseDtos.SellerRound1ResponseRequest("APPROVE_RETURN", "Đồng ý trả hàng", "CMD_SR1_" + UUID.randomUUID())));

        Long shipmentId = tx.execute(status -> {
            ReturnCaseDtos.ReturnCaseDetailResponse submitted = returnCaseService.submitReturnShipment(
                    fBuyerId, caseId,
                    new ReturnCaseDtos.SubmitReturnShipmentRequest("CARRIER", "GHN", "GHN-R2-" + UUID.randomUUID().toString().substring(0, 8), "CMD_SHIP_" + UUID.randomUUID()));
            return submitted.returnShipment().shipmentId();
        });

        tx.executeWithoutResult(status -> returnCaseService.confirmReturnDeliveryByCarrier(caseId, shipmentId, Instant.now()));

        // Assign KTV and open pending R2 evidence request
        tx.executeWithoutResult(status -> {
            jdbcTemplate.update("UPDATE og_compat.cases SET assigned_to = ? WHERE case_id = ?", fKtvId, caseId);
        });

        // Seller submits initial damage exemption first so Round 2 has valid_damage_request_at + submission_no=1
        tx.executeWithoutResult(status -> {
            byte[] png = minimalPngBytes();
            var uploaded = evidenceStorageService.storePrivateEvidence(fSellerId,
                    new MockMultipartFile("file", "init-dmg.png", "image/png", png), "IMAGE");
            returnCaseService.submitDamageExemptionRequest(fSellerId, caseId,
                    new ReturnCaseDtos.SellerDamageExemptionRequest("Khiếu nại ban đầu", List.of(uploaded), "CMD_DMG1_" + UUID.randomUUID()));
        });

        ReturnCaseDtos.KtvRequestEvidenceResponse reqRes = tx.execute(status -> caseEvidenceRequestService.requestEvidence(
                fKtvId, caseId,
                new ReturnCaseDtos.KtvRequestEvidenceRequest("Cần bổ sung thêm ảnh chụp tem niêm phong", List.of("Tem niêm phong"), "CMD_REQ_R2_" + UUID.randomUUID()))
                .response());
        return reqRes.requestId();
    }

    private Long createUser(String prefix) {
        String normalizedPrefix = prefix.toLowerCase(java.util.Locale.ROOT);
        return jdbcTemplate.queryForObject(
                "INSERT INTO og_compat.users(email, password_hash, full_name, status) VALUES (?, 'fixture_pass', ?, 'ACTIVE') RETURNING user_id",
                Long.class,
                normalizedPrefix + "_" + UUID.randomUUID() + "@example.test",
                "User " + prefix);
    }

    private void grantRole(Long userId, String roleName) {
        jdbcTemplate.update("""
                INSERT INTO og_compat.user_roles(user_id, role_id)
                SELECT ?, r.role_id FROM og_compat.roles r
                WHERE r.role_name = ?
                  AND NOT EXISTS (
                      SELECT 1 FROM og_compat.user_roles ur
                      WHERE ur.user_id = ? AND ur.role_id = r.role_id
                  )
                """, userId, roleName, userId);
    }

    private Long createDeliveredUc83OrderInTx(Long fBuyerId, Long fSellerId) {
        TransactionTemplate tx = new TransactionTemplate(transactionManager);
        tx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        return tx.execute(status -> createCommittedUc83Order(fBuyerId, fSellerId));
    }

    private Long createCommittedUc83Order(Long fBuyerId, Long fSellerId) {
        grantRole(fSellerId, "BUYER");
        jdbcTemplate.update("UPDATE og_compat.users SET identity_workflow = 'UC83' WHERE user_id IN (?, ?)", fBuyerId, fSellerId);

        Long categoryId = jdbcTemplate.queryForObject("SELECT min(category_id) FROM og_compat.categories", Long.class);
        Long spCheck = jdbcTemplate.query(
                "SELECT seller_profile_id FROM og_compat.seller_profiles WHERE user_id = ? AND state = 'APPROVED' LIMIT 1",
                (rs, rn) -> rs.getLong(1), fSellerId).stream().findFirst().orElse(null);

        if (spCheck == null) {
            Long ekycId = jdbcTemplate.queryForObject(
                    "INSERT INTO og_compat.ekyc_profiles(user_id, revision_no) VALUES (?, 1) RETURNING profile_id",
                    Long.class, fSellerId);
            String hDigest = UUID.randomUUID().toString().replace("-", "") + UUID.randomUUID().toString().replace("-", "");
            jdbcTemplate.update(
                    "INSERT INTO og_compat.identity_document_registry(document_digest, digest_key_version, user_id, first_profile_id) VALUES (?, 'TEST-HMAC', ?, ?)",
                    hDigest, fSellerId, ekycId);
            for (String type : new String[]{"CARD_FRONT", "CARD_BACK", "LIVE_FRAME", "REFERENCE"}) {
                jdbcTemplate.update(
                        "INSERT INTO og_compat.ekyc_private_assets(profile_id, asset_type, object_key, content_digest, expires_at) VALUES (?, ?, ?, ?, CURRENT_TIMESTAMP + INTERVAL '2 months')",
                        ekycId, type, "p/" + type, hDigest);
            }
            jdbcTemplate.update("UPDATE og_compat.ekyc_profiles SET document_digest = ?, state = 'PENDING', submitted_at = CURRENT_TIMESTAMP WHERE profile_id = ?", hDigest, ekycId);
            jdbcTemplate.update("INSERT INTO og_compat.ekyc_decisions(profile_id, reviewed_by, outcome, command_key) VALUES (?, ?, 'VERIFIED', ?)", ekycId, ktvId, "CMD_EKYC_" + UUID.randomUUID());

            Long addrId = jdbcTemplate.queryForObject(
                    "INSERT INTO og_compat.addresses(user_id, recipient_name, phone_number, province, district, ward, detail_address) VALUES (?, 'Seller', '0900000000', 'P', 'D', 'W', 'A') RETURNING address_id",
                    Long.class, fSellerId);
            Long spId = jdbcTemplate.queryForObject(
                    "INSERT INTO og_compat.seller_profiles(user_id, state, ekyc_profile_id, pickup_address_id, bank_snapshot) VALUES (?, 'PENDING_REVIEW', ?, ?, '{\"bank_name\":\"Mock\",\"account_number\":\"FAKE\",\"account_holder\":\"Seller\"}'::jsonb) RETURNING seller_profile_id",
                    Long.class, fSellerId, ekycId, addrId);
            jdbcTemplate.update("INSERT INTO og_compat.seller_profile_decisions(seller_profile_id, revision_no, reviewed_by, outcome, profile_snapshot, command_key) VALUES (?, 1, ?, 'APPROVED', '{}'::jsonb, ?)", spId, ktvId, "CMD_SPD_" + UUID.randomUUID());
        }

        BigDecimal subtotal = new BigDecimal("250000.00");
        Long pid = jdbcTemplate.queryForObject("""
                INSERT INTO og_compat.products(seller_id, category_id, title, description, listed_price, condition, workflow_model, quantity_total, return_allowed, delivery_options)
                VALUES (?, ?, 'Item', 'Desc', ?, 'GOOD', 'UC83', 10, true, '["SELF_PICKUP","CARRIER"]'::jsonb) RETURNING product_id
                """, Long.class, fSellerId, categoryId, subtotal);
        jdbcTemplate.update("INSERT INTO og_compat.product_categories(product_id, category_id) VALUES (?, ?)", pid, categoryId);

        Long rid = jdbcTemplate.queryForObject("""
                INSERT INTO og_compat.product_revisions(product_id, revision_no, provenance, listed_unit_price, quantity, return_allowed, delivery_options, content_snapshot)
                VALUES (?, 1, 'UC83', ?, 10, true, '["SELF_PICKUP","CARRIER"]'::jsonb, '{}'::jsonb) RETURNING revision_id
                """, Long.class, pid, subtotal);
        jdbcTemplate.update("INSERT INTO og_compat.revision_media(revision_id, media_type, object_key, content_digest, file_size_bytes, duration_seconds, display_order) VALUES (?, 'IMAGE', 't/img', repeat('a', 64), 100, 10, 0)", rid);
        jdbcTemplate.update("INSERT INTO og_compat.revision_media(revision_id, media_type, object_key, content_digest, file_size_bytes, duration_seconds, display_order) VALUES (?, 'VIDEO', 't/vid', repeat('a', 64), 100, 10, 1)", rid);
        jdbcTemplate.update("UPDATE og_compat.product_revisions SET state = 'SUBMITTED', submitted_at = CURRENT_TIMESTAMP WHERE revision_id = ?", rid);
        jdbcTemplate.update("UPDATE og_compat.products SET status = 'PENDING', current_revision_id = ? WHERE product_id = ?", rid, pid);

        Long decId = jdbcTemplate.queryForObject(
                "INSERT INTO og_compat.product_moderation_decisions(product_id, revision_id, reviewer_id, decision, product_version, command_key) VALUES (?, ?, ?, 'APPROVED', 0, ?) RETURNING decision_id",
                Long.class, pid, rid, ktvId, "CMD_MD_" + UUID.randomUUID());
        Long assessId = jdbcTemplate.queryForObject("""
                INSERT INTO og_compat.listing_fee_assessments(product_id, revision_id, decision_id, policy_id, listed_total, computed_fee, cumulative_before, amount_due, command_key)
                SELECT ?, ?, ?, policy_id, 1, 0, 0, 0, ? FROM og_compat.listing_fee_policies WHERE effective_until IS NULL RETURNING assessment_id
                """, Long.class, pid, rid, decId, "CMD_LFA_" + UUID.randomUUID());
        BigDecimal due = jdbcTemplate.queryForObject("SELECT amount_due FROM og_compat.listing_fee_assessments WHERE assessment_id = ?", BigDecimal.class, assessId);
        if (due != null && due.compareTo(BigDecimal.ZERO) > 0) {
            UUID chargeId = UUID.randomUUID();
            jdbcTemplate.update("INSERT INTO og_compat.listing_fee_charges(charge_id, assessment_id, amount, command_key) VALUES (?, ?, ?, ?)", chargeId, assessId, due, "CMD_CHG_" + UUID.randomUUID());
            jdbcTemplate.update("INSERT INTO og_compat.listing_fee_receipts(receipt_id, charge_id, provider, provider_transaction_id, amount) VALUES (?, ?, 'BANK_TRANSFER_MOCK', ?, ?)", UUID.randomUUID(), chargeId, "TXN_LFR_" + UUID.randomUUID(), due);
        }
        jdbcTemplate.update("UPDATE og_compat.products SET status = 'ACTIVE', public_revision_id = ?, listed_price = ?, quantity_total = 10, return_allowed = true, delivery_options = '[\"SELF_PICKUP\",\"CARRIER\"]'::jsonb WHERE product_id = ?", rid, subtotal, pid);

        UUID gid = UUID.randomUUID();
        jdbcTemplate.update("""
                INSERT INTO og_compat.checkout_groups(group_id, buyer_id, command_key, request_digest, address_snapshot, delivery_method, payment_method, expected_total, payment_due_at)
                VALUES (?, ?, ?, repeat('a', 64), '{"recipient_name":"Buyer","phone_number":"0900000000","province":"P","district":"D","ward":"W","detail_address":"A"}'::jsonb, 'SELF_PICKUP', 'BANK_TRANSFER_MOCK', ?, CURRENT_TIMESTAMP + INTERVAL '1 hour')
                """, gid, fBuyerId, "CMD_CG_" + UUID.randomUUID(), subtotal);

        Long oid = jdbcTemplate.queryForObject("""
                INSERT INTO og_compat.orders(
                    checkout_group_id, group_id, buyer_id, seller_id, shipping_recipient_name, shipping_phone_number,
                    shipping_province, shipping_district, shipping_ward, shipping_detail_address,
                    subtotal, total_amount, buyer_system_fee, seller_system_fee, seller_proceeds,
                    payment_due_at, workflow_model, return_allowed, delivery_method, handover_day, voucher_discount_amount
                ) VALUES (
                    ?, ?, ?, ?, 'Buyer', '0900000000', 'P', 'D', 'W', 'A',
                    ?, ?, 0, 0, ?,
                    CURRENT_TIMESTAMP + INTERVAL '1 hour', 'UC83', true, 'SELF_PICKUP', CURRENT_DATE, 0
                ) RETURNING order_id
                """, Long.class, gid, gid, fBuyerId, fSellerId, subtotal, subtotal, subtotal);

        Long iid = jdbcTemplate.queryForObject("""
                INSERT INTO og_compat.order_items(
                    order_id, product_id, product_title, quantity, listed_price, agreed_price,
                    buyer_system_fee, seller_system_fee, buyer_line_total, seller_line_proceeds,
                    workflow_model, product_revision_id, return_allowed, voucher_allocation
                ) VALUES (
                    ?, ?, 'Item', 1, ?, ?, 0, 0, ?, ?, 'UC83', ?, true, 0
                ) RETURNING order_item_id
                """, Long.class, oid, pid, subtotal, subtotal, subtotal, subtotal, rid);

        UUID resId = UUID.randomUUID();
        jdbcTemplate.update("""
                INSERT INTO og_compat.inventory_reservations(reservation_id, order_item_id, product_id, quantity, expires_at, command_key)
                VALUES (?, ?, ?, 1, CURRENT_TIMESTAMP + INTERVAL '1 hour', ?)
                """, resId, iid, pid, "CMD_RES_" + UUID.randomUUID());

        UUID intentId = UUID.randomUUID();
        jdbcTemplate.update("""
                INSERT INTO og_compat.payment_intents(intent_id, purpose, group_id, expected_amount, payment_method, deadline, command_key)
                VALUES (?, 'CHECKOUT_GROUP', ?, ?, 'BANK_TRANSFER_MOCK', CURRENT_TIMESTAMP + INTERVAL '1 hour', ?)
                """, intentId, gid, subtotal, "CMD_PI_" + UUID.randomUUID());
        Long attId = jdbcTemplate.queryForObject("""
                INSERT INTO og_compat.payment_attempts(intent_id, txn_ref, amount, provider)
                VALUES (?, ?, ?, 'BANK_TRANSFER_MOCK') RETURNING attempt_id
                """, Long.class, intentId, "TXN_" + UUID.randomUUID(), subtotal);
        UUID confId = UUID.randomUUID();
        jdbcTemplate.update("""
                INSERT INTO og_compat.payment_confirmations(confirmation_id, intent_id, attempt_id, provider, provider_transaction_id, amount)
                VALUES (?, ?, ?, 'BANK_TRANSFER_MOCK', ?, ?)
                """, confId, intentId, attId, "TXN_P_" + UUID.randomUUID(), subtotal);
        UUID allocId = UUID.randomUUID();
        jdbcTemplate.update("""
                INSERT INTO og_compat.payment_allocations(allocation_id, intent_id, confirmation_id, order_id, amount)
                VALUES (?, ?, ?, ?, ?)
                """, allocId, intentId, confId, oid, subtotal);
        jdbcTemplate.update("""
                UPDATE og_compat.orders SET status = 'PAID_HELD',
                    paid_confirmed_at = (SELECT confirmed_at FROM og_compat.payment_confirmations WHERE confirmation_id = ?),
                    seller_accept_due_at = (SELECT confirmed_at + INTERVAL '2 days' FROM og_compat.payment_confirmations WHERE confirmation_id = ?)
                WHERE order_id = ?
                """, confId, confId, oid);
        jdbcTemplate.update("UPDATE og_compat.inventory_reservations SET state = 'FUNDED' WHERE reservation_id = ?", resId);
        jdbcTemplate.update("UPDATE og_compat.checkout_groups SET state = 'PAID' WHERE group_id = ?", gid);
        jdbcTemplate.update("""
                INSERT INTO og_compat.order_fund_components(component_id, order_id, allocation_id, component_type, confirmed_amount)
                VALUES (?, ?, ?, 'GOODS_CASH', ?)
                """, UUID.randomUUID(), oid, allocId, subtotal);

        jdbcTemplate.update("UPDATE og_compat.orders SET status = 'SELLER_CONFIRMED' WHERE order_id = ?", oid);
        jdbcTemplate.update("UPDATE og_compat.inventory_reservations SET state = 'CONSUMED' WHERE reservation_id = ?", resId);
        Long shipId = jdbcTemplate.queryForObject("""
                INSERT INTO og_compat.shipments(order_id, carrier, status, workflow_model, delivery_method, fee_snapshot, fee_payer)
                VALUES (?, 'SELF_PICKUP', 'PENDING', 'UC83', 'SELF_PICKUP', 0, 'BUYER') RETURNING shipment_id
                """, Long.class, oid);
        jdbcTemplate.update("""
                INSERT INTO og_compat.handover_confirmations(confirmation_id, shipment_id, party, user_id, outcome, command_key)
                VALUES (?, ?, 'BUYER', ?, 'SUCCESS', ?),
                       (?, ?, 'SELLER', ?, 'SUCCESS', ?)
                """, UUID.randomUUID(), shipId, fBuyerId, "CMD_HCB_" + UUID.randomUUID(),
                UUID.randomUUID(), shipId, fSellerId, "CMD_HCS_" + UUID.randomUUID());
        jdbcTemplate.queryForObject("SELECT og_confirm_direct_handover(?)", Boolean.class, shipId);
        jdbcTemplate.update("""
                UPDATE public.shipments
                SET valid_delivery_at = (SELECT valid_delivered_at FROM og_compat.orders WHERE order_id = ?),
                    status = 'DELIVERED',
                    delivered_at = (SELECT valid_delivered_at FROM og_compat.orders WHERE order_id = ?),
                    shipped_at = COALESCE(shipped_at, initiated_at)
                WHERE shipment_id = ?
                """, oid, oid, shipId);
        return oid;
    }

    private static byte[] minimalPngBytes() {
        return new byte[]{
                (byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A,
                0x00, 0x00, 0x00, 0x0D, 0x49, 0x48, 0x44, 0x52,
                0x00, 0x00, 0x00, 0x01, 0x00, 0x00, 0x00, 0x01,
                0x08, 0x06, 0x00, 0x00, 0x00, 0x1F, 0x15, (byte) 0xC4, (byte) 0x89,
                0x00, 0x00, 0x00, 0x00, 0x49, 0x45, 0x4E, 0x44,
                (byte) 0xAE, 0x42, 0x60, (byte) 0x82
        };
    }

    private static byte[] buildMp4Bytes(int timescale, long duration) {
        byte[] ftyp = new byte[16];
        ByteBuffer.wrap(ftyp).putInt(16).put("ftyp".getBytes(StandardCharsets.US_ASCII))
                .put("isom".getBytes(StandardCharsets.US_ASCII)).putInt(512);
        int mvhdLen = 108;
        byte[] mvhd = new byte[mvhdLen];
        ByteBuffer m = ByteBuffer.wrap(mvhd);
        m.putInt(mvhdLen).put("mvhd".getBytes(StandardCharsets.US_ASCII));
        m.put((byte) 0).put(new byte[3]);
        m.putInt(0).putInt(0);
        m.putInt(timescale).putInt((int) duration);
        byte[] moov = new byte[8 + mvhdLen];
        ByteBuffer mv = ByteBuffer.wrap(moov);
        mv.putInt(moov.length).put("moov".getBytes(StandardCharsets.US_ASCII)).put(mvhd);
        byte[] all = new byte[ftyp.length + moov.length];
        System.arraycopy(ftyp, 0, all, 0, ftyp.length);
        System.arraycopy(moov, 0, all, ftyp.length, moov.length);
        return all;
    }

    private static String sha256Hex(byte[] bytes) throws Exception {
        MessageDigest md = MessageDigest.getInstance("SHA-256");
        return HexFormat.of().formatHex(md.digest(bytes));
    }

    private static String repeatHex(char c) {
        return String.valueOf(c).repeat(64);
    }
}
