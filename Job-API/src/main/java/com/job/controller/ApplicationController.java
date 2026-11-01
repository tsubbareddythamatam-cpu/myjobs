package com.job.controller;

import java.util.List;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.job.dto.ApplicationActionResponse;
import com.job.dto.CreateApplicationRequest;
import com.job.dto.NotificationResult;
import com.job.dto.UpdateApplicationRequest;
import com.job.model.JobApplication;
import com.job.service.ApplicationTrackerService;
import com.job.service.JobNotificationService;

@RestController
@RequestMapping("/api/applications")
public class ApplicationController {
	private final ApplicationTrackerService trackerService;
	private final JobNotificationService notificationService;

	public ApplicationController(ApplicationTrackerService trackerService, JobNotificationService notificationService) {
		this.trackerService = trackerService;
		this.notificationService = notificationService;
	}

	@GetMapping
	public List<JobApplication> getApplications(@AuthenticationPrincipal Jwt jwt) {
		return trackerService.getApplications(jwt.getSubject());
	}

	@PostMapping
	public ResponseEntity<ApplicationActionResponse> create(@AuthenticationPrincipal Jwt jwt,
			@Valid @RequestBody CreateApplicationRequest request) {
		return ResponseEntity.status(HttpStatus.CREATED).body(trackerService.create(jwt.getSubject(), request));
	}

	@PatchMapping("/{id}")
	public ApplicationActionResponse update(@AuthenticationPrincipal Jwt jwt, @PathVariable String id,
			@Valid @RequestBody UpdateApplicationRequest request) {
		return trackerService.update(jwt.getSubject(), id, request);
	}

	@PostMapping("/test-email")
	public NotificationResult sendTestEmail() {
		return notificationService.sendTest();
	}

	@DeleteMapping("/{id}")
	public ResponseEntity<Void> delete(@AuthenticationPrincipal Jwt jwt, @PathVariable String id) {
		trackerService.delete(jwt.getSubject(), id);
		return ResponseEntity.noContent().build();
	}
}
