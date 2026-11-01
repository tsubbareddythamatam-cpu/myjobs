package com.job.service;

import java.io.IOException;
import java.io.ByteArrayInputStream;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.apache.poi.xwpf.extractor.XWPFWordExtractor;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.job.model.CandidateProfile;
import com.job.repo.CandidateProfileRepo;

@Service
public class ResumeProfileService {
	private static final Logger LOGGER = LoggerFactory.getLogger(ResumeProfileService.class);
	private static final Pattern EXPERIENCE_PATTERN =
			Pattern.compile("(\\d+(?:\\.\\d+)?)\\+?\\s*(?:years?|yrs?)", Pattern.CASE_INSENSITIVE);
	private static final Pattern SKILLS_HEADING_PATTERN = Pattern.compile(
			"(?i)^\\s*(?:(?:technical|key|core|professional|it)\\s+)?(?:skills|competencies|proficiencies|expertise|technologies|technology\\s+stack|tools\\s+and\\s+technologies|areas\\s+of\\s+expertise)(?:\\s*(?:&|and)\\s*(?:expertise|competencies|technologies))?\\s*[:\\-]?\\s*(.*)$");
	private static final Pattern ROLE_LABEL_PATTERN = Pattern.compile(
			"(?i)^\\s*(?:target\\s+role|desired\\s+(?:role|position)|professional\\s+title|current\\s+title|job\\s+title)\\s*[:\\-]\\s*(.+)$");
	private static final Pattern LOCATION_LABEL_PATTERN = Pattern.compile(
			"(?i)^\\s*(?:location|based\\s+in|current\\s+location)\\s*[:\\-]\\s*(.{2,80})$");
	private static final Pattern NAME_LABEL_PATTERN = Pattern.compile(
			"(?i)^\\s*(?:full\\s+name|candidate(?:\\s+name)?|name)\\s*[:\\-]\\s*(.+)$");
	private static final Pattern NAME_LINE_PATTERN = Pattern.compile(
			"(?iu)^[\\p{L}][\\p{L}'’.-]*(?:\\s+[\\p{L}][\\p{L}'’.-]*){1,4}$");
	private static final Pattern RESUME_HEADING_PATTERN = Pattern.compile(
			"(?i)^(?:professional\\s+)?(?:summary|profile|objective|skills|technical\\s+skills|"
					+ "work\\s+experience|professional\\s+experience|experience|employment|education|"
					+ "certifications|projects?|references|achievements|career\\s+highlights|"
					+ "career\\s+objective|core\\s+competencies|key\\s+competencies|"
					+ "learning\\s+initiatives|publications|languages|interests)\\s*:?$");
	private static final Pattern ROLE_TITLE_PATTERN = Pattern.compile(
			"(?i)\\b(?:engineer|developer|technical\\s+lead|team\\s+lead|project\\s+manager|"
					+ "product\\s+manager|consultant|architect|analyst|designer|administrator|"
					+ "scientist|specialist|director|officer|coordinator|intern|executive)\\b");
	private static final Pattern ROLE_PHRASE_PATTERN = Pattern.compile(
			"(?i)\\b((?:(?:senior|lead|principal|staff|junior|associate|chief|head|"
					+ "sr\\.?|jr\\.?)\\s+)?(?:[\\p{L}][\\p{L}&/]*\\s+){0,2}"
					+ "(?:engineer|developer|designer|technical\\s+lead|team\\s+lead|"
					+ "project\\s+manager|product\\s+manager|consultant|architect|analyst|"
					+ "administrator|scientist|specialist|director|officer|coordinator|intern|"
					+ "executive))\\b");
	private static final Pattern ORGANIZATION_NAME_PATTERN = Pattern.compile(
			"(?i)\\b(?:pvt\\.?|private|ltd\\.?|limited|inc\\.?|incorporated|llc|corp\\.?|"
					+ "corporation|company|technologies|edutech|solutions|systems|services)\\b");
	private static final Pattern NON_NAME_LINE_PATTERN = Pattern.compile(
			"(?i)\\b(?:jan(?:uary)?|feb(?:ruary)?|mar(?:ch)?|apr(?:il)?|may|jun(?:e)?|"
					+ "jul(?:y)?|aug(?:ust)?|sep(?:tember)?|oct(?:ober)?|nov(?:ember)?|dec(?:ember)?)"
					+ "\\s+\\d{4}\\b|\\b\\d{4}\\b|@|https?://|www\\.|\\b(?:resume|curriculum\\s+vitae|"
					+ "software\\s+engineer|designer|developer|architect|consultant|manager|analyst|"
					+ "teamwork\\s+skills|leadership\\s+skills|portfolio)\\b");
	private static final Pattern SKILLS_END_HEADING_PATTERN = Pattern.compile(
			"(?i)(?:^|[,;|•·\\s])(?:professional\\s+)?(?:work\\s+experience|employment|work\\s+history|education|project\\s+details|projects|certifications|summary|profile|objective|achievements|publications|references)\\s*:?");
	private static final Pattern SKILL_SEPARATOR_PATTERN = Pattern.compile("[,;|•·]");
	private static final Pattern EMAIL_PATTERN = Pattern.compile("(?i)\\b[\\w.+-]+@[\\w.-]+\\.[a-z]{2,}\\b");
	private static final Pattern PHONE_NUMBER_PATTERN = Pattern.compile("(?<!\\p{Alnum})(?:\\+?\\d[\\d() .-]{8,}\\d)(?!\\p{Alnum})");
	private static final Pattern POSTAL_CODE_LINE_PATTERN = Pattern.compile("^\\s*\\d{6}\\s*$");
	private static final Pattern POSTAL_CODE_PATTERN = Pattern.compile("(?<!\\d)\\d{5,6}(?!\\d)");
	private static final Pattern PERSONAL_LABEL_PATTERN = Pattern.compile(
			"(?i)^\\s*(?:e-?mail|phone|mobile|contact|address|pin\\s*code|postal\\s*code)\\s*[:\\-]");
	private static final Pattern SOFT_SKILL_PATTERN = Pattern.compile(
			"(?i)\\b(?:teamwork|leadership|communication|interpersonal|time\\s+management|"
					+ "problem\\s+solving|adaptability|friendship|loyalty)\\s+skills?\\b");
	private static final Pattern ROLE_OR_PORTFOLIO_PATTERN = Pattern.compile(
			"(?i)\\b(?:designer|developer|engineer|manager|analyst|consultant|portfolio)\\b");
	private static final Pattern JAVA_VERSION_RANGE_PATTERN = Pattern.compile(
			"(?i)\\bjava\\s*\\d+(?:\\s*(?:to|/|,|and)\\s*(?:java\\s*)?\\d+)*");
	private static final Pattern REST_API_PATTERN = Pattern.compile("(?i)\\b(RESTful|REST)\\s+APIs?\\b");
	private static final Pattern MICROSERVICE_PATTERN = Pattern.compile("(?i)\\bmicroservices?\\b");
	private static final Pattern VERSION_NUMBER_PATTERN = Pattern.compile("\\d+");
	private static final Pattern PROSE_INDICATOR_PATTERN = Pattern.compile(
			"(?i)\\b(?:proven|track record|leading|complex|experience|utilizing|utilising|"
					+ "deployment|deployments|workflows|workflow|modernization|modernisation|"
					+ "refactoring|strong|familiarity|knowledge|expertise|developing|narratives|"
					+ "audiences|engage|ability|anyone|everyone|harmony|valued|workplace|"
					+ "empower|earn|respect|friendship|loyality|loyalty|portfolio|behance|link)\\b");
	private static final List<String> KNOWN_SKILLS = List.of(
			// --- Original Provided Skills ---
			"Adobe After Effects", "Adobe Illustrator", "Adobe InDesign", "Adobe Photoshop",
			"Adobe Premiere Pro", "Adobe Animate", "Microsoft Office", "MS Office",
			"Graphic Design", "Visual Design", "Motion Graphics", "Typography", "Figma",
			"Azure Kubernetes Service", "Google Cloud Storage", "Spring Data JPA", "GitHub Copilot",
			"IntelliJ IDEA", "Spring WebFlux", "Spring Security", "Spring Cloud", "Spring Batch",
			"Spring Boot", "Spring Core", "Spring MVC", "Pivotal Cloud Foundry",
			"Microservices Architecture", "Reactive Programming", "RESTful API Design",
			"Distributed Systems", "Prompt Engineering", "Generative AI", "CI/CD", "JavaScript",
			"PostgreSQL", "SonarQube", "Checkstyle", "Hibernate", "Mockito", "JaCoCo",
			"Jenkins", "J2EE", "JPA", "Groovy", "DevOps", "MySQL", "MongoDB", "Redis",
			"Maven", "Gradle", "Argo CD", "Veracode", "Splunk", "Grafana", "New Relic",
			"Eclipse", "Postman", "Swagger", "GitHub", "Java", "SQL", "Oracle", "SVN",
			"JUnit", "OOP", "Cloud", "Spring",

			// --- Expanded PHP & Python Ecosystem ---
			"PHP", "Python", "Laravel", "Symfony", "CodeIgniter", "Yii", "Zend Framework",
			"WordPress", "Drupal", "Magento", "Composer", "PHPUnit", "Twig", "Blade",
			"Django", "Flask", "FastAPI", "PyTest", "Poetry", "Pipenv", "Celery",
			"SQLAlchemy", "Alembic", "Django REST Framework", "Fabric", "Jinja2",
			"Asyncio", "Tornado", "BeautifulSoup", "Scrapy", "NumPy", "Pandas",

			// --- Deepened AI, Generative AI & Prompt Engineering ---
			"Advanced Prompt Engineering", "Few-Shot Prompting", "Chain-of-Thought Prompting",
			"Retrieval-Augmented Generation (RAG)", "Fine-Tuning", "Large Language Models (LLMs)",
			"OpenAI API", "Claude API", "LangChain", "LlamaIndex", "Hugging Face",
			"ChromaDB", "Milvus", "Pinecone", "Vector Databases", "Agentic AI",
			"AutoGPT", "CrewAI", "Microsoft Autogen", "Semantic Kernel", "Stable Diffusion",
			"Midjourney", "DALL-E", "Prompt Evaluation", "LLM Evaluation", "Ragas",
			"Prompt Flow", "Token Optimization", "AI Safety", "Guardrails AI", "NeMo Guardrails",
			"Machine Learning", "Deep Learning", "TensorFlow", "PyTorch", "Keras",
			"Scikit-Learn", "Natural Language Processing (NLP)", "Computer Vision",
			"Transformers", "BERT", "GPT Architecture", "MLOps", "MLflow", "Kubeflow",

			// --- Previously Added Software & Creative Skills ---
			"TypeScript", "Go (Golang)", "HTML5", "CSS3", "SASS", "Node.js", "React.js",
			"Angular", "Next.js", "Event-Driven Architecture", "Serverless Architecture",
			"Domain-Driven Design (DDD)", "AWS (Amazon Web Services)", "Microsoft Azure",
			"Google Cloud Platform (GCP)", "Docker", "Kubernetes (K8s)", "GitLab CI/CD",
			"Terraform", "Ansible", "Helm", "Apache Cassandra", "DynamoDB", "Elasticsearch",
			"Selenium", "Playwright", "TDD (Test-Driven Development)", "BDD (Cucumber)",
			"Prometheus", "ELK Stack", "Datadog", "Jira", "Confluence", "Bitbucket",
			"C++", "C#", "Rust", "Kotlin", "Scala", "GraphQL", "gRPC", "WebSockets",
			"Kafka", "RabbitMQ", "ActiveMQ", "Redux", "Vue.js", "Express.js", "Liquibase",
			"Flyway", "Neo4j", "Couchbase", "Amazon S3", "AWS Lambda", "Google Cloud Functions",
			"Azure Functions", "CircleCI", "GitHub Actions", "Podman", "OpenShift",
			"SonarCloud", "OWASP Top 10", "Wireshark", "JMeter", "Gatling", "Logback",
			"Log4j2", "AppDynamics", "Dynatrace", "VS Code", "NetBeans", "SoapUI",
			"Git", "GitLab", "Nexus", "Artifactory", "Lombok", "MapStruct", "RxJava",
			"Project Reactor", "OAuth2", "OIDC", "SAML", "JWT", "HashiCorp Vault",
			"Sketch", "Adobe XD", "Blender", "UI/UX Design", "Brand Identity Design",
			"Product Design", "Wireframing", "Prototyping", "User Research", "Google Workspace",
			"Microsoft Project", "Visio", "Scrum", "Kanban", "Sprint Planning",
			"Product Backlog Grooming", "Project Lifecycle Management", "Risk Management",
			"Business Analysis", "Requirements Gathering", "Technical Documentation",
			"Stakeholder Management", "Vendor Management", "Power BI", "Tableau",
			"User Interface Design", "User Experience Design", "Interaction Design",
			"Information Architecture", "Design Systems", "Design Tokens",
			"Storyboard Generation", "Video Editing", "Color Theory", "Layout Design",
			"Vector Illustration", "Digital Painting", "3D Rendering", "Cinema 4D",
			"CorelDraw", "Canva", "InVision", "Zeplin", "Miro", "Lucidchart", "Trello",
			"Asana", "Monday.com", "Basecamp", "Agile Coaching", "SAFe (Scaled Agile)",
			"Product Ownership", "Change Management", "Resource Allocation", "Budgeting",
			"Systems Analysis", "Functional Specifications", "UML Modeling", "Use Case Modeling",
			"Data Mapping", "Process Automation", "Cross-Functional Collaboration",
			"Public Speaking", "Technical Writing", "Client Onboarding",

			// --- Previously Added Hardware & Medical Skills ---
			"Hardware Architecture", "Embedded Systems", "Firmware Development", "FPGA Design",
			"VHDL", "Verilog", "Microcontrollers", "Arduino", "Raspberry Pi", "PCB Design",
			"Altium Designer", "Eagle PCB", "Circuit Design", "Signal Integrity", "oscilloscopes",
			"Logic Analyzers", "IoT Architecture", "ASIC Design", "RTOS (Real-Time Operating Systems)",
			"ARM Cortex", "I2C", "SPI", "UART", "CAN bus", "Modbus", "PLC Programming",
			"SCADA Systems", "Robotics Engineering", "Hardware-in-the-Loop (HIL) Testing",
			"System-on-Chip (SoC)", "RF Engineering", "Telecommunications Engineering",
			"Medical Coding", "ICD-10", "CPT Coding", "HCPCS", "Medical Transcription",
			"Electronic Health Records (EHR)", "Electronic Medical Records (EMR)", "Epic Systems",
			"Cerner", "HL7", "FHIR", "DICOM", "HIPAA Compliance", "Medical Report Analysis",
			"Clinical Data Management", "Bioinformatics", "Medical Terminology", "Clinical Copywriting",
			"Healthcare Informatics", "Patient Chart Auditing", "Laboratory Information Systems (LIS)",
			"PACS (Picture Archiving and Communication System)", "Clinical Workflow Optimization",
			"Medical Billing", "Pharmacovigilance", "FDA Regulations", "ISO 13485",
			"Good Clinical Practice (GCP)", "Medical Devices Engineering", "Biomedical Signal Processing"
	);
	private final CandidateProfileRepo profileRepo;
	private final Path resumeStorageDirectory;

