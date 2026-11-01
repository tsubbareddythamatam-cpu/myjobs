package com.job.service;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.mail.MailException;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.job.dto.AuthMessageResponse;
import com.job.dto.AuthRequest;
import com.job.dto.AuthResponse;
import com.job.dto.EmailRequest;
import com.job.dto.ResetPasswordRequest;
import com.job.model.AppUser;
import com.job.model.AuthActionPurpose;
import com.job.model.AuthActionToken;
import com.job.repo.AppUserRepo;
import com.job.repo.AuthActionTokenRepo;

@Service
public class AuthService {
	private static final Logger LOGGER = LoggerFactory.getLogger(AuthService.class);
	private static final String INVALID_ACTION_LINK = "This link is invalid, expired, or has already been used.";
	private static final String VERIFICATION_MESSAGE =
			"If the account can be verified, a verification email will be sent shortly.";
	private static final String RESET_MESSAGE =
			"If an account exists for that email, a password reset email will be sent shortly.";

	private final AppUserRepo userRepo;
	private final AuthActionTokenRepo actionTokenRepo;
	private final org.springframework.security.crypto.password.PasswordEncoder passwordEncoder;
	private final AuthenticationManager authenticationManager;
	private final JwtEncoder jwtEncoder;
	private final JwtDecoder actionTokenJwtDecoder;
	private final AuthEmailService authEmailService;
	private final long tokenTtlSeconds;
	private final long emailVerificationTtlSeconds;
	private final long passwordResetTtlSeconds;

	public AuthService(AppUserRepo userRepo, AuthActionTokenRepo actionTokenRepo,
			org.springframework.security.crypto.password.PasswordEncoder passwordEncoder,
			AuthenticationManager authenticationManager, JwtEncoder jwtEncoder,
			@Qualifier("actionTokenJwtDecoder") JwtDecoder actionTokenJwtDecoder,
			AuthEmailService authEmailService,
			@Value("${jobsearch.jwt.ttl-seconds:3600}") long tokenTtlSeconds,
			@Value("${jobsearch.auth.email-verification-ttl-seconds:86400}") long emailVerificationTtlSeconds,
			@Value("${jobsearch.auth.password-reset-ttl-seconds:1800}") long passwordResetTtlSeconds) {
		this.userRepo = userRepo;
		this.actionTokenRepo = actionTokenRepo;
		this.passwordEncoder = passwordEncoder;
		this.authenticationManager = authenticationManager;
		this.jwtEncoder = jwtEncoder;
		this.actionTokenJwtDecoder = actionTokenJwtDecoder;
		this.authEmailService = authEmailService;
		if (tokenTtlSeconds <= 0 || emailVerificationTtlSeconds <= 0 || passwordResetTtlSeconds <= 0) {
			throw new IllegalStateException("Authentication token lifetimes must be greater than zero.");
		}
		this.tokenTtlSeconds = tokenTtlSeconds;
		this.emailVerificationTtlSeconds = emailVerificationTtlSeconds;
		this.passwordResetTtlSeconds = passwordResetTtlSeconds;
	}

	@Transactional
	public AuthMessageResponse register(AuthRequest request) {
		String email = normalizeEmail(request.getEmail());
		validatePasswordLength(request.getPassword());
		if (userRepo.existsByEmailIgnoreCase(email)) {
			throw new IllegalArgumentException("An account with this email already exists.");
		}
		AppUser user = new AppUser();
		user.setId(UUID.randomUUID().toString());
		user.setEmail(email);
		user.setPasswordHash(passwordEncoder.encode(request.getPassword()));
		user.setCreatedAt(LocalDateTime.now());
		user.setEmailVerified(false);
		try {
			user = userRepo.saveAndFlush(user);
		} catch (DataIntegrityViolationException exception) {
			throw new IllegalArgumentException("An account with this email already exists.");
		}
		String token = createActionToken(user, AuthActionPurpose.EMAIL_VERIFICATION,
				emailVerificationTtlSeconds);
		if (sendVerificationEmail(user, token)) {
			return new AuthMessageResponse("Account created. Check your email to verify your account before signing in.");
		}
		return new AuthMessageResponse(
				"Account created, but the verification email could not be sent. Use the resend option to try again.");
	}

