package com.oldbutgold.shop.modules.fulfillment;

import com.oldbutgold.shop.modules.fulfillment.infrastructure.client.GhnApiException;
import com.oldbutgold.shop.modules.fulfillment.infrastructure.client.GhnClient;
import com.oldbutgold.shop.modules.fulfillment.infrastructure.client.GhnProperties;
import com.oldbutgold.shop.modules.fulfillment.infrastructure.client.dto.GhnMasterDataDtos;
import com.oldbutgold.shop.modules.fulfillment.infrastructure.client.dto.GhnShippingDtos.AvailableServiceDto;
import com.oldbutgold.shop.modules.fulfillment.infrastructure.client.dto.GhnShippingDtos.FeeQueryRequest;
import com.oldbutgold.shop.modules.fulfillment.infrastructure.client.dto.GhnShippingDtos.FeeResponseDto;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@ActiveProfiles("test")
@SpringBootTest
@Tag("integration")
@EnabledIf("hasGhnCredentials")
class GhnClientIntegrationTest {

    static {
        // Tự động nạp .env vào System Properties trước khi Spring Boot context khởi động
        Path[] possiblePaths = new Path[]{
                Paths.get(".env"),
                Paths.get("../.env"),
                Paths.get("../../.env")
        };
        for (Path path : possiblePaths) {
            if (Files.exists(path) && !Files.isDirectory(path)) {
                try {
                    List<String> lines = Files.readAllLines(path);
                    for (String line : lines) {
                        line = line.trim();
                        if (line.isEmpty() || line.startsWith("#")) continue;
                        int eqIdx = line.indexOf('=');
                        if (eqIdx > 0) {
                            String key = line.substring(0, eqIdx).trim();
                            String value = line.substring(eqIdx + 1).trim();
                            if ((value.startsWith("\"") && value.endsWith("\"")) ||
                                (value.startsWith("'") && value.endsWith("'"))) {
                                value = value.substring(1, value.length() - 1);
                            }
                            if (System.getProperty(key) == null && System.getenv(key) == null) {
                                System.setProperty(key, value);
                            }
                        }
                    }
                    break;
                } catch (IOException ignored) {}
            }
        }
    }

    static boolean hasGhnCredentials() {
        return (System.getProperty("GHN_API_TOKEN") != null && !System.getProperty("GHN_API_TOKEN").isBlank())
                || (System.getenv("GHN_API_TOKEN") != null && !System.getenv("GHN_API_TOKEN").isBlank());
    }

    @Autowired
    private GhnClient ghnClient;

    @Autowired
    private GhnProperties ghnProperties;

    @Test
    void shouldGetProvincesFromGhn() {
        List<GhnMasterDataDtos.ProvinceDto> provinces = ghnClient.getProvinces();

        assertNotNull(provinces, "Danh sách tỉnh/thành không được null");
        assertFalse(provinces.isEmpty(), "Danh sách tỉnh/thành không được rỗng");

        System.out.println("==================================================");
        System.out.println("✅ [1/3] TEST TỈNH/THÀNH PHỐ THÀNH CÔNG!");
        System.out.println("Tổng số tỉnh/thành phố nhận được: " + provinces.size());
        System.out.println("Tỉnh đầu tiên: ID = " + provinces.get(0).provinceId() 
                + ", Tên = " + provinces.get(0).provinceName()
                + ", Mã = " + provinces.get(0).code());
        System.out.println("==================================================");
    }

    @Test
    void shouldGetDistrictsFromGhn() {
        int provinceIdHcm = 202; // TP. Hồ Chí Minh
        List<GhnMasterDataDtos.DistrictDto> districts = ghnClient.getDistricts(provinceIdHcm);

        assertNotNull(districts, "Danh sách quận/huyện không được null");
        assertFalse(districts.isEmpty(), "Danh sách quận/huyện không được rỗng");

        System.out.println("==================================================");
        System.out.println("✅ [2/3] TEST QUẬN/HUYỆN THÀNH CÔNG!");
        System.out.println("Số lượng quận/huyện của TP.HCM: " + districts.size());
        System.out.println("Quận/Huyện đầu tiên: ID = " + districts.get(0).districtId()
                + ", Tên = " + districts.get(0).districtName()
                + ", Mã = " + districts.get(0).code());
        System.out.println("==================================================");
    }

