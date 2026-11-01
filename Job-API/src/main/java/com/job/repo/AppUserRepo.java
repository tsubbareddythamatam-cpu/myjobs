package com.job.repo;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.job.model.AppUser;

public interface AppUserRepo extends JpaRepository<AppUser, String> {
	Optional<AppUser> findByEmailIgnoreCase(String email);

	boolean existsByEmailIgnoreCase(String email);
}
