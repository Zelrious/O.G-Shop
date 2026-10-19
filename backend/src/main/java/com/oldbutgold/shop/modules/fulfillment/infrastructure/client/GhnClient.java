package com.oldbutgold.shop.modules.fulfillment.infrastructure.client;

import com.oldbutgold.shop.modules.fulfillment.infrastructure.client.dto.GhnApiResponse;
import com.oldbutgold.shop.modules.fulfillment.infrastructure.client.dto.GhnMasterDataDtos;
import com.oldbutgold.shop.modules.fulfillment.infrastructure.client.dto.GhnShippingDtos.AvailableServiceDto;
import com.oldbutgold.shop.modules.fulfillment.infrastructure.client.dto.GhnShippingDtos.AvailableServiceQueryRequest;
import com.oldbutgold.shop.modules.fulfillment.infrastructure.client.dto.GhnShippingDtos.CreateOrderResponseDto;
import com.oldbutgold.shop.modules.fulfillment.infrastructure.client.dto.GhnShippingDtos.CreateShippingOrderRequest;
import com.oldbutgold.shop.modules.fulfillment.infrastructure.client.dto.GhnShippingDtos.FeeQueryRequest;
import com.oldbutgold.shop.modules.fulfillment.infrastructure.client.dto.GhnShippingDtos.FeeResponseDto;
import com.oldbutgold.shop.modules.fulfillment.infrastructure.client.dto.GhnShippingDtos.OrderDetailQueryRequest;
import com.oldbutgold.shop.modules.fulfillment.infrastructure.client.dto.GhnShippingDtos.OrderDetailResponseDto;
import com.oldbutgold.shop.modules.fulfillment.infrastructure.client.dto.GhnShippingDtos.PrintTokenQueryRequest;
import com.oldbutgold.shop.modules.fulfillment.infrastructure.client.dto.GhnShippingDtos.PrintTokenResponseDto;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

@Component
public class GhnClient {
    private final RestClient restClient;
    private final int shopId;

    @Autowired
    public GhnClient(GhnProperties properties) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(properties.connectTimeout());
        factory.setReadTimeout(properties.readTimeout());

