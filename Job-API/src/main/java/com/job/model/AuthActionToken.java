package com.job.model;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "auth_action_token")
@Getter
@Setter
public class AuthActionToken {
	@Id
	@Column(length = 36)
	private String id;

	@Column(nullable = false, length = 36)
	private String userId;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 32)
	private AuthActionPurpose purpose;

	@Column(nullable = false)
	private LocalDateTime expiresAt;

	private LocalDateTime consumedAt;
}
