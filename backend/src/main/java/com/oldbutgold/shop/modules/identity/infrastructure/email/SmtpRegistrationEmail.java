package com.oldbutgold.shop.modules.identity.infrastructure.email;

import com.oldbutgold.shop.modules.identity.application.RegistrationEmailPort;
import com.oldbutgold.shop.modules.identity.application.RegistrationException;
import com.oldbutgold.shop.shared.config.RegistrationProperties;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;
import java.time.Duration;

@Component
public class SmtpRegistrationEmail implements RegistrationEmailPort {
    private final ObjectProvider<JavaMailSender> sender;
    private final RegistrationProperties properties;

    public SmtpRegistrationEmail(ObjectProvider<JavaMailSender> sender, RegistrationProperties properties) {
        this.sender = sender;
        this.properties = properties;
    }

    @Override public void sendCode(String email, String code, Duration lifetime) {
        sendAuthCode(email,code,lifetime,"REGISTER_EMAIL");
    }

    @Override public void sendAuthCode(String email, String code, Duration lifetime, String purpose) {
        JavaMailSender mail = sender.getIfAvailable();
        if (!properties.mailEnabled() || mail == null) throw unavailable();
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(properties.from());
        message.setTo(email);
        String action = switch(purpose) {
            case "PASSWORD_RESET" -> "đặt lại mật khẩu";
            case "LOGIN_EMAIL_2FA" -> "đăng nhập";
            case "GOOGLE_LINK" -> "liên kết Google";
            default -> "đăng ký";
        };
        message.setSubject("Mã xác thực " + action + " O.G Shop");
        message.setText("Mã xác thực " + action + " của bạn: " + code + "\n\nMã có hiệu lực trong "
                + lifetime.toMinutes() + " phút. Không chia sẻ mã này với người khác."
                + "\nNếu bạn không yêu cầu " + action + ", hãy bỏ qua email này.");
        try {
            mail.send(message);
        } catch (MailException exception) {
            // Provider exceptions can contain recipient/SMTP details; keep them out of the API/logs.
            throw unavailable();
        }
    }

    private static RegistrationException unavailable() {
        return new RegistrationException("OTP_EMAIL_UNAVAILABLE",
                "Chưa gửi được email xác thực. Vui lòng thử lại sau.", 503);
    }
}
