package com.oldbutgold.shop.modules.identity.api;

import jakarta.validation.constraints.*;
import com.oldbutgold.shop.modules.platform.application.GovernanceHistoryFacade;
import com.oldbutgold.shop.modules.commerce.application.AccountObligationsFacade;
import com.oldbutgold.shop.modules.trustsafety.application.AccountTrustFacade;
import java.time.Instant;
import java.util.*;

public final class AccountManagementDtos {
    private AccountManagementDtos() {}
    public enum Scope { NEW_LISTINGS, CHAT, BUY_EKYC, BUY_ALL, NEW_TRANSACTIONS }
    public enum Action { RESTRICT, LIFT, LOCK, ACTIVATE, DEACTIVATE, COMPENSATE }
    public record Command(@NotNull Action action,@NotNull @Min(0) Long expectedVersion,
                          @NotBlank @Size(max=100) String commandKey,@NotBlank @Size(max=500) String reason,
                          Scope scope,UUID restrictionId,Instant expiresAt,Long systemCaseId,
                          Instant affectedFrom,Instant affectedUntil,Integer durationWeeks,Long targetCaseId) {}
    public record Restriction(UUID id,String scope,long imposedBy,String reason,Instant openedAt,
                              Instant expiresAt,Instant closedAt,boolean legacy) {
        public boolean active(Instant now) { return closedAt==null && (expiresAt==null || expiresAt.isAfter(now)); }
    }
    public record Customer(long userId,String fullName,String email,String phoneNumber,List<String> roles,
                           String status,String authenticationStatus,long version,int reputation,int activeRestrictions,Instant createdAt) {}
    public record Page(List<Customer> items,int page,int size,long totalElements) {}
    public record Detail(Customer user,String reason,Instant expiresAt,List<Restriction> restrictions,
                         List<AccountObligationsFacade.OrderObligation> orders,List<AccountTrustFacade.CaseSummary> cases,
                         AccountTrustFacade.Reputation reputation,List<GovernanceHistoryFacade.Entry> history,long historyTotal,
                         List<Scope> allowedScopes,List<Action> allowedActions,List<Integer> allowedWeeks,
                         List<AccountTrustFacade.Outage> outages,Instant serverTime) {}
    public record CommandResult(long userId,long version,String action,String message) {}
    public record HistoryPage(List<GovernanceHistoryFacade.Entry> items,int page,int size,long totalElements) {}
}
