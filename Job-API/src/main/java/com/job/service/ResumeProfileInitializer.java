package com.job.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

@Component
public class ResumeProfileInitializer implements CommandLineRunner {
	private static final Logger LOGGER = LoggerFactory.getLogger(ResumeProfileInitializer.class);
	private final String resumePath;

	public ResumeProfileInitializer(@Value("${jobsearch.resume-path:}") String resumePath) {
		this.resumePath = resumePath;
	}

	@Override
	public void run(String... args) {
		if (!resumePath.isBlank()) {
			LOGGER.warn("Ignoring JOB_RESUME_PATH because resumes are now owned by authenticated accounts; "
					+ "upload the resume after signing in.");
		}
	}
}
