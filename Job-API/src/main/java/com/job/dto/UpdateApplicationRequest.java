package com.job.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import lombok.Data;

@Data
public class UpdateApplicationRequest {
	@NotBlank
	private String status;

	@Size(max = 4000)
	private String notes;
}
