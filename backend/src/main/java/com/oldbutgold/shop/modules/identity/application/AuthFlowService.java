package com.oldbutgold.shop.modules.identity.application;

import com.oldbutgold.shop.modules.identity.domain.PasswordPolicy;
import com.oldbutgold.shop.modules.identity.infrastructure.persistence.*;
import com.oldbutgold.shop.shared.config.RegistrationProperties;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import java.security.MessageDigest;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.*;
import static com.oldbutgold.shop.modules.identity.infrastructure.persistence.AuthFlowRepository.*;

/** Auth challenges are purpose-bound and consumed in the transaction that grants their effect. */
@Service
public class AuthFlowService {
    private final AuthService auth;
    private final UserRepository users;
    private final AuthFlowRepository flows;
    private final RegistrationOtp otp;
    private final RegistrationEmailPort mail;
    private final RegistrationProperties properties;
    private final PasswordEncoder passwords;
    private final Clock clock;
    private final TransactionTemplate tx;
    private final ResetEmailDispatcher resetMail;
    public AuthFlowService(AuthService auth,UserRepository users,AuthFlowRepository flows,RegistrationOtp otp,
                           RegistrationEmailPort mail,RegistrationProperties properties,PasswordEncoder passwords,
                           Clock clock,org.springframework.transaction.PlatformTransactionManager manager,ResetEmailDispatcher resetMail) {
        this.auth=auth;this.users=users;this.flows=flows;this.otp=otp;this.mail=mail;this.properties=properties;
        this.passwords=passwords;this.clock=clock;this.tx=new TransactionTemplate(manager);
        this.resetMail=resetMail;
    }
    public record Pending(String status,UUID challengeId,String email,Instant expiresAt,Instant resendAt,
                          int attemptsRemaining,String purpose) {}
    public record Result(AuthService.SessionResult session,Pending pending) {}
    public record GoogleProfile(String subject,String email,String fullName) {}
    public record GoogleResult(Result login,UUID registrationTicket,String email,String fullName) {}
    private record Attempt(Result result,RegistrationException error) {}

