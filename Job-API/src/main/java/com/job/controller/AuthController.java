package com.job.controller;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.job.dto.AuthRequest;
import com.job.dto.AuthMessageResponse;
import com.job.dto.AuthResponse;
import com.job.dto.ActionTokenRequest;
import com.job.dto.EmailRequest;
import com.job.dto.ResetPasswordRequest;
import com.job.service.AuthService;

@RestController
@RequestMapping("/api/auth")
public class AuthController {
	private final AuthService authService;

	public AuthController(AuthService authService) {
		this.authService = authService;
	}

	@PostMapping("/register")
	public ResponseEntity<AuthMessageResponse> register(@Valid @RequestBody AuthRequest request) {
		return ResponseEntity.status(HttpStatus.CREATED).body(authService.register(request));
	}

	@PostMapping("/login")
	public ResponseEntity<?> login(@Valid @RequestBody AuthRequest request) {
		try {
			return ResponseEntity.ok(authService.login(request));
		} catch (AuthenticationException exception) {
			return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
					.body(new AuthMessageResponse("Invalid email or password."));
		}
	}

	@PostMapping("/verify-email")
	public AuthMessageResponse verifyEmail(@Valid @RequestBody ActionTokenRequest request) {
		return authService.verifyEmail(request.token());
	}

	@PostMapping("/resend-verification")
	public AuthMessageResponse resendVerification(@Valid @RequestBody EmailRequest request) {
		return authService.resendVerificationEmail(request);
	}

	@PostMapping("/forgot-password")
	public AuthMessageResponse requestPasswordReset(@Valid @RequestBody EmailRequest request) {
		return authService.requestPasswordReset(request);
	}

	@PostMapping("/reset-password")
	public AuthMessageResponse resetPassword(@Valid @RequestBody ResetPasswordRequest request) {
		return authService.resetPassword(request);
	}
}