	@Autowired
	public ResumeProfileService(CandidateProfileRepo profileRepo,
			@Value("${jobsearch.resume-storage-dir:./data/resumes}") String resumeStorageDirectory) {
		this.profileRepo = profileRepo;
		this.resumeStorageDirectory = Paths.get(resumeStorageDirectory).toAbsolutePath().normalize();
	}

	public CandidateProfile getProfile(String userId) {
		CandidateProfile profile = profileRepo.findById(userId)
				.orElseThrow(() -> new IllegalStateException("Upload a resume before matching jobs."));
		String extractedName = extractName(profile.getResumeText());
		boolean changed = false;
		if (extractedName != null && !Objects.equals(profile.getFullName(), extractedName)) {
			profile.setFullName(extractedName);
			changed = true;
		} else if (extractedName == null && profile.getFullName() != null
				&& (cleanNameCandidate(profile.getFullName()) == null
						|| ORGANIZATION_NAME_PATTERN.matcher(profile.getFullName()).find())) {
			profile.setFullName(null);
			changed = true;
		}
		String extractedRole = extractTargetRole(profile.getResumeText());
		if (extractedRole != null && !Objects.equals(profile.getTargetRole(), extractedRole)) {
			profile.setTargetRole(extractedRole);
			changed = true;
		}
		String extractedLocation = extractLocation(profile.getResumeText());
		if (extractedLocation != null
				&& !Objects.equals(profile.getPreferredLocation(), extractedLocation)) {
			profile.setPreferredLocation(extractedLocation);
			changed = true;
		}
		String extractedSkills = extractMatchingSkills(profile.getResumeText()).stream()
				.collect(Collectors.joining(","));
		if (!Objects.equals(profile.getSkills(), extractedSkills)) {
			profile.setSkills(extractedSkills);
			changed = true;
		}
		return changed ? profileRepo.save(profile) : profile;
	}

