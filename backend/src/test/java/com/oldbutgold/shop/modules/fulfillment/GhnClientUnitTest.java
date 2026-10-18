package com.oldbutgold.shop.modules.fulfillment;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.oldbutgold.shop.modules.fulfillment.infrastructure.client.GhnApiException;
import com.oldbutgold.shop.modules.fulfillment.infrastructure.client.GhnClient;
import com.oldbutgold.shop.modules.fulfillment.infrastructure.client.dto.GhnShippingDtos.AvailableServiceDto;
import com.oldbutgold.shop.modules.fulfillment.infrastructure.client.dto.GhnShippingDtos.CreateOrderResponseDto;
import com.oldbutgold.shop.modules.fulfillment.infrastructure.client.dto.GhnShippingDtos.CreateShippingOrderRequest;
import com.oldbutgold.shop.modules.fulfillment.infrastructure.client.dto.GhnShippingDtos.FeeQueryRequest;
import com.oldbutgold.shop.modules.fulfillment.infrastructure.client.dto.GhnShippingDtos.FeeResponseDto;
import com.oldbutgold.shop.modules.fulfillment.infrastructure.client.dto.GhnShippingDtos.OrderItemDto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

import java.io.IOException;
import java.net.URI;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

class GhnClientUnitTest {

    private static final String BASE_URL = "https://dev-online-gateway.ghn.vn/shiip/public-api/";
    private static final int TEST_SHOP_ID = 229762;

    private MockRestServiceServer mockServer;
    private GhnClient ghnClient;
    private RestClient.Builder restClientBuilder;

    @BeforeEach
    void setUp() {
        restClientBuilder = RestClient.builder().baseUrl(BASE_URL);
        mockServer = MockRestServiceServer.bindTo(restClientBuilder).build();
        ghnClient = new GhnClient(restClientBuilder.build(), TEST_SHOP_ID);
    }