    public Result login(String email,String password,String requester) {
        return tx.execute(s->{
            flows.lock("requester:"+otp.requesterDigest(requester));
            flows.lock("email:"+normalize(email));
            flows.lockUserByEmail(normalize(email));
            UserEntity user=auth.authenticatePassword(email,password);
            return loginUser(user,requester);
        });
    }
    private Result loginUser(UserEntity user,String requester) {
        requireActive(user);
        if(customer(user) && flows.twoFactor(user.getId()))
            return new Result(null,issue("LOGIN_EMAIL_2FA",user.getEmail(),data(user,null),requester));
        return new Result(auth.newSession(user),null);
    }
    public Pending startReset(String email,String requester) {
        String normalized=normalize(email);
        return tx.execute(s->{
            flows.lock("requester:"+otp.requesterDigest(requester));
            flows.lock("email:"+normalized);
            flows.lockUserByEmail(normalized);
            UserEntity user=users.findByEmail(normalized).orElse(null);
            boolean eligible=user!=null && customer(user) && "ACTIVE".equals(user.getStatus())
                    && user.getEmailVerifiedAt()!=null && flows.loginRestriction(user.getId()).isEmpty();
            return issue("PASSWORD_RESET",normalized,eligible?data(user,null):new Data(null,0,null,normalized,null,false),requester);
        });
    }
    public Result verify(UUID id,String code,String purpose) {
        Challenge initial=challenge(id,purpose,false);
        Attempt attempt=tx.execute(s->{
            flows.lock("email:"+initial.email());
            Challenge c=challenge(id,purpose,true);
            requireOpen(c);
            if(c.attempts()>=c.maxAttempts()) return new Attempt(null,error("OTP_ATTEMPTS_EXHAUSTED","Đã hết lượt nhập mã. Hãy gửi mã mới.",429));
            boolean matches=MessageDigest.isEqual(otp.digest(id,code).getBytes(StandardCharsets.UTF_8),c.digest().getBytes(StandardCharsets.UTF_8));
            if(!matches || c.data().userId()==null) {
                flows.attempt(id);
                return new Attempt(null,error("OTP_INVALID","Mã xác thực không đúng.",400));
            }
            UserEntity user=boundUser(c);
            requireActive(user);
            if("PASSWORD_RESET".equals(purpose)) {
                flows.consume(id,clock.instant());
                Data d=c.data();UUID grant=UUID.randomUUID();Instant now=clock.instant(),expiry=now.plus(properties.otpTtl());
                flows.open(c.email(),"PASSWORD_RESET_COMPLETE").ifPresent(previous->flows.invalidate(previous.id(),now));
                flows.insert(grant,"PASSWORD_RESET_COMPLETE",c.email(),otp.digest(grant,grant.toString()),now,expiry,now,1,null,
                        new Data(d.userId(),d.version(),null,d.email(),null,true));
                return new Attempt(new Result(null,new Pending("RESET_READY",grant,c.email(),expiry,now,0,"PASSWORD_RESET")),null);
            }
            flows.consume(id,clock.instant());
            if("GOOGLE_LINK".equals(purpose)) {
                flows.linkGoogle(user.getId(),c.data().googleSubject());
                // Linking proves ownership; configured login 2FA is a separate challenge.
                return new Attempt(loginUser(user,"google-link:"+id),null);
            }
            return new Attempt(new Result(auth.newSession(user),null),null);
        });
        if(attempt.error()!=null) throw attempt.error();
        return attempt.result();
    }
    public void finishReset(UUID id,String password,String confirmation) {
        PasswordPolicy.requireNewPassword(password);
        if(!password.equals(confirmation)) throw error("PASSWORD_CONFIRMATION_MISMATCH","Xác nhận mật khẩu không khớp.",400);
        String hash=passwords.encode(password);
        Challenge initial=challenge(id,"PASSWORD_RESET_COMPLETE",false);
        tx.executeWithoutResult(s->{
            flows.lock("email:"+initial.email());
            Challenge c=challenge(id,"PASSWORD_RESET_COMPLETE",true); requireOpen(c);
            if(!c.data().verified()) throw unavailable();
            UserEntity user=boundUser(c); requireActive(user);
            if(!customer(user) || user.getEmailVerifiedAt()==null) throw unavailable();
            if(user.getPasswordHash()!=null && passwords.matches(password, user.getPasswordHash())) {
                throw error("PASSWORD_SAME_AS_OLD","Mật khẩu mới không được trùng với mật khẩu cũ.",400);
            }
            user.updatePassword(hash,clock.instant()); users.saveAndFlush(user);
            flows.consume(id,clock.instant());
        });
    }
    public Pending resend(UUID id,String requester) {
        Challenge initial=challenge(id,null,false);
        requireOtpPurpose(initial.purpose());
        return tx.execute(s->{
            flows.lock("requester:"+otp.requesterDigest(requester));
            flows.lock("email:"+initial.email());
            Challenge c=challenge(id,initial.purpose(),true);
            if(!"OPEN".equals(c.state()) || c.data()==null || c.data().verified()) throw unavailable();
            if(c.data().userId()!=null) requireActive(boundUser(c));
            return issue(c.purpose(),c.email(),c.data(),requester);
        });
    }
    public void cancel(UUID id) {
        Challenge initial=challenge(id,null,false);
        if(!"PASSWORD_RESET_COMPLETE".equals(initial.purpose())) requireOtpPurpose(initial.purpose());
        tx.executeWithoutResult(s->{flows.lock("email:"+initial.email());flows.invalidate(id,clock.instant());});
    }
    private Pending issue(String purpose,String email,Data data,String requester) {
        String digest=otp.requesterDigest(requester); flows.lock("requester:"+digest);
        Instant now=clock.instant();
        var previous=flows.open(email,purpose);
        if(previous.isPresent() && previous.get().resendAt().isAfter(now))
            throw new RegistrationException("OTP_RESEND_TOO_SOON","Vui lòng chờ trước khi gửi lại mã.",429,
                    Math.max(1,Duration.between(now,previous.get().resendAt()).getSeconds()+1));
        long rate=flows.rate(email,digest,now,properties.emailHourlyLimit(),properties.ipHourlyLimit());
        if(rate>0) throw new RegistrationException("OTP_RATE_LIMIT","Đã đạt giới hạn gửi mã. Vui lòng thử lại sau.",429,rate);
        previous.ifPresent(c->flows.invalidate(c.id(),now));
        UUID id=UUID.randomUUID(); String code=otp.generate();
        Instant expires=now.plus(properties.otpTtl()),resend=now.plus(properties.resendCooldown());
        flows.insert(id,purpose,email,otp.digest(id,code),now,expires,resend,properties.maxAttempts(),digest,data);
        // Decoys have the same challenge/cooldown contract, but cannot grant reset or send mail.
        if(data.userId()!=null) {
            if("PASSWORD_RESET".equals(purpose)) resetMail.afterCommit(email,code,expires);
            else mail.sendAuthCode(email,code,properties.otpTtl(),purpose);
        }
        return new Pending("OTP_REQUIRED",id,email,expires,resend,properties.maxAttempts(),purpose);
    }
    public UUID googleNonce(String requester) {
        return tx.execute(s->{
            String digest=otp.requesterDigest(requester); flows.lock("requester:"+digest);
            Instant now=clock.instant(); long rate=flows.rate("google-nonce",digest,now,properties.ipHourlyLimit(),properties.ipHourlyLimit());
            if(rate>0) throw new RegistrationException("OTP_RATE_LIMIT","Vui lòng thử lại sau.",429,rate);
            UUID id=UUID.randomUUID();
            flows.insert(id,"GOOGLE_NONCE","google-nonce:"+id,otp.digest(id,id.toString()),now,now.plus(properties.otpTtl()),
                    now,1,digest,new Data(null,0,null,null,null,false)); return id;
        });
    }
    public GoogleResult google(UUID nonce,GoogleProfile profile,String requester) {
        return tx.execute(s->{
            flows.lock("requester:"+otp.requesterDigest(requester));
            flows.lock("email:"+profile.email());
            Challenge n=challenge(nonce,"GOOGLE_NONCE",true);requireOpen(n);flows.consume(nonce,clock.instant());
            var linked=flows.googleUser(profile.subject());
            if(linked.isPresent()) {
                flows.lockUser(linked.get());
                var user=users.findById(linked.get()).orElseThrow(AuthFlowService::unavailable);
                if(!customer(user)) throw error("GOOGLE_NOT_ALLOWED","Tài khoản này dùng phương thức đăng nhập do Admin cấp.",403);
                return new GoogleResult(loginUser(user,requester),null,null,null);
            }
            flows.lockUserByEmail(profile.email());
            var existing=users.findByEmail(profile.email());
            if(existing.isPresent()) {
                requireActive(existing.get());
                if(!customer(existing.get())) throw error("GOOGLE_NOT_ALLOWED","Tài khoản này dùng phương thức đăng nhập do Admin cấp.",403);
                return new GoogleResult(new Result(null,issue("GOOGLE_LINK",profile.email(),data(existing.get(),profile.subject()),requester)),null,null,null);
            }
            Instant now=clock.instant(); UUID ticket=UUID.randomUUID();
            flows.insert(ticket,"GOOGLE_REGISTER",profile.email(),otp.digest(ticket,ticket.toString()),now,
                    now.plus(properties.otpTtl()),now,1,null,new Data(null,0,profile.subject(),profile.email(),profile.fullName(),true));
            return new GoogleResult(null,ticket,profile.email(),profile.fullName());
        });
    }
    /** Called inside the registration transaction; rollback retains the ticket on SMTP failure. */
    public GoogleProfile consumeRegistrationTicket(UUID id,String email) {
        Challenge c=challenge(id,"GOOGLE_REGISTER",true); requireOpen(c);
        if(!normalize(email).equals(c.email())) throw error("GOOGLE_EMAIL_MISMATCH","Email phải trùng với tài khoản Google đã chọn.",400);
        flows.consume(id,clock.instant());
        return new GoogleProfile(c.data().googleSubject(),c.email(),c.data().fullName());
    }
    private UserEntity boundUser(Challenge c) {
        flows.lockUser(c.data().userId());
        UserEntity user=users.findById(c.data().userId()).orElseThrow(AuthFlowService::unavailable);
        if(user.getCredentialVersion()!=c.data().version()) throw unavailable();
        return user;
    }
    private void requireActive(UserEntity user) {
        var restriction=flows.loginRestriction(user.getId());
        if(!"ACTIVE".equals(user.getStatus()) || restriction.isPresent())
            throw new AccountRestrictedException(restriction.isPresent()?"LOGIN_RESTRICTED":user.getStatus(),
                    restriction.orElseGet(()->flows.activeRestrictionReason(user.getId())
                        .orElse("Tài khoản đang bị khóa hoặc ngừng hoạt động. Liên hệ hỗ trợ để biết lý do.")));
    }
    private Challenge challenge(UUID id,String purpose,boolean locked) {
        return flows.find(id,locked).filter(c->c.data()!=null && (purpose==null || purpose.equals(c.purpose()))).orElseThrow(AuthFlowService::unavailable);
    }
    private void requireOpen(Challenge c) {
        if(!"OPEN".equals(c.state()) || c.data()==null) throw unavailable();
        if(!c.expiresAt().isAfter(clock.instant())) throw error("OTP_EXPIRED","Mã đã hết hạn. Hãy gửi mã mới.",400);
    }
    private static void requireOtpPurpose(String p) {
        if(!Set.of("LOGIN_EMAIL_2FA","PASSWORD_RESET","GOOGLE_LINK").contains(p)) throw unavailable();
    }
    private static Data data(UserEntity u,String subject) { return new Data(u.getId(),u.getCredentialVersion(),subject,u.getEmail(),null,false); }
    private static boolean customer(UserEntity u) {
        return u.getRoles().stream().anyMatch(r->Set.of("BUYER","SELLER").contains(r.getRoleName()))
                && u.getRoles().stream().noneMatch(r->Set.of("ADMIN","KTV").contains(r.getRoleName()));
    }
    private static String normalize(String email) { return email.strip().toLowerCase(Locale.ROOT); }
    private static RegistrationException unavailable() { return error("OTP_UNAVAILABLE","Yêu cầu xác thực không còn hiệu lực. Hãy bắt đầu lại.",400); }
    private static RegistrationException error(String code,String message,int status) { return new RegistrationException(code,message,status); }
}
