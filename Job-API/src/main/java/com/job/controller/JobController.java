package com.job.controller;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.job.dto.CandidateProfileResponse;
import com.job.model.CandidateProfile;
import com.job.service.JobFeedProvider;
import com.job.service.ResumeProfileService;

@RestController
@Validated
public class JobController {
	private final ResumeProfileService profileService;
	private final JobFeedProvider jobFeedProvider;

	public JobController(ResumeProfileService profileService, JobFeedProvider jobFeedProvider) {
		this.profileService = profileService;
		this.jobFeedProvider = jobFeedProvider;
	}

	@PostMapping("/api/profile/resume")
	public CandidateProfileResponse uploadResume(@AuthenticationPrincipal Jwt jwt,
			@RequestParam("file") MultipartFile file) throws IOException {
		CandidateProfile profile = profileService.saveResume(jwt.getSubject(), file);
		jobFeedProvider.invalidateCache(jwt.getSubject());
		return new CandidateProfileResponse(profile);
	}

	@GetMapping("/api/profile")
	public CandidateProfileResponse getProfile(@AuthenticationPrincipal Jwt jwt) {
		return new CandidateProfileResponse(profileService.getProfile(jwt.getSubject()));
	}

	@GetMapping("/api/profile/resume")
	public ResponseEntity<byte[]> downloadResume(@AuthenticationPrincipal Jwt jwt) throws IOException {
		CandidateProfile profile = profileService.getProfile(jwt.getSubject());
		return ResponseEntity.ok()
				.contentType(MediaType.parseMediaType(profile.getResumeContentType()))
				.header(HttpHeaders.CONTENT_DISPOSITION,
						ContentDisposition.attachment()
								.filename(profile.getResumeFileName(), StandardCharsets.UTF_8).build().toString())
				.body(profileService.readResume(profile));
	}
}
