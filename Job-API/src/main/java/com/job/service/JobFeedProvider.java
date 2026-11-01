package com.job.service;

import com.job.dto.JobSearchResponse;

public interface JobFeedProvider {
	JobSearchResponse search(String accountId, int requestedLimit, int requestedMinimumScore, int requestedDays);

	void invalidateCache(String accountId);
}
