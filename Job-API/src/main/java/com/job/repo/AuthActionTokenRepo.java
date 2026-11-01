package com.job.repo;

import java.util.List;
import java.util.Optional;

import jakarta.persistence.LockModeType;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.job.model.AuthActionPurpose;
import com.job.model.AuthActionToken;

public interface AuthActionTokenRepo extends JpaRepository<AuthActionToken, String> {
	List<AuthActionToken> findAllByUserIdAndPurposeAndConsumedAtIsNull(String userId, AuthActionPurpose purpose);

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select token from AuthActionToken token where token.id = :id")
	Optional<AuthActionToken> findByIdForUpdate(@Param("id") String id);
}
