package com.job.dto;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import com.job.model.CandidateProfile;

import lombok.Getter;

@Getter
public class CandidateProfileResponse {
	private final String fullName;
	private final String targetRole;
	private final String preferredLocation;
	private final double experienceYears;
	private final List<String> skills;
	private final String updatedAt;

	public CandidateProfileResponse(CandidateProfile profile) {
		this.fullName = profile.getFullName();
		this.targetRole = profile.getTargetRole();
		this.preferredLocation = profile.getPreferredLocation();
		this.experienceYears = profile.getExperienceYears();
		this.skills = profile.getSkills() == null || profile.getSkills().isEmpty()
				? Collections.emptyList()
				: Arrays.stream(profile.getSkills().split(","))
						.map(String::trim).filter(skill -> !skill.isEmpty()).collect(Collectors.toList());
		this.updatedAt = profile.getUpdatedAt() == null ? null : profile.getUpdatedAt().toString();
	}
}
