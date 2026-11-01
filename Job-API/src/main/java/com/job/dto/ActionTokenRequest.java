package com.job.dto;

import jakarta.validation.constraints.NotBlank;

public record ActionTokenRequest(@NotBlank String token) {
}
