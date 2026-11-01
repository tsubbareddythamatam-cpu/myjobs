package com.job.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.BadCredentialsException;

import com.job.dto.AuthMessageResponse;
import com.job.dto.AuthRequest;
import com.job.service.AuthService;

class AuthControllerTest {
	@Test
	void invalidLoginReturnsClearUnauthorizedResponse() {
		AuthService authService = mock(AuthService.class);
		AuthController controller = new AuthController(authService);
		AuthRequest request = new AuthRequest();
		request.setEmail("candidate@example.com");
		request.setPassword("incorrect-password");
		when(authService.login(request)).thenThrow(new BadCredentialsException("Bad credentials"));

		ResponseEntity<?> response = controller.login(request);

		assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
		assertEquals("Invalid email or password.", ((AuthMessageResponse) response.getBody()).message());
	}
}
