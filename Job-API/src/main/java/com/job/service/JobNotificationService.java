package com.job.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

import com.job.dto.NotificationResult;
import com.job.model.JobApplication;

@Service
public class JobNotificationService {
	private static final Logger LOGGER = LoggerFactory.getLogger(JobNotificationService.class);

	private final JavaMailSender mailSender;
	private final boolean enabled;
	private final String host;
	private final String username;
	private final String password;
	private final String recipient;

	public JobNotificationService(JavaMailSender mailSender,
			@Value("${jobsearch.notifications.enabled:false}") boolean enabled,
			@Value("${spring.mail.host:}") String host,
			@Value("${spring.mail.username:}") String username,
			@Value("${spring.mail.password:}") String password,
			@Value("${jobsearch.notifications.recipient:}") String recipient) {
		this.mailSender = mailSender;
		this.enabled = enabled;
		this.host = host;
		this.username = username;
		this.password = password;
		this.recipient = recipient;
	}

	public NotificationResult notifyApplication(JobApplication application, String action) {
		if (!enabled) {
			return new NotificationResult(false, false, null);
		}
		if (host.trim().isEmpty() || username.trim().isEmpty() || password.isEmpty()
				|| recipient.trim().isEmpty()) {
			return new NotificationResult(true, false,
					"Email notification not sent. Configure MAIL_HOST, MAIL_USERNAME, MAIL_PASSWORD, and JOB_NOTIFICATION_RECIPIENT.");
		}

		SimpleMailMessage message = new SimpleMailMessage();
		message.setFrom(username);
		message.setTo(recipient);
		message.setSubject("MyJobs-API: " + action + " — " + application.getTitle());
		message.setText(String.format(
				"MyJobs-API application tracker update%n%nAction: %s%nJob: %s%nCompany: %s%nSource: %s%nStatus: %s%nJob link: %s%nUpdated: %s%n",
				action, application.getTitle(), valueOr(application.getCompany(), "Not provided"),
				valueOr(application.getSource(), "Not provided"), application.getStatus(),
				valueOr(application.getJobUrl(), "Not provided"), application.getUpdatedAt()));
		try {
			mailSender.send(message);
			return new NotificationResult(true, true, null);
		} catch (MailException exception) {
			LOGGER.warn("MyJobs-API email notification could not be delivered.", exception);
			return new NotificationResult(true, false,
					"Job saved, but the email notification could not be delivered. Check SMTP settings and server logs.");
		}
	}

	public NotificationResult sendTest() {
		JobApplication testApplication = new JobApplication();
		testApplication.setTitle("SMTP configuration test");
		testApplication.setCompany("MyJobs-API");
		testApplication.setSource("Configuration check");
		testApplication.setStatus("TEST");
		testApplication.setUpdatedAt(java.time.LocalDateTime.now());
		return notifyApplication(testApplication, "Email test");
	}

	private static String valueOr(String value, String fallback) {
		return value == null || value.trim().isEmpty() ? fallback : value;
	}
}