	public AuthResponse login(AuthRequest request) {
		String email = normalizeEmail(request.getEmail());
		Authentication authentication = authenticationManager.authenticate(
				UsernamePasswordAuthenticationToken.unauthenticated(email, request.getPassword()));
		String userId = authentication.getName();
		AppUser user = userRepo.findById(userId)
				.orElseThrow(() -> new IllegalStateException("Authenticated account could not be loaded."));
		if (!user.isEmailVerified()) {
			throw new IllegalArgumentException("Verify your email before signing in. You can resend the verification email.");
		}
		return createToken(user);
	}

	@Transactional
	public AuthMessageResponse resendVerificationEmail(EmailRequest request) {
		userRepo.findByEmailIgnoreCase(normalizeEmail(request.email()))
				.filter(user -> !user.isEmailVerified())
				.ifPresent(user -> sendVerificationEmail(user,
						createActionToken(user, AuthActionPurpose.EMAIL_VERIFICATION, emailVerificationTtlSeconds)));
		return new AuthMessageResponse(VERIFICATION_MESSAGE);
	}

	@Transactional
	public AuthMessageResponse requestPasswordReset(EmailRequest request) {
		userRepo.findByEmailIgnoreCase(normalizeEmail(request.email()))
				.filter(AppUser::isEmailVerified)
				.ifPresent(user -> {
					String token = createActionToken(user, AuthActionPurpose.PASSWORD_RESET, passwordResetTtlSeconds);
					try {
						authEmailService.sendPasswordResetEmail(user, token);
					} catch (MailException | IllegalStateException exception) {
						LOGGER.error("Failed to send password reset email for userId={}", user.getId(), exception);
					}
				});
		return new AuthMessageResponse(RESET_MESSAGE);
	}

	@Transactional
	public AuthMessageResponse verifyEmail(String token) {
		Jwt actionJwt = decodeActionToken(token, AuthActionPurpose.EMAIL_VERIFICATION);
		AuthActionToken actionToken = loadUnusedActionToken(actionJwt, AuthActionPurpose.EMAIL_VERIFICATION);
		AppUser user = userRepo.findById(actionToken.getUserId())
				.orElseThrow(() -> new IllegalArgumentException(INVALID_ACTION_LINK));
		user.setEmailVerified(true);
		userRepo.save(user);
		consumeActionToken(actionToken);
		return new AuthMessageResponse("Email verified. You can now sign in.");
	}

	@Transactional
	public AuthMessageResponse resetPassword(ResetPasswordRequest request) {
		validatePasswordLength(request.newPassword());
		Jwt actionJwt = decodeActionToken(request.token(), AuthActionPurpose.PASSWORD_RESET);
		AuthActionToken actionToken = loadUnusedActionToken(actionJwt, AuthActionPurpose.PASSWORD_RESET);
		AppUser user = userRepo.findById(actionToken.getUserId())
				.orElseThrow(() -> new IllegalArgumentException(INVALID_ACTION_LINK));
		user.setPasswordHash(passwordEncoder.encode(request.newPassword()));
		userRepo.save(user);
		consumeActionToken(actionToken);
		invalidateUnusedActionTokens(user.getId(), AuthActionPurpose.PASSWORD_RESET);
		return new AuthMessageResponse("Your password has been reset. You can now sign in.");
	}

	private boolean sendVerificationEmail(AppUser user, String token) {
		try {
			authEmailService.sendVerificationEmail(user, token);
			return true;
		} catch (MailException | IllegalStateException exception) {
			LOGGER.error("Failed to send verification email for userId={}", user.getId(), exception);
			return false;
		}
	}

