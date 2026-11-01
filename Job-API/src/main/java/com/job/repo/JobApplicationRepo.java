package com.job.repo;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.job.model.JobApplication;

public interface JobApplicationRepo extends JpaRepository<JobApplication, String> {
	List<JobApplication> findAllByOwnerIdOrderByUpdatedAtDesc(String ownerId);

	java.util.Optional<JobApplication> findByIdAndOwnerId(String id, String ownerId);

	boolean existsByIdAndOwnerId(String id, String ownerId);

	void deleteByIdAndOwnerId(String id, String ownerId);
}
