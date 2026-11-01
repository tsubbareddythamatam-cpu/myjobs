package com.job.service;

import java.util.List;
import java.util.Locale;
import java.util.Optional;

final class SkillRelationMatcher {
	private static final List<SkillFamily> SKILL_FAMILIES = List.of(
			new SkillFamily("CI/CD", List.of("Jenkins", "GitHub Actions", "GitLab CI",
					"GitLab CI/CD", "CircleCI", "TeamCity", "Bamboo", "Buildkite",
					"CI/CD", "continuous integration", "continuous delivery",
					"continuous deployment")),
			new SkillFamily("Java", List.of("Java", "Java 8", "Java 11", "Java 17",
					"Java 21", "Java 25")),
			new SkillFamily("JPA and Hibernate", List.of("JPA", "Hibernate",
					"Spring Data JPA")));

	private SkillRelationMatcher() {
	}

	static Optional<String> findRelatedEvidence(String resumeSkill, String jobText) {
		String resumeFamily = findFamily(resumeSkill);
		if (resumeFamily == null || jobText == null || jobText.isBlank()) {
			return Optional.empty();
		}
		for (SkillFamily family : SKILL_FAMILIES) {
			if (!family.name().equals(resumeFamily)) {
				continue;
			}
			for (String keyword : family.keywords()) {
				if (!sameSkill(resumeSkill, keyword) && containsTerm(jobText, keyword)) {
					return Optional.of(keyword);
				}
			}
		}
		return Optional.empty();
	}

	static boolean areRelated(String resumeSkill, String jobKeyword) {
		String resumeFamily = findFamily(resumeSkill);
		return resumeFamily != null && resumeFamily.equals(findFamily(jobKeyword))
				&& !sameSkill(resumeSkill, jobKeyword);
	}

	private static String findFamily(String value) {
		if (value == null || value.isBlank()) {
			return null;
		}
		for (SkillFamily family : SKILL_FAMILIES) {
			if (family.keywords().stream().anyMatch(keyword -> containsTerm(value, keyword))) {
				return family.name();
			}
		}
		return null;
	}

	private static boolean sameSkill(String left, String right) {
		return normalize(left).equals(normalize(right));
	}

	private static boolean containsTerm(String text, String term) {
		String normalizedText = " " + normalize(text) + " ";
		String normalizedTerm = normalize(term);
		return !normalizedTerm.isEmpty() && normalizedText.contains(" " + normalizedTerm + " ");
	}

	private static String normalize(String value) {
		return value.toLowerCase(Locale.ROOT).replaceAll("[^\\p{Alnum}]+", " ")
				.trim().replaceAll("\\s+", " ");
	}

	private record SkillFamily(String name, List<String> keywords) {
	}
}