    @Test
    void shouldGetWardsFromGhn() {
        int districtIdQ1 = 1442; // Quận 1, TP.HCM
        List<GhnMasterDataDtos.WardDto> wards = ghnClient.getWards(districtIdQ1);

        assertNotNull(wards, "Danh sách phường/xã không được null");
        assertFalse(wards.isEmpty(), "Danh sách phường/xã không được rỗng");

        System.out.println("==================================================");
        System.out.println("✅ [3/3] TEST PHƯỜNG/XÃ THÀNH CÔNG!");
        System.out.println("Số lượng phường của Quận 1: " + wards.size());
        System.out.println("Phường đầu tiên: Mã phường = " + wards.get(0).wardCode()
                + ", Tên = " + wards.get(0).wardName());
        System.out.println("==================================================");
    }

    @Test
    void shouldGetAvailableServicesAndCalculateFee() {
        System.out.println("==================================================");
        System.out.println("🔍 [KIỂM TRA CẤU HÌNH SHOP ID]");
        System.out.println(" - System.getProperty(\"GHN_SHOP_ID\"): " + System.getProperty("GHN_SHOP_ID"));
        System.out.println(" - GhnProperties.shopId(): " + ghnProperties.shopId());
        System.out.println(" - GhnClient.getShopId(): " + ghnClient.getShopId());
        System.out.println(" - HTTP Header \"ShopId\" gửi đi: " + ghnClient.getShopId());
        System.out.println(" - Body \"shop_id\" gửi trong available-services: " + ghnClient.getShopId());
        System.out.println("==================================================");

        // ====================================================================
        // BƯỚC 0: Tra cứu Master-data lấy tên chính xác từ API GHN
        // ====================================================================
        List<GhnMasterDataDtos.ProvinceDto> provinces = ghnClient.getProvinces();
        var hcmProvince = provinces.stream()
                .filter(p -> p.provinceName().contains("Hồ Chí Minh") || "202".equals(String.valueOf(p.provinceId())))
                .findFirst().orElseThrow();
        var daNangProvince = provinces.stream()
                .filter(p -> p.provinceName().contains("Đà Nẵng") || p.provinceName().contains("Da Nang"))
                .findFirst().orElseThrow();

        List<GhnMasterDataDtos.DistrictDto> hcmDistricts = ghnClient.getDistricts(hcmProvince.provinceId());
        var district1442 = hcmDistricts.stream().filter(d -> Integer.valueOf(1442).equals(d.districtId())).findFirst().orElseThrow();
        var district1444 = hcmDistricts.stream().filter(d -> Integer.valueOf(1444).equals(d.districtId())).findFirst().orElseThrow();

        List<GhnMasterDataDtos.DistrictDto> daNangDistricts = ghnClient.getDistricts(daNangProvince.provinceId());
        System.out.println("🏛️ [MASTER-DATA QUẬN CỦA ĐÀ NẴNG TRẢ VỀ TỪ API GHN]:");
        for (var d : daNangDistricts) {
            System.out.println("  * District: ID = " + d.districtId() + ", Name = \"" + d.districtName() + "\", Code = \"" + d.code() + "\"");
        }

        // Chọn quận Đà Nẵng: ưu tiên Hải Châu (1526 hoặc tìm theo tên)
        var daNangDistrict = daNangDistricts.stream()
                .filter(d -> d.districtName().contains("Hải Châu") || Integer.valueOf(1526).equals(d.districtId()))
                .findFirst().orElse(daNangDistricts.get(0));

        var daNangWards = ghnClient.getWards(daNangDistrict.districtId());
        var daNangWard = daNangWards.get(0);

        System.out.println("🏛️ [MASTER-DATA TÊN QUẬN/HUYỆN TRẢ VỀ TỪ API GHN]:");
        System.out.println(" - District 1442: \"" + district1442.districtName() + "\" (ID = " + district1442.districtId() + ", Tỉnh: " + hcmProvince.provinceName() + ")");
        System.out.println(" - District 1444: \"" + district1444.districtName() + "\" (ID = " + district1444.districtId() + ", Tỉnh: " + hcmProvince.provinceName() + ")");
        System.out.println(" - District Đà Nẵng: \"" + daNangDistrict.districtName() + "\" (ID = " + daNangDistrict.districtId() + ", Tỉnh: " + daNangProvince.provinceName() + ")");
        System.out.println(" - Ward Đà Nẵng: \"" + daNangWard.wardName() + "\" (Code = \"" + daNangWard.wardCode() + "\")");
        System.out.println("==================================================");

        // ====================================================================
        // BƯỚC 1: Lấy danh sách dịch vụ khả dụng (Available Services) Q1 -> Bình Thạnh
        // ====================================================================
        List<AvailableServiceDto> services = ghnClient.getAvailableServices(district1442.districtId(), district1444.districtId());
        assertNotNull(services, "Danh sách dịch vụ không được null");
        assertFalse(services.isEmpty(), "Phải có ít nhất 1 dịch vụ khả dụng");

        System.out.println("📦 [1/4] KẾT QUẢ GỌI AVAILABLE-SERVICES SANDBOX THÀNH CÔNG (1442 -> 1444):");
        System.out.println("Endpoint: POST /v2/shipping-order/available-services");
        System.out.println("Tham số tuyến: from_district = " + district1442.districtId() + " (" + district1442.districtName() + ") -> to_district = " + district1444.districtId() + " (" + district1444.districtName() + ")");
        System.out.println("Tổng số dịch vụ khả dụng trả về: " + services.size());
        for (AvailableServiceDto s : services) {
            System.out.println(" - service_id: " + s.serviceId()
                    + " | service_type_id: " + s.serviceTypeId()
                    + " | short_name: \"" + s.shortName() + "\"");
        }

        AvailableServiceDto matchedService = services.stream()
                .filter(s -> Integer.valueOf(2).equals(s.serviceTypeId()))
                .findFirst()
                .orElse(null);

        assertNotNull(matchedService, "Không tìm thấy dịch vụ phù hợp cho hàng nhẹ (service_type_id = 2) từ kết quả available-services!");
        System.out.println("-> Đã chọn dịch vụ phù hợp cho hàng nhẹ: service_type_id = " + matchedService.serviceTypeId()
                + " (service_id tham chiếu = " + matchedService.serviceId() + ", short_name = \"" + matchedService.shortName() + "\")");

        // ====================================================================
        // BƯỚC 2: Tính cước thực tế với địa chỉ Seller tại TP.HCM (1442 -> 1444)
        // ====================================================================
        FeeQueryRequest sellerFeeReq = new FeeQueryRequest(
                district1442.districtId(),
                "20101",
                district1444.districtId(),
                "20311",
                null, // TUYỆT ĐỐI không truyền service_id, chỉ dùng service_type_id theo hợp đồng
                matchedService.serviceTypeId(),
                10,
                15,
                10,
                1000,
                0
        );
        FeeResponseDto sellerFee = ghnClient.calculateShippingFee(sellerFeeReq);
        assertNotNull(sellerFee, "Response phí vận chuyển với địa chỉ seller không được null");
        assertNotNull(sellerFee.total(), "Tổng cước phí không được null");

        System.out.println("--------------------------------------------------");
        System.out.println("💰 [2/4] KẾT QUẢ TÍNH CƯỚC VỚI ĐỊA CHỈ SELLER TP.HCM (" + district1442.districtName() + " -> " + district1444.districtName() + "):");
        System.out.println("Endpoint: POST /v2/shipping-order/fee");
        System.out.println("Payload: from_district=" + district1442.districtId() + ", from_ward=\"20101\", to_district=" + district1444.districtId() + ", to_ward=\"20311\", service_type_id=" + matchedService.serviceTypeId() + ", service_id=null, weight=1000g, 15x10x10 cm");
        System.out.println("Tổng cước thực tế từ GHN: " + sellerFee.total() + " VNĐ (service_fee: " + sellerFee.serviceFee() + ", insurance_fee: " + sellerFee.insuranceFee() + ")");

        // ====================================================================
        // BƯỚC 3: Đo kịch bản còn thiếu: from_* ở Đà Nẵng -> 1444 (20311)
        // ====================================================================
        System.out.println("--------------------------------------------------");
        System.out.println("💰 [3/4] KẾT QUẢ TÍNH CƯỚC SELLER TẠI ĐÀ NẴNG -> TP.HCM (" + daNangDistrict.districtName() + " -> " + district1444.districtName() + "):");
        List<AvailableServiceDto> servicesDaNang = ghnClient.getAvailableServices(daNangDistrict.districtId(), district1444.districtId());
        AvailableServiceDto matchedServiceDaNang = servicesDaNang.stream()
                .filter(s -> Integer.valueOf(2).equals(s.serviceTypeId()))
                .findFirst().orElseThrow();

        FeeQueryRequest daNangFeeReq = new FeeQueryRequest(
                daNangDistrict.districtId(),
                daNangWard.wardCode(),
                district1444.districtId(),
                "20311",
                null,
                matchedServiceDaNang.serviceTypeId(),
                10,
                15,
                10,
                1000,
                0
        );
        FeeResponseDto daNangFee = ghnClient.calculateShippingFee(daNangFeeReq);
        assertNotNull(daNangFee);
        assertNotNull(daNangFee.total());
        System.out.println("Endpoint: POST /v2/shipping-order/fee");
        System.out.println("Payload: from_district=" + daNangDistrict.districtId() + " (" + daNangDistrict.districtName() + "), from_ward=\"" + daNangWard.wardCode() + "\" (" + daNangWard.wardName() + "), to_district=" + district1444.districtId() + " (" + district1444.districtName() + "), to_ward=\"20311\", service_type_id=" + matchedServiceDaNang.serviceTypeId() + ", weight=1000g, 15x10x10 cm");
        System.out.println("Tổng cước thực tế từ GHN: " + daNangFee.total() + " VNĐ (service_fee: " + daNangFee.serviceFee() + ", insurance_fee: " + daNangFee.insuranceFee() + ")");

        // ====================================================================
        // BƯỚC 4: Đối chiếu tính cước dùng kho mặc định của shop 229762 (BỎ from_*)
        // ====================================================================
        System.out.println("--------------------------------------------------");
        System.out.println("🏬 [4/4] ĐỐI CHIẾU TÍNH CƯỚC DÙNG KHO MẶC ĐỊNH CỦA SHOP 229762 (BỎ from_*):");
        System.out.println("Endpoint: POST /v2/shipping-order/fee");
        System.out.println("Payload: from_district_id=null, from_ward_code=null, to_district=" + district1444.districtId() + ", to_ward=\"20311\", service_type_id=2, weight=1000g");
        FeeQueryRequest defaultWarehouseReq = new FeeQueryRequest(
                null,
                null,
                district1444.districtId(),
                "20311",
                null,
                matchedService.serviceTypeId(),
                10,
                15,
                10,
                1000,
                0
        );
        FeeResponseDto defaultFee = ghnClient.calculateShippingFee(defaultWarehouseReq);
        System.out.println("✅ Phản hồi từ GHN thành công!");
        System.out.println("Tổng cước kho mặc định shop 229762: " + defaultFee.total() + " VNĐ (service_fee: " + defaultFee.serviceFee() + ", insurance_fee: " + defaultFee.insuranceFee() + ")");
        System.out.println("==================================================");
    }

