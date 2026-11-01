package com.job.model;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import lombok.Data;

@Entity
@Table(name = "job_application")
@Data
public class JobApplication {
	@Id
	private String id;
	@Column(name = "owner_id", length = 36)
	private String ownerId;
	private String jobId;
	private String title;
	private String company;
	private String source;
	@Column(length = 2048)
	private String jobUrl;
	@Column(length = 4000)
	private String description;
	private String status;
	@Column(length = 4000)
	private String notes;
	private LocalDateTime createdAt;
	private LocalDateTime updatedAt;
}
