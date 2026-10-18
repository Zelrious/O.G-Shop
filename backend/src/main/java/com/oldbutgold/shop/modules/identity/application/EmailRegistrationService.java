package com.oldbutgold.shop.modules.identity.application;

import com.oldbutgold.shop.modules.commerce.application.RegistrationCommerceFacade;
import com.oldbutgold.shop.modules.identity.domain.PasswordPolicy;
import com.oldbutgold.shop.modules.identity.infrastructure.persistence.RegistrationRepository;
import com.oldbutgold.shop.modules.identity.infrastructure.persistence.RegistrationRepository.Challenge;
import com.oldbutgold.shop.shared.config.RegistrationProperties;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;

@Service
public class EmailRegistrationService {
    private final RegistrationRepository repository;
    private final AuthService auth;
    private final RegistrationCommerceFacade commerce;
    private final PasswordEncoder passwords;
    private final RegistrationEmailPort email;
    private final RegistrationOtp otp;
    private final RegistrationProperties properties;
    private final Clock clock;
    private final TransactionTemplate transactions;
    private final AuthFlowService flows;
    private final com.oldbutgold.shop.modules.identity.infrastructure.persistence.AuthFlowRepository identities;

    public EmailRegistrationService(RegistrationRepository repository, AuthService auth,
                                    RegistrationCommerceFacade commerce, PasswordEncoder passwords,
                                    RegistrationEmailPort email, RegistrationOtp otp, RegistrationProperties properties,
                                    Clock clock, PlatformTransactionManager manager, AuthFlowService flows,
                                    com.oldbutgold.shop.modules.identity.infrastructure.persistence.AuthFlowRepository identities) {
        this.repository = repository; this.auth = auth; this.commerce = commerce; this.passwords = passwords;
        this.email = email; this.otp = otp; this.properties = properties; this.clock = clock;
        this.transactions = new TransactionTemplate(manager);
        this.flows=flows;this.identities=identities;
    }

    public Pending start(Input input, String requester) {
        return start(input,requester,null);
    }

    public Pending start(Input input, String requester, UUID googleTicket) {
        PasswordPolicy.requireNewPassword(input.password());
        if (input.accountType() == null || (input.accountType() == RegistrationDraft.Type.SELLER
                ? input.seller() == null : input.seller() != null)) {
            throw new IllegalArgumentException("Vui lòng chọn Người mua hoặc bổ sung đầy đủ hồ sơ Người bán.");
        }
        RegistrationDraft draft = new RegistrationDraft(input.email().strip().toLowerCase(Locale.ROOT),
                passwords.encode(input.password()), input.fullName().strip(),
                input.phoneNumber() == null || input.phoneNumber().isBlank() ? null : input.phoneNumber().strip(),
                input.accountType(), input.seller());
        String requesterDigest = otp.requesterDigest(requester);
        return Objects.requireNonNull(transactions.execute(tx -> {
            repository.lockRequester(requesterDigest);
            repository.lockEmail(draft.email());
            String googleSubject=googleTicket==null?null:flows.consumeRegistrationTicket(googleTicket,draft.email()).subject();
            if (repository.emailExists(draft.email())) throw new DuplicateEmailException();
            Challenge existing = repository.openForEmail(draft.email()).orElse(null);
            if (existing != null && (existing.draft() == null || existing.draftExpiresAt() == null
                    || !existing.draftExpiresAt().isAfter(clock.instant()))) {
                repository.invalidate(existing.id(), clock.instant());
                existing = null;
            }
            var boundDraft=new RegistrationDraft(draft.email(),draft.passwordHash(),draft.fullName(),draft.phoneNumber(),
                    draft.accountType(),draft.seller(),googleSubject);
            return issue(boundDraft, existing, requesterDigest);
        }));
    }

    public Pending resend(UUID id, String requester) {
        String subject = repository.emailFor(id).orElseThrow(EmailRegistrationService::unavailable);
        String requesterDigest = otp.requesterDigest(requester);
        return Objects.requireNonNull(transactions.execute(tx -> {
            repository.lockRequester(requesterDigest);
            repository.lockEmail(subject);
            Challenge existing = repository.findLocked(id).orElseThrow(EmailRegistrationService::unavailable);
            requireOpen(existing);
            if (repository.emailExists(subject)) throw new DuplicateEmailException();
            return issue(existing.draft(), existing, requesterDigest);
        }));
    }

    private Pending issue(RegistrationDraft draft, Challenge previous, String requesterDigest) {
        Instant now = clock.instant();
        Instant draftExpiry = previous == null ? now.plus(properties.draftTtl()) : previous.draftExpiresAt();
        if (draftExpiry == null || !draftExpiry.isAfter(now.plus(properties.resendCooldown()))) throw unavailable();
        if (previous != null && previous.resendAt().isAfter(now)) {
            throw new RegistrationException("OTP_RESEND_TOO_SOON", "Vui lòng chờ trước khi gửi mã mới.", 429,
                    Duration.between(now, previous.resendAt()).getSeconds() + 1);
        }
        long retry = Math.max(repository.emailRetryAfter(draft.email(), now, properties.emailHourlyLimit()),
                repository.requesterRetryAfter(requesterDigest, now, properties.ipHourlyLimit()));
        if (retry > 0) throw new RegistrationException("OTP_RATE_LIMIT",
                "Đã đạt giới hạn gửi mã. Vui lòng thử lại sau.", 429, retry);
        if (previous != null) repository.invalidate(previous.id(), now);
        UUID id = UUID.randomUUID();
        String code = otp.generate();
        Instant expiry = now.plus(properties.otpTtl()).isBefore(draftExpiry) ? now.plus(properties.otpTtl()) : draftExpiry;
        Instant resendAt = now.plus(properties.resendCooldown());
        repository.insert(id, draft, otp.digest(id, code), requesterDigest, now, expiry, draftExpiry, resendAt, properties.maxAttempts());
        // Bounded SMTP call: failure rolls back the new draft and preserves the previous code.
        email.sendCode(draft.email(), code, Duration.between(now, expiry));
        return new Pending(id, draft.email(), expiry, resendAt, properties.maxAttempts());
    }

