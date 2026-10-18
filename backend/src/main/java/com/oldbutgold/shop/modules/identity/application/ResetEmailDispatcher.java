package com.oldbutgold.shop.modules.identity.application;

import org.springframework.stereotype.Component;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.transaction.support.*;
import java.time.Duration;
import java.time.Clock;
import java.time.Instant;
import java.util.concurrent.*;

/** Reset responses do not depend on SMTP timing or availability. The queue is bounded. */
@Component
public class ResetEmailDispatcher implements DisposableBean {
    private final RegistrationEmailPort mail;
    private final Clock clock;
    private final ThreadPoolExecutor workers=new ThreadPoolExecutor(2,2,0,TimeUnit.SECONDS,
            new ArrayBlockingQueue<>(100),r->{Thread t=new Thread(r,"password-reset-mail");t.setDaemon(true);return t;});
    public ResetEmailDispatcher(RegistrationEmailPort mail,Clock clock) {this.mail=mail;this.clock=clock;}
    public void afterCommit(String email,String code,Instant expiresAt) {
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override public void afterCommit() {
                try { workers.execute(()->{
                    Duration remaining=Duration.between(clock.instant(),expiresAt);
                    if(remaining.isNegative() || remaining.isZero()) return;
                    try {mail.sendAuthCode(email,code,remaining,"PASSWORD_RESET");}
                    catch(RuntimeException ignored) { /* Generic acceptance; user may resend after cooldown. */ }
                }); }
                catch(RejectedExecutionException ignored) { /* Fail closed; no reset effect and no account disclosure. */ }
            }
        });
    }
    @Override public void destroy() {workers.shutdownNow();}
}
