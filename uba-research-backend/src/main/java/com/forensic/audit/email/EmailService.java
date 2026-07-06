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

    private static final String DASHBOARD_URL = "http://localhost:4200/dashboard";

    private static final String TO_EMAIL = "mithil.baria@postgrad.manchester.ac.uk";

    // -----------------------------------------------------------------------
    // Public API — called from VaeRequestConsumer after state transitions
    // -----------------------------------------------------------------------

    /**
     * VAE_ACCEPTED — user registered or logged in successfully.
     * Register: warm welcome, account created.
     * Login: welcome back with dashboard link.
     */
    @Async
    public void sendWelcomeEmail(String toEmail, String username, String requestType) {
        if ("LOGIN".equals(requestType)) {
            send(toEmail,
                    "ZeroTrust Forensics — Login Successful",
                    buildLoginSuccessHtml(username));
        } else {
            send(toEmail,
                    "Welcome to ZeroTrust Forensics",
                    buildWelcomeHtml(username));
        }
    }

    /**
     * Backwards-compatible overload — defaults to REGISTER behaviour.
     */
    @Async
    public void sendWelcomeEmail(String toEmail, String username) {
        sendWelcomeEmail(toEmail, username, "REGISTER");
    }

    /**
     * VAE_REJECTED — bot detected.
     * Deliberately vague — do not tell the user why they were rejected.
     * Avoids giving an attacker feedback to tune their bot.
     */
    @Async
    public void sendRejectionEmail(String toEmail) {
        send(toEmail,
                "ZeroTrust Forensics — Registration Update",
                buildRejectionHtml());
    }

    /**
     * VAE_REVIEW / DEAD_LETTER — borderline case or system error.
     * User is not at fault. Tell them registration is on hold.
     */
    @Async
    public void sendOnHoldEmail(String toEmail) {
        send(toEmail,
                "ZeroTrust Forensics — Registration On Hold",
                buildOnHoldHtml());
    }

    // -----------------------------------------------------------------------
    // Core send — reused by all templates
    // -----------------------------------------------------------------------

    private void send(String to, String subject, String htmlBody) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

            helper.setTo(TO_EMAIL);
            helper.setSubject(subject);
            helper.setText(htmlBody, true);
            helper.setFrom("noreply@zerotrustforensics.com");

            mailSender.send(message);
            log.info("[Email] Sent '{}' to {}", subject, to);

        } catch (Exception e) {
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

    private String buildLoginSuccessHtml(String username) {
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
                .btn { display:inline-block; margin-top:20px; padding:12px 28px;
                       background:linear-gradient(135deg,#38bdf8,#818cf8);
                       color:#fff; text-decoration:none; border-radius:8px;
                       font-weight:600; font-size:15px; }
                .warning { margin-top:24px; font-size:13px; color:#64748b; }
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
                  <span class="badge">✓ Login Verified</span>
                  <h2>Welcome back, %s!</h2>
                  <p>Your identity has been verified by our behavioural analysis system. Click below to access your dashboard.</p>
                  <a href="%s" class="btn">Go to Dashboard →</a>
                  <p class="warning">If you did not attempt to log in, please contact us immediately — your account may be compromised.</p>
                </div>
                <div class="footer">
                  ZeroTrust Forensics · Blockchain Audit System<br/>
                  This is an automated message, please do not reply.
                </div>
              </div>
            </body>
            </html>
            """.formatted(username, DASHBOARD_URL);
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


    @Async
    public void sendTamperAlertEmail(String adminEmail, String recordId,
                                     String currentHash, String ledgerHash) {
        send(adminEmail,
                "🚨 ZeroTrust Forensics — TAMPER ALERT DETECTED",
                buildTamperAlertHtml(recordId, currentHash, ledgerHash));
    }

    private String buildTamperAlertHtml(String recordId,
                                        String currentHash,
                                        String ledgerHash) {
        return """
        <!DOCTYPE html>
        <html>
        <head>
          <meta charset="UTF-8"/>
          <style>
            body { font-family: 'Segoe UI', Arial, sans-serif; background:#0f172a; margin:0; padding:0; }
            .wrapper { max-width:560px; margin:40px auto; background:#1e293b; border-radius:12px; overflow:hidden; }
            .header { background:linear-gradient(135deg,#dc2626,#991b1b); padding:36px 32px; text-align:center; }
            .header h1 { color:#fff; margin:0; font-size:22px; font-weight:700; }
            .header p { color:rgba(255,255,255,0.85); margin:6px 0 0; font-size:13px; }
            .body { padding:32px; color:#cbd5e1; line-height:1.7; font-size:15px; }
            .body h2 { color:#f87171; font-size:18px; margin-top:0; }
            .badge { display:inline-block; background:#4c1d1d; color:#f87171; border-radius:6px;
                     padding:4px 10px; font-size:12px; font-weight:600; margin-bottom:20px; }
            .hash-box { background:#0f172a; border-radius:8px; padding:16px;
                        font-family:monospace; font-size:11px; color:#94a3b8;
                        margin:12px 0; word-break:break-all; }
            .hash-box .label { color:#64748b; font-size:10px; margin-bottom:4px; }
            .hash-box .value { color:#f87171; }
            .hash-box .value--ledger { color:#34d399; }
            .footer { padding:20px 32px; text-align:center; color:#475569; font-size:12px;
                      border-top:1px solid #334155; }
          </style>
        </head>
        <body>
          <div class="wrapper">
            <div class="header">
              <h1>⚠ Tamper Alert</h1>
              <p>ZeroTrust Forensics — Blockchain Audit System</p>
            </div>
            <div class="body">
              <span class="badge">🚨 CRITICAL — Immediate Action Required</span>
              <h2>Database record has been tampered with</h2>
              <p>The ZeroTrust Forensics tamper detection system has identified a hash mismatch
                 between the current database record and the immutable Hyperledger Fabric ledger entry.
                 This indicates that a database record was modified after it was committed to the blockchain.</p>

              <div class="hash-box">
                <div class="label">RECORD ID</div>
                <div class="value">%s</div>
              </div>
              <div class="hash-box">
                <div class="label">CURRENT DATABASE HASH (modified)</div>
                <div class="value">%s</div>
              </div>
              <div class="hash-box">
                <div class="label">ORIGINAL FABRIC LEDGER HASH (immutable)</div>
                <div class="value value--ledger">%s</div>
              </div>

              <p>Immediate actions recommended:</p>
              <ol style="color:#94a3b8; padding-left:20px;">
                <li>Identify who made the database modification (check PostgreSQL audit logs)</li>
                <li>Determine what was changed by comparing the two hashes</li>
                <li>Restore the original record from the Fabric ledger if possible</li>
                <li>Review all records committed around the same time</li>
              </ol>
              <p style="color:#64748b; font-size:13px;">
                The Fabric ledger entry remains intact and serves as the authoritative record.
              </p>
            </div>
            <div class="footer">
              ZeroTrust Forensics · Blockchain Audit System<br/>
              This is an automated security alert — do not reply.
            </div>
          </div>
        </body>
        </html>
        """.formatted(recordId, currentHash, ledgerHash);
    }
}