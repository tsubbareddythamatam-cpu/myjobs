package com.job.dto;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;

@Getter
public class JobSearchResponse {
	private final String candidateRole;
	private final String candidateLocation;
	private final String fetchedAt;
	private final List<JobPosting> jobs;
	private final List<JobFeedStatus> feeds;

	@JsonCreator
	public JobSearchResponse(@JsonProperty("candidateRole") String candidateRole,
			@JsonProperty("candidateLocation") String candidateLocation,
			@JsonProperty("fetchedAt") String fetchedAt,
			@JsonProperty("jobs") List<JobPosting> jobs,
			@JsonProperty("feeds") List<JobFeedStatus> feeds) {
		this.candidateRole = candidateRole;
		this.candidateLocation = candidateLocation;
		this.fetchedAt = fetchedAt;
		this.jobs = jobs;
		this.feeds = feeds;
	}
}
