package com.forensic.audit.email;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import jakarta.mail.internet.MimeMessage;

@Slf4j
@Service
@RequiredArgsConstructor
public class EmailService {

    private final JavaMailSender mailSender;

    // -----------------------------------------------------------------------
    // Public API — called from VaeRequestConsumer after state transitions
    // -----------------------------------------------------------------------

    /**
     * VAE_ACCEPTED — user registered successfully.
     * Warm welcome email, no mention of security analysis.
     */
    @Async
    public void sendWelcomeEmail(String toEmail, String username) {
        String subject = "Welcome to ZeroTrust Forensics";
        String body    = buildWelcomeHtml(username);
        send(toEmail, subject, body);
    }

    /**
     * VAE_REJECTED — bot detected.
     * Deliberately vague — do not tell the user why they were rejected.
     * Avoids giving an attacker feedback to tune their bot.
     */
    @Async
    public void sendRejectionEmail(String toEmail) {
        String subject = "ZeroTrust Forensics — Registration Update";
        String body    = buildRejectionHtml();
        send(toEmail, subject, body);
    }

    /**
     * DEAD_LETTER — system error, not bot.
     * Kafka retried 3 times and failed. User is not at fault.
     * Tell them registration is on hold, team has been notified.
     */
    @Async
    public void sendOnHoldEmail(String toEmail) {
        String subject = "ZeroTrust Forensics — Registration On Hold";
        String body    = buildOnHoldHtml();
        send(toEmail, subject, body);
    }

    // -----------------------------------------------------------------------
    // Core send — reused by all three templates
    // -----------------------------------------------------------------------

    private void send(String to, String subject, String htmlBody) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

            helper.setTo(to);
            helper.setSubject(subject);
            helper.setText(htmlBody, true); // true = HTML
            helper.setFrom("noreply@zerotrustforensics.com");

