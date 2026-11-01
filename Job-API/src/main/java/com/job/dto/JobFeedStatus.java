package com.job.dto;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;

@Getter
public class JobFeedStatus {
	private final String source;
	private final boolean available;
	private final int count;
	private final String message;

	@JsonCreator
	public JobFeedStatus(@JsonProperty("source") String source,
			@JsonProperty("available") boolean available,
			@JsonProperty("count") int count, @JsonProperty("message") String message) {
		this.source = source;
		this.available = available;
		this.count = count;
		this.message = message;
	}
}
