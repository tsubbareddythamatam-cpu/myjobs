package com.job.config;

import java.util.ArrayList;
import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;

import lombok.Data;

@Data
@ConfigurationProperties(prefix = "jobsearch")
public class JobWebsiteProperties {
	private List<Website> listofwebsites = new ArrayList<>();

	@Data
	public static class Website {
		private String name;
		private String url;
	}
}
