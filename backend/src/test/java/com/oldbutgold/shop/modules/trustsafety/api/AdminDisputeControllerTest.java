package com.oldbutgold.shop.modules.trustsafety.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.oldbutgold.shop.modules.trustsafety.application.ReturnCaseDtos;
import com.oldbutgold.shop.modules.trustsafety.application.ReturnCaseService;
import com.oldbutgold.shop.modules.trustsafety.application.TrustSafetyBusinessException;
import com.oldbutgold.shop.modules.trustsafety.infrastructure.persistence.CaseDecisionEntity;
import com.oldbutgold.shop.modules.trustsafety.infrastructure.persistence.CaseEntity;
import com.oldbutgold.shop.modules.trustsafety.infrastructure.persistence.CaseRoundEntity;
import com.oldbutgold.shop.modules.trustsafety.infrastructure.persistence.ReturnReasonCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ActiveProfiles("test")
@SpringBootTest
@AutoConfigureMockMvc
class AdminDisputeControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private ReturnCaseService returnCaseService;

    private static final Long ADMIN_ID = 99L;
    private static final Long KTV_ID = 300L;
    private static final Long BUYER_ID = 101L;
    private static final Long SELLER_ID = 202L;
    private static final Long CASE_ID = 505L;
    private static final Long ORDER_ID = 7001L;

    private ReturnCaseDtos.AdminEscalatedCaseSummaryResponse sampleEscalatedItem(Long caseId, Long ktvId) {
        Instant subTime = Instant.parse("2026-11-14T08:00:00Z");
        Instant escTime = Instant.parse("2026-11-16T08:00:00Z");
        return new ReturnCaseDtos.AdminEscalatedCaseSummaryResponse(
                caseId,
                "CASE-" + caseId,
                ORDER_ID,
                "ORD-" + ORDER_ID,
                BUYER_ID,
                SELLER_ID,
                ktvId,
                ktvId != null ? "KTV #" + ktvId : null,
                CaseEntity.STATE_POST_RETURN,
                2,
                CaseRoundEntity.STAGE_POST_RETURN_RESOLUTION,
                subTime,
                escTime,
                4.5,
                "KTV_SLA_EXPIRED_48H"
        );
    }

    private ReturnCaseDtos.ReturnCaseDetailResponse stubDetailResponse(Long caseId, String state) {
        return new ReturnCaseDtos.ReturnCaseDetailResponse(
                caseId,
                ORDER_ID,
                CaseEntity.TYPE_RETURN,
                state,
                ReturnReasonCode.DAMAGED_OR_DEFECTIVE,
                "Lỗi kỹ thuật",
                "Lỗi kỹ thuật chi tiết",
                BUYER_ID,
                Instant.now(),
                Instant.now(),
                Instant.now(),
                Instant.now(),
                Instant.now(),
                null,
                List.of(),
                List.of(),
                null,
                List.of()
        );
    }

    // =========================================================================
    // ENDPOINT A: GET /api/v1/admin/cases/escalated
    // =========================================================================
    @Nested
    @DisplayName("Endpoint A: GET /api/v1/admin/cases/escalated")
    class GetEscalatedCasesTests {

        @Test
        @DisplayName("1. Admin truy cập thành công -> 200 OK và trả về danh sách phân trang")
        void adminAccessSuccess_200() throws Exception {
            ReturnCaseDtos.AdminEscalatedCaseSummaryResponse item = sampleEscalatedItem(CASE_ID, KTV_ID);
            ReturnCaseDtos.AdminEscalatedCasePageResponse pageResp = new ReturnCaseDtos.AdminEscalatedCasePageResponse(
                    List.of(item), 0, 20, 1L, 1, true
            );
            when(returnCaseService.getEscalatedCases(null, 0, 20)).thenReturn(pageResp);

            mockMvc.perform(get("/api/v1/admin/cases/escalated")
                            .with(jwt().jwt(j -> j.subject(String.valueOf(ADMIN_ID)))
                                    .authorities(new SimpleGrantedAuthority("ROLE_ADMIN"))))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content[0].caseId").value(CASE_ID))
                    .andExpect(jsonPath("$.content[0].caseNumber").value("CASE-" + CASE_ID))
                    .andExpect(jsonPath("$.content[0].orderId").value(ORDER_ID))
                    .andExpect(jsonPath("$.content[0].orderCode").value("ORD-" + ORDER_ID))
                    .andExpect(jsonPath("$.content[0].buyerId").value(BUYER_ID))
                    .andExpect(jsonPath("$.content[0].sellerId").value(SELLER_ID))
                    .andExpect(jsonPath("$.content[0].assignedKtvId").value(KTV_ID))
                    .andExpect(jsonPath("$.content[0].caseState").value(CaseEntity.STATE_POST_RETURN))
                    .andExpect(jsonPath("$.content[0].roundNo").value(2))
                    .andExpect(jsonPath("$.content[0].roundStage").value(CaseRoundEntity.STAGE_POST_RETURN_RESOLUTION))
                    .andExpect(jsonPath("$.content[0].slaOverdueHours").value(4.5))
                    .andExpect(jsonPath("$.page").value(0))
                    .andExpect(jsonPath("$.size").value(20))
                    .andExpect(jsonPath("$.totalElements").value(1))
                    .andExpect(jsonPath("$.totalPages").value(1))
                    .andExpect(jsonPath("$.last").value(true));

            verify(returnCaseService).getEscalatedCases(null, 0, 20);
        }

        @Test
        @DisplayName("2. Unauthenticated (chưa đăng nhập) -> 401 Unauthorized")
        void unauthenticatedReturns401() throws Exception {
            mockMvc.perform(get("/api/v1/admin/cases/escalated"))
                    .andExpect(status().isUnauthorized());

            verify(returnCaseService, never()).getEscalatedCases(any(), any(Integer.class), any(Integer.class));
        }

        @Test
        @DisplayName("3. Buyer truy cập -> 403 Forbidden")
        void buyerAccessForbidden403() throws Exception {
            mockMvc.perform(get("/api/v1/admin/cases/escalated")
                            .with(jwt().jwt(j -> j.subject(String.valueOf(BUYER_ID)))
                                    .authorities(new SimpleGrantedAuthority("ROLE_BUYER"))))
                    .andExpect(status().isForbidden());

            verify(returnCaseService, never()).getEscalatedCases(any(), any(Integer.class), any(Integer.class));
        }

        @Test
        @DisplayName("4. Seller truy cập -> 403 Forbidden")
        void sellerAccessForbidden403() throws Exception {
            mockMvc.perform(get("/api/v1/admin/cases/escalated")
                            .with(jwt().jwt(j -> j.subject(String.valueOf(SELLER_ID)))
                                    .authorities(new SimpleGrantedAuthority("ROLE_SELLER"))))
                    .andExpect(status().isForbidden());

            verify(returnCaseService, never()).getEscalatedCases(any(), any(Integer.class), any(Integer.class));
        }

        @Test
        @DisplayName("5. KTV truy cập admin escalated queue -> 403 Forbidden")
        void ktvAccessForbidden403() throws Exception {
            mockMvc.perform(get("/api/v1/admin/cases/escalated")
                            .with(jwt().jwt(j -> j.subject(String.valueOf(KTV_ID)))
                                    .authorities(new SimpleGrantedAuthority("ROLE_KTV"))))
                    .andExpect(status().isForbidden());

            verify(returnCaseService, never()).getEscalatedCases(any(), any(Integer.class), any(Integer.class));
        }

        @Test
        @DisplayName("6. Pagination mặc định đúng (page=0, size=20)")
        void defaultPaginationParameters() throws Exception {
            ReturnCaseDtos.AdminEscalatedCasePageResponse emptyPage = new ReturnCaseDtos.AdminEscalatedCasePageResponse(
                    List.of(), 0, 20, 0L, 0, true
            );
            when(returnCaseService.getEscalatedCases(null, 0, 20)).thenReturn(emptyPage);

            mockMvc.perform(get("/api/v1/admin/cases/escalated")
                            .with(jwt().jwt(j -> j.subject(String.valueOf(ADMIN_ID)))
                                    .authorities(new SimpleGrantedAuthority("ROLE_ADMIN"))))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.page").value(0))
                    .andExpect(jsonPath("$.size").value(20));

            verify(returnCaseService).getEscalatedCases(null, 0, 20);
        }

        @Test
        @DisplayName("7. Size ngoài giới hạn bị từ chối -> 400 Bad Request (size=0, size=101, page=-1)")
        void outOfBoundsPaginationParametersRejected() throws Exception {
            // size < 1
            mockMvc.perform(get("/api/v1/admin/cases/escalated")
                            .param("size", "0")
                            .with(jwt().jwt(j -> j.subject(String.valueOf(ADMIN_ID)))
                                    .authorities(new SimpleGrantedAuthority("ROLE_ADMIN"))))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("INVALID_SIZE"));

            // size > 100
            mockMvc.perform(get("/api/v1/admin/cases/escalated")
                            .param("size", "101")
                            .with(jwt().jwt(j -> j.subject(String.valueOf(ADMIN_ID)))
                                    .authorities(new SimpleGrantedAuthority("ROLE_ADMIN"))))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("INVALID_SIZE"));

            // page < 0
            mockMvc.perform(get("/api/v1/admin/cases/escalated")
                            .param("page", "-1")
                            .with(jwt().jwt(j -> j.subject(String.valueOf(ADMIN_ID)))
                                    .authorities(new SimpleGrantedAuthority("ROLE_ADMIN"))))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("INVALID_PAGE"));

            verify(returnCaseService, never()).getEscalatedCases(any(), any(Integer.class), any(Integer.class));
        }

        @Test
        @DisplayName("8. Không có Case escalated trả về danh sách rỗng (200 OK, content: [])")
        void emptyEscalatedListReturns200WithEmptyContent() throws Exception {
            ReturnCaseDtos.AdminEscalatedCasePageResponse emptyPage = new ReturnCaseDtos.AdminEscalatedCasePageResponse(
                    List.of(), 0, 20, 0L, 0, true
            );
            when(returnCaseService.getEscalatedCases(null, 0, 20)).thenReturn(emptyPage);

            mockMvc.perform(get("/api/v1/admin/cases/escalated")
                            .with(jwt().jwt(j -> j.subject(String.valueOf(ADMIN_ID)))
                                    .authorities(new SimpleGrantedAuthority("ROLE_ADMIN"))))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content").isEmpty())
                    .andExpect(jsonPath("$.totalElements").value(0))
                    .andExpect(jsonPath("$.totalPages").value(0));
        }

        @Test
        @DisplayName("9. Lọc theo ktvId thành công và truyền đúng tham số xuống service")
        void filterByKtvIdSuccess() throws Exception {
            ReturnCaseDtos.AdminEscalatedCaseSummaryResponse item = sampleEscalatedItem(CASE_ID, KTV_ID);
            ReturnCaseDtos.AdminEscalatedCasePageResponse pageResp = new ReturnCaseDtos.AdminEscalatedCasePageResponse(
                    List.of(item), 1, 10, 1L, 1, true
            );
            when(returnCaseService.getEscalatedCases(eq(KTV_ID), eq(1), eq(10))).thenReturn(pageResp);

            mockMvc.perform(get("/api/v1/admin/cases/escalated")
                            .param("ktvId", String.valueOf(KTV_ID))
                            .param("page", "1")
                            .param("size", "10")
                            .with(jwt().jwt(j -> j.subject(String.valueOf(ADMIN_ID)))
                                    .authorities(new SimpleGrantedAuthority("ROLE_ADMIN"))))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content[0].assignedKtvId").value(KTV_ID))
                    .andExpect(jsonPath("$.page").value(1))
                    .andExpect(jsonPath("$.size").value(10));

            verify(returnCaseService).getEscalatedCases(eq(KTV_ID), eq(1), eq(10));
        }
    }

    // =========================================================================
    // ENDPOINT B: POST /api/v1/admin/cases/{caseId}/decide-round2
    // =========================================================================
    @Nested
    @DisplayName("Endpoint B: POST /api/v1/admin/cases/{caseId}/decide-round2")
    class AdminDecideRound2Tests {

        private ReturnCaseDtos.KtvRound2DecisionRequest validRequest() {
            return new ReturnCaseDtos.KtvRound2DecisionRequest(
                    CaseDecisionEntity.OUTCOME_FULL_GOODS_REFUND,
                    "Admin phán quyết chấp nhận hoàn tiền toàn phần sau khi KTV quá hạn SLA",
                    "CMD:ADMIN:R2:" + CASE_ID
            );
        }

        @Test
        @DisplayName("1. Admin hợp lệ có thể gửi quyết định -> 200 OK")
        void adminValidDecisionSuccess_200() throws Exception {
            ReturnCaseDtos.KtvRound2DecisionRequest request = validRequest();
            ReturnCaseDtos.ReturnCaseDetailResponse detail = stubDetailResponse(CASE_ID, CaseEntity.STATE_CLOSED);

            when(returnCaseService.adminDecideRound2(eq(ADMIN_ID), eq(CASE_ID), any()))
                    .thenReturn(detail);

            mockMvc.perform(post("/api/v1/admin/cases/{caseId}/decide-round2", CASE_ID)
                            .with(jwt().jwt(j -> j.subject(String.valueOf(ADMIN_ID)))
                                    .authorities(new SimpleGrantedAuthority("ROLE_ADMIN")))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.caseId").value(CASE_ID))
                    .andExpect(jsonPath("$.state").value(CaseEntity.STATE_CLOSED));

            verify(returnCaseService).adminDecideRound2(eq(ADMIN_ID), eq(CASE_ID), any());
        }

        @Test
        @DisplayName("2. Unauthenticated bị từ chối -> 401 Unauthorized")
        void unauthenticatedDecisionReturns401() throws Exception {
            ReturnCaseDtos.KtvRound2DecisionRequest request = validRequest();

            mockMvc.perform(post("/api/v1/admin/cases/{caseId}/decide-round2", CASE_ID)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isUnauthorized());

            verify(returnCaseService, never()).adminDecideRound2(any(), any(), any());
        }

        @Test
        @DisplayName("3. Non-Admin (Buyer/Seller) bị từ chối -> 403 Forbidden")
        void nonAdminDecisionForbidden403() throws Exception {
            ReturnCaseDtos.KtvRound2DecisionRequest request = validRequest();

            mockMvc.perform(post("/api/v1/admin/cases/{caseId}/decide-round2", CASE_ID)
                            .with(jwt().jwt(j -> j.subject(String.valueOf(BUYER_ID)))
                                    .authorities(new SimpleGrantedAuthority("ROLE_BUYER")))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isForbidden());

            verify(returnCaseService, never()).adminDecideRound2(any(), any(), any());
        }

        @Test
        @DisplayName("4. KTV không thể sử dụng Admin decision endpoint -> 403 Forbidden")
        void ktvCannotUseAdminDecisionEndpointForbidden403() throws Exception {
            ReturnCaseDtos.KtvRound2DecisionRequest request = validRequest();

            mockMvc.perform(post("/api/v1/admin/cases/{caseId}/decide-round2", CASE_ID)
                            .with(jwt().jwt(j -> j.subject(String.valueOf(KTV_ID)))
                                    .authorities(new SimpleGrantedAuthority("ROLE_KTV")))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isForbidden());

            verify(returnCaseService, never()).adminDecideRound2(any(), any(), any());
        }

        @Test
        @DisplayName("5. Case chưa escalation không được xử lý qua Admin escalation path -> 400 CASE_NOT_ESCALATED")
        void nonEscalatedCaseRejected400() throws Exception {
            ReturnCaseDtos.KtvRound2DecisionRequest request = validRequest();

            when(returnCaseService.adminDecideRound2(eq(ADMIN_ID), eq(CASE_ID), any()))
                    .thenThrow(new TrustSafetyBusinessException(HttpStatus.BAD_REQUEST, "CASE_NOT_ESCALATED",
                            "Hồ sơ chưa được chuyển lên Quản trị viên (Admin) do chưa quá hạn SLA hoặc chưa được leo thang."));

            mockMvc.perform(post("/api/v1/admin/cases/{caseId}/decide-round2", CASE_ID)
                            .with(jwt().jwt(j -> j.subject(String.valueOf(ADMIN_ID)))
                                    .authorities(new SimpleGrantedAuthority("ROLE_ADMIN")))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("CASE_NOT_ESCALATED"));
        }

        @Test
        @DisplayName("6. Case đã có final decision không được quyết định lần nữa -> 400 ILLEGAL_STATE")
        void alreadyDecidedCaseRejected400() throws Exception {
            ReturnCaseDtos.KtvRound2DecisionRequest request = validRequest();

            when(returnCaseService.adminDecideRound2(eq(ADMIN_ID), eq(CASE_ID), any()))
                    .thenThrow(new IllegalStateException("Vòng 2 đã kết thúc, không thể đưa ra quyết định (Trạng thái hiện tại: DECIDED)"));

            mockMvc.perform(post("/api/v1/admin/cases/{caseId}/decide-round2", CASE_ID)
                            .with(jwt().jwt(j -> j.subject(String.valueOf(ADMIN_ID)))
                                    .authorities(new SimpleGrantedAuthority("ROLE_ADMIN")))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("ILLEGAL_STATE"));
        }

        @Test
        @DisplayName("7. Không thể giả mạo Admin ID (ID lấy trực tiếp từ JWT principal subject)")
        void adminIdExtractedStrictlyFromJwtPrincipal() throws Exception {
            ReturnCaseDtos.KtvRound2DecisionRequest request = validRequest();
            ReturnCaseDtos.ReturnCaseDetailResponse detail = stubDetailResponse(CASE_ID, CaseEntity.STATE_CLOSED);

            when(returnCaseService.adminDecideRound2(eq(ADMIN_ID), eq(CASE_ID), any()))
                    .thenReturn(detail);

            // JWT subject là ADMIN_ID = 99L
            mockMvc.perform(post("/api/v1/admin/cases/{caseId}/decide-round2", CASE_ID)
                            .with(jwt().jwt(j -> j.subject(String.valueOf(ADMIN_ID)))
                                    .authorities(new SimpleGrantedAuthority("ROLE_ADMIN")))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk());

            // Xác minh service nhận chính xác ADMIN_ID từ principal, không nhận fake ID nào khác
            verify(returnCaseService).adminDecideRound2(eq(ADMIN_ID), eq(CASE_ID), any());
            verify(returnCaseService, never()).adminDecideRound2(eq(KTV_ID), any(), any());
            verify(returnCaseService, never()).adminDecideRound2(eq(BUYER_ID), any(), any());
        }

        @Test
        @DisplayName("8. Business validation: Thiếu outcome hoặc reason trong request body trả 400 BAD_REQUEST")
        void invalidRequestBodyFailsValidation() throws Exception {
            // Thiếu outcome và reason quá ngắn (< 5 ký tự)
            String invalidJson = """
                    {
                        "outcome": null,
                        "reason": "abc",
                        "commandKey": "CMD:123"
                    }
                    """;

            mockMvc.perform(post("/api/v1/admin/cases/{caseId}/decide-round2", CASE_ID)
                            .with(jwt().jwt(j -> j.subject(String.valueOf(ADMIN_ID)))
                                    .authorities(new SimpleGrantedAuthority("ROLE_ADMIN")))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(invalidJson))
                    .andExpect(status().isBadRequest());

            verify(returnCaseService, never()).adminDecideRound2(any(), any(), any());
        }
    }
}
