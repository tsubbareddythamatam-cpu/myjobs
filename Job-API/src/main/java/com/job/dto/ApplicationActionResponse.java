package com.job.dto;

import com.job.model.JobApplication;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class ApplicationActionResponse {
	private final JobApplication application;
	private final boolean notificationEnabled;
	private final boolean notificationSent;
	private final String notificationMessage;
}
