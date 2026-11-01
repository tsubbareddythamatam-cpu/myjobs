package com.job.controller;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.job.config.JobWebsiteProperties;
import com.job.config.JobWebsiteProperties.Website;
import com.job.config.JobSourceDirectory;
import com.job.config.JobSourceDirectory.Source;

@RestController
@RequestMapping("/api/websites")
public class JobWebsiteController {
	private final JobWebsiteProperties websiteProperties;
	private final JobSourceDirectory sourceDirectory;

	public JobWebsiteController(JobWebsiteProperties websiteProperties, JobSourceDirectory sourceDirectory) {
		this.websiteProperties = websiteProperties;
		this.sourceDirectory = sourceDirectory;
	}

	@GetMapping
	public List<Website> getWebsites() {
		return websiteProperties.getListofwebsites();
	}

	@GetMapping("/catalog")
	public List<Source> getSourceCatalog() {
		return sourceDirectory.getSources();
	}
}
