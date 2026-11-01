package com.oldbutgold.shop.modules.identity.infrastructure.email;

import com.oldbutgold.shop.modules.identity.application.RegistrationException;
import com.oldbutgold.shop.shared.config.RegistrationProperties;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.support.StaticListableBeanFactory;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.MailAuthenticationException;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import java.io.*;
import java.net.ServerSocket;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class SmtpRegistrationEmailTest {
    private RegistrationProperties properties(boolean enabled) {
        return new RegistrationProperties(Duration.ofMinutes(5),Duration.ofMinutes(30),Duration.ofMinutes(1),
                5,5,20,enabled,"no-reply@example.invalid");
    }
    private SmtpRegistrationEmail adapter(JavaMailSender sender,boolean enabled) {
        var beans=new StaticListableBeanFactory();
        if(sender!=null)beans.addBean("mail",sender);
        return new SmtpRegistrationEmail(beans.getBeanProvider(JavaMailSender.class),properties(enabled));
    }
    @Test void missingOrDisabledProviderFailsWithoutExposingOtp() {
        var mail=mock(JavaMailSender.class);
        for(var adapter:List.of(adapter(null,true),adapter(mail,false))) {
            assertThatThrownBy(()->adapter.sendCode("fixture@example.invalid","123456",Duration.ofMinutes(5)))
                    .isInstanceOfSatisfying(RegistrationException.class,error->{
                        assertThat(error.status()).isEqualTo(503);
                        assertThat(error.getMessage()).doesNotContain("123456");
                    });
        }
        verifyNoInteractions(mail);
    }
    @Test void authenticationFailureDoesNotExposeProviderDetails() {
        var mail=mock(JavaMailSender.class);
        doThrow(new MailAuthenticationException("Fixture private credentials")).when(mail).send(any(org.springframework.mail.SimpleMailMessage.class));
        assertThatThrownBy(()->adapter(mail,true).sendCode("fixture@example.invalid","123456",Duration.ofMinutes(5)))
                .isInstanceOfSatisfying(RegistrationException.class,error->
                        assertThat(error.getMessage()).doesNotContain("credentials","123456"));
    }
    @Test void realSmtpTransportDeliversCodeToAnIsolatedLocalMailbox() throws Exception {
        try(var listener=new ServerSocket(0, 50, java.net.InetAddress.getByName("127.0.0.1"));var worker=Executors.newSingleThreadExecutor()) {
            listener.setSoTimeout(5000);
            var delivered=worker.submit(()->{
                try(var socket=listener.accept()) {
                    socket.setSoTimeout(5000);
                    var reader=new BufferedReader(new InputStreamReader(socket.getInputStream(),StandardCharsets.UTF_8));
                    var writer=new PrintWriter(new OutputStreamWriter(socket.getOutputStream(),StandardCharsets.UTF_8),true);
                    writer.print("220 localhost test SMTP\r\n");writer.flush();
                    var message=new StringBuilder(); boolean data=false; String line;
                    while((line=reader.readLine())!=null) {
                        if(data) {
                            if(line.equals(".")){data=false;writer.print("250 accepted\r\n");writer.flush();}
                            else message.append(line).append("\r\n");
                        } else if(line.startsWith("DATA")) {data=true;writer.print("354 send message\r\n");writer.flush();}
                        else if(line.startsWith("QUIT")) {writer.print("221 bye\r\n");writer.flush();break;}
                        else {writer.print("250 localhost\r\n");writer.flush();}
                    }
                    return message.toString();
                }
            });
            var sender=new JavaMailSenderImpl();
            sender.setHost("127.0.0.1");sender.setPort(listener.getLocalPort());sender.setDefaultEncoding("UTF-8");
            sender.getJavaMailProperties().put("mail.smtp.localhost","localhost");
            sender.getJavaMailProperties().put("mail.smtp.connectiontimeout","2000");
            sender.getJavaMailProperties().put("mail.smtp.timeout","2000");
            sender.getJavaMailProperties().put("mail.smtp.writetimeout","2000");
            adapter(sender,true).sendCode("fixture@example.invalid","123456",Duration.ofMinutes(5));
            var message=new MimeMessage(Session.getInstance(new Properties()),new ByteArrayInputStream(
                    delivered.get(5,TimeUnit.SECONDS).getBytes(StandardCharsets.UTF_8)));
            assertThat(message.getAllRecipients()[0].toString()).isEqualTo("fixture@example.invalid");
            assertThat(message.getContent().toString()).contains("123456","5");
        }
    }
}
