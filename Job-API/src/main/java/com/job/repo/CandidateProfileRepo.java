package com.job.repo;

import org.springframework.data.jpa.repository.JpaRepository;

import com.job.model.CandidateProfile;

public interface CandidateProfileRepo extends JpaRepository<CandidateProfile, String> {
}
