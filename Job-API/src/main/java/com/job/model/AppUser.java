package com.job.model;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "app_user")
@Getter
@Setter
public class AppUser {
	@Id
	private String id;

	@Column(nullable = false, unique = true, length = 254)
	private String email;

	@Column(nullable = false)
	private String passwordHash;

	@Column(nullable = false)
	private LocalDateTime createdAt;

	@Column(nullable = false, columnDefinition = "boolean default true")
	private boolean emailVerified = true;
}