	@Deprecated
	public CandidateProfile getProfile() {
		return getProfile("default");
	}

	public List<CandidateProfile> getAllProfiles() {
		return profileRepo.findAll().stream()
				.filter(profile -> profile.getId() != null && !"default".equals(profile.getId()))
				.peek(profile -> profile.setSkills(extractMatchingSkills(profile.getResumeText()).stream()
						.collect(Collectors.joining(","))))
				.collect(Collectors.toList());
	}

	public boolean hasProfile(String userId) {
		return profileRepo.existsById(userId);
	}

	public CandidateProfile saveResume(String userId, MultipartFile file) throws IOException {
		if (file == null || file.isEmpty() || !isSupportedResume(file.getOriginalFilename())) {
			throw new IllegalArgumentException("Upload a non-empty .docx or .pdf resume.");
		}
		return saveResume(userId, file.getOriginalFilename(), file.getBytes());
	}

	@Deprecated
	public CandidateProfile saveResume(MultipartFile file) throws IOException {
		return saveResume("default", file);
	}

	@Deprecated
	public synchronized CandidateProfile saveResume(String filename, byte[] content) throws IOException {
		return saveResume("default", filename, content);
	}

	public synchronized CandidateProfile saveResume(String userId, String filename, byte[] content)
			throws IOException {
		if (!isSupportedResume(filename)
				|| content == null || content.length == 0 || userId == null || userId.isBlank()) {
			throw new IllegalArgumentException("Upload a non-empty .docx or .pdf resume.");
		}
		String text = extractResumeText(filename, content);
		if (text.isEmpty()) {
			throw new IllegalArgumentException("No readable text was found in the resume.");
		}
		CandidateProfile profile = profileRepo.findById(userId).orElseGet(CandidateProfile::new);
		Path previousResumePath = profile.getResumeStoragePath() == null
				? null : Paths.get(profile.getResumeStoragePath()).toAbsolutePath().normalize();
		boolean resumeChanged = !hasSameStoredResume(profile, content);
		Path newResumePath = resumeChanged ? storeResume(content, filename) : previousResumePath;
		profile.setId(userId);
		profile.setResumeText(text);
		profile.setResumeFile(null);
		profile.setResumeFileName(safeFilename(filename));
		profile.setResumeContentType(filename.toLowerCase(Locale.ROOT).endsWith(".pdf")
				? "application/pdf"
				: "application/vnd.openxmlformats-officedocument.wordprocessingml.document");
		profile.setResumeStoragePath(newResumePath.toString());
		profile.setFullName(extractName(text));
		profile.setTargetRole(extractTargetRole(text));
		profile.setPreferredLocation(extractLocation(text));
		profile.setExperienceYears(extractExperience(text));
		profile.setSkills(extractMatchingSkills(text).stream().collect(Collectors.joining(",")));
		profile.setUpdatedAt(java.time.LocalDateTime.now());
		try {
			CandidateProfile saved = profileRepo.save(profile);
			if (resumeChanged) {
				deletePreviousResume(previousResumePath, newResumePath);
			}
			return saved;
		} catch (RuntimeException exception) {
			if (resumeChanged) {
				try {
					Files.deleteIfExists(newResumePath);
				} catch (IOException cleanupException) {
					exception.addSuppressed(cleanupException);
				}
			}
			throw exception;
		}
	}