        this.shopId = properties.shopId();
        this.restClient = RestClient.builder()
                .baseUrl(properties.baseUrl())
                .requestFactory(factory)
                .defaultHeader("Token", properties.apiToken())
                .defaultHeader("ShopId", String.valueOf(properties.shopId()))
                .build();
    }

    // Constructor for testing with custom/mock RestClient
    public GhnClient(RestClient restClient, int shopId) {
        this.restClient = restClient;
        this.shopId = shopId;
    }

    public int getShopId() {
        return shopId;
    }

    // ========================================================================
    // Master Data APIs
    // ========================================================================

    public List<GhnMasterDataDtos.ProvinceDto> getProvinces() {
        return get("/master-data/province", new ParameterizedTypeReference<GhnApiResponse<List<GhnMasterDataDtos.ProvinceDto>>>() {});
    }

    public List<GhnMasterDataDtos.DistrictDto> getDistricts(int provinceId) {
        return post("/master-data/district", Map.of("province_id", provinceId), new ParameterizedTypeReference<GhnApiResponse<List<GhnMasterDataDtos.DistrictDto>>>() {});
    }

    public List<GhnMasterDataDtos.WardDto> getWards(int districtId) {
        return post("/master-data/ward", Map.of("district_id", districtId), new ParameterizedTypeReference<GhnApiResponse<List<GhnMasterDataDtos.WardDto>>>() {});
    }

    // ========================================================================
    // Shipping Services & Fee Calculation
    // ========================================================================

    public List<AvailableServiceDto> getAvailableServices(int fromDistrictId, int toDistrictId) {
        AvailableServiceQueryRequest request = new AvailableServiceQueryRequest(this.shopId, fromDistrictId, toDistrictId);
        return post("/v2/shipping-order/available-services", request, new ParameterizedTypeReference<GhnApiResponse<List<AvailableServiceDto>>>() {});
    }

    public FeeResponseDto calculateShippingFee(FeeQueryRequest request) {
        return post("/v2/shipping-order/fee", request, new ParameterizedTypeReference<GhnApiResponse<FeeResponseDto>>() {});
    }

    // ========================================================================
    // Order Lifecycle: Create, Detail & Print Token
    // ========================================================================

    public CreateOrderResponseDto createShippingOrder(CreateShippingOrderRequest request) {
        return post("/v2/shipping-order/create", request, new ParameterizedTypeReference<GhnApiResponse<CreateOrderResponseDto>>() {});
    }

    public OrderDetailResponseDto getOrderDetail(String orderCode) {
        OrderDetailQueryRequest request = new OrderDetailQueryRequest(orderCode);
        return post("/v2/shipping-order/detail", request, new ParameterizedTypeReference<GhnApiResponse<OrderDetailResponseDto>>() {});
    }

    public OrderDetailResponseDto getOrderDetailByClientCode(String clientOrderCode) {
        return get("/v2/shipping-order/detail-by-client-code?client_order_code=" + clientOrderCode,
                new ParameterizedTypeReference<GhnApiResponse<OrderDetailResponseDto>>() {});
    }

    public PrintTokenResponseDto generatePrintToken(List<String> orderCodes) {
        PrintTokenQueryRequest request = new PrintTokenQueryRequest(orderCodes);
        return post("/v2/a5/gen-token", request, new ParameterizedTypeReference<GhnApiResponse<PrintTokenResponseDto>>() {});
    }

    // ========================================================================
    // Internal HTTP Helpers
    // ========================================================================

    private <T> T post(String uri, Object body, ParameterizedTypeReference<GhnApiResponse<T>> typeRef) {
        try {
            GhnApiResponse<T> response = restClient.post()
                    .uri(uri)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .onStatus(HttpStatusCode::isError, (req, resp) -> {
                        String errorBody = new String(resp.getBody().readAllBytes(), StandardCharsets.UTF_8);
                        throw new GhnApiException("GHN API error (" + resp.getStatusCode() + "): " + errorBody);
                    })
                    .body(typeRef);
            return unwrap(response);
        } catch (ResourceAccessException ex) {
            throw new GhnApiException("Lỗi kết nối/timeout khi gọi GHN API: " + uri, ex);
        } catch (RestClientException ex) {
            throw new GhnApiException("Lỗi xử lý phản hồi từ GHN API (invalid JSON / parse error): " + uri, ex);
        }
    }

    private <T> T get(String uri, ParameterizedTypeReference<GhnApiResponse<T>> typeRef) {
        try {
            GhnApiResponse<T> response = restClient.get()
                    .uri(uri)
                    .retrieve()
                    .onStatus(HttpStatusCode::isError, (req, resp) -> {
                        String errorBody = new String(resp.getBody().readAllBytes(), StandardCharsets.UTF_8);
                        throw new GhnApiException("GHN API error (" + resp.getStatusCode() + "): " + errorBody);
                    })
                    .body(typeRef);
            return unwrap(response);
        } catch (ResourceAccessException ex) {
            throw new GhnApiException("Lỗi kết nối/timeout khi gọi GHN API: " + uri, ex);
        } catch (RestClientException ex) {
            throw new GhnApiException("Lỗi xử lý phản hồi từ GHN API (invalid JSON / parse error): " + uri, ex);
        }
    }

    private <T> T unwrap(GhnApiResponse<T> response) {
        if (response == null) {
            throw new GhnApiException("GHN API phản hồi rỗng (empty body)");
        }
        if (!response.isSuccess()) {
            String errorDetail = response.message();
            if (errorDetail == null || errorDetail.isBlank()) {
                errorDetail = response.codeMessage();
            }
            if (errorDetail == null || errorDetail.isBlank()) {
                errorDetail = response.codeMessageValue();
            }
            if (errorDetail == null || errorDetail.isBlank()) {
                errorDetail = "Lỗi nghiệp vụ không xác định";
            }
            throw new GhnApiException("GHN API error (code " + response.code() + "): " + errorDetail);
        }
        if (response.data() == null) {
            throw new GhnApiException("GHN API phản hồi thành công (code 200) nhưng không có dữ liệu (data is null)");
        }
        return response.data();
    }
}