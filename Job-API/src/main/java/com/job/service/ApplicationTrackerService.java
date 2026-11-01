package com.job.service;

import java.net.URI;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import org.springframework.stereotype.Service;

import com.job.dto.ApplicationActionResponse;
import com.job.dto.CreateApplicationRequest;
import com.job.dto.UpdateApplicationRequest;
import com.job.model.JobApplication;
import com.job.repo.JobApplicationRepo;

@Service
public class ApplicationTrackerService {
	private static final List<String> STATUSES =
			Arrays.asList("SAVED", "APPLIED", "INTERVIEW", "OFFER", "REJECTED", "WITHDRAWN");
	private final JobApplicationRepo applicationRepo;
	private final JobNotificationService notificationService;

	public ApplicationTrackerService(JobApplicationRepo applicationRepo, JobNotificationService notificationService) {
		this.applicationRepo = applicationRepo;
		this.notificationService = notificationService;
	}

	public List<JobApplication> getApplications(String ownerId) {
		return applicationRepo.findAllByOwnerIdOrderByUpdatedAtDesc(ownerId);
	}

	public ApplicationActionResponse create(String ownerId, CreateApplicationRequest request) {
		JobApplication application = new JobApplication();
		application.setId(UUID.randomUUID().toString());
		application.setOwnerId(ownerId);
		application.setJobId(trimToNull(request.getJobId()));
		application.setTitle(request.getTitle().trim());
		application.setCompany(trimToNull(request.getCompany()));
		application.setSource(trimToNull(request.getSource()));
		application.setJobUrl(validateUrl(request.getJobUrl()));
		application.setDescription(request.getDescription().trim());
		application.setStatus("SAVED");
		application.setNotes(trimToNull(request.getNotes()));
		LocalDateTime now = LocalDateTime.now();
		application.setCreatedAt(now);
		application.setUpdatedAt(now);
		JobApplication saved = applicationRepo.save(application);
		return response(saved, notificationService.notifyApplication(saved, "Job saved"));
	}

	public ApplicationActionResponse update(String ownerId, String id, UpdateApplicationRequest request) {
		JobApplication application = applicationRepo.findByIdAndOwnerId(id, ownerId)
				.orElseThrow(() -> new IllegalArgumentException("Application was not found."));
		String status = request.getStatus().trim().toUpperCase(Locale.ROOT);
		if (!STATUSES.contains(status)) {
			throw new IllegalArgumentException("Status must be one of: " + String.join(", ", STATUSES) + ".");
		}
		application.setStatus(status);
		application.setNotes(trimToNull(request.getNotes()));
		application.setUpdatedAt(LocalDateTime.now());
		JobApplication saved = applicationRepo.save(application);
		return response(saved, notificationService.notifyApplication(saved, "Application status updated"));
	}

	public void delete(String ownerId, String id) {
		if (!applicationRepo.existsByIdAndOwnerId(id, ownerId)) {
			throw new IllegalArgumentException("Application was not found.");
		}
		applicationRepo.deleteByIdAndOwnerId(id, ownerId);
	}

	private static String validateUrl(String value) {
		String url = trimToNull(value);
		if (url == null) {
			return null;
		}
		URI uri;
		try {
			uri = URI.create(url);
		} catch (IllegalArgumentException ex) {
			throw new IllegalArgumentException("Enter a valid http or https job link.");
		}
		String scheme = uri.getScheme();
		if (!"https".equalsIgnoreCase(scheme) && !"http".equalsIgnoreCase(scheme)) {
			throw new IllegalArgumentException("Job links must use http or https.");
		}
		if (uri.getHost() == null) {
			throw new IllegalArgumentException("Enter a valid http or https job link.");
		}
		return url;
	}

	private static String trimToNull(String value) {
		return value == null || value.trim().isEmpty() ? null : value.trim();
	}

	private static ApplicationActionResponse response(JobApplication application,
			com.job.dto.NotificationResult notification) {
		return new ApplicationActionResponse(application, notification.isEnabled(), notification.isSent(),
				notification.getMessage());
	}
}