	private boolean hasSameStoredResume(CandidateProfile profile, byte[] content) throws IOException {
		String storedPath = profile.getResumeStoragePath();
		if (storedPath != null && !storedPath.trim().isEmpty()) {
			Path path = Paths.get(storedPath).toAbsolutePath().normalize();
			if (!Files.isRegularFile(path)) {
				throw new IOException("The previously stored resume file is missing.");
			}
			return Arrays.equals(content, Files.readAllBytes(path));
		}
		return false;
	}

	public byte[] readResume(CandidateProfile profile) throws IOException {
		String storedPath = profile.getResumeStoragePath();
		if (storedPath != null && !storedPath.trim().isEmpty()) {
			Path path = Paths.get(storedPath).toAbsolutePath().normalize();
			if (!Files.isRegularFile(path)) {
				throw new IOException("The locally stored resume file is missing.");
			}
			return Files.readAllBytes(path);
		}
		if (profile.getResumeFile() != null) {
			return profile.getResumeFile();
		}
		throw new IOException("No stored resume file is available.");
	}

	private Path storeResume(byte[] content, String filename) throws IOException {
		Files.createDirectories(resumeStorageDirectory);
		String suffix = filename.toLowerCase(Locale.ROOT).endsWith(".pdf") ? ".pdf" : ".docx";
		Path destination = resumeStorageDirectory.resolve(UUID.randomUUID() + suffix);
		Path temporary = Files.createTempFile(resumeStorageDirectory, "resume-", ".tmp");
		try {
			Files.write(temporary, content);
			try {
				Files.move(temporary, destination, StandardCopyOption.ATOMIC_MOVE);
			} catch (AtomicMoveNotSupportedException exception) {
				Files.move(temporary, destination);
			}
			return destination;
		} finally {
			Files.deleteIfExists(temporary);
		}
	}

