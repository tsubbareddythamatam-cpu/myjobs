package com.job.config;

import java.util.ArrayList;
import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;

import lombok.Data;

@Data
@ConfigurationProperties(prefix = "jobsearch")
public class JobFeedProperties {
	private List<Feed> feeds = new ArrayList<>();

	@Data
	public static class Feed {
		private String name;
		private String type;
		private String url;
	}
}
