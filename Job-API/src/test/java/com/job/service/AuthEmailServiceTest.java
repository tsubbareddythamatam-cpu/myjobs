package com.job.service;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Properties;

import jakarta.mail.Multipart;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mail.javamail.JavaMailSender;

import com.job.model.AppUser;

class AuthEmailServiceTest {
	@Test
	void missingSmtpPasswordFailsWithConfigurationErrorBeforeSending() {
		JavaMailSender mailSender = mock(JavaMailSender.class);
		AuthEmailService emailService = new AuthEmailService(mailSender, "sender@example.com", "",
				"https://jobs.example.com");
		AppUser user = new AppUser();
		user.setEmail("candidate@example.com");

		IllegalStateException exception = assertThrows(IllegalStateException.class,
				() -> emailService.sendVerificationEmail(user, "signed-token"));

		assertTrue(exception.getMessage().contains("MAIL_PASSWORD"));
		verify(mailSender, never()).send(any(jakarta.mail.internet.MimeMessage.class));
	}

	@Test
	void verificationEmailIncludesClickableButtonWithActionLink() throws Exception {
		JavaMailSender mailSender = mock(JavaMailSender.class);
		when(mailSender.createMimeMessage()).thenReturn(new MimeMessage(Session.getInstance(new Properties())));
		AuthEmailService emailService = new AuthEmailService(mailSender, "sender@example.com", "test-app-password",
				"https://jobs.example.com");
		AppUser user = new AppUser();
		user.setEmail("candidate@example.com");

		emailService.sendVerificationEmail(user, "signed-token");

		ArgumentCaptor<MimeMessage> messageCaptor = ArgumentCaptor.forClass(MimeMessage.class);
		verify(mailSender).send(messageCaptor.capture());
		String html = findHtmlContent(messageCaptor.getValue().getContent());
		assertTrue(html.contains("href=\"https://jobs.example.com/?verifyToken=signed-token\""));
		assertTrue(html.contains(">Verify email</a>"));
	}

	@Test
	void passwordResetEmailIncludesClickableButtonWithResetLink() throws Exception {
		JavaMailSender mailSender = mock(JavaMailSender.class);
		when(mailSender.createMimeMessage()).thenReturn(new MimeMessage(Session.getInstance(new Properties())));
		AuthEmailService emailService = new AuthEmailService(mailSender, "sender@example.com", "test-app-password",
				"https://jobs.example.com");
		AppUser user = new AppUser();
		user.setEmail("candidate@example.com");

		emailService.sendPasswordResetEmail(user, "signed-token");

		ArgumentCaptor<MimeMessage> messageCaptor = ArgumentCaptor.forClass(MimeMessage.class);
		verify(mailSender).send(messageCaptor.capture());
		String html = findHtmlContent(messageCaptor.getValue().getContent());
		assertTrue(html.contains("href=\"https://jobs.example.com/?resetToken=signed-token\""));
		assertTrue(html.contains(">Reset password</a>"));
	}

	private static String findHtmlContent(Object content) throws Exception {
		if (content instanceof String text && text.toLowerCase().contains("<!doctype html>")) {
			return text;
		}
		if (content instanceof Multipart multipart) {
			for (int index = 0; index < multipart.getCount(); index++) {
				String match = findHtmlContent(multipart.getBodyPart(index).getContent());
				if (match != null) {
					return match;
				}
			}
		}
		return null;
	}
}
