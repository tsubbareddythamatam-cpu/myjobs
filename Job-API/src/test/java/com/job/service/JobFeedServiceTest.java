package com.job.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestTemplate;

import tools.jackson.databind.ObjectMapper;
import com.job.config.JobFeedProperties;
import com.job.config.JobFeedProperties.Feed;
import com.job.dto.JobSearchResponse;
import com.job.model.CandidateProfile;

class JobFeedServiceTest {
	@Test
	void searchesPublicLeverFeedAndKeepsRecentIndiaListing() throws Exception {
		JobFeedProperties feedProperties = new JobFeedProperties();
		Feed feed = new Feed();
		feed.setName("Paytm");
		feed.setType("lever-json");
		feed.setUrl("https://api.lever.co/v0/postings/paytm?mode=json");
		feedProperties.setFeeds(List.of(feed));

		CandidateProfile profile = new CandidateProfile();
		profile.setTargetRole("Software Engineer");
		profile.setPreferredLocation("India");
		profile.setSkills("Java");
		profile.setUpdatedAt(LocalDateTime.now());

		ResumeProfileService profileService = mock(ResumeProfileService.class);
		when(profileService.getProfile()).thenReturn(profile);

		long createdAt = Instant.now().minusSeconds(60).toEpochMilli();
		String responseBody = """
				[{
				  "id": "job-1",
				  "text": "Software Engineer",
				  "categories": {
				    "location": "Bengaluru, India",
				    "team": "Engineering",
				    "commitment": "Full-time"
				  },
				  "createdAt": %d,
				  "hostedUrl": "https://jobs.lever.co/paytm/job-1",
				  "descriptionPlain": "Build Java services."
				}]
				""".formatted(createdAt);
		RestTemplate restTemplate = mock(RestTemplate.class);
		when(restTemplate.getForObject(anyString(), eq(String.class))).thenReturn(responseBody);

		JobFeedService service = new JobFeedService(
				feedProperties, profileService, restTemplate, new ObjectMapper());

		JobSearchResponse result = service.search("default", 10, 0, 7);

		assertEquals(1, result.getJobs().size());
		assertEquals("Paytm", result.getJobs().get(0).getSource());
		assertEquals("Bengaluru, India", result.getJobs().get(0).getLocation());
		assertEquals("https://jobs.lever.co/paytm/job-1", result.getJobs().get(0).getUrl());
		assertEquals(Instant.ofEpochMilli(createdAt).toString(), result.getJobs().get(0).getPostedAt());
		assertTrue(result.getFeeds().get(0).isAvailable());
		assertEquals(1, result.getFeeds().get(0).getCount());
	}
}