	private void deletePreviousResume(Path previousPath, Path currentPath) {
		if (previousPath == null || previousPath.equals(currentPath)
				|| !previousPath.startsWith(resumeStorageDirectory)) {
			return;
		}
		try {
			Files.deleteIfExists(previousPath);
		} catch (IOException exception) {
			LOGGER.warn("Could not remove replaced local resume file '{}': {}",
					previousPath, exception.getMessage());
		}
	}

	static List<String> extractSkills(String text) {
		Map<String, String> found = new LinkedHashMap<>();
		if (text == null || text.trim().isEmpty()) {
			return new ArrayList<>();
		}
		Set<String> personalDetails = personalDetailLines(text);
		String candidateName = extractName(text);
		boolean inSkillsSection = false;
		for (String line : text.split("\\R")) {
			Matcher heading = SKILLS_HEADING_PATTERN.matcher(line);
			if (heading.matches()) {
				Matcher endHeading = SKILLS_END_HEADING_PATTERN.matcher(heading.group(1));
				if (endHeading.find()) {
					addSkillLine(found, heading.group(1).substring(0, endHeading.start()), personalDetails,
							candidateName);
					break;
				}
				inSkillsSection = true;
				addSkillLine(found, heading.group(1), personalDetails, candidateName);
				continue;
			}
			if (inSkillsSection) {
				Matcher endHeading = SKILLS_END_HEADING_PATTERN.matcher(line);
				if (endHeading.find()) {
					addSkillLine(found, line.substring(0, endHeading.start()), personalDetails, candidateName);
					break;
				}
				addSkillLine(found, line, personalDetails, candidateName);
			}
		}
		return new ArrayList<>(found.values());
	}

