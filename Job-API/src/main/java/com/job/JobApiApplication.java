package com.job;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableScheduling;

import com.job.config.JobFeedProperties;
import com.job.config.JobWebsiteProperties;

@SpringBootApplication
@EnableScheduling
@EnableConfigurationProperties({JobWebsiteProperties.class, JobFeedProperties.class})
public class JobApiApplication {

	public static void main(String[] args) {
		SpringApplication.run(JobApiApplication.class, args);
	}

}
