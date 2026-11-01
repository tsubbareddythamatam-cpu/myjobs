package com.job.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import lombok.Data;

@Data
public class CreateApplicationRequest {
	private String jobId;

	@NotBlank
	@Size(max = 255)
	private String title;

	@Size(max = 255)
	private String company;

	@Size(max = 100)
	private String source;

	@Size(max = 2048)
	private String jobUrl;

	@NotBlank
	@Size(max = 4000)
	private String description;

	@Size(max = 4000)
	private String notes;
}