	static List<String> extractMatchingSkills(String text) {
		Map<String, String> found = new LinkedHashMap<>();
		extractSkills(text).forEach(skill -> addSkill(found, skill));
		if (text != null && !text.trim().isEmpty()) {
			addJavaVersions(found, text);
			addKnownSkills(found, text);
			Matcher restApiMatcher = REST_API_PATTERN.matcher(text);
			while (restApiMatcher.find()) {
				addSkill(found, "RESTful".equalsIgnoreCase(restApiMatcher.group(1))
						? "RESTful API Design" : "REST APIs");
			}
			if (MICROSERVICE_PATTERN.matcher(text).find()) {
				addSkill(found, "Microservices Architecture");
			}
			Set<String> personalDetails = personalDetailLines(text);
			String candidateName = normalizePersonalDetail(extractName(text));
			found.entrySet().removeIf(entry -> personalDetails.contains(normalizePersonalDetail(entry.getValue()))
					|| (!candidateName.isEmpty()
							&& normalizePersonalDetail(entry.getValue()).equals(candidateName)));
		}
		return new ArrayList<>(found.values());
	}

	static String extractMatchingContext(String text) {
		if (text == null || text.trim().isEmpty()) {
			return "";
		}
		return text.trim();
	}

	private static void addSkillLine(Map<String, String> found, String rawLine,
			Set<String> personalDetails, String candidateName) {
		if (rawLine == null || rawLine.trim().isEmpty()) {
			return;
		}
		String line = rawLine.replaceFirst("^\\s*[-*•▪◦]+\\s*", "").trim();
		if (isPersonalDetail(line, personalDetails, candidateName)) {
			return;
		}
		int categorySeparator = line.indexOf(':');
		if (categorySeparator >= 0 && categorySeparator < line.length() - 1) {
			line = line.substring(categorySeparator + 1).trim();
		}
		addJavaVersions(found, line);
		addKnownSkills(found, line);
		for (String candidate : SKILL_SEPARATOR_PATTERN.split(line)) {
			String skill = JAVA_VERSION_RANGE_PATTERN.matcher(candidate).replaceAll(" ")
					.replaceAll("\\([^)]*\\)", "").trim()
					.replaceAll("^[\\s:–-]+|[\\s.]+$", "");
			int wordCount = skill.isEmpty() ? 0 : skill.split("\\s+").length;
			if (skill.length() > 1 && skill.length() <= 80
					&& !isPersonalDetail(skill, personalDetails, candidateName)
					&& wordCount <= 5
					&& !PROSE_INDICATOR_PATTERN.matcher(skill).find()
					&& !SOFT_SKILL_PATTERN.matcher(skill).find()
					&& !ROLE_OR_PORTFOLIO_PATTERN.matcher(skill).find()
					&& !skill.matches("(?i).*(?:\\s+and\\s+|\\s+/\\s+).*")) {
				addSkill(found, skill);
			}
		}
	}

	private static Set<String> personalDetailLines(String text) {
		Set<String> personalDetails = new HashSet<>();
		List<String> lines = Arrays.stream(text.split("\\R"))
				.map(String::trim)
				.filter(line -> !line.isEmpty())
				.toList();
		for (int index = 0; index < lines.size(); index++) {
			String line = lines.get(index);
			if (EMAIL_PATTERN.matcher(line).find() || PHONE_NUMBER_PATTERN.matcher(line).find()
					|| PERSONAL_LABEL_PATTERN.matcher(line).find()) {
				personalDetails.add(normalizePersonalDetail(line));
			}
			if (POSTAL_CODE_LINE_PATTERN.matcher(line).matches()) {
				personalDetails.add(normalizePersonalDetail(line));
				for (int preceding = Math.max(0, index - 2); preceding < index; preceding++) {
					personalDetails.add(normalizePersonalDetail(lines.get(preceding)));
				}
			}
		}
		return personalDetails;
	}

	private static boolean isPersonalDetail(String value, Set<String> personalDetails, String candidateName) {
		String normalized = normalizePersonalDetail(value);
		return normalized.isEmpty()
				|| normalized.equals(normalizePersonalDetail(candidateName))
				|| personalDetails.contains(normalized)
				|| EMAIL_PATTERN.matcher(value).find()
				|| PHONE_NUMBER_PATTERN.matcher(value).find()
				|| PERSONAL_LABEL_PATTERN.matcher(value).find()
				|| POSTAL_CODE_LINE_PATTERN.matcher(value).matches();
	}

	private static String normalizePersonalDetail(String value) {
		return value == null ? "" : value.toLowerCase(Locale.ROOT).replaceAll("[^\\p{Alnum}]+", " ").trim();
	}