    public Completion verify(UUID id, String code) {
        String subject = repository.emailFor(id).orElseThrow(EmailRegistrationService::unavailable);
        Attempt attempt = Objects.requireNonNull(transactions.execute(tx -> {
            repository.lockEmail(subject);
            Challenge challenge = repository.findLocked(id).orElseThrow(EmailRegistrationService::unavailable);
            requireOpen(challenge);
            Instant now = clock.instant();
            if (challenge.draftExpiresAt() == null || !challenge.draftExpiresAt().isAfter(now)) {
                repository.invalidate(id, now);
                return Attempt.failed(unavailable());
            }
            if (!challenge.expiresAt().isAfter(now)) {
                return Attempt.failed(new RegistrationException("OTP_EXPIRED",
                        "Mã đã hết hạn. Vui lòng gửi mã mới hoặc đăng ký lại.", 400));
            }
            if (challenge.attemptCount() >= challenge.maxAttempts()) return exhausted();
            boolean matches = MessageDigest.isEqual(otp.digest(id, code).getBytes(StandardCharsets.UTF_8),
                    challenge.digest().getBytes(StandardCharsets.UTF_8));
            repository.recordAttempt(id, matches, now);
            if (!matches) {
                if (challenge.attemptCount() + 1 >= challenge.maxAttempts()) return exhausted();
                return Attempt.failed(new RegistrationException("OTP_INVALID", "Mã xác thực không đúng. Còn "
                        + (challenge.maxAttempts() - challenge.attemptCount() - 1) + " lượt nhập.", 400));
            }
            if (repository.emailExists(subject)) return Attempt.failed(new RegistrationException(
                    "EMAIL_ALREADY_REGISTERED", "Email này đã được sử dụng. Vui lòng đăng nhập.", 409));
            var user = auth.createVerifiedAccount(challenge.draft());
            if(challenge.draft().googleSubject()!=null) identities.linkGoogle(user.getId(),challenge.draft().googleSubject());
            commerce.grantRegistrationReward(user.getId());
            if (challenge.draft().accountType() == RegistrationDraft.Type.SELLER) {
                RegistrationDraft.Seller seller = challenge.draft().seller();
                var addresses = commerce.createSellerAddresses(user.getId(), user.getFullName(),
                        seller.contactPhone(), address(seller.pickupAddress()), address(seller.returnAddress()));
                repository.createSellerDraft(user.getId(), seller, addresses.pickupId(), addresses.returnId(), now);
            }
            return new Attempt(new Completion(auth.newSession(user),
                    challenge.draft().accountType() == RegistrationDraft.Type.SELLER ? "SELLER_VERIFICATION" : "PROFILE",
                    3000), null);
        }));
        // Throw after commit: wrong OTP attempts must not be erased by exception rollback.
        if (attempt.error() != null) throw attempt.error();
        return attempt.completion();
    }

    public void cancel(UUID id) {
        repository.emailFor(id).ifPresent(subject -> transactions.executeWithoutResult(tx -> {
            repository.lockEmail(subject);
            repository.findLocked(id).filter(c -> "OPEN".equals(c.state()))
                    .ifPresent(c -> repository.invalidate(id, clock.instant()));
        }));
    }

    private static RegistrationCommerceFacade.Address address(RegistrationDraft.Address source) {
        return new RegistrationCommerceFacade.Address(source.province(), source.district(),
                source.ward(), source.detailAddress());
    }
    private static void requireOpen(Challenge challenge) {
        if (!"OPEN".equals(challenge.state()) || challenge.draft() == null) throw unavailable();
    }
    private static Attempt exhausted() {
        return Attempt.failed(new RegistrationException("OTP_ATTEMPTS_EXHAUSTED",
                "Đã hết lượt nhập mã. Vui lòng gửi mã mới hoặc đăng ký lại.", 400));
    }
    private static RegistrationException unavailable() {
        return new RegistrationException("OTP_UNAVAILABLE", "Yêu cầu xác thực không còn hiệu lực. Vui lòng đăng ký lại.", 400);
    }

    public record Input(String fullName, String email, String phoneNumber, String password,
                        RegistrationDraft.Type accountType, RegistrationDraft.Seller seller) {
        @Override public String toString() { return "RegistrationInput[redacted]"; }
    }
    public record Pending(UUID id, String email, Instant expiresAt, Instant resendAt, int attemptsRemaining) {}
    public record Completion(AuthService.SessionResult session, String nextStep, long awardedCoins) {}
    private record Attempt(Completion completion, RegistrationException error) {
        static Attempt failed(RegistrationException error) { return new Attempt(null, error); }
    }
}