	private String createActionToken(AppUser user, AuthActionPurpose purpose, long ttlSeconds) {
		invalidateUnusedActionTokens(user.getId(), purpose);
		Instant issuedAt = Instant.now();
		Instant expiresAt = issuedAt.plusSeconds(ttlSeconds);
		String tokenId = UUID.randomUUID().toString();

		AuthActionToken actionToken = new AuthActionToken();
		actionToken.setId(tokenId);
		actionToken.setUserId(user.getId());
		actionToken.setPurpose(purpose);
		actionToken.setExpiresAt(LocalDateTime.ofInstant(expiresAt, ZoneOffset.UTC));
		actionTokenRepo.save(actionToken);

		JwtClaimsSet claims = JwtClaimsSet.builder()
				.issuer("myjobs-api")
				.issuedAt(issuedAt)
				.expiresAt(expiresAt)
				.subject(user.getId())
				.id(tokenId)
				.claim("token_use", "action")
				.claim("purpose", purpose.name())
				.build();
		JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
		return jwtEncoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
	}

	private Jwt decodeActionToken(String token, AuthActionPurpose expectedPurpose) {
		try {
			Jwt jwt = actionTokenJwtDecoder.decode(token);
			if (!"myjobs-api".equals(jwt.getClaims().get("iss"))
					|| !"action".equals(jwt.getClaimAsString("token_use"))
					|| !expectedPurpose.name().equals(jwt.getClaimAsString("purpose"))
					|| jwt.getId() == null || jwt.getSubject() == null) {
				throw new IllegalArgumentException(INVALID_ACTION_LINK);
			}
			return jwt;
		} catch (JwtException exception) {
			throw new IllegalArgumentException(INVALID_ACTION_LINK);
		}
	}

	private AuthActionToken loadUnusedActionToken(Jwt jwt, AuthActionPurpose expectedPurpose) {
		AuthActionToken actionToken = actionTokenRepo.findByIdForUpdate(jwt.getId())
				.orElseThrow(() -> new IllegalArgumentException(INVALID_ACTION_LINK));
		if (!jwt.getSubject().equals(actionToken.getUserId())
				|| actionToken.getPurpose() != expectedPurpose
				|| actionToken.getConsumedAt() != null
				|| !actionToken.getExpiresAt().isAfter(LocalDateTime.now(ZoneOffset.UTC))) {
			throw new IllegalArgumentException(INVALID_ACTION_LINK);
		}
		return actionToken;
	}

	private void consumeActionToken(AuthActionToken actionToken) {
		actionToken.setConsumedAt(LocalDateTime.now(ZoneOffset.UTC));
		actionTokenRepo.save(actionToken);
	}

	private void invalidateUnusedActionTokens(String userId, AuthActionPurpose purpose) {
		LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
		actionTokenRepo.findAllByUserIdAndPurposeAndConsumedAtIsNull(userId, purpose)
				.forEach(token -> token.setConsumedAt(now));
	}

	private AuthResponse createToken(AppUser user) {
		Instant issuedAt = Instant.now();
		JwtClaimsSet claims = JwtClaimsSet.builder()
				.issuer("myjobs-api")
				.issuedAt(issuedAt)
				.expiresAt(issuedAt.plusSeconds(tokenTtlSeconds))
				.subject(user.getId())
				.claim("email", user.getEmail())
				.claim("token_use", "access")
				.build();
		JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
		return new AuthResponse(jwtEncoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue(),
				"Bearer", tokenTtlSeconds, user.getEmail());
	}

	private static void validatePasswordLength(String password) {
		if (password.getBytes(StandardCharsets.UTF_8).length > 72) {
			throw new IllegalArgumentException("Password must not exceed 72 UTF-8 bytes.");
		}
	}

	private static String normalizeEmail(String email) {
		return email.trim().toLowerCase(java.util.Locale.ROOT);
	}
}