	private static void addJavaVersions(Map<String, String> found, String line) {
		Matcher rangeMatcher = JAVA_VERSION_RANGE_PATTERN.matcher(line);
		while (rangeMatcher.find()) {
			addSkill(found, "Java");
			Matcher versionMatcher = VERSION_NUMBER_PATTERN.matcher(rangeMatcher.group());
			while (versionMatcher.find()) {
				addSkill(found, "Java " + versionMatcher.group());
			}
		}
	}

	private static void addKnownSkills(Map<String, String> found, String line) {
		for (String skill : KNOWN_SKILLS) {

			String normalizedSkill = skill.replaceAll("[^\\p{Alnum}]", "").toLowerCase();

			String normalizedLine = line.replaceAll("[^\\p{Alnum}\\s]", "").toLowerCase();

			Pattern skillPattern = Pattern.compile(
					"(?<!\\p{Alnum})" +
							Pattern.quote(normalizedSkill) +
							"(?!\\p{Alnum})"
			);

			Matcher matcher = skillPattern.matcher(normalizedLine);

			if (matcher.find()) {
				addSkill(found, skill);
			}
		}
	}

	private static boolean isEmbeddedBaseSkill(String skill, String line, int start, int end) {
		if ("Spring".equalsIgnoreCase(skill)) {
			return line.substring(end).matches("(?i)^\\s+(?:Boot|Cloud|WebFlux|Batch|Core|MVC|Security)\\b.*");
		}
		return false;
	}

	private static void addSkill(Map<String, String> found, String skill) {
		String key = normalizeSkillKey(skill);
		if ("rest".equals(key) || "rest api".equals(key) || "rest apis".equals(key)) {
			found.putIfAbsent("rest apis", "REST APIs");
			return;
		}
		found.putIfAbsent(key, skill);
	}

	private static String normalizeSkillKey(String skill) {
		return skill.toLowerCase(Locale.ROOT).replaceAll("[^\\p{Alnum}]+", " ").trim();
	}

	static String extractName(String text) {
		List<String> lines = Arrays.stream(text.split("\\R"))
				.map(String::trim)
				.filter(line -> !line.isEmpty())
				.limit(200)
				.collect(Collectors.toList());
		for (int index = 0; index < lines.size(); index++) {
			String line = lines.get(index);
			Matcher labeledName = NAME_LABEL_PATTERN.matcher(line);
			if (labeledName.matches()) {
				String candidate = cleanNameCandidate(labeledName.group(1));
				if (candidate != null) {
					return candidate;
				}
			}
		}
		for (int index = 0; index < lines.size(); index++) {
			String candidate = cleanNameCandidate(lines.get(index));
			if (candidate != null && hasNearbyContactDetails(lines, index)) {
				return candidate;
			}
		}
		for (String line : lines.stream().limit(12).toList()) {
			String candidate = cleanNameCandidate(line);
			if (candidate != null) {
				return candidate;
			}
		}
		return null;
	}

	private static boolean hasNearbyContactDetails(List<String> lines, int candidateIndex) {
		for (int index = Math.max(0, candidateIndex - 3);
				index <= Math.min(lines.size() - 1, candidateIndex + 3); index++) {
			String line = lines.get(index);
			if (EMAIL_PATTERN.matcher(line).find()
					|| PHONE_NUMBER_PATTERN.matcher(line).find()
					|| POSTAL_CODE_PATTERN.matcher(line).find()
					|| LOCATION_LABEL_PATTERN.matcher(line).matches()) {
				return true;
			}
		}
		return false;
	}

	private static String cleanNameCandidate(String line) {
		String candidate = line.split("\\s*[|•·]\\s*", 2)[0].trim();
		if (RESUME_HEADING_PATTERN.matcher(candidate).matches()
				|| candidate.equalsIgnoreCase("candidate")
				|| ORGANIZATION_NAME_PATTERN.matcher(candidate).find()
				|| ROLE_TITLE_PATTERN.matcher(candidate).find()
				|| candidate.equals(candidate.toLowerCase(Locale.ROOT))
				|| NON_NAME_LINE_PATTERN.matcher(candidate).find()
				|| !NAME_LINE_PATTERN.matcher(candidate).matches()) {
			return null;
		}
		return candidate;
	}

	private static double extractExperience(String text) {
		Matcher matcher = EXPERIENCE_PATTERN.matcher(text);
		return matcher.find() ? Double.parseDouble(matcher.group(1)) : 0;
	}

