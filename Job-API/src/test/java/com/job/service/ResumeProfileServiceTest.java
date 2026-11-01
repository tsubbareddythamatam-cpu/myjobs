package com.job.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.Test;

import com.job.model.CandidateProfile;
import com.job.repo.CandidateProfileRepo;

class ResumeProfileServiceTest {
	@Test
	void getProfileFindsContactDetailsPlacedAfterResumeBody() {
		CandidateProfileRepo profileRepo = mock(CandidateProfileRepo.class);
		CandidateProfile profile = new CandidateProfile();
		profile.setId("candidate-contact-block");
		profile.setFullName("learning initiatives");
		profile.setPreferredLocation(null);
		profile.setResumeText("""
				July 2016 - May 2020, Oct 2020 - Jun 2025 Hyderabad
				March 2013 - July 2016 Hyderabad
				Professional Summary
				Creative and results-driven Lead Graphic Designer with 12+ years of experience.
				Led and mentored a cross-functional creative team across digital learning initiatives.
				email@example.com
				Bachupally, Hyderabad, 500090.
				Gangadhar Mundru
				+91 9876543210
				My Portfolio:
				Lead Graphic Designer
				""");
		when(profileRepo.findById("candidate-contact-block")).thenReturn(Optional.of(profile));
		when(profileRepo.save(profile)).thenReturn(profile);

		CandidateProfile result = new ResumeProfileService(profileRepo, "./data/resumes")
				.getProfile("candidate-contact-block");

		assertEquals("Gangadhar Mundru", result.getFullName());
		assertEquals("Hyderabad", result.getPreferredLocation());
		verify(profileRepo).save(profile);
	}

	@Test
	void getProfileRepairsCandidateNameAndHyderabadLocation() {
		CandidateProfileRepo profileRepo = mock(CandidateProfileRepo.class);
		CandidateProfile profile = new CandidateProfile();
		profile.setId("candidate-gm");
		profile.setFullName("learning initiatives");
		profile.setPreferredLocation("Not available");
		profile.setResumeText("""
				Gangadhar Mundru
				Senior Software Engineer
				Phone: +91 9876543210 | Email: gangadhar@example.com: Hyderabad, India | LinkedIn: www.linkedin.com/in/gangadhar
				PROFESSIONAL SUMMARY
				Experienced software professional with a strong technical background.
				""");
		when(profileRepo.findById("candidate-gm")).thenReturn(Optional.of(profile));
		when(profileRepo.save(profile)).thenReturn(profile);

		CandidateProfile result = new ResumeProfileService(profileRepo, "./data/resumes")
				.getProfile("candidate-gm");

		assertEquals("Gangadhar Mundru", result.getFullName());
		assertEquals("Hyderabad, India", result.getPreferredLocation());
		verify(profileRepo).save(profile);
	}

	@Test
	void getProfileReplacesSectionHeadingWithResumeCandidateName() {
		CandidateProfileRepo profileRepo = mock(CandidateProfileRepo.class);
		CandidateProfile profile = new CandidateProfile();
		profile.setId("candidate-4");
		profile.setFullName("learning initiatives");
		profile.setResumeText("""
				Asha Reddy
				Senior Program Manager
				PROFESSIONAL SUMMARY
				Learning initiatives focused on employee development and career growth.
				""");
		when(profileRepo.findById("candidate-4")).thenReturn(Optional.of(profile));
		when(profileRepo.save(profile)).thenReturn(profile);

		CandidateProfile result = new ResumeProfileService(profileRepo, "./data/resumes")
				.getProfile("candidate-4");

		assertEquals("Asha Reddy", result.getFullName());
		verify(profileRepo).save(profile);
	}

	@Test
	void getProfileDoesNotKeepAnOrganizationAsTheCandidateName() {
		CandidateProfileRepo profileRepo = mock(CandidateProfileRepo.class);
		CandidateProfile profile = new CandidateProfile();
		profile.setId("candidate-3");
		profile.setFullName("MediSys EduTech Pvt. Ltd.");
		profile.setResumeText("""
				MediSys EduTech Pvt. Ltd.
				PROFESSIONAL SUMMARY
				Creative and results-driven Lead Graphic Designer with 12+ years of experience.
				""");
		when(profileRepo.findById("candidate-3")).thenReturn(Optional.of(profile));
		when(profileRepo.save(profile)).thenReturn(profile);

		CandidateProfile result = new ResumeProfileService(profileRepo, "./data/resumes")
				.getProfile("candidate-3");

		assertNull(result.getFullName());
		assertEquals("Lead Graphic Designer", result.getTargetRole());
		verify(profileRepo).save(profile);
	}

	@Test
	void getProfileReplacesOrganizationAndSummaryWithCandidateDetails() {
		CandidateProfileRepo profileRepo = mock(CandidateProfileRepo.class);
		CandidateProfile profile = new CandidateProfile();
		profile.setId("candidate-2");
		profile.setFullName("MediSys EduTech Pvt. Ltd.");
		profile.setTargetRole("Creative and results-driven Lead Graphic Designer with 12+ years of experience");
		profile.setResumeText("""
				Asha Reddy
				MediSys EduTech Pvt. Ltd.
				PROFESSIONAL SUMMARY
				Creative and results-driven Lead Graphic Designer with 12+ years of experience in print, digital, and interactive design.
				Phone: +1 555 123 4567 | Email: asha@example.com: Hyderabad, India | LinkedIn: www.linkedin.com/in/asha
				""");
		when(profileRepo.findById("candidate-2")).thenReturn(Optional.of(profile));
		when(profileRepo.save(profile)).thenReturn(profile);

		CandidateProfile result = new ResumeProfileService(profileRepo, "./data/resumes")
				.getProfile("candidate-2");

		assertEquals("Asha Reddy", result.getFullName());
		assertEquals("Lead Graphic Designer", result.getTargetRole());
		assertEquals("Hyderabad, India", result.getPreferredLocation());
		verify(profileRepo).save(profile);
	}

	@Test
	void getProfileRepairsNameRoleAndLocationFromResumeHeader() {
		CandidateProfileRepo profileRepo = mock(CandidateProfileRepo.class);
		CandidateProfile profile = new CandidateProfile();
		profile.setId("candidate-1");
		profile.setFullName("Professional Summary");
		profile.setResumeText("""
				Subba Reddy T
				Senior Java Backend Developer / Technical Lead
				subba@example.com | Hyderabad, India | +91 9876543210
				Professional Summary
				Backend developer with extensive experience building Java applications.
				""");
		when(profileRepo.findById("candidate-1")).thenReturn(Optional.of(profile));
		when(profileRepo.save(profile)).thenReturn(profile);

		CandidateProfile result = new ResumeProfileService(profileRepo, "./data/resumes")
				.getProfile("candidate-1");

		assertEquals("Subba Reddy T", result.getFullName());
		assertEquals("Senior Java Backend Developer / Technical Lead", result.getTargetRole());
		assertEquals("Hyderabad, India", result.getPreferredLocation());
		verify(profileRepo).save(profile);
	}
}
