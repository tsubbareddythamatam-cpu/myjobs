package com.job.model;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;

import lombok.Data;

@Entity
@Table(name = "candidate_profile")
@Data
public class CandidateProfile {
	@Id
	private String id;
	private String fullName;
	private String targetRole;
	private String preferredLocation;
	private double experienceYears;
	@Lob
	private String resumeText;
	@Lob
	private byte[] resumeFile;
	private String resumeFileName;
	private String resumeContentType;
	@Column(length = 1024)
	private String resumeStoragePath;
	@Lob
	private String skills;
	private LocalDateTime updatedAt;
}