	private static String extractTargetRole(String text) {
		for (String line : text.split("\\R")) {
			Matcher matcher = ROLE_LABEL_PATTERN.matcher(line);
			if (matcher.matches()) {
				String role = matcher.group(1).trim();
				Matcher phrase = ROLE_PHRASE_PATTERN.matcher(role);
				if ((role.length() > 80 || EXPERIENCE_PATTERN.matcher(role).find())
						&& phrase.find()) {
					return phrase.group(1).trim();
				}
				return role;
			}
		}
		List<String> headerLines = Arrays.stream(text.split("\\R"))
				.map(String::trim)
				.filter(line -> !line.isEmpty())
				.limit(8)
				.collect(Collectors.toList());
		for (String line : headerLines) {
			if (!EMAIL_PATTERN.matcher(line).find()
					&& !PHONE_NUMBER_PATTERN.matcher(line).find()
					&& !RESUME_HEADING_PATTERN.matcher(line).matches()
					&& ROLE_TITLE_PATTERN.matcher(line).find()) {
				Matcher phrase = ROLE_PHRASE_PATTERN.matcher(line);
				if ((line.length() > 80 || EXPERIENCE_PATTERN.matcher(line).find())
						&& phrase.find()) {
					return phrase.group(1).trim();
				}
				return line.split("\\s*[|•·]\\s*", 2)[0].trim();
			}
		}
		for (String line : text.split("\\R")) {
			Matcher phrase = ROLE_PHRASE_PATTERN.matcher(line);
			if (phrase.find() && (line.length() > 80 || EXPERIENCE_PATTERN.matcher(line).find())) {
				return phrase.group(1).trim();
			}
		}
		return null;
	}

	private static String extractLocation(String text) {
		for (String line : text.split("\\R")) {
			Matcher matcher = LOCATION_LABEL_PATTERN.matcher(line);
			if (matcher.matches()) {
				String location = matcher.group(1).trim();
				location = location.split("\\s*[|;]\\s*", 2)[0];
				location = location.replaceFirst(
						"(?i)\\s+(?:linkedin|e-?mail|phone|mobile|website|github)\\s*[:\\-]?.*$", "");
				location = location.replaceFirst("(?i)\\s+(?:https?://|www\\.).*$", "");
				return location.replaceAll("[,\\s]+$", "").trim();
			}
		}
		List<String> headerLines = Arrays.stream(text.split("\\R"))
				.map(String::trim)
				.filter(line -> !line.isEmpty())
				.limit(10)
				.collect(Collectors.toList());
		for (String line : headerLines) {
			if (!EMAIL_PATTERN.matcher(line).find() && !PHONE_NUMBER_PATTERN.matcher(line).find()) {
				continue;
			}
			for (String segment : line.split("\\s*[|•·;]\\s*")) {
				Matcher email = EMAIL_PATTERN.matcher(segment);
				if (email.find()) {
					segment = segment.substring(email.end());
				}
				segment = PHONE_NUMBER_PATTERN.matcher(segment).replaceAll(" ");
				segment = segment.replaceFirst(
						"(?i)^\\s*(?:e-?mail|phone|mobile|contact|linkedin|github|website)\\s*[:\\-]?\\s*", "");
				segment = segment.replaceFirst("(?i)\\s+(?:linkedin|github|website)\\s*[:\\-]?.*$", "");
				segment = segment.replaceFirst("(?i)\\s*(?:https?://|www\\.).*$", "")
						.replaceFirst("^[\\s:|,\\-]+", "").replaceAll("[,\\s]+$", "").trim();
				if (segment.length() >= 2 && segment.length() <= 80
						&& segment.matches(".*\\p{L}.*")
						&& !RESUME_HEADING_PATTERN.matcher(segment).matches()) {
					return segment;
				}
			}
		}
		for (String line : text.split("\\R")) {
			Matcher postalCode = POSTAL_CODE_PATTERN.matcher(line);
			if (!postalCode.find() || !line.contains(",")) {
				continue;
			}
			String address = postalCode.replaceAll("").replaceAll("[.\\s]+$", "").trim();
			String[] addressParts = address.split("\\s*,\\s*");
			String locality = addressParts[addressParts.length - 1].trim();
			if (locality.length() >= 2 && locality.length() <= 60
					&& locality.matches(".*\\p{L}.*")) {
				return locality;
			}
		}
		return null;
	}

	private static boolean isSupportedResume(String filename) {
		return filename != null && (filename.toLowerCase(Locale.ROOT).endsWith(".docx")
				|| filename.toLowerCase(Locale.ROOT).endsWith(".pdf"));
	}

	private static String extractResumeText(String filename, byte[] content) throws IOException {
		if (filename.toLowerCase(Locale.ROOT).endsWith(".pdf")) {
			try (PDDocument document = Loader.loadPDF(content)) {
				return new PDFTextStripper().getText(document).trim();
			}
		}
		try (XWPFDocument document = new XWPFDocument(new ByteArrayInputStream(content));
				XWPFWordExtractor extractor = new XWPFWordExtractor(document)) {
			return extractor.getText().trim();
		}
	}

	private static String safeFilename(String filename) {
		String normalized = filename.replace('\\', '/');
		return normalized.substring(normalized.lastIndexOf('/') + 1).replaceAll("[\\r\\n\"]", "");
	}
}
