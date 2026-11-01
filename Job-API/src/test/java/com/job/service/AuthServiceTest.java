package com.job.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

import com.job.dto.AuthRequest;
import com.job.dto.EmailRequest;
import com.job.dto.ResetPasswordRequest;
import com.job.model.AppUser;
import com.job.model.AuthActionPurpose;
import com.job.model.AuthActionToken;
import com.job.repo.AppUserRepo;
import com.job.repo.AuthActionTokenRepo;
import com.nimbusds.jose.jwk.source.ImmutableSecret;

class AuthServiceTest {
	private final AppUserRepo userRepo = mock(AppUserRepo.class);
	private final AuthActionTokenRepo actionTokenRepo = mock(AuthActionTokenRepo.class);
	private final AuthenticationManager authenticationManager = mock(AuthenticationManager.class);
	private final AuthEmailService emailService = mock(AuthEmailService.class);
	private final Map<String, AuthActionToken> actionTokens = new HashMap<>();
	private final AppUser user = new AppUser();
	private AuthService authService;

	@BeforeEach
	void setUp() {
		byte[] keyBytes = "test-signing-key-must-be-at-least-32-bytes".getBytes(StandardCharsets.UTF_8);
		SecretKey secretKey = new SecretKeySpec(keyBytes, "HmacSHA256");
		JwtEncoder jwtEncoder = new NimbusJwtEncoder(new ImmutableSecret<>(secretKey));
		JwtDecoder actionDecoder = NimbusJwtDecoder.withSecretKey(secretKey).build();
		authService = new AuthService(userRepo, actionTokenRepo, new BCryptPasswordEncoder(),
				authenticationManager, jwtEncoder, actionDecoder, emailService, 3600, 86400, 1800);

		user.setId("user-1");
		user.setEmail("candidate@example.com");
		user.setPasswordHash(new BCryptPasswordEncoder().encode("a-correct-password"));
		user.setCreatedAt(LocalDateTime.now());
		user.setEmailVerified(true);

		when(actionTokenRepo.save(any(AuthActionToken.class))).thenAnswer(invocation -> {
			AuthActionToken token = invocation.getArgument(0);
			actionTokens.put(token.getId(), token);
			return token;
		});
		when(actionTokenRepo.findByIdForUpdate(anyString())).thenAnswer(invocation ->
				Optional.ofNullable(actionTokens.get(invocation.getArgument(0))));
		when(actionTokenRepo.findAllByUserIdAndPurposeAndConsumedAtIsNull(anyString(), any()))
				.thenAnswer(invocation -> actionTokens.values().stream()
						.filter(token -> invocation.getArgument(0).equals(token.getUserId()))
						.filter(token -> invocation.getArgument(1) == token.getPurpose())
						.filter(token -> token.getConsumedAt() == null)
						.toList());
	}

	@Test
	void registrationCreatesUnverifiedAccountAndSendsVerificationLink() {
		when(userRepo.existsByEmailIgnoreCase("candidate@example.com")).thenReturn(false);
		AtomicReference<AppUser> registeredUser = new AtomicReference<>();
		when(userRepo.saveAndFlush(any(AppUser.class))).thenAnswer(invocation -> {
			AppUser savedUser = invocation.getArgument(0);
			registeredUser.set(savedUser);
			return savedUser;
		});
		AuthRequest request = new AuthRequest();
		request.setEmail(" Candidate@Example.com ");
		request.setPassword("a-correct-password");

		assertTrue(authService.register(request).message().contains("Check your email"));

		assertEquals("candidate@example.com", registeredUser.get().getEmail());
		assertFalse(registeredUser.get().isEmailVerified());
		assertEquals(AuthActionPurpose.EMAIL_VERIFICATION,
				actionTokens.values().iterator().next().getPurpose());
		verify(emailService).sendVerificationEmail(eq(registeredUser.get()), anyString());
	}