    @Test
    @DisplayName("1. HTTP Error (400 Bad Request): Ném GhnApiException kèm mã và body lỗi, không kèm token")
    void shouldThrowGhnApiExceptionOnHttpError() {
        mockServer.expect(requestTo(BASE_URL + "master-data/district"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withStatus(HttpStatus.BAD_REQUEST)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("{\"code\":400,\"message\":\"Sai thông tin tham số\"}"));

        GhnApiException ex = assertThrows(GhnApiException.class, () -> ghnClient.getDistricts(99999));

        assertTrue(ex.getMessage().contains("400"));
        assertTrue(ex.getMessage().contains("Sai thông tin tham số"));
        assertFalse(ex.getMessage().contains("Token"));
        mockServer.verify();
    }

    @Test
    @DisplayName("2. Lỗi nghiệp vụ trong body (HTTP 200 nhưng code != 200): Ném GhnApiException kèm mã code và message")
    void shouldThrowGhnApiExceptionOnBusinessErrorCodeInBody() {
        mockServer.expect(requestTo(BASE_URL + "v2/shipping-order/detail"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess("{\"code\":401,\"message\":\"Token không hợp lệ hoặc hết hạn\",\"data\":null}", MediaType.APPLICATION_JSON));

        GhnApiException ex = assertThrows(GhnApiException.class, () -> ghnClient.getOrderDetail("INVALID_CODE"));

        assertTrue(ex.getMessage().contains("401"));
        assertTrue(ex.getMessage().contains("Token không hợp lệ hoặc hết hạn"));
        mockServer.verify();
    }

    @Test
    @DisplayName("3. HTTP 200 nhưng body rỗng (empty body): Ném GhnApiException phản hồi rỗng")
    void shouldThrowGhnApiExceptionOnEmptyBody() {
        mockServer.expect(requestTo(BASE_URL + "master-data/province"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("", MediaType.APPLICATION_JSON));

        GhnApiException ex = assertThrows(GhnApiException.class, () -> ghnClient.getProvinces());

        assertTrue(ex.getMessage().contains("phản hồi rỗng"));
        mockServer.verify();
    }

    @Test
    @DisplayName("4. HTTP 200 nhưng data = null: Ném GhnApiException dữ liệu null, không phát sinh NPE")
    void shouldThrowGhnApiExceptionWhenDataIsNull() {
        mockServer.expect(requestTo(BASE_URL + "v2/shipping-order/fee"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess("{\"code\":200,\"message\":\"Success\",\"data\":null}", MediaType.APPLICATION_JSON));

        FeeQueryRequest request = new FeeQueryRequest(1442, "20101", 1444, "20311", 1000);

        GhnApiException ex = assertThrows(GhnApiException.class, () -> ghnClient.calculateShippingFee(request));

        assertTrue(ex.getMessage().contains("data is null"));
        mockServer.verify();
    }

    @Test
    @DisplayName("5. HTTP 200 với data = [] (danh sách rỗng): Trả về danh sách rỗng an toàn, không phát sinh NPE")
    void shouldHandleEmptyDataListSafelyWithoutNpe() {
        mockServer.expect(requestTo(BASE_URL + "v2/shipping-order/available-services"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess("{\"code\":200,\"message\":\"Success\",\"data\":[]}", MediaType.APPLICATION_JSON));

        List<AvailableServiceDto> services = ghnClient.getAvailableServices(1442, 1444);

        assertNotNull(services, "Danh sách không được null khi data là []");
        assertTrue(services.isEmpty(), "Danh sách phải rỗng");
        assertEquals(0, services.size(), "Kích thước danh sách phải bằng 0");
        mockServer.verify();
    }

    @Test
    @DisplayName("6. JSON không hợp lệ (HTML/Malformed text): Ném GhnApiException invalid JSON, không văng lỗi unhandled")
    void shouldThrowGhnApiExceptionOnInvalidJson() {
        mockServer.expect(requestTo(BASE_URL + "master-data/province"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("<html><body>502 Bad Gateway</body></html>", MediaType.TEXT_HTML));

        GhnApiException ex = assertThrows(GhnApiException.class, () -> ghnClient.getProvinces());

        assertTrue(ex.getMessage().contains("invalid JSON / parse error"));
        mockServer.verify();
    }

    @Test
    @DisplayName("7. Timeout/Mạng (ResourceAccessException): Ném GhnApiException bọc lỗi kết nối/timeout")
    void shouldThrowGhnApiExceptionOnTimeoutOrNetworkError() {
        // Tạo RestClient giả lập ném ResourceAccessException (ví dụ ReadTimeout)
        RestClient customClient = RestClient.builder()
                .requestFactory((URI uri, HttpMethod method) -> {
                    throw new ResourceAccessException("I/O error on POST request: Read timed out");
                })
                .baseUrl(BASE_URL)
                .build();
        GhnClient timeoutGhnClient = new GhnClient(customClient, TEST_SHOP_ID);

        GhnApiException ex = assertThrows(GhnApiException.class, () -> timeoutGhnClient.getProvinces());

        assertTrue(ex.getMessage().contains("Lỗi kết nối/timeout"));
        assertInstanceOf(ResourceAccessException.class, ex.getCause());
    }

    @Test
    @DisplayName("8. Chứng minh FeeQueryRequest truyền service_type_id = 2 và bỏ qua service_id null khi serialize")
    void shouldSendServiceTypeIdAndOmitNullServiceId() {
        mockServer.expect(requestTo(BASE_URL + "v2/shipping-order/fee"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(jsonPath("$.service_type_id").value(2))
                .andExpect(jsonPath("$.service_id").doesNotExist())
                .andExpect(jsonPath("$.from_district_id").value(1442))
                .andExpect(jsonPath("$.to_district_id").value(1444))
                .andRespond(withSuccess("{\"code\":200,\"message\":\"Success\",\"data\":{\"total\":20900,\"service_fee\":20900,\"insurance_fee\":0}}", MediaType.APPLICATION_JSON));

        FeeQueryRequest request = new FeeQueryRequest(1442, "20101", 1444, "20311", 1000);
        FeeResponseDto response = ghnClient.calculateShippingFee(request);

        assertNotNull(response);
        assertEquals(20900, response.total());
        mockServer.verify();
    }

    @Test
    @DisplayName("9. Serialization CreateShippingOrderRequest qua MockServer: có service_type_id, client_order_code, required_note CHOTHUHANG, from_* dạng tên, to_district_id + to_ward_code, không chứa trường null")
    void shouldSerializeCreateShippingOrderRequestCorrectly() {
        mockServer.expect(requestTo(BASE_URL + "v2/shipping-order/create"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(jsonPath("$.service_type_id").value(2))
                .andExpect(jsonPath("$.client_order_code").value("OG-ORDER-12345"))
                .andExpect(jsonPath("$.required_note").value("CHOTHUHANG"))
                .andExpect(jsonPath("$.from_name").value("Minh Camera"))
                .andExpect(jsonPath("$.from_phone").value("0914405241"))
                .andExpect(jsonPath("$.from_address").value("123 Le Loi"))
                .andExpect(jsonPath("$.from_ward_name").value("Phuong Ben Nghe"))
                .andExpect(jsonPath("$.from_district_name").value("Quan 1"))
                .andExpect(jsonPath("$.from_province_name").value("Ho Chi Minh"))
                .andExpect(jsonPath("$.return_phone").value("0914405241"))
                .andExpect(jsonPath("$.return_address").value("123 Le Loi"))
                .andExpect(jsonPath("$.to_name").value("Huynh Long Bao Khanh"))
                .andExpect(jsonPath("$.to_phone").value("0908123456"))
                .andExpect(jsonPath("$.to_address").value("Landmark 81"))
                .andExpect(jsonPath("$.to_district_id").value(1444))
                .andExpect(jsonPath("$.to_ward_code").value("20311"))
                .andExpect(jsonPath("$.cod_amount").value(0))
                .andExpect(jsonPath("$.payment_type_id").value(1))
                .andExpect(jsonPath("$.weight").value(1000))
                .andExpect(jsonPath("$.service_id").doesNotExist())
                .andExpect(jsonPath("$.note").doesNotExist())
                .andRespond(withSuccess("{\"code\":200,\"message\":\"Success\",\"data\":{\"order_code\":\"L8RL38\",\"total_fee\":20900}}", MediaType.APPLICATION_JSON));

        CreateShippingOrderRequest request = new CreateShippingOrderRequest(
                CreateShippingOrderRequest.DEFAULT_PAYMENT_TYPE_SHOP_PAYS,
                null,
                CreateShippingOrderRequest.REQUIRED_NOTE_CHO_THU_HANG,
                "OG-ORDER-12345",
                "Minh Camera",
                "0914405241",
                "123 Le Loi",
                "Phuong Ben Nghe",
                "Quan 1",
                "Ho Chi Minh",
                "0914405241",
                "123 Le Loi",
                "Huynh Long Bao Khanh",
                "0908123456",
                "Landmark 81",
                "20311",
                1444,
                1000,
                15,
                10,
                10,
                CreateShippingOrderRequest.DEFAULT_SERVICE_TYPE_ID_STANDARD,
                null,
                CreateShippingOrderRequest.DEFAULT_COD_AMOUNT_ZERO,
                List.of(new OrderItemDto("Sony A7 III", "CAM-01", 1, 25000000, 15, 10, 10, 1000))
        );

        CreateOrderResponseDto response = ghnClient.createShippingOrder(request);

        assertNotNull(response);
        assertEquals("L8RL38", response.orderCode());
        assertEquals(20900, response.totalFee());
        mockServer.verify();
    }

    @Test
    @DisplayName("10. Jackson ObjectMapper test: JSON của CreateShippingOrderRequest tuyệt đối không chứa key null")
    void shouldNotContainAnyNullFieldInSerializedCreateShippingOrderRequestJson() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        CreateShippingOrderRequest request = new CreateShippingOrderRequest(
                1, null, "CHOTHUHANG", "OG-ORDER-12345",
                "Minh Camera", "0914405241", "123 Le Loi", "Phuong Ben Nghe", "Quan 1", "Ho Chi Minh",
                "0914405241", "123 Le Loi",
                "Huynh Long Bao Khanh", "0908123456", "Landmark 81", "20311", 1444,
                1000, 15, 10, 10, 2, null, 0,
                List.of(new OrderItemDto("Item", "IT01", 1, 10000, null, null, null, 1000))
        );

        String json = mapper.writeValueAsString(request);
        assertFalse(json.contains(":null"), "JSON không được chứa trường null");
        assertTrue(json.contains("\"service_type_id\":2"), "Phải có service_type_id = 2");
        assertTrue(json.contains("\"client_order_code\":\"OG-ORDER-12345\""), "Phải có client_order_code");
        assertTrue(json.contains("\"required_note\":\"CHOTHUHANG\""), "Phải có required_note = CHOTHUHANG");
        assertTrue(json.contains("\"from_ward_name\":\"Phuong Ben Nghe\""), "Phải giữ from_* dạng tên");
        assertTrue(json.contains("\"to_ward_code\":\"20311\""), "Phải giữ to_ward_code");
        assertTrue(json.contains("\"to_district_id\":1444"), "Phải giữ to_district_id");
        assertNotNull(request.clientOrderCode());
        assertTrue(request.clientOrderCode().length() <= CreateShippingOrderRequest.MAX_CLIENT_ORDER_CODE_LENGTH,
                "client_order_code phải có độ dài <= 50 ký tự theo đặc tả GHN");
        assertFalse(json.contains("\"service_id\""), "Trường service_id null phải được loại bỏ");
        assertFalse(json.contains("\"note\""), "Trường note null phải được loại bỏ");
    }

    @Test
    @DisplayName("11. client_order_code sinh ra phải đảm bảo độ dài <= 50 ký tự theo giới hạn của GHN")
    void shouldEnsureClientOrderCodeDoesNotExceedMaxLimit() {
        String sampleClientOrderCode = "OG-" + System.currentTimeMillis() + "-ATTEMPT-1";
        assertTrue(sampleClientOrderCode.length() <= CreateShippingOrderRequest.MAX_CLIENT_ORDER_CODE_LENGTH,
                "Độ dài mã client_order_code sinh ra (" + sampleClientOrderCode.length() + ") vượt quá giới hạn 50 ký tự của GHN");
        assertEquals(50, CreateShippingOrderRequest.MAX_CLIENT_ORDER_CODE_LENGTH);
    }
}
