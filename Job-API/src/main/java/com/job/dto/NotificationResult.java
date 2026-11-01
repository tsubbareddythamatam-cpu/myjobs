package com.job.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class NotificationResult {
	private final boolean enabled;
	private final boolean sent;
	private final String message;
}
