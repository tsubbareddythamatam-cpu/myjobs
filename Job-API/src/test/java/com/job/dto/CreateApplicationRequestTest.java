package com.job.dto;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import jakarta.validation.Validation;

import org.junit.jupiter.api.Test;

class CreateApplicationRequestTest {
	@Test
	void descriptionIsRequiredForTrackedJobs() {
		CreateApplicationRequest request = new CreateApplicationRequest();
		request.setTitle("Software Engineer");

		try (var validatorFactory = Validation.buildDefaultValidatorFactory()) {
			assertTrue(validatorFactory.getValidator().validate(request).stream()
					.anyMatch(violation -> violation.getPropertyPath().toString().equals("description")));

			request.setDescription("Build and maintain backend services.");
			assertFalse(validatorFactory.getValidator().validate(request).stream()
					.anyMatch(violation -> violation.getPropertyPath().toString().equals("description")));
		}
	}
}
