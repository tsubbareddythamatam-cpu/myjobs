package com.job.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.charset.StandardCharsets;
import java.time.Instant;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.JwsHeader;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

class SecurityConfigurationTest {
	@Test
	void actionTokensCannotAuthenticateToProtectedApiEndpoints() {
		SecretKey secretKey = new SecretKeySpec(
				"test-signing-key-must-be-at-least-32-bytes".getBytes(StandardCharsets.UTF_8), "HmacSHA256");
		SecurityConfiguration configuration = new SecurityConfiguration();
		JwtEncoder encoder = new NimbusJwtEncoder(new ImmutableSecret<>(secretKey));
		Instant now = Instant.now();
		String token = encoder.encode(JwtEncoderParameters.from(
				JwsHeader.with(MacAlgorithm.HS256).build(),
				JwtClaimsSet.builder()
						.issuer("myjobs-api")
						.issuedAt(now)
						.expiresAt(now.plusSeconds(300))
						.subject("user-1")
						.id("action-1")
						.claim("token_use", "action")
						.claim("purpose", "EMAIL_VERIFICATION")
						.build())).getTokenValue();

		assertEquals("action", configuration.actionTokenJwtDecoder(secretKey)
				.decode(token).getClaimAsString("token_use"));
		assertThrows(JwtException.class, () -> configuration.jwtDecoder(secretKey).decode(token));
	}
}
