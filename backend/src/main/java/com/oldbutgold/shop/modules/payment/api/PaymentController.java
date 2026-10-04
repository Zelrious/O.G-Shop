package com.oldbutgold.shop.modules.payment.api;

import com.oldbutgold.shop.modules.payment.application.VnPayService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.Map;


@RestController 
@RequestMapping("/api/v1/payments")
public class PaymentController {

    private final VnPayService vnPayService;

    public PaymentController(VnPayService vnPayService) {
        this.vnPayService = vnPayService;
    }

    @PostMapping("/vnpay-url")
    public PaymentDtos.PaymentUrlResponse createPaymentUrlResponse(
        @Valid @RequestBody PaymentDtos.CreateVnPaymentRequest request,
        HttpServletRequest servletRequest
    ) {
        String clientIp = servletRequest.getRemoteAddr();
        
        String paymentUrl = vnPayService.createPaymentUrl(
            request.orderRef(),
            request.amountVnd(),
            request.orderInfo(),
            clientIp
        );

        return new PaymentDtos.PaymentUrlResponse(paymentUrl);
    }

    @GetMapping("/vnpay-ipn")
    public PaymentDtos.VnPayIpnResponse handleVnPayIpn(@RequestParam Map<String, String> allParams) {
        boolean isValidChecksum = vnPayService.verifyCallback(allParams);
        if(!isValidChecksum)
        {
            return PaymentDtos.VnPayIpnResponse.invalidChecksum();
        }

        String responseCode = allParams.get("vnp_ResponseCode");
        if("00".equals(responseCode)) {
            return PaymentDtos.VnPayIpnResponse.success();
        }

        return PaymentDtos.VnPayIpnResponse.orderNotFound();
    }
}
