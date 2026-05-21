package com.camerarental.service.impl;

import com.camerarental.service.EmailService;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class EmailServiceImpl implements EmailService {

    private final JavaMailSender mailSender;

    @Value("${spring.mail.username:noreply@lensrent.com}")
    private String fromEmail;

    @Value("${spring.mail.host:smtp.gmail.com}")
    private String mailHost;

    @Value("${spring.mail.port:587}")
    private int mailPort;

    @Override
    public void sendPasswordResetEmail(String to, String resetLink) {
        log.info("[FORGOT_PASSWORD] === EMAIL SERVICE START ===");
        log.info("[FORGOT_PASSWORD] incoming email: {}", to);
        log.info("[FORGOT_PASSWORD] reset link: {}", resetLink);
        log.info("[FORGOT_PASSWORD] from email: {}", fromEmail);
        log.info("[FORGOT_PASSWORD] mail host: {}", mailHost);
        log.info("[FORGOT_PASSWORD] mail port: {}", mailPort);

        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

            helper.setFrom(fromEmail);
            helper.setTo(to);
            helper.setSubject("Đặt lại mật khẩu - LensRent");

            String htmlContent = buildPasswordResetEmailHtml(resetLink);
            helper.setText(htmlContent, true);

            log.info("[FORGOT_PASSWORD] sending email to: {}", to);
            mailSender.send(message);
            log.info("[FORGOT_PASSWORD] email sent successfully to: {}", to);
            log.info("[FORGOT_PASSWORD] === EMAIL SERVICE END (SUCCESS) ===");

        } catch (MailException e) {
            log.error("[FORGOT_PASSWORD] MailException while sending to: {}", to, e);
            log.error("[FORGOT_PASSWORD] Mail error message: {}", e.getMessage());
            log.error("[FORGOT_PASSWORD] === EMAIL SERVICE END (MAIL ERROR) ===");
            throw new RuntimeException("Failed to send password reset email: " + e.getMessage(), e);
        } catch (MessagingException e) {
            log.error("[FORGOT_PASSWORD] MessagingException while sending to: {}", to, e);
            log.error("[FORGOT_PASSWORD] Messaging error message: {}", e.getMessage());
            log.error("[FORGOT_PASSWORD] === EMAIL SERVICE END (MESSAGING ERROR) ===");
            throw new RuntimeException("Failed to create email message: " + e.getMessage(), e);
        } catch (Exception e) {
            log.error("[FORGOT_PASSWORD] Unexpected error while sending to: {}", to, e);
            log.error("[FORGOT_PASSWORD] Unexpected error message: {}", e.getMessage());
            log.error("[FORGOT_PASSWORD] Stack trace: ", e);
            log.error("[FORGOT_PASSWORD] === EMAIL SERVICE END (UNEXPECTED ERROR) ===");
            throw new RuntimeException("Failed to send password reset email: " + e.getMessage(), e);
        }
    }

    private String buildPasswordResetEmailHtml(String resetLink) {
        return """
            <!DOCTYPE html>
            <html>
            <head>
              <meta charset="UTF-8">
              <style>
                body { font-family: Arial, sans-serif; background-color: #0f172a; margin: 0; padding: 0; }
                .container { max-width: 480px; margin: 40px auto; background-color: #1e293b; border-radius: 16px; overflow: hidden; border: 1px solid #334155; }
                .header { background: linear-gradient(135deg, #d4af37, #92400e); padding: 32px; text-align: center; }
                .header h1 { color: #0f172a; margin: 0; font-size: 24px; font-weight: bold; }
                .header p { color: #1e293b; margin: 4px 0 0; font-size: 12px; }
                .body { padding: 32px; }
                .body h2 { color: #f1f5f9; margin: 0 0 16px; font-size: 18px; }
                .body p { color: #94a3b8; font-size: 14px; line-height: 1.6; margin: 0 0 24px; }
                .button { display: inline-block; background: linear-gradient(135deg, #d4af37, #b8860b); color: #0f172a; text-decoration: none; font-weight: bold; font-size: 14px; padding: 14px 32px; border-radius: 10px; }
                .button:hover { background: linear-gradient(135deg, #e5c158, #d4af37); }
                .note { margin-top: 24px; font-size: 12px; color: #64748b; line-height: 1.5; }
                .note strong { color: #94a3b8; }
                .footer { text-align: center; padding: 20px 32px; border-top: 1px solid #334155; font-size: 11px; color: #475569; }
              </style>
            </head>
            <body>
              <div class="container">
                <div class="header">
                  <h1>LENSRENT</h1>
                  <p>Premium Camera Rentals</p>
                </div>
                <div class="body">
                  <h2>Yêu cầu đặt lại mật khẩu</h2>
                  <p>
                    Chúng tôi đã nhận được yêu cầu đặt lại mật khẩu cho tài khoản của bạn.
                    Nhấn nút bên dưới để đặt lại mật khẩu mới.
                    Liên kết này sẽ hết hạn sau <strong>1 giờ</strong>.
                  </p>
                  <div style="text-align: center;">
                    <a href="%s" class="button">Đặt lại mật khẩu</a>
                  </div>
                  <div class="note">
                    <strong>Lưu ý:</strong><br>
                    • Nếu bạn không yêu cầu đặt lại mật khẩu, vui lòng bỏ qua email này.<br>
                    • Liên kết chỉ có hiệu lực trong 1 giờ.<br>
                    • Liên kết chỉ có thể sử dụng một lần.
                  </div>
                </div>
                <div class="footer">
                  © 2026 LensRent — Premium Camera Rentals
                </div>
              </div>
            </body>
            </html>
            """.formatted(resetLink);
    }
}