	@Test
	void verificationEnablesAccountAndConsumesLinkOnlyOnce() {
		when(userRepo.existsByEmailIgnoreCase(user.getEmail())).thenReturn(false);
		AtomicReference<AppUser> registeredUser = new AtomicReference<>();
		when(userRepo.saveAndFlush(any(AppUser.class))).thenAnswer(invocation -> {
			AppUser savedUser = invocation.getArgument(0);
			registeredUser.set(savedUser);
			return savedUser;
		});
		when(userRepo.findById(anyString())).thenAnswer(invocation ->
				Optional.ofNullable(registeredUser.get()));
		when(userRepo.save(any(AppUser.class))).thenAnswer(invocation -> invocation.getArgument(0));
		AuthRequest request = new AuthRequest();
		request.setEmail(user.getEmail());
		request.setPassword("a-correct-password");
		authService.register(request);
		ArgumentCaptor<String> tokenCaptor = ArgumentCaptor.forClass(String.class);
		verify(emailService).sendVerificationEmail(any(AppUser.class), tokenCaptor.capture());
		String rawToken = tokenCaptor.getValue();

		assertEquals("Email verified. You can now sign in.", authService.verifyEmail(rawToken).message());

		assertTrue(registeredUser.get().isEmailVerified());
		assertNotNull(actionTokens.values().iterator().next().getConsumedAt());
		assertThrows(IllegalArgumentException.class, () -> authService.verifyEmail(rawToken));
	}

	@Test
	void loginRejectsUnverifiedAccounts() {
		user.setEmailVerified(false);
		when(authenticationManager.authenticate(any())).thenReturn(
				UsernamePasswordAuthenticationToken.authenticated(user.getId(), null, List.of()));
		when(userRepo.findById(user.getId())).thenReturn(Optional.of(user));
		AuthRequest request = new AuthRequest();
		request.setEmail(user.getEmail());
		request.setPassword("a-correct-password");

		assertThrows(IllegalArgumentException.class, () -> authService.login(request));
	}

	@Test
	void passwordResetChangesPasswordAndConsumesLink() {
		when(userRepo.findByEmailIgnoreCase(user.getEmail())).thenReturn(Optional.of(user));
		when(userRepo.findById(user.getId())).thenReturn(Optional.of(user));
		when(userRepo.save(any(AppUser.class))).thenAnswer(invocation -> invocation.getArgument(0));
		authService.requestPasswordReset(new EmailRequest(user.getEmail()));
		AuthActionToken actionToken = actionTokens.values().stream()
				.filter(token -> token.getPurpose() == AuthActionPurpose.PASSWORD_RESET)
				.findFirst()
				.orElseThrow();
		ArgumentCaptor<String> tokenCaptor = ArgumentCaptor.forClass(String.class);
		verify(emailService).sendPasswordResetEmail(eq(user), tokenCaptor.capture());
		String rawToken = tokenCaptor.getValue();
		String oldHash = user.getPasswordHash();

		assertEquals("Your password has been reset. You can now sign in.",
				authService.resetPassword(new ResetPasswordRequest(rawToken, "a-new-correct-password")).message());

		assertNotEquals(oldHash, user.getPasswordHash());
		assertTrue(new BCryptPasswordEncoder().matches("a-new-correct-password", user.getPasswordHash()));
		assertNotNull(actionToken.getConsumedAt());
		assertThrows(IllegalArgumentException.class,
				() -> authService.resetPassword(new ResetPasswordRequest(rawToken, "another-new-password")));
	}

	@Test
	void passwordResetRequestDoesNotRevealWhetherAccountExists() {
		when(userRepo.findByEmailIgnoreCase(anyString())).thenReturn(Optional.empty());

		assertEquals(authService.requestPasswordReset(new EmailRequest(user.getEmail())).message(),
				authService.requestPasswordReset(new EmailRequest("missing@example.com")).message());
		verify(emailService, never()).sendPasswordResetEmail(any(AppUser.class), anyString());
	}
}