    @Test
    void shouldGetOrderDetailAndGeneratePrintToken() {
        String testOrderCode = "L8RL38";

        // 1. Order detail
        var detail = ghnClient.getOrderDetail(testOrderCode);
        assertNotNull(detail, "Chi tiết đơn hàng không được null");
        org.junit.jupiter.api.Assertions.assertEquals(testOrderCode, detail.orderCode());
        org.junit.jupiter.api.Assertions.assertEquals("CHOTHUHANG", detail.requiredNote());

        System.out.println("==================================================");
        System.out.println("✅ [6/6] TEST TRA CỨU ĐƠN & TOKEN IN THÀNH CÔNG!");
        System.out.println("Đơn: " + detail.orderCode() + " | Trạng thái: " + detail.status()
                + " | Ghi chú: " + detail.requiredNote()
                + " | Người gửi: " + detail.fromName());

        // 2. Generate print token
        var printTokenRes = ghnClient.generatePrintToken(List.of(testOrderCode));
        assertNotNull(printTokenRes, "Response token in không được null");
        assertNotNull(printTokenRes.token(), "Token in không được null");
        assertFalse(printTokenRes.token().isBlank(), "Token in không được rỗng");

        System.out.println("Sinh Print Token thành công: " + printTokenRes.token().substring(0, 8) + "...");
        System.out.println("==================================================");
    }
}
