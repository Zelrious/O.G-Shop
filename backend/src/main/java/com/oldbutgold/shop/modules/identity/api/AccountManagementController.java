package com.oldbutgold.shop.modules.identity.api;

import com.oldbutgold.shop.modules.identity.application.AccountManagementService;
import com.oldbutgold.shop.modules.trustsafety.application.AccountTrustFacade;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import java.util.*;

@RestController
@RequestMapping("/api/v1")
public class AccountManagementController {
    private final AccountManagementService service; private final AccountTrustFacade trust;
    public AccountManagementController(AccountManagementService service,AccountTrustFacade trust) { this.service=service;this.trust=trust; }
    @GetMapping("/ktv/users") public AccountManagementDtos.Page list(@AuthenticationPrincipal Jwt jwt,@RequestParam(defaultValue="") String query,
        @RequestParam(defaultValue="ALL") String status,@RequestParam(defaultValue="ALL") String role,@RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="20") int size) {
        return service.list(id(jwt),query,status,role,page,size);
    }
    @GetMapping("/ktv/users/{userId}") public AccountManagementDtos.Detail detail(@AuthenticationPrincipal Jwt jwt,@PathVariable long userId) { return service.detail(id(jwt),userId); }
    @PostMapping("/ktv/users/{userId}/commands") public AccountManagementDtos.CommandResult command(@AuthenticationPrincipal Jwt jwt,@PathVariable long userId,@Valid @RequestBody AccountManagementDtos.Command body) { return service.command(id(jwt),userId,body); }
    @GetMapping("/ktv/users/{userId}/history") public AccountManagementDtos.HistoryPage history(@AuthenticationPrincipal Jwt jwt,@PathVariable long userId,@RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="20") int size) { return service.history(id(jwt),userId,page,size,false); }
    @GetMapping("/account") public AccountManagementDtos.Detail self(@AuthenticationPrincipal Jwt jwt) { return service.self(id(jwt)); }
    @GetMapping("/account/history") public AccountManagementDtos.HistoryPage selfHistory(@AuthenticationPrincipal Jwt jwt,@RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="20") int size) { return service.history(id(jwt),id(jwt),page,size,true); }
    @GetMapping("/account/cases/{caseId}/responses") public List<AccountTrustFacade.Response> responses(@AuthenticationPrincipal Jwt jwt,@PathVariable long caseId) { service.self(id(jwt));return trust.responses(id(jwt),caseId); }
    public record PartyResponse(@NotBlank @Size(max=2000) String text,@NotBlank @Size(max=100) String commandKey) {}
    @PostMapping("/account/cases/{caseId}/responses") public Map<String,UUID> respond(@AuthenticationPrincipal Jwt jwt,@PathVariable long caseId,@Valid @RequestBody PartyResponse body) { service.self(id(jwt));return Map.of("id",trust.respond(id(jwt),caseId,body.text().strip(),body.commandKey())); }
    private long id(Jwt jwt) { return Long.parseLong(jwt.getSubject()); }
}
