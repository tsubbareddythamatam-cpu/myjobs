package com.job.config;

import java.time.Duration;
import java.util.concurrent.Executor;

import org.springframework.boot.restclient.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.web.client.RestTemplate;

@Configuration
public class JobFeedConfiguration {
	@Bean
	public RestTemplate jobFeedRestTemplate(RestTemplateBuilder builder) {
		return builder
				.connectTimeout(Duration.ofSeconds(3))
				.readTimeout(Duration.ofSeconds(4))
				.additionalInterceptors((request, body, execution) -> {
					request.getHeaders().set(HttpHeaders.USER_AGENT, "MyJobs-API/1.0");
					return execution.execute(request, body);
				})
				.build();
	}

	@Bean
	public Executor jobFeedExecutor() {
		ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
		executor.setCorePoolSize(12);
		executor.setMaxPoolSize(12);
		executor.setQueueCapacity(256);
		executor.setThreadNamePrefix("job-feed-");
		executor.setRejectedExecutionHandler(new java.util.concurrent.ThreadPoolExecutor.CallerRunsPolicy());
		executor.initialize();
		return executor;
	}
}
