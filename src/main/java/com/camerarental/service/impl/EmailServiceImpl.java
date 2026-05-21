package com.camerarental.service.impl;

import com.camerarental.service.EmailService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class EmailServiceImpl implements EmailService {

    private static final String BREVO_API_URL = "https://api.brevo.com/v3/smtp/email";

    private final RestTemplate restTemplate;

    @Value("${brevo.sender.email:noreply@lensrent.com}")
    private String senderEmail;

    @Value("${brevo.sender.name:LensRent}")
    private String senderName;

    @Value("${brevo.api.key}")
    private String brevoApiKey;

    @Override
    public void sendPasswordResetEmail(String to, String resetLink) {
        log.info("[FORGOT_PASSWORD] === BREVO EMAIL SERVICE START ===");
        log.info("[FORGOT_PASSWORD] incoming email: {}", to);
        log.info("[FORGOT_PASSWORD] reset link: {}", resetLink);
        log.info("[FORGOT_PASSWORD] sender: {} <{}>", senderName, senderEmail);

        try {
            // Build Brevo API request body
            Map<String, Object> requestBody = new HashMap<>();

            // Subject
            requestBody.put("subject", "Đặt lại mật khẩu - LensRent");

            // Sender
            Map<String, String> sender = new HashMap<>();
            sender.put("name", senderName);
            sender.put("email", senderEmail);
            requestBody.put("sender", sender);

            // Recipient
            Map<String, String> recipient = new HashMap<>();
            recipient.put("email", to);
            requestBody.put("to", new Map[]{recipient});

            // HTML content
            requestBody.put("htmlContent", buildPasswordResetEmailHtml(resetLink));

            log.info("[FORGOT_PASSWORD] Sending request to Brevo API...");
            log.info("[FORGOT_PASSWORD] API endpoint: {}", BREVO_API_URL);

            // Set headers
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("api-key", brevoApiKey);

            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, headers);

            // Call Brevo API
            ResponseEntity<String> response = restTemplate.exchange(
                    BREVO_API_URL,
                    HttpMethod.POST,
                    entity,
                    String.class
            );

            if (response.getStatusCode().is2xxSuccessful()) {
                log.info("[FORGOT_PASSWORD] Email sent successfully to: {}", to);
                log.info("[FORGOT_PASSWORD] Response status: {}", response.getStatusCode());
                log.info("[FORGOT_PASSWORD] === BREVO EMAIL SERVICE END (SUCCESS) ===");
            } else {
                log.error("[FORGOT_PASSWORD] Email send failed - Status: {}", response.getStatusCode());
                log.error("[FORGOT_PASSWORD] Response body: {}", response.getBody());
                log.error("[FORGOT_PASSWORD] === BREVO EMAIL SERVICE END (FAILED) ===");
                throw new RuntimeException("Failed to send email via Brevo API. Status: " + response.getStatusCode());
            }

        } catch (RestClientException e) {
            log.error("[FORGOT_PASSWORD] RestClientException while sending to: {}", to, e);
            log.error("[FORGOT_PASSWORD] Error message: {}", e.getMessage());
            log.error("[FORGOT_PASSWORD] === BREVO EMAIL SERVICE END (REST ERROR) ===");
            throw new RuntimeException("Failed to send password reset email via Brevo API: " + e.getMessage(), e);
        } catch (Exception e) {
            log.error("[FORGOT_PASSWORD] Unexpected error while sending to: {}", to, e);
            log.error("[FORGOT_PASSWORD] Error message: {}", e.getMessage());
            log.error("[FORGOT_PASSWORD] Stack trace: ", e);
            log.error("[FORGOT_PASSWORD] === BREVO EMAIL SERVICE END (UNEXPECTED ERROR) ===");
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
