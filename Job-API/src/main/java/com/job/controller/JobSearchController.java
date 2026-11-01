package com.job.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;

import com.job.dto.JobSearchResponse;
import com.job.service.JobFeedProvider;

@RestController
@RequestMapping("/api/jobs")
public class JobSearchController {
	private final JobFeedProvider jobFeedProvider;

	public JobSearchController(JobFeedProvider jobFeedProvider) {
		this.jobFeedProvider = jobFeedProvider;
	}

	@GetMapping
	public JobSearchResponse search(@AuthenticationPrincipal Jwt jwt,
			@RequestParam(defaultValue = "100") int limit,
			@RequestParam(defaultValue = "10") int minimumMatchScore,
			@RequestParam(defaultValue = "7") int postedWithinDays) {
		return jobFeedProvider.search(jwt.getSubject(), limit, minimumMatchScore, postedWithinDays);
	}
}