            mailSender.send(message);
            log.info("[Email] Sent '{}' to {}", subject, to);

        } catch (Exception e) {
            // Email failure should never crash the consumer
            log.error("[Email] Failed to send to {} — {}", to, e.getMessage());
        }
    }

    // -----------------------------------------------------------------------
    // HTML templates
    // -----------------------------------------------------------------------

    private String buildWelcomeHtml(String username) {
        return """
            <!DOCTYPE html>
            <html>
            <head>
              <meta charset="UTF-8"/>
              <style>
                body { font-family: 'Segoe UI', Arial, sans-serif; background:#0f172a; margin:0; padding:0; }
                .wrapper { max-width:560px; margin:40px auto; background:#1e293b; border-radius:12px; overflow:hidden; }
                .header { background:linear-gradient(135deg,#38bdf8,#818cf8); padding:36px 32px; text-align:center; }
                .header h1 { color:#fff; margin:0; font-size:22px; font-weight:700; letter-spacing:0.5px; }
                .header p { color:rgba(255,255,255,0.85); margin:6px 0 0; font-size:13px; }
                .body { padding:32px; color:#cbd5e1; line-height:1.7; font-size:15px; }
                .body h2 { color:#f1f5f9; font-size:18px; margin-top:0; }
                .badge { display:inline-block; background:#064e3b; color:#34d399; border-radius:6px;
                         padding:4px 10px; font-size:12px; font-weight:600; margin-bottom:20px; }
                .footer { padding:20px 32px; text-align:center; color:#475569; font-size:12px;
                          border-top:1px solid #334155; }
              </style>
            </head>
            <body>
              <div class="wrapper">
                <div class="header">
                  <h1>ZeroTrust Forensics</h1>
                  <p>Blockchain Audit System</p>
                </div>
                <div class="body">
                  <span class="badge">✓ Account Activated</span>
                  <h2>Welcome, %s!</h2>
                  <p>Your account has been created successfully. You can now sign in and access the ZeroTrust Forensics platform.</p>
                  <p>If you did not create this account, please contact us immediately.</p>
                </div>
                <div class="footer">
                  ZeroTrust Forensics · Blockchain Audit System<br/>
                  This is an automated message, please do not reply.
                </div>
              </div>
            </body>
            </html>
            """.formatted(username);
    }

    private String buildRejectionHtml() {
        return """
            <!DOCTYPE html>
            <html>
            <head>
              <meta charset="UTF-8"/>
              <style>
                body { font-family: 'Segoe UI', Arial, sans-serif; background:#0f172a; margin:0; padding:0; }
                .wrapper { max-width:560px; margin:40px auto; background:#1e293b; border-radius:12px; overflow:hidden; }
                .header { background:linear-gradient(135deg,#38bdf8,#818cf8); padding:36px 32px; text-align:center; }
                .header h1 { color:#fff; margin:0; font-size:22px; font-weight:700; }
                .header p { color:rgba(255,255,255,0.85); margin:6px 0 0; font-size:13px; }
                .body { padding:32px; color:#cbd5e1; line-height:1.7; font-size:15px; }
                .body h2 { color:#f1f5f9; font-size:18px; margin-top:0; }
                .badge { display:inline-block; background:#4c1d1d; color:#f87171; border-radius:6px;
                         padding:4px 10px; font-size:12px; font-weight:600; margin-bottom:20px; }
                .footer { padding:20px 32px; text-align:center; color:#475569; font-size:12px;
                          border-top:1px solid #334155; }
              </style>
            </head>
            <body>
              <div class="wrapper">
                <div class="header">
                  <h1>ZeroTrust Forensics</h1>
                  <p>Blockchain Audit System</p>
                </div>
                <div class="body">
                  <span class="badge">Registration Update</span>
                  <h2>We could not complete your registration</h2>
                  <p>Unfortunately we were unable to complete your registration request at this time.</p>
                  <p>If you believe this is a mistake, please try again or contact our support team.</p>
                </div>
                <div class="footer">
                  ZeroTrust Forensics · Blockchain Audit System<br/>
                  This is an automated message, please do not reply.
                </div>
              </div>
            </body>
            </html>
            """;
    }

    private String buildOnHoldHtml() {
        return """
            <!DOCTYPE html>
            <html>
            <head>
              <meta charset="UTF-8"/>
              <style>
                body { font-family: 'Segoe UI', Arial, sans-serif; background:#0f172a; margin:0; padding:0; }
                .wrapper { max-width:560px; margin:40px auto; background:#1e293b; border-radius:12px; overflow:hidden; }
                .header { background:linear-gradient(135deg,#38bdf8,#818cf8); padding:36px 32px; text-align:center; }
                .header h1 { color:#fff; margin:0; font-size:22px; font-weight:700; }
                .header p { color:rgba(255,255,255,0.85); margin:6px 0 0; font-size:13px; }
                .body { padding:32px; color:#cbd5e1; line-height:1.7; font-size:15px; }
                .body h2 { color:#f1f5f9; font-size:18px; margin-top:0; }
                .badge { display:inline-block; background:#451a03; color:#fb923c; border-radius:6px;
                         padding:4px 10px; font-size:12px; font-weight:600; margin-bottom:20px; }
                .footer { padding:20px 32px; text-align:center; color:#475569; font-size:12px;
                          border-top:1px solid #334155; }
              </style>
            </head>
            <body>
              <div class="wrapper">
                <div class="header">
                  <h1>ZeroTrust Forensics</h1>
                  <p>Blockchain Audit System</p>
                </div>
                <div class="body">
                  <span class="badge">⏳ Registration On Hold</span>
                  <h2>Your registration is on hold</h2>
                  <p>We received your registration request but encountered a temporary issue on our end while processing it.</p>
                  <p>Our team has been notified and is looking into it. You do not need to do anything — we will follow up with you shortly.</p>
                  <p>We apologise for the inconvenience.</p>
                </div>
                <div class="footer">
                  ZeroTrust Forensics · Blockchain Audit System<br/>
                  This is an automated message, please do not reply.
                </div>
              </div>
            </body>
            </html>
            """;
    }
}