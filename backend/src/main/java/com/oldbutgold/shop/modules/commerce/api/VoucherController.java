package com.oldbutgold.shop.modules.commerce.api;
import com.oldbutgold.shop.modules.commerce.application.VoucherService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import java.util.*;

@RestController @RequestMapping("/api/v1")
public class VoucherController {
    private final VoucherService service;
    public VoucherController(VoucherService service){this.service=service;}
    @GetMapping("/admin/vouchers") public List<VoucherDtos.Campaign> admin(@AuthenticationPrincipal Jwt jwt){return service.campaigns(id(jwt),"ADMIN");}
    @PostMapping("/admin/vouchers") public VoucherDtos.Campaign create(@AuthenticationPrincipal Jwt jwt,@Valid @RequestBody VoucherDtos.Save body){return service.save(id(jwt),null,body);}
    @PutMapping("/admin/vouchers/{voucherId}") public VoucherDtos.Campaign update(@AuthenticationPrincipal Jwt jwt,@PathVariable long voucherId,@Valid @RequestBody VoucherDtos.Save body){return service.save(id(jwt),voucherId,body);}
    @GetMapping("/ktv/vouchers") public List<VoucherDtos.Campaign> ktv(@AuthenticationPrincipal Jwt jwt){return service.campaigns(id(jwt),"KTV");}
    @GetMapping("/ktv/vouchers/{voucherId}/targets") public VoucherDtos.Targets targets(@AuthenticationPrincipal Jwt jwt,@PathVariable long voucherId,@RequestParam(defaultValue="GRANT") String action,@RequestParam(defaultValue="") String query,@RequestParam(defaultValue="ALL") String role,@RequestParam(defaultValue="ACTIVE") String status,@RequestParam(defaultValue="true") boolean eligibleOnly,@RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="20") int size){return service.targets(id(jwt),voucherId,action,query,role,status,eligibleOnly,page,size);}
    @PostMapping("/ktv/vouchers/{voucherId}/preview") public VoucherDtos.Batch preview(@AuthenticationPrincipal Jwt jwt,@PathVariable long voucherId,@Valid @RequestBody VoucherDtos.Bulk body){return service.bulk(id(jwt),voucherId,body,true);}
    @PostMapping("/ktv/vouchers/{voucherId}/commands") public VoucherDtos.Batch bulk(@AuthenticationPrincipal Jwt jwt,@PathVariable long voucherId,@Valid @RequestBody VoucherDtos.Bulk body){return service.bulk(id(jwt),voucherId,body,false);}
    @GetMapping("/commerce/vouchers") public VoucherDtos.Wallet wallet(@AuthenticationPrincipal Jwt jwt){return service.wallet(id(jwt));}
    @PostMapping("/commerce/vouchers/{voucherId}/claim") public VoucherDtos.Grant claim(@AuthenticationPrincipal Jwt jwt,@PathVariable long voucherId,@Valid @RequestBody VoucherDtos.Claim body){return service.claim(id(jwt),voucherId,body);}
    private long id(Jwt jwt){return Long.parseLong(jwt.getSubject());}
}
