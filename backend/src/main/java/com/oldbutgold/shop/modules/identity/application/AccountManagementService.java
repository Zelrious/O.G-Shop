package com.oldbutgold.shop.modules.identity.application;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.oldbutgold.shop.modules.identity.api.AccountManagementDtos.*;
import com.oldbutgold.shop.modules.identity.infrastructure.persistence.AccountManagementRepository;
import com.oldbutgold.shop.modules.identity.infrastructure.persistence.AccountManagementRepository.State;
import com.oldbutgold.shop.modules.commerce.application.AccountObligationsFacade;
import com.oldbutgold.shop.modules.trustsafety.application.AccountTrustFacade;
import com.oldbutgold.shop.modules.platform.application.GovernanceHistoryFacade;
import com.oldbutgold.shop.modules.platform.application.PlatformNotificationFacade;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.security.access.AccessDeniedException;
import java.time.*;
import java.util.*;

@Service
public class AccountManagementService {
    private final AccountManagementRepository repository; private final AccountObligationsFacade obligations;
    private final AccountTrustFacade trust; private final GovernanceHistoryFacade history;
    private final PlatformNotificationFacade notices; private final ObjectMapper json; private final Clock clock;
    public AccountManagementService(AccountManagementRepository repository,AccountObligationsFacade obligations,
        AccountTrustFacade trust,GovernanceHistoryFacade history,PlatformNotificationFacade notices,ObjectMapper json,Clock clock) {
        this.repository=repository;this.obligations=obligations;this.trust=trust;this.history=history;this.notices=notices;this.json=json;this.clock=clock;
    }
    @Transactional(readOnly=true) public Page list(long actor,String query,String status,String role,int page,int size) {
        ktv(actor);String q=query==null?"":query.strip();
        if(q.length()>100) throw problem("ACCOUNT_FILTER_INVALID","Từ khóa tối đa 100 ký tự.",400);
        String s=status==null?"ALL":status.toUpperCase(Locale.ROOT),r=role==null?"ALL":role.toUpperCase(Locale.ROOT);
        if(!Set.of("ALL","ACTIVE","LOCKED","SUSPENDED","DEACTIVATED").contains(s) || !Set.of("ALL","BUYER","SELLER").contains(r))
            throw problem("ACCOUNT_FILTER_INVALID","Bộ lọc tài khoản không hợp lệ.",400);
        int p=Math.max(0,page),z=Math.min(50,Math.max(1,size));
        var items=repository.search(q,s,r,p,z).stream().map(this::customer).toList();
        return new Page(items,p,z,repository.count(q,s,r));
    }
    @Transactional(readOnly=true) public Detail detail(long actor,long target) { ktv(actor);return detail(target); }
    @Transactional(readOnly=true) public Detail self(long target) { return detail(target); }
    @Transactional(readOnly=true) public HistoryPage history(long actor,long target,int page,int size,boolean self) {
        if(!self) ktv(actor); else if(actor!=target) throw new AccessDeniedException("Chỉ xem lịch sử của mình.");
        state(target,false);int p=Math.max(0,page),s=Math.min(50,Math.max(1,size));return new HistoryPage(history.history(target,p,s),p,s,history.count(target));
    }
    private Detail detail(long target) {
        State s=state(target,false);var all=restrictions(s);
        List<Scope> scopes=new ArrayList<>(List.of(Scope.CHAT,Scope.BUY_EKYC,Scope.BUY_ALL,Scope.NEW_TRANSACTIONS));
        if(s.roles().contains("SELLER")) scopes.addFirst(Scope.NEW_LISTINGS);
        var cases=trust.cases(target);var outages=trust.outages(target);
        List<Action> actions=new ArrayList<>(List.of(Action.RESTRICT,Action.LOCK,Action.DEACTIVATE));
        if(!s.state().equals("ACTIVE") || !s.authenticationStatus().equals("ACTIVE")) actions.add(Action.ACTIVATE);
        if(all.stream().anyMatch(r->r.active(clock.instant()))) actions.add(Action.LIFT);
        if(!outages.isEmpty() && cases.stream().anyMatch(AccountTrustFacade.CaseSummary::canCompensate)) actions.add(Action.COMPENSATE);
        int maxWeeks=(int)(Duration.between(clock.instant(),clock.instant().atZone(ZoneOffset.UTC).plusMonths(3).toInstant()).toDays()/7);
        return new Detail(customer(s),s.reason(),s.until(),all,obligations.orders(target),cases,trust.reputation(target),history.history(target,0,20),history.count(target),scopes,actions,
            java.util.stream.IntStream.rangeClosed(1,maxWeeks).boxed().toList(),outages,clock.instant());
    }
    private Customer customer(State s) {
        Instant now=clock.instant();String effective=(s.until()!=null&&!s.until().isAfter(now))?"ACTIVE":s.state();
        if("ACTIVE".equals(effective)) effective=s.authenticationStatus();
        return new Customer(s.id(),s.name(),s.email(),s.phone(),s.roles(),effective,s.authenticationStatus(),s.version(),trust.reputation(s.id()).score(),
            (int)restrictions(s).stream().filter(r->r.active(now)).count(),s.createdAt());
    }
    private List<Restriction> restrictions(State s) { var all=new ArrayList<>(s.restrictions());all.addAll(repository.legacy(s.id()));return all; }
    @Transactional public CommandResult command(long actor,long target,Command command) {
        ktv(actor);repository.lockCommand(command.commandKey());
        Map<String,Object> request=commandData(command);request.remove("commandKey");
        var prior=history.command(command.commandKey());
        if(prior.isPresent()) {
            var p=prior.get();
            if(p.actorId()!=actor || p.targetId()!=target || !p.request().equals(request)) throw problem("COMMAND_KEY_CONFLICT","Mã thao tác đã dùng cho quyết định khác.",409);
            return result(target,((Number)p.after().get("version")).longValue(),p.action());
        }
        State old=state(target,true);
        if(old.version()!=command.expectedVersion()) throw problem("ACCOUNT_VERSION_CONFLICT","Tài khoản đã thay đổi. Tải lại trước khi quyết định.",409);
        Instant now=clock.instant().truncatedTo(java.time.temporal.ChronoUnit.MICROS),until=old.until();String state=old.state(),reason=old.reason();
        List<Restriction> restrictions=new ArrayList<>(old.restrictions());
        String decisionReason=command.reason().strip();
        if(decisionReason.isEmpty()) throw problem("ACCOUNT_REASON_REQUIRED","Cần nêu lý do quyết định.",400);
        switch(command.action()) {
            case RESTRICT -> {
                Scope scope=command.scope();
                if(scope==null || (scope==Scope.NEW_LISTINGS&&!old.roles().contains("SELLER"))) throw problem("ACCOUNT_SCOPE_INVALID","Phạm vi hạn chế không phù hợp tài khoản.",400);
                Instant end=command.expiresAt()==null?null:command.expiresAt().truncatedTo(java.time.temporal.ChronoUnit.MICROS);
                if(command.durationWeeks()!=null) {
                    if(command.durationWeeks()<1 || command.durationWeeks()>13) throw problem("ACCOUNT_DEADLINE_INVALID","Thời hạn tuần không hợp lệ.",400);
                    end=now.plus(Duration.ofDays(7L*command.durationWeeks()));
                }
                validateDeadline(end,scope,now);
                if(restrictions(old).stream().anyMatch(r->r.scope().equals(scope.name())&&r.active(now))) throw problem("ACCOUNT_STATE_CONFLICT","Đã có hạn chế đang áp dụng cho phạm vi này.",409);
                restrictions.add(new Restriction(UUID.randomUUID(),scope.name(),actor,decisionReason,now,end,null,false));
            }
            case LIFT -> {
                Restriction r=restrictions(old).stream().filter(v->v.id().equals(command.restrictionId())&&v.active(now)).findFirst()
                    .orElseThrow(()->problem("ACCOUNT_RESTRICTION_NOT_FOUND","Không có hạn chế đang áp dụng với mã này.",404));
                if(r.legacy()) repository.liftLegacy(target,r.id(),now);
                else for(int i=0;i<restrictions.size();i++) if(restrictions.get(i).id().equals(r.id())) restrictions.set(i,new Restriction(r.id(),r.scope(),r.imposedBy(),r.reason(),r.openedAt(),r.expiresAt(),now,false));
            }
            case LOCK,DEACTIVATE -> {
                if(command.expiresAt()!=null && (!command.expiresAt().isAfter(now) || command.expiresAt().isAfter(now.atZone(ZoneOffset.UTC).plusMonths(3).toInstant())))
                    throw problem("ACCOUNT_DEADLINE_INVALID","Thời hạn phải ở tương lai và tối đa 3 tháng.",400);
                if(command.action()==Action.DEACTIVATE && command.expiresAt()!=null) throw problem("ACCOUNT_DEADLINE_INVALID","Ngừng mềm không có thời hạn; dùng khóa tạm thời nếu cần.",400);
                state=command.action()==Action.LOCK?"LOCKED":"DEACTIVATED";until=command.expiresAt()==null?null:command.expiresAt().truncatedTo(java.time.temporal.ChronoUnit.MICROS);reason=decisionReason;
            }
            case ACTIVATE -> {
                if("ACTIVE".equals(state) && "ACTIVE".equals(old.authenticationStatus())) throw problem("ACCOUNT_STATE_CONFLICT","Tài khoản đã hoạt động. Gỡ hạn chế cụ thể nếu cần.",409);
                state="ACTIVE";until=null;reason=null;
                for(var r:repository.legacy(target)) if(r.scope().equals("LOGIN")&&r.active(now)) repository.liftLegacy(target,r.id(),now);
            }
            case COMPENSATE -> {
                if(command.systemCaseId()==null || command.affectedFrom()==null || command.affectedUntil()==null
                    || !command.affectedUntil().isAfter(command.affectedFrom()) || command.affectedUntil().isAfter(now)
                    || !trust.confirmedSystemIssue(target,command.systemCaseId())
                    || !trust.confirmedOutage(command.systemCaseId(),command.affectedFrom(),command.affectedUntil()))
                    throw problem("ACCOUNT_COMPENSATION_UNCONFIRMED","Cần hồ sơ lỗi hệ thống đã xác nhận đúng khoảng thời gian bị ảnh hưởng.",400);
                if(command.targetCaseId()==null) throw problem("ACCOUNT_COMPENSATION_UNCONFIRMED","Cần chọn hồ sơ đơn cũ cần bù thời gian phản hồi.",400);
                String rid=command.targetCaseId().toString();
                if(history.hasCompensation(target,command.systemCaseId(),rid)) throw problem("ACCOUNT_COMPENSATION_DUPLICATE","Khoảng gián đoạn của hồ sơ đã được bù cho biện pháp này.",409);
                trust.compensateDeadline(actor,target,command.targetCaseId(),command.systemCaseId(),command.affectedFrom(),command.affectedUntil(),decisionReason,command.commandKey());
            }
        }
        var before=repository.snapshot(old.version(),old.state(),old.until(),old.reason(),old.restrictions());
        var after=repository.snapshot(old.version()+1,state,until,reason,restrictions);
        history.record(actor,target,command.action().name(),command.commandKey(),request,before,after);
        repository.update(old,state,until,reason,restrictions,command.action()==Action.ACTIVATE);
        String description="Quyết định "+actionLabel(command.action())+": "+decisionReason+". Lịch sử và nghĩa vụ đơn cũ được giữ. Xem thời hạn/phạm vi tại tình trạng tài khoản; liên hệ hỗ trợ nếu không đồng ý.";
        notices.enqueue("ACCOUNT:"+command.commandKey(),target,List.of(
            new PlatformNotificationFacade.Notice(target,"ACCOUNT_REVIEWED","Tình trạng tài khoản đã cập nhật",description,PlatformNotificationFacade.Destination.ACCOUNT_SELF,target,old.version()+1),
            new PlatformNotificationFacade.Notice(actor,"ACCOUNT_CONFIRMED","Đã ghi nhận quyết định tài khoản","Tài khoản #"+target+" — "+description,PlatformNotificationFacade.Destination.KTV_ACCOUNT,target,old.version()+1)));
        return result(target,old.version()+1,command.action().name());
    }
    private void validateDeadline(Instant end,Scope scope,Instant now) {
        long seconds=end==null?0:Duration.between(now,end).getSeconds();
        if(end==null || seconds<7*86400L || end.isAfter(now.atZone(ZoneOffset.UTC).plusMonths(3).toInstant()))
            throw problem("ACCOUNT_DEADLINE_INVALID","Hạn chế tạm thời từ 1 tuần đến tối đa 3 tháng.",400);
        // Timestamp fields can include a few seconds between form and request.
        // Weekly policies are submitted as durations from the server timestamp.
        if(scope!=Scope.NEW_LISTINGS && Math.abs(seconds-Math.round((double)seconds/(7*86400))*(7*86400))>120)
            throw problem("ACCOUNT_DEADLINE_INVALID","Phạm vi này dùng thời hạn theo mốc tuần.",400);
    }
    private State state(long id,boolean lock) { return repository.customer(id,lock).orElseThrow(()->problem("ACCOUNT_NOT_FOUND","Không tìm thấy tài khoản khách hàng trong phạm vi KTV.",404)); }
    private void ktv(long actor) { if(!repository.activeKtv(actor)) throw new AccessDeniedException("Chỉ KTV đang hoạt động được quản lý tài khoản Người mua/Người bán."); }
    private CommandResult result(long id,long version,String action) { return new CommandResult(id,version,action,"Đã ghi nhận quyết định. Người dùng giữ quyền phục vụ nghĩa vụ cũ."); }
    private String actionLabel(Action action) { return switch(action) { case RESTRICT->"áp dụng hạn chế";case LIFT->"gỡ hạn chế";case LOCK->"khóa hoạt động mới";case ACTIVATE->"kích hoạt lại";case DEACTIVATE->"ngừng mềm tài khoản";case COMPENSATE->"bù thời hạn do lỗi hệ thống"; }; }
    private RegistrationException problem(String code,String message,int status) { return new RegistrationException(code,message,status); }
    private Map<String,Object> commandData(Command command) {
        try { return json.readValue(json.writeValueAsString(command),new TypeReference<>(){}); }
        catch(com.fasterxml.jackson.core.JsonProcessingException e) { throw new IllegalStateException("Cannot normalize account command",e); }
    }
}
