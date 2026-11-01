package com.job.dto;

public record AuthResponse(String accessToken, String tokenType, long expiresIn, String email) {
}
