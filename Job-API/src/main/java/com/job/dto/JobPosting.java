package com.job.dto;

import java.util.List;
import java.util.ArrayList;
import java.util.Collections;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;

@Getter
public class JobPosting {
	private final String id;
	private final String title;
	private final String company;
	private final String location;
	private final String source;
	private final String url;
	private final String description;
	private final String postedAt;
	private final List<String> tags;
	private final int matchScore;
	private final List<String> matchedSkills;
	private final List<String> jobKeywords;
	private final List<String> missingSkills;
	private final String matchingMethod;
	private final List<RelatedSkillMatch> relatedSkillMatches;
	private final String matchExplanation;

	@JsonCreator
	public JobPosting(@JsonProperty("id") String id, @JsonProperty("title") String title,
			@JsonProperty("company") String company, @JsonProperty("location") String location,
			@JsonProperty("source") String source, @JsonProperty("url") String url,
			@JsonProperty("description") String description, @JsonProperty("postedAt") String postedAt,
			@JsonProperty("tags") List<String> tags, @JsonProperty("matchScore") int matchScore,
			@JsonProperty("matchedSkills") List<String> matchedSkills,
			@JsonProperty("jobKeywords") List<String> jobKeywords,
			@JsonProperty("missingSkills") List<String> missingSkills,
			@JsonProperty("matchingMethod") String matchingMethod,
			@JsonProperty("relatedSkillMatches") List<RelatedSkillMatch> relatedSkillMatches,
			@JsonProperty("matchExplanation") String matchExplanation) {
		this.id = id;
		this.title = title;
		this.company = company;
		this.location = location;
		this.source = source;
		this.url = url;
		this.description = description;
		this.postedAt = postedAt;
		this.tags = tags;
		this.matchScore = matchScore;
		this.matchedSkills = matchedSkills;
		this.jobKeywords = jobKeywords;
		this.missingSkills = missingSkills;
		this.matchingMethod = matchingMethod;
		this.relatedSkillMatches = relatedSkillMatches;
		this.matchExplanation = matchExplanation;
	}

	public JobPosting(String id, String title, String company, String location, String source,
			String url, String description, String postedAt, List<String> tags, int matchScore,
			List<String> matchedSkills) {
		this(id, title, company, location, source, url, description, postedAt, tags, matchScore,
				matchedSkills, tags == null ? Collections.emptyList() : new ArrayList<>(tags),
				Collections.emptyList(), "Exact skill matching",
				Collections.emptyList(),
				"Matched skills were found in the job title, description, or keywords.");
	}

	public JobPosting withAiSkillComparison(List<String> aiMatchedSkills, List<String> keywords,
			List<RelatedSkillMatch> relatedMatches, String explanation, int score) {
		List<String> coveredSkills = new ArrayList<>(aiMatchedSkills);
		relatedMatches.stream().map(RelatedSkillMatch::resumeSkill).forEach(coveredSkills::add);
		return new JobPosting(id, title, company, location, source, url, description, postedAt, tags,
				score, aiMatchedSkills, keywords, missingSkills.stream()
						.filter(skill -> coveredSkills.stream().noneMatch(skill::equalsIgnoreCase))
						.collect(java.util.stream.Collectors.toList()),
				relatedMatches.isEmpty() ? "AI-assisted with validated job evidence"
						: "AI-assisted direct and related skill matching",
				relatedMatches, explanation);
	}
}
