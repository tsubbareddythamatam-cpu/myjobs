package com.job.service;

import java.nio.charset.StandardCharsets;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;
import org.springframework.web.util.UriComponentsBuilder;

import com.job.model.AppUser;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;

@Service
public class AuthEmailService {
	private final JavaMailSender mailSender;
	private final String mailFrom;
	private final String mailPassword;
	private final String applicationUrl;

	public AuthEmailService(JavaMailSender mailSender,
			@Value("${spring.mail.username:}") String mailFrom,
			@Value("${spring.mail.password:}") String mailPassword,
			@Value("${jobsearch.app.base-url:http://localhost:8089}") String applicationUrl) {
		this.mailSender = mailSender;
		this.mailFrom = mailFrom;
		this.mailPassword = mailPassword;
		this.applicationUrl = applicationUrl;
	}

	public void sendVerificationEmail(AppUser user, String token) {
		String link = createActionLink("verifyToken", token);
		send(user.getEmail(), "Verify your MyJobs account",
				"Verify your email address to activate your MyJobs account.", "Verify email", link,
				"This single-use link expires automatically.");
	}

	public void sendPasswordResetEmail(AppUser user, String token) {
		String link = createActionLink("resetToken", token);
		send(user.getEmail(), "Reset your MyJobs password",
				"We received a request to reset your MyJobs password.", "Reset password", link,
				"This single-use link expires automatically. If you did not request a reset, you can ignore this email.");
	}

	private void send(String recipient, String subject, String introduction, String buttonText, String link,
			String footer) {
		if (mailFrom == null || mailFrom.isBlank()) {
			throw new IllegalStateException("Configure MAIL_USERNAME before sending authentication emails.");
		}
		if (mailPassword == null || mailPassword.isBlank()) {
			throw new IllegalStateException(
					"MAIL_PASSWORD is not configured for this application process. Set it in the launch environment and restart the app.");
		}
		try {
			MimeMessage message = mailSender.createMimeMessage();
			MimeMessageHelper helper = new MimeMessageHelper(message, true, StandardCharsets.UTF_8.name());
			helper.setFrom(mailFrom);
			helper.setTo(recipient);
			helper.setSubject(subject);
			helper.setText(introduction + "\n\n" + buttonText + ": " + link + "\n\n" + footer,
					"<!doctype html><html><body style=\"margin:0;padding:32px;background:#0b0f17;"
							+ "font-family:Arial,sans-serif;color:#cbd5e1\"><div style=\"max-width:560px;"
							+ "margin:0 auto;padding:32px;background:#0f172a;border:1px solid #1e293b;"
							+ "border-radius:16px\"><p style=\"margin:0 0 24px;color:#a5b4fc;font-size:12px;"
							+ "font-weight:bold;letter-spacing:2px;text-transform:uppercase\">MyJobs · SkillPulse</p>"
							+ "<h1 style=\"margin:0 0 12px;color:#f8fafc;font-size:24px\">" + subject + "</h1>"
							+ "<p style=\"margin:0 0 24px;color:#cbd5e1;font-size:15px;line-height:1.6\">"
							+ introduction + "</p><p style=\"margin:0 0 24px\"><a href=\"" + link
							+ "\" style=\"display:inline-block;padding:13px 22px;border-radius:10px;"
							+ "background:#4f46e5;color:#fff;font-size:14px;font-weight:bold;"
							+ "text-decoration:none\">" + buttonText + "</a></p><p style=\"margin:0;color:#94a3b8;"
							+ "font-size:12px;line-height:1.6\">" + footer + "</p></div></body></html>");
			mailSender.send(message);
		} catch (MessagingException exception) {
			throw new IllegalStateException("Could not create authentication email.", exception);
		}
	}

	private String createActionLink(String parameter, String token) {
		return UriComponentsBuilder.fromUriString(applicationUrl)
				.replacePath("/")
				.replaceQuery(null)
				.queryParam(parameter, token)
				.build()
				.encode(StandardCharsets.UTF_8)
				.toUriString();
	}
}
