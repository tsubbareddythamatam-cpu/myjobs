package com.job.service;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.Instant;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.Executor;
import java.util.Set;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.util.UriUtils;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.core.JacksonException;
import com.job.config.JobFeedProperties;
import com.job.config.JobFeedProperties.Feed;
import com.job.config.JobSourceDirectory;
import com.job.dto.JobFeedStatus;
import com.job.dto.JobPosting;
import com.job.dto.RelatedSkillMatch;
import com.job.dto.JobSearchResponse;
import com.job.model.CandidateProfile;

@Service
public class JobFeedService implements JobFeedProvider {
	private static final Logger LOGGER = LoggerFactory.getLogger(JobFeedService.class);
	private static final int MAX_RESULTS = 300;
	private static final int MAX_HIMALAYAS_PAGES = 5;
	private static final Pattern MINIMUM_EXPERIENCE_PATTERN = Pattern.compile(
			"(?i)(?:at least|minimum(?: of)?|more than|over)\\s+(\\d+(?:\\.\\d+)?)\\s*\\+?\\s*(?:years?|yrs?)");
	private static final Pattern EXPERIENCE_REQUIREMENT_PATTERN = Pattern.compile(
			"(?i)(\\d+(?:\\.\\d+)?)\\s*\\+?\\s*(?:years?|yrs?)\\s+(?:of\\s+)?(?:relevant\\s+)?experience");
	private static final Set<String> ALLOWED_FEED_HOSTS =
			new HashSet<>(Arrays.asList("remoteok.com", "www.remoteok.com", "weworkremotely.com",
					"www.weworkremotely.com", "workingnomads.com", "www.workingnomads.com",
					"jobicy.com", "www.jobicy.com", "remotive.com", "www.remotive.com",
					"himalayas.app", "www.himalayas.app", "arbeitnow.com", "www.arbeitnow.com",
					"freehire.me", "www.freehire.me", "boards-api.greenhouse.io", "api.lever.co",
					"api.ashbyhq.com", "apply.workable.com", "api.smartrecruiters.com",
					"ats.rippling.com", "www.themuse.com", "hn.algolia.com"));

	private final JobFeedProperties feedProperties;
	private final ResumeProfileService profileService;
	private final RestTemplate restTemplate;
	private final ObjectMapper objectMapper;
	private final ConcurrentMap<String, Object> accountCacheLocks = new ConcurrentHashMap<>();
	private StringRedisTemplate redisTemplate;
	private AiJobSkillMatcher aiJobSkillMatcher;
	private Executor feedExecutor = Runnable::run;
	private final Map<String, CachedSearch> cachedSearches = new ConcurrentHashMap<>();
	private final ConcurrentMap<String, BoardDiscovery> discoveredCompanyBoards = new ConcurrentHashMap<>();
	private JobSourceDirectory sourceDirectory = new JobSourceDirectory();
	private boolean additionalSourcesEnabled;
	@Value("${jobsearch.company-discovery.enabled:false}")
	private boolean companyDiscoveryEnabled;
	@Value("${ADZUNA_APP_ID:}")
	private String adzunaAppId;
	@Value("${ADZUNA_APP_KEY:}")
	private String adzunaAppKey;
	@Value("${JOOBLE_API_KEY:}")
	private String joobleApiKey;
	@Value("${CAREERJET_AFFILIATE_ID:}")
	private String careerjetAffiliateId;
	@Value("${FINDWORK_API_KEY:}")
	private String findworkApiKey;
	@Value("${REED_API_KEY:}")
	private String reedApiKey;
	@Value("${USAJOBS_API_KEY:}")
	private String usajobsApiKey;
	@Value("${USAJOBS_USER_AGENT:}")
	private String usajobsUserAgent;
	@Value("${jobsearch.cache.refresh-interval-ms:900000}")
	private long cacheRefreshIntervalMillis = 900000;
	@Value("${jobsearch.cache.redis-ttl-ms:900000}")
	private long redisCacheTtlMillis = 900000;
	private static final String REDIS_CACHE_KEY_PREFIX = "jobsearch:feed:";

	public JobFeedService(JobFeedProperties feedProperties, ResumeProfileService profileService,
			RestTemplate restTemplate, ObjectMapper objectMapper) {
		this.feedProperties = feedProperties;
		this.profileService = profileService;
		this.restTemplate = restTemplate;
		this.objectMapper = objectMapper;
	}

	@Autowired(required = false)
	public void setAiJobSkillMatcher(AiJobSkillMatcher aiJobSkillMatcher) {
		this.aiJobSkillMatcher = aiJobSkillMatcher;
	}

	@Autowired(required = false)
	public void setRedisTemplate(StringRedisTemplate redisTemplate) {
		this.redisTemplate = redisTemplate;
	}

	@Autowired(required = false)
	public void setJobSourceDirectory(JobSourceDirectory sourceDirectory) {
		this.sourceDirectory = sourceDirectory;
		this.additionalSourcesEnabled = true;
	}

	@Autowired(required = false)
	public void setFeedExecutor(@Qualifier("jobFeedExecutor") Executor feedExecutor) {
		this.feedExecutor = feedExecutor;
	}

	public JobSearchResponse search(int requestedLimit) {
		return search("default", requestedLimit, 10, 7);
	}

	public JobSearchResponse search(int requestedLimit, int requestedMinimumScore) {
		return search("default", requestedLimit, requestedMinimumScore, 7);
	}

	public JobSearchResponse search(int requestedLimit, int requestedMinimumScore, int requestedDays) {
		return search("default", requestedLimit, requestedMinimumScore, requestedDays);
	}

	@Override
	public JobSearchResponse search(String accountId, int requestedLimit, int requestedMinimumScore,
			int requestedDays) {
		CandidateProfile profile = "default".equals(accountId)
				? profileService.getProfile() : profileService.getProfile(accountId);
		int limit = Math.max(1, Math.min(requestedLimit, MAX_RESULTS));
		int minimumScore = Math.max(0, Math.min(requestedMinimumScore, 100));
		int days = Math.max(1, Math.min(requestedDays, 30));
		Instant cutoff = Instant.now().minus(java.time.Duration.ofDays(days));
		JobSearchResponse cached = getCachedSearch(accountId, profile);
		List<JobPosting> jobs = cached.getJobs().stream()
				.filter(job -> job.getMatchScore() >= minimumScore)
				.filter(job -> isRecent(job.getPostedAt(), cutoff))
				.limit(limit)
				.collect(Collectors.toList());
		return new JobSearchResponse(cached.getCandidateRole(), cached.getCandidateLocation(),
				cached.getFetchedAt(), jobs, cached.getFeeds());
	}

	@Override
	public void invalidateCache(String accountId) {
		synchronized (accountCacheLock(accountId)) {
			cachedSearches.remove(accountId);
			if (redisTemplate != null) {
				try {
					redisTemplate.delete(redisCacheKey(accountId));
				} catch (DataAccessException exception) {
					LOGGER.warn("Could not invalidate Redis job-feed cache: {}", exception.getMessage());
				}
			}
		}
	}

	public void invalidateCache() {
		invalidateCache("default");
	}

	@Scheduled(fixedDelayString = "${jobsearch.cache.refresh-interval-ms:900000}")
	public void refreshJobCache() {
		for (CandidateProfile profile : profileService.getAllProfiles()) {
			synchronized (accountCacheLock(profile.getId())) {
				CachedSearch refreshed = createCachedSearch(profile);
				cachedSearches.put(profile.getId(), refreshed);
				writeRedisCache(profile.getId(), refreshed);
			}
		}
	}

	private JobSearchResponse getCachedSearch(String accountId, CandidateProfile profile) {
		synchronized (accountCacheLock(accountId)) {
			CachedSearch cached = cachedSearches.get(accountId);
			if (!isCacheFresh(cached, profile)) {
				cached = readRedisCache(accountId);
				if (isCacheFresh(cached, profile)) {
					cachedSearches.put(accountId, cached);
				}
			}
			if (!isCacheFresh(cached, profile)) {
				cached = createCachedSearch(profile);
				cachedSearches.put(accountId, cached);
				writeRedisCache(accountId, cached);
			}
			return cached.response;
		}
	}

	private CachedSearch readRedisCache(String accountId) {
		if (redisTemplate == null) {
			return null;
		}
		try {
			String value = redisTemplate.opsForValue().get(redisCacheKey(accountId));
			return value == null ? null : objectMapper.readValue(value, CachedSearch.class);
		} catch (DataAccessException | JacksonException exception) {
			LOGGER.warn("Could not read Redis job-feed cache; using local cache: {}",
					exception.getMessage());
			return null;
		}
	}

	private void writeRedisCache(String accountId, CachedSearch cached) {
		if (redisTemplate == null) {
			return;
		}
		try {
			String value = objectMapper.writeValueAsString(cached);
			redisTemplate.opsForValue().set(redisCacheKey(accountId), value,
					Duration.ofMillis(Math.max(1000, redisCacheTtlMillis)));
		} catch (DataAccessException | JacksonException exception) {
			LOGGER.warn("Could not write Redis job-feed cache; keeping local cache: {}",
					exception.getMessage());
		}
	}

	private static String redisCacheKey(String accountId) {
		return REDIS_CACHE_KEY_PREFIX + accountId;
	}

	private boolean isCacheFresh(CachedSearch cached, CandidateProfile profile) {
		return cached != null
				&& java.util.Objects.equals(cached.profileUpdatedAt, profile.getUpdatedAt())
				&& cached.refreshedAt.plusMillis(cacheRefreshIntervalMillis).isAfter(Instant.now());
	}

	private CachedSearch createCachedSearch(CandidateProfile profile) {
		List<JobPosting> jobs = new ArrayList<>();
		List<JobFeedStatus> statuses = new ArrayList<>();
		List<CompletableFuture<FeedFetchResult>> feedTasks = feedProperties.getFeeds().stream()
				.map(feed -> CompletableFuture.supplyAsync(() -> fetchConfiguredFeed(feed, profile), feedExecutor))
				.toList();
		for (CompletableFuture<FeedFetchResult> feedTask : feedTasks) {
			FeedFetchResult result = feedTask.join();
			for (JobPosting job : result.jobs()) {
				jobs.add(rank(job, profile));
			}
			statuses.add(result.status());
		}
		if (additionalSourcesEnabled) {
			fetchAdditionalPublicFeeds(profile, jobs, statuses);
			fetchKeyedSources(profile, jobs, statuses);
		}
		if (companyDiscoveryEnabled) {
			fetchDiscoveredCompanyFeeds(profile, jobs, statuses);
		}
		Instant cutoff = Instant.now().minus(Duration.ofDays(30));
		Map<String, JobPosting> uniqueJobs = new LinkedHashMap<>();
		jobs.stream().filter(job -> isRecent(job.getPostedAt(), cutoff))
				.forEach(job -> uniqueJobs.putIfAbsent(jobDeduplicationKey(job), job));
		jobs = new ArrayList<>(uniqueJobs.values());
		jobs.sort(Comparator.comparingInt(JobPosting::getMatchScore).reversed()
				.thenComparing(job -> safe(job.getPostedAt()), Comparator.reverseOrder())
				.thenComparing(job -> safe(job.getTitle()), String.CASE_INSENSITIVE_ORDER));
		if (aiJobSkillMatcher != null && !jobs.isEmpty()) {
			List<String> resumeSkills = new ArrayList<>(
					ResumeProfileService.extractMatchingSkills(profile.getResumeText()));
			if (profile.getSkills() != null) {
				Arrays.stream(profile.getSkills().split(",")).map(String::trim)
						.filter(skill -> !skill.isEmpty())
						.filter(skill -> resumeSkills.stream()
								.noneMatch(existing -> existing.equalsIgnoreCase(skill)))
						.forEach(resumeSkills::add);
			}
			String resumeContext = ResumeProfileService.extractMatchingContext(profile.getResumeText());
			Map<String, JobPosting> aiMatches = aiJobSkillMatcher.compare(resumeSkills, resumeContext, jobs);
			if (!aiMatches.isEmpty()) {
				jobs = jobs.stream().map(job -> aiMatches.getOrDefault(job.getId(), job))
						.sorted(Comparator.comparingInt(JobPosting::getMatchScore).reversed()
								.thenComparing(job -> safe(job.getPostedAt()), Comparator.reverseOrder())
								.thenComparing(job -> safe(job.getTitle()), String.CASE_INSENSITIVE_ORDER))
						.collect(Collectors.toList());
			}
		}
		if (jobs.size() > MAX_RESULTS) {
			jobs = new ArrayList<>(jobs.subList(0, MAX_RESULTS));
		}
		JobSearchResponse response = new JobSearchResponse(profile.getTargetRole(),
				profile.getPreferredLocation(), LocalDateTime.now().toString(), jobs, statuses);
		return new CachedSearch(profile.getUpdatedAt(), Instant.now(), response);
	}

	private FeedFetchResult fetchConfiguredFeed(Feed feed, CandidateProfile profile) {
		String name = feed.getName() == null ? "Job feed" : feed.getName();
		String feedUrl = resolveFeedUrl(feed.getUrl(), profile);
		if (!isAllowedFeed(feedUrl)) {
			return new FeedFetchResult(Collections.emptyList(), new JobFeedStatus(name, false, 0,
					"Feed URL must use HTTPS and belong to a supported public job-board host."));
		}
		try {
			String response = restTemplate.getForObject(feedUrl, String.class);
			if (response == null || response.trim().isEmpty()) {
				throw new IllegalStateException("The job board returned an empty feed.");
			}
			List<JobPosting> feedJobs = parseFeed(feed, response);
			if ("himalayas-json".equalsIgnoreCase(feed.getType())) {
				String cursor = nextCursor(response);
				for (int page = 1; cursor != null && page < MAX_HIMALAYAS_PAGES; page++) {
					String pageUrl = appendQueryParameter(feedUrl, "cursor", cursor);
					String pageResponse;
					try {
						pageResponse = restTemplate.getForObject(pageUrl, String.class);
					} catch (RestClientException exception) {
						LOGGER.warn("Himalayas pagination stopped after {} pages: {}", page, exception.getMessage());
						break;
					}
					if (pageResponse == null || pageResponse.trim().isEmpty()) {
						break;
					}
					feedJobs.addAll(parseFeed(feed, pageResponse));
					cursor = nextCursor(pageResponse);
				}
			}
			return new FeedFetchResult(feedJobs, new JobFeedStatus(name, true, feedJobs.size(), null));
		} catch (RestClientException | IOException | SAXException | ParserConfigurationException
				| IllegalArgumentException | IllegalStateException exception) {
			LOGGER.warn("Job feed '{}' could not be fetched: {}", name, exception.getMessage());
			return new FeedFetchResult(Collections.emptyList(), new JobFeedStatus(name, false, 0,
					"Could not fetch this public feed. Try refreshing later."));
		}
	}

	private void fetchDiscoveredCompanyFeeds(CandidateProfile profile, List<JobPosting> jobs,
			List<JobFeedStatus> statuses) {
		int companiesWithoutPublicBoard = 0;
		for (String companySlug : sourceDirectory.getAutoCompanySlugs()) {
			if (hasConfiguredCompanyFeed(companySlug)) {
				continue;
			}
			BoardDiscovery discovery = discoveredCompanyBoards.compute(companySlug, (slug, cached) -> {
				if (cached != null && cached.expiresAt().isAfter(Instant.now())) {
					return cached;
				}
				return discoverCompanyBoard(slug);
			});
			if (discovery.feed() == null) {
				companiesWithoutPublicBoard++;
				continue;
			}
			try {
				String response = restTemplate.getForObject(discovery.feed().getUrl(), String.class);
				List<JobPosting> feedJobs = response == null || response.isBlank()
						? Collections.emptyList() : parseFeed(discovery.feed(), response);
				feedJobs.forEach(job -> jobs.add(rank(job, profile)));
				statuses.add(new JobFeedStatus(discovery.feed().getName(), true, feedJobs.size(),
						feedJobs.isEmpty() ? "ATS found; no current postings were returned." : null));
			} catch (RestClientException | IOException | SAXException | ParserConfigurationException
					| IllegalArgumentException | IllegalStateException exception) {
				LOGGER.debug("Discovered ATS endpoint for '{}' is temporarily unavailable: {}",
						companySlug, exception.getMessage());
				statuses.add(new JobFeedStatus(discovery.feed().getName(), false, 0,
						"Discovered ATS endpoint is temporarily unavailable."));
			}
		}
		if (companiesWithoutPublicBoard > 0) {
			statuses.add(new JobFeedStatus("Company ATS discovery", false, 0,
					"No supported public ATS endpoint with current postings was found for "
							+ companiesWithoutPublicBoard + " companies."));
		}
	}

	private BoardDiscovery discoverCompanyBoard(String companySlug) {
		for (Feed candidate : companyFeedCandidates(companySlug)) {
			try {
				if (!isAllowedFeed(candidate.getUrl())) {
					continue;
				}
				String response = restTemplate.getForObject(candidate.getUrl(), String.class);
				if (response == null || response.isBlank()) {
					continue;
				}
				List<JobPosting> jobs = parseFeed(candidate, response);
				if (!jobs.isEmpty()) {
					return new BoardDiscovery(candidate, Instant.now().plus(Duration.ofHours(24)));
				}
			} catch (RestClientException | IOException | SAXException | ParserConfigurationException
					| IllegalArgumentException | IllegalStateException exception) {
				LOGGER.debug("ATS discovery for '{}' did not match {}: {}",
						companySlug, candidate.getType(), exception.getMessage());
			}
		}
		return new BoardDiscovery(null, Instant.now().plus(Duration.ofHours(6)));
	}

	private List<Feed> companyFeedCandidates(String slug) {
		String company = displayCompanyName(slug);
		return List.of(
				companyFeed(company, "greenhouse-json",
						"https://boards-api.greenhouse.io/v1/boards/" + slug + "/jobs?content=true"),
				companyFeed(company, "lever-json",
						"https://api.lever.co/v0/postings/" + slug + "?mode=json"),
				companyFeed(company, "ashby-json",
						"https://api.ashbyhq.com/posting-api/job-board/" + slug),
				companyFeed(company, "workable-json",
						"https://apply.workable.com/api/v1/widget/accounts/" + slug + "?details=true"),
				companyFeed(company, "smartrecruiters-json",
						"https://api.smartrecruiters.com/v1/companies/" + slug + "/postings?limit=100&offset=0"),
				companyFeed(company, "recruitee-json",
						"https://" + slug + ".recruitee.com/api/offers/"),
				companyFeed(company, "breezy-json", "https://" + slug + ".breezy.hr/json"),
				companyFeed(company, "rippling-json",
						"https://ats.rippling.com/api/v2/board/" + slug + "/jobs?page=1"),
				companyFeed(company, "personio-xml",
						"https://" + slug + ".jobs.personio.de/xml?language=en"),
				companyFeed(company, "teamtailor-rss", "https://" + slug + ".teamtailor.com/jobs.rss"));
	}

	private static Feed companyFeed(String company, String type, String url) {
		Feed feed = new Feed();
		feed.setName(company + " (" + type.replace("-json", "").replace("-xml", "").replace("-rss", "") + ")");
		feed.setType(type);
		feed.setUrl(url);
		return feed;
	}

	private boolean hasConfiguredCompanyFeed(String slug) {
		return feedProperties.getFeeds().stream()
				.anyMatch(feed -> feed.getName() != null
						&& feed.getName().toLowerCase(Locale.ROOT).contains(slug));
	}

	private static String displayCompanyName(String slug) {
		return Arrays.stream(slug.split("-"))
				.map(word -> Character.toUpperCase(word.charAt(0)) + word.substring(1))
				.collect(Collectors.joining(" "));
	}

	private void fetchAdditionalPublicFeeds(CandidateProfile profile, List<JobPosting> jobs,
			List<JobFeedStatus> statuses) {
		Feed muse = companyFeed("The Muse", "themuse-json",
				"https://www.themuse.com/api/public/jobs?page=1&category=Engineering");
		Feed hn = companyFeed("Hacker News Who's Hiring", "hn-hiring-json",
				"https://hn.algolia.com/api/v1/search_by_date?tags=comment&query="
						+ UriUtils.encodeQueryParam(firstNonEmpty(profile.getTargetRole(), "software engineer"),
								StandardCharsets.UTF_8));
		Feed arbeitnowUk = companyFeed("Arbeitnow UK", "arbeitnow-uk-json",
				"https://www.arbeitnow.com/api/job-board-api");
		List<CompletableFuture<FeedFetchResult>> tasks = List.of(muse, hn, arbeitnowUk).stream()
				.map(feed -> CompletableFuture.supplyAsync(() -> fetchAdditionalFeed(feed, profile), feedExecutor))
				.toList();
		for (CompletableFuture<FeedFetchResult> task : tasks) {
			FeedFetchResult result = task.join();
			for (JobPosting job : result.jobs()) {
				jobs.add(rank(job, profile));
			}
			statuses.add(result.status());
		}
	}

	private FeedFetchResult fetchAdditionalFeed(Feed feed, CandidateProfile profile) {
		try {
			String body = restTemplate.getForObject(feed.getUrl(), String.class);
			if (body == null || body.isBlank()) {
				return new FeedFetchResult(Collections.emptyList(), new JobFeedStatus(feed.getName(), false, 0,
						"Public source returned an empty response."));
			}
			List<JobPosting> feedJobs = parseFeed(feed, body);
			return new FeedFetchResult(feedJobs, new JobFeedStatus(feed.getName(), true, feedJobs.size(), null));
		} catch (RestClientException | IOException | SAXException | ParserConfigurationException
				| IllegalArgumentException | IllegalStateException exception) {
			LOGGER.debug("Public source '{}' could not be fetched: {}", feed.getName(), exception.getMessage());
			return new FeedFetchResult(Collections.emptyList(), new JobFeedStatus(feed.getName(), false, 0,
					"Public source is temporarily unavailable."));
		}
	}

	private void fetchKeyedSources(CandidateProfile profile, List<JobPosting> jobs,
			List<JobFeedStatus> statuses) {
		Map<String, Integer> missingCredentials = new LinkedHashMap<>();
		for (JobSourceDirectory.Source source : sourceDirectory.getSourcesByKind("key")) {
			String provider = source.slug().startsWith("adzuna-") ? "adzuna" : source.slug();
			if (!hasCredentials(provider)) {
				missingCredentials.merge(provider, 1, Integer::sum);
				continue;
			}
			try {
				List<JobPosting> providerJobs = switch (provider) {
				case "adzuna" -> fetchAdzuna(source.slug(), profile);
				case "jooble" -> fetchJooble(profile);
				case "careerjet" -> fetchCareerjet(profile);
				case "findwork" -> fetchFindwork(profile);
				case "reed" -> fetchReed(profile);
				case "usajobs" -> fetchUsaJobs(profile);
				default -> Collections.emptyList();
				};
				providerJobs.forEach(job -> jobs.add(rank(job, profile)));
				statuses.add(new JobFeedStatus(source.name(), true, providerJobs.size(), null));
			} catch (RestClientException | IOException | IllegalArgumentException exception) {
				LOGGER.warn("Key-based job provider '{}' could not be queried.", provider);
				statuses.add(new JobFeedStatus(source.name(), false, 0,
						"Provider request failed. Check its API key and service availability."));
			}
		}
		missingCredentials.forEach((provider, count) -> statuses.add(new JobFeedStatus(
				displayName(provider), false, 0,
				providerEnvironmentName(provider) + " is not configured for " + count + " source(s).")));
	}

	private boolean hasCredentials(String provider) {
		return switch (provider) {
		case "adzuna" -> !adzunaAppId.isBlank() && !adzunaAppKey.isBlank();
		case "jooble" -> !joobleApiKey.isBlank();
		case "careerjet" -> !careerjetAffiliateId.isBlank();
		case "findwork" -> !findworkApiKey.isBlank();
		case "reed" -> !reedApiKey.isBlank();
		case "usajobs" -> !usajobsApiKey.isBlank() && !usajobsUserAgent.isBlank();
		default -> false;
		};
	}

	private List<JobPosting> fetchAdzuna(String sourceSlug, CandidateProfile profile) throws IOException {
		String country = sourceSlug.substring(sourceSlug.lastIndexOf('-') + 1);
		String query = firstNonEmpty(profile.getTargetRole(), firstProfileSkill(profile), "software engineer");
		String url = "https://api.adzuna.com/v1/api/jobs/" + country + "/search/1?app_id="
				+ UriUtils.encodeQueryParam(adzunaAppId, StandardCharsets.UTF_8) + "&app_key="
				+ UriUtils.encodeQueryParam(adzunaAppKey, StandardCharsets.UTF_8) + "&results_per_page=50&what="
				+ UriUtils.encodeQueryParam(query, StandardCharsets.UTF_8)
				+ (profile.getPreferredLocation() == null ? ""
						: "&where=" + UriUtils.encodeQueryParam(profile.getPreferredLocation(), StandardCharsets.UTF_8));
		String body = restTemplate.getForObject(url, String.class);
		return body == null ? Collections.emptyList() : parseAdzuna(body, sourceSlug);
	}

	private List<JobPosting> fetchJooble(CandidateProfile profile) throws IOException {
		Map<String, String> query = new LinkedHashMap<>();
		query.put("keywords", firstNonEmpty(profile.getTargetRole(), firstProfileSkill(profile), "software engineer"));
		if (profile.getPreferredLocation() != null) {
			query.put("location", profile.getPreferredLocation());
		}
		String body = restTemplate.postForObject("https://jooble.org/api/" + joobleApiKey,
				query, String.class);
		return body == null ? Collections.emptyList() : parseJooble(body);
	}

	private List<JobPosting> fetchCareerjet(CandidateProfile profile) throws IOException {
		String keywords = firstNonEmpty(profile.getTargetRole(), firstProfileSkill(profile), "software engineer");
		String url = "https://public.api.careerjet.net/search?locale_code=en_US&keywords="
				+ UriUtils.encodeQueryParam(keywords, StandardCharsets.UTF_8)
				+ "&user_ip=127.0.0.1&user_agent=MyJobs-API&affid="
				+ UriUtils.encodeQueryParam(careerjetAffiliateId, StandardCharsets.UTF_8);
		String body = restTemplate.getForObject(url, String.class);
		return body == null ? Collections.emptyList() : parseCareerjet(body);
	}

	private List<JobPosting> fetchFindwork(CandidateProfile profile) throws IOException {
		String query = firstNonEmpty(profile.getTargetRole(), firstProfileSkill(profile), "software engineer");
		String url = "https://findwork.dev/api/jobs/?search="
				+ UriUtils.encodeQueryParam(query, StandardCharsets.UTF_8);
		HttpHeaders headers = new HttpHeaders();
		headers.set("Authorization", "Token " + findworkApiKey);
		ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.GET,
				new HttpEntity<>(headers), String.class);
		return response.getBody() == null ? Collections.emptyList() : parseFindwork(response.getBody());
	}

	private List<JobPosting> fetchReed(CandidateProfile profile) throws IOException {
		String query = firstNonEmpty(profile.getTargetRole(), firstProfileSkill(profile), "software engineer");
		String url = "https://www.reed.co.uk/api/1.0/search?keywords="
				+ UriUtils.encodeQueryParam(query, StandardCharsets.UTF_8);
		if (profile.getPreferredLocation() != null) {
			url += "&locationName=" + UriUtils.encodeQueryParam(profile.getPreferredLocation(), StandardCharsets.UTF_8);
		}
		HttpHeaders headers = new HttpHeaders();
		headers.setBasicAuth(reedApiKey, "");
		ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.GET,
				new HttpEntity<>(headers), String.class);
		return response.getBody() == null ? Collections.emptyList() : parseReed(response.getBody());
	}

	private List<JobPosting> fetchUsaJobs(CandidateProfile profile) throws IOException {
		String query = firstNonEmpty(profile.getTargetRole(), firstProfileSkill(profile), "software engineer");
		String url = "https://data.usajobs.gov/api/search?Keyword="
				+ UriUtils.encodeQueryParam(query, StandardCharsets.UTF_8);
		HttpHeaders headers = new HttpHeaders();
		headers.set("Authorization-Key", usajobsApiKey);
		headers.set("User-Agent", usajobsUserAgent);
		headers.setAccept(List.of(MediaType.APPLICATION_JSON));
		ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.GET,
				new HttpEntity<>(headers), String.class);
		return response.getBody() == null ? Collections.emptyList() : parseUsaJobs(response.getBody());
	}

	private List<JobPosting> parseAdzuna(String body, String source) throws IOException {
		return mapJsonJobs(objectMapper.readTree(body).path("results"), "Adzuna " + source.toUpperCase(Locale.ROOT),
				item -> {
					String url = text(item, "redirect_url");
					return posting(text(item, "id"), text(item, "title"),
							nestedText(item.get("company"), "display_name"),
							nestedText(item.get("location"), "display_name"), url,
							text(item, "description"), text(item, "created"),
							Collections.singletonList(nestedText(item.get("category"), "label")));
				});
	}

	private List<JobPosting> parseTheMuse(String body) throws IOException {
		JsonNode items = objectMapper.readTree(body).path("results");
		return mapJsonJobs(items, "The Muse", item -> {
			List<String> tags = new ArrayList<>();
			addJsonArrayText(tags, item.path("categories"), "name");
			addJsonArrayText(tags, item.path("levels"), "name");
			List<String> locations = new ArrayList<>();
			addJsonArrayText(locations, item.path("locations"), "name");
			String url = nestedText(item.path("refs"), "landing_page");
			return posting(firstText(item, "id"), text(item, "name"),
					nestedText(item.path("company"), "name"),
					String.join(", ", locations), url, text(item, "contents"),
					text(item, "publication_date"), tags);
		});
	}

	private List<JobPosting> parseHackerNewsHiring(String body) throws IOException {
		JsonNode items = objectMapper.readTree(body).path("hits");
		return mapJsonJobs(items, "Hacker News Who's Hiring", item -> {
			String id = firstText(item, "objectID");
			String url = id == null ? null : "https://news.ycombinator.com/item?id=" + id;
			String description = text(item, "comment_text");
			String title = description == null ? null : description.replaceAll("<[^>]+>", " ")
					.replaceAll("\\s+", " ").trim();
			if (title != null && title.length() > 120) {
				title = title.substring(0, 120);
			}
			return posting(id, title, firstText(item, "author"), null, url, description,
					text(item, "created_at"), Collections.emptyList());
		});
	}

	private static void addJsonArrayText(List<String> target, JsonNode array, String field) {
		if (array != null && array.isArray()) {
			array.forEach(item -> {
				String value = text(item, field);
				if (value != null && !value.isBlank()) {
					target.add(value.trim());
				}
			});
		}
	}

	private List<JobPosting> parseJooble(String body) throws IOException {
		return mapJsonJobs(objectMapper.readTree(body).path("jobs"), "Jooble",
				item -> posting(firstText(item, "id", "jobId"), text(item, "title"),
						text(item, "company"), text(item, "location"), firstText(item, "link", "url"),
						text(item, "snippet"), firstText(item, "updated", "updatedAt"),
						Collections.singletonList(text(item, "type"))));
	}

	private List<JobPosting> parseCareerjet(String body) throws IOException {
		return mapJsonJobs(objectMapper.readTree(body).path("jobs"), "Careerjet",
				item -> posting(firstText(item, "id", "job_id"), text(item, "title"),
						text(item, "company"), firstText(item, "locations", "location"),
						firstText(item, "url", "link"), text(item, "description"),
						firstText(item, "date", "posted_at"), Collections.emptyList()));
	}

	private List<JobPosting> parseFindwork(String body) throws IOException {
		return mapJsonJobs(objectMapper.readTree(body).path("results"), "Findwork",
				item -> posting(firstText(item, "id", "url"), text(item, "role"),
						text(item, "company_name"), text(item, "location"), text(item, "url"),
						text(item, "text"), firstText(item, "date_posted", "created_at"),
						Arrays.asList(text(item, "employment_type"), text(item, "remote"))));
	}

	private List<JobPosting> parseReed(String body) throws IOException {
		return mapJsonJobs(objectMapper.readTree(body).path("results"), "Reed",
				item -> posting(text(item, "jobId"), text(item, "jobTitle"), text(item, "employerName"),
						text(item, "locationName"), text(item, "jobUrl"), text(item, "jobDescription"),
						text(item, "date"), Collections.emptyList()));
	}

	private List<JobPosting> parseUsaJobs(String body) throws IOException {
		return mapJsonJobs(objectMapper.readTree(body).path("SearchResult").path("SearchResultItems"),
				"USAJOBS", item -> {
					JsonNode descriptor = item.path("MatchedObjectDescriptor");
					String url = firstText(descriptor, "PositionURI");
					String id = firstText(descriptor, "PositionID", "PositionOpeningID");
					return posting(id, text(descriptor, "PositionTitle"),
							text(descriptor, "OrganizationName"),
							text(descriptor, "PositionLocationDisplay"), url,
							nestedText(descriptor.path("UserArea").path("Details"), "JobSummary"),
							text(descriptor, "PublicationStartDate"), Collections.emptyList());
				});
	}

	private String firstProfileSkill(CandidateProfile profile) {
		return profile.getSkills() == null ? null
				: Arrays.stream(profile.getSkills().split(",")).map(String::trim)
						.filter(skill -> !skill.isEmpty()).findFirst().orElse(null);
	}

	private static String providerEnvironmentName(String provider) {
		return switch (provider) {
		case "adzuna" -> "ADZUNA_APP_ID and ADZUNA_APP_KEY";
		case "jooble" -> "JOOBLE_API_KEY";
		case "careerjet" -> "CAREERJET_AFFILIATE_ID";
		case "findwork" -> "FINDWORK_API_KEY";
		case "reed" -> "REED_API_KEY";
		case "usajobs" -> "USAJOBS_API_KEY and USAJOBS_USER_AGENT";
		default -> "provider credentials";
		};
	}

	private static String displayName(String provider) {
		return Character.toUpperCase(provider.charAt(0)) + provider.substring(1);
	}

	private Object accountCacheLock(String accountId) {
		return accountCacheLocks.computeIfAbsent(accountId, ignored -> new Object());
	}

	private List<JobPosting> parseFeed(Feed feed, String body)
			throws IOException, SAXException, ParserConfigurationException {
		if ("remoteok-json".equalsIgnoreCase(feed.getType())) {
			return parseRemoteOk(body, feed.getName());
		}
		if ("workingnomads-json".equalsIgnoreCase(feed.getType())) {
			return parseWorkingNomads(body, feed.getName());
		}
		if ("jobicy-json".equalsIgnoreCase(feed.getType())) {
			return parseJobicy(body, feed.getName());
		}
		if ("remotive-json".equalsIgnoreCase(feed.getType())) {
			return parseRemotive(body, feed.getName());
		}
		if ("himalayas-json".equalsIgnoreCase(feed.getType())) {
			return parseHimalayas(body, feed.getName());
		}
		if ("arbeitnow-json".equalsIgnoreCase(feed.getType())) {
			return parseArbeitnow(body, feed.getName());
		}
		if ("arbeitnow-uk-json".equalsIgnoreCase(feed.getType())) {
			return parseArbeitnow(body, feed.getName()).stream()
					.filter(job -> job.getLocation() != null
							&& job.getLocation().matches("(?i).*\\b(UK|United Kingdom|England|Scotland|Wales|Northern Ireland)\\b.*"))
					.collect(Collectors.toList());
		}
		if ("freehire-json".equalsIgnoreCase(feed.getType())) {
			return parseFreeHire(body, feed.getName());
		}
		if ("greenhouse-json".equalsIgnoreCase(feed.getType())) {
			return parseGreenhouse(body, feed.getName());
		}
		if ("lever-json".equalsIgnoreCase(feed.getType())) {
			return parseLever(body, feed.getName());
		}
		if ("ashby-json".equalsIgnoreCase(feed.getType())) {
			return parseAshby(body, feed.getName());
		}
		if ("workable-json".equalsIgnoreCase(feed.getType())) {
			return parseWorkable(body, feed.getName());
		}
		if ("smartrecruiters-json".equalsIgnoreCase(feed.getType())) {
			return parseSmartRecruiters(body, feed.getName(), feed.getUrl());
		}
		if ("recruitee-json".equalsIgnoreCase(feed.getType())) {
			return parseRecruitee(body, feed.getName());
		}
		if ("breezy-json".equalsIgnoreCase(feed.getType())) {
			return parseBreezy(body, feed.getName());
		}
		if ("rippling-json".equalsIgnoreCase(feed.getType())) {
			return parseRippling(body, feed.getName());
		}
		if ("themuse-json".equalsIgnoreCase(feed.getType())) {
			return parseTheMuse(body);
		}
		if ("hn-hiring-json".equalsIgnoreCase(feed.getType())) {
			return parseHackerNewsHiring(body);
		}
		if ("personio-xml".equalsIgnoreCase(feed.getType())) {
			return parsePersonio(body, feed.getName());
		}
		if ("teamtailor-rss".equalsIgnoreCase(feed.getType())) {
			return parseRss(body, feed.getName(), feed.getUrl());
		}
		if ("rss".equalsIgnoreCase(feed.getType())) {
			return parseRss(body, feed.getName(), feed.getUrl());
		}
		throw new IllegalArgumentException("Unsupported job-feed type.");
	}

	private List<JobPosting> parseRemoteOk(String body, String source) throws IOException {
		JsonNode root = objectMapper.readTree(body);
		if (root == null || !root.isArray()) {
			throw new IOException("Job feed response was not a JSON array.");
		}
		List<JobPosting> jobs = new ArrayList<>();
		for (JsonNode item : root) {
			String title = text(item, "position");
			String url = absoluteUrl(text(item, "url"), "https://remoteok.com");
			if (title == null || url == null) {
				continue;
			}
			List<String> tags = new ArrayList<>();
			JsonNode tagNode = item.get("tags");
			if (tagNode != null && tagNode.isArray()) {
				tagNode.forEach(tag -> {
					if (tag.isTextual() && !tag.asText().trim().isEmpty()) {
						tags.add(tag.asText().trim());
					}
				});
			}
			String id = text(item, "id");
			String description = text(item, "description");
			jobs.add(new JobPosting(id == null ? url : id, title, text(item, "company"),
					text(item, "location"), source, url, description, text(item, "date"),
					tags, 0, Collections.emptyList()));
		}
		return jobs;
	}

	private List<JobPosting> parseWorkingNomads(String body, String source) throws IOException {
		JsonNode root = objectMapper.readTree(body);
		if (root == null || !root.isArray()) {
			throw new IOException("Job feed response was not a JSON array.");
		}
		List<JobPosting> jobs = new ArrayList<>();
		for (JsonNode item : root) {
			String url = absoluteUrl(text(item, "url"), "https://www.workingnomads.com");
			String title = text(item, "title");
			if (url == null || title == null) {
				continue;
			}
			String tagsValue = text(item, "tags");
			List<String> tags = tagsValue == null ? Collections.emptyList()
					: Arrays.stream(tagsValue.split(",")).map(String::trim)
							.filter(tag -> !tag.isEmpty()).collect(Collectors.toList());
			String id = text(item, "id");
			jobs.add(new JobPosting(id == null ? url : id, title, text(item, "company_name"),
					text(item, "location"), source, url, text(item, "description"),
					text(item, "pub_date"), tags, 0, Collections.emptyList()));
		}
		return jobs;
	}

	private List<JobPosting> parseJobicy(String body, String source) throws IOException {
		JsonNode root = objectMapper.readTree(body);
		JsonNode items = root == null ? null : root.get("jobs");
		if (items == null || !items.isArray()) {
			throw new IOException("Job feed response did not contain a jobs array.");
		}
		List<JobPosting> jobs = new ArrayList<>();
		for (JsonNode item : items) {
			String url = absoluteUrl(text(item, "url"), "https://jobicy.com");
			String title = text(item, "jobTitle");
			if (url == null || title == null) {
				continue;
			}
			List<String> tags = new ArrayList<>();
			addText(tags, text(item, "jobIndustry"));
			addText(tags, text(item, "jobType"));
			addText(tags, text(item, "jobGeo"));
			String description = text(item, "jobDescription");
			if (description == null) {
				description = text(item, "jobExcerpt");
			}
			String id = text(item, "id");
			jobs.add(new JobPosting(id == null ? url : id, title, text(item, "companyName"),
					text(item, "jobGeo"), source, url, description, text(item, "pubDate"),
					tags, 0, Collections.emptyList()));
		}
		return jobs;
	}

	private List<JobPosting> parseRemotive(String body, String source) throws IOException {
		JsonNode root = objectMapper.readTree(body);
		JsonNode items = root == null ? null : root.get("jobs");
		if (items == null || !items.isArray()) {
			throw new IOException("Job feed response did not contain a jobs array.");
		}
		List<JobPosting> jobs = new ArrayList<>();
		for (JsonNode item : items) {
			String url = absoluteUrl(text(item, "url"), "https://remotive.com");
			String title = text(item, "title");
			if (url == null || title == null) {
				continue;
			}
			List<String> tags = new ArrayList<>();
			addText(tags, text(item, "category"));
			addText(tags, text(item, "tags"));
			addText(tags, text(item, "job_type"));
			String id = text(item, "id");
			jobs.add(new JobPosting(id == null ? url : id, title, text(item, "company_name"),
					text(item, "candidate_required_location"), source, url,
					text(item, "description"), text(item, "publication_date"),
					tags, 0, Collections.emptyList()));
		}
		return jobs;
	}

	private List<JobPosting> parseHimalayas(String body, String source) throws IOException {
		JsonNode root = objectMapper.readTree(body);
		JsonNode items = root == null ? null : root.get("jobs");
		if (items == null || !items.isArray()) {
			throw new IOException("Job feed response did not contain a jobs array.");
		}
		List<JobPosting> jobs = new ArrayList<>();
		for (JsonNode item : items) {
			String url = absoluteUrl(text(item, "applicationLink"), "https://himalayas.app");
			String title = text(item, "title");
			if (url == null || title == null) {
				continue;
			}
			List<String> tags = new ArrayList<>();
			addText(tags, text(item, "categories"));
			addText(tags, text(item, "parentCategories"));
			addText(tags, text(item, "locationRestrictions"));
			addText(tags, text(item, "employmentType"));
			String id = text(item, "guid");
			String location = text(item, "location");
			if (location == null) {
				location = text(item, "locationRestrictions");
			}
			jobs.add(new JobPosting(id == null ? url : id, title, text(item, "companyName"),
					location, source, url, text(item, "description"),
					text(item, "pubDate"), tags, 0, Collections.emptyList()));
		}
		return jobs;
	}

	private List<JobPosting> parseArbeitnow(String body, String source) throws IOException {
		JsonNode root = objectMapper.readTree(body);
		JsonNode items = root == null ? null : root.get("data");
		if (items == null || !items.isArray()) {
			throw new IOException("Job feed response did not contain a data array.");
		}
		List<JobPosting> jobs = new ArrayList<>();
		for (JsonNode item : items) {
			String url = absoluteUrl(text(item, "url"), "https://www.arbeitnow.com");
			String title = text(item, "title");
			if (url == null || title == null) {
				continue;
			}
			List<String> tags = new ArrayList<>();
			JsonNode tagNode = item.get("tags");
			if (tagNode != null && tagNode.isArray()) {
				tagNode.forEach(tag -> addText(tags, tag.isValueNode() ? tag.asText() : null));
			}
			addText(tags, text(item, "job_types"));
			addText(tags, text(item, "location"));
			String id = text(item, "slug");
			jobs.add(new JobPosting(id == null ? url : id, title, text(item, "company_name"),
					text(item, "location"), source, url, text(item, "description"),
					text(item, "created_at"), tags, 0, Collections.emptyList()));
		}
		return jobs;
	}

	private List<JobPosting> parseFreeHire(String body, String source) throws IOException {
		JsonNode root = objectMapper.readTree(body);
		JsonNode items = root == null ? null : root.get("data");
		if (items == null || !items.isArray()) {
			throw new IOException("Job feed response did not contain a data array.");
		}
		List<JobPosting> jobs = new ArrayList<>();
		for (JsonNode item : items) {
			String url = absoluteUrl(text(item, "url"), "https://freehire.me");
			String title = text(item, "title");
			if (url == null || title == null) {
				continue;
			}
			List<String> tags = new ArrayList<>();
			addArrayText(tags, item.get("skills"));
			addArrayText(tags, item.get("regions"));
			addText(tags, text(item, "work_mode"));
			addText(tags, text(item, "seniority"));
			String id = text(item, "public_slug");
			if (id == null) {
				id = text(item, "external_id");
			}
			jobs.add(new JobPosting(id == null ? url : id, title, text(item, "company"),
					text(item, "location"), source, url, text(item, "description"),
					text(item, "posted_at"), tags, 0, Collections.emptyList()));
		}
		return jobs;
	}

	private List<JobPosting> parseGreenhouse(String body, String source) throws IOException {
		JsonNode items = objectMapper.readTree(body).path("jobs");
		return mapJsonJobs(items, source, item -> {
			String location = nestedText(item, "location", "name");
			String id = text(item, "id");
			String url = text(item, "absolute_url");
			return posting(id, text(item, "title"), text(item, "company_name"), location,
					url, text(item, "content"), firstText(item, "first_published", "updated_at"),
					arrayTexts(item.get("departments")));
		});
	}

	private List<JobPosting> parseLever(String body, String source) throws IOException {
		return mapJsonJobs(objectMapper.readTree(body), source, item -> {
			List<String> tags = new ArrayList<>();
			JsonNode categories = item.get("categories");
			addText(tags, nestedText(categories, "location"));
			addText(tags, nestedText(categories, "team"));
			addText(tags, nestedText(categories, "commitment"));
			String description = text(item, "descriptionPlain");
			if (description == null) {
				description = text(item, "description");
			}
			String postedAt = text(item, "createdAt");
			if (postedAt != null) {
				postedAt = epochTimestamp(postedAt);
			}
			return posting(text(item, "id"), text(item, "text"), null,
					nestedText(categories, "location"), text(item, "hostedUrl"),
					description, postedAt, tags);
		});
	}

	private List<JobPosting> parseAshby(String body, String source) throws IOException {
		return mapJsonJobs(objectMapper.readTree(body).path("jobs"), source, item -> {
			List<String> tags = new ArrayList<>();
			addText(tags, text(item, "department"));
			addText(tags, text(item, "employmentType"));
			addText(tags, text(item, "isRemote"));
			String description = text(item, "descriptionHtml");
			if (description == null) {
				description = text(item, "description");
			}
			String url = text(item, "jobUrl");
			if (url == null) {
				url = text(item, "applyUrl");
			}
			return posting(text(item, "id"), text(item, "title"), text(item, "companyName"),
					text(item, "location"), url, description, text(item, "publishedAt"), tags);
		});
	}

	private List<JobPosting> parseWorkable(String body, String source) throws IOException {
		JsonNode root = objectMapper.readTree(body);
		JsonNode items = root.isArray() ? root : root.path("jobs");
		return mapJsonJobs(items, source, item -> posting(
				firstText(item, "shortcode", "id"), firstText(item, "title", "name"),
				firstText(item, "company", "account"), locationText(item.get("location")),
				firstText(item, "url", "application_url"), firstText(item, "description", "description_plain"),
				firstText(item, "created_at", "published_at"),
				Arrays.asList(firstText(item, "department", "department_name"),
						firstText(item, "employment_type", "employmentType"))));
	}

	private List<JobPosting> parseSmartRecruiters(String body, String source, String feedUrl)
			throws IOException {
		JsonNode items = objectMapper.readTree(body).path("content");
		String companySlug = pathSegment(feedUrl, "/companies/");
		return mapJsonJobs(items, source, item -> {
			String id = text(item, "id");
			String url = firstText(item, "applyUrl", "url");
			if (url == null && id != null && companySlug != null) {
				url = "https://jobs.smartrecruiters.com/" + companySlug + "/" + id;
			}
			JsonNode location = item.get("location");
			String locationText = joinNonEmpty(nestedText(location, "city"),
					nestedText(location, "region"), nestedText(location, "country"));
			return posting(id, text(item, "name"), text(item, "company"), locationText,
					url, firstText(item, "jobAd", "description"), text(item, "releasedDate"),
					Collections.emptyList());
		});
	}

	private List<JobPosting> parseRecruitee(String body, String source) throws IOException {
		return mapJsonJobs(objectMapper.readTree(body).path("offers"), source, item -> posting(
				text(item, "id"), text(item, "title"), firstText(item, "company", "company_name"),
				locationText(item.get("location")), firstText(item, "careers_url", "careers_apply_url"),
				text(item, "description"), firstText(item, "created_at", "published_at"),
				Arrays.asList(firstText(item, "department", "department_name"),
						firstText(item, "employment_type", "employmentType"))));
	}

	private List<JobPosting> parseBreezy(String body, String source) throws IOException {
		JsonNode root = objectMapper.readTree(body);
		JsonNode items = root.isArray() ? root : root.path("jobs");
		return mapJsonJobs(items, source, item -> posting(
				text(item, "id"), firstText(item, "name", "title"), text(item, "company"),
				locationText(item.get("location")), firstText(item, "url", "apply_url"),
				text(item, "description"), firstText(item, "published_date", "created_at"),
				Arrays.asList(text(item, "department"), text(item, "type"))));
	}

	private List<JobPosting> parseRippling(String body, String source) throws IOException {
		JsonNode root = objectMapper.readTree(body);
		JsonNode items = root.isArray() ? root : root.has("items") ? root.path("items")
				: root.has("jobs") ? root.path("jobs") : root.path("data");
		return mapJsonJobs(items, source, item -> posting(
				firstText(item, "id", "job_id"), firstText(item, "title", "name"),
				firstText(item, "company_name", "company"),
				firstText(item, "location", "locations") == null
						? locationText(item.get("locations")) : firstText(item, "location", "locations"),
				firstText(item, "url", "job_url", "apply_url"),
				firstText(item, "description", "description_html"),
				firstText(item, "published_at", "created_at", "posted_at"),
				Arrays.asList(text(item, "department"), text(item, "employment_type"))));
	}

	private List<JobPosting> parsePersonio(String body, String source)
			throws IOException, SAXException, ParserConfigurationException {
		DocumentBuilderFactory factory = secureXmlFactory();
		org.w3c.dom.Document document = factory.newDocumentBuilder()
				.parse(new ByteArrayInputStream(body.getBytes(StandardCharsets.UTF_8)));
		NodeList positions = document.getElementsByTagName("position");
		List<JobPosting> jobs = new ArrayList<>();
		for (int index = 0; index < positions.getLength(); index++) {
			Node node = positions.item(index);
			if (!(node instanceof Element)) {
				continue;
			}
			Element item = (Element) node;
			String id = childText(item, "id");
			String title = childText(item, "name");
			String url = absoluteUrl(childText(item, "url"), "https://jobs.personio.de");
			if (url == null && id != null) {
				url = "https://jobs.personio.de/job/" + id;
			}
			if (title == null || url == null) {
				continue;
			}
			String location = joinNonEmpty(childText(item, "office"), childText(item, "schedule"));
			String description = childText(item, "jobDescription");
			String postedAt = childText(item, "createdAt");
			jobs.add(posting(id == null ? url : id, title, null, location, url, description,
					postedAt, Collections.singletonList(childText(item, "department"))));
		}
		return jobs;
	}

	private static List<JobPosting> mapJsonJobs(JsonNode items, String source,
			Function<JsonNode, JobPosting> mapper) throws IOException {
		if (items == null || !items.isArray()) {
			throw new IOException("Job feed response did not contain a jobs array.");
		}
		List<JobPosting> jobs = new ArrayList<>();
		for (JsonNode item : items) {
			JobPosting job = mapper.apply(item);
			String url = job == null ? null : validHttpsUrl(job.getUrl());
			if (job != null && url != null && job.getTitle() != null) {
				jobs.add(new JobPosting(job.getId() == null ? url : job.getId(), job.getTitle(),
						job.getCompany(), job.getLocation(), source, url, job.getDescription(),
						job.getPostedAt(), job.getTags(), 0, Collections.emptyList()));
			}
		}
		return jobs;
	}

	private static JobPosting posting(String id, String title, String company, String location,
			String url, String description, String postedAt, List<String> tags) {
		if (title == null || url == null) {
			return null;
		}
		List<String> cleanTags = tags == null ? Collections.emptyList()
				: tags.stream().filter(tag -> tag != null && !tag.trim().isEmpty())
						.map(String::trim).distinct().collect(Collectors.toList());
		return new JobPosting(id, title, company, location, null, url, description,
				postedAt, cleanTags, 0, Collections.emptyList());
	}

	private static String validHttpsUrl(String value) {
		if (value == null || value.trim().isEmpty()) {
			return null;
		}
		try {
			URI uri = URI.create(value.trim());
			return "https".equalsIgnoreCase(uri.getScheme()) && uri.getHost() != null
					? uri.toString() : null;
		} catch (IllegalArgumentException exception) {
			return null;
		}
	}

	private static String firstText(JsonNode item, String... keys) {
		for (String key : keys) {
			String value = text(item, key);
			if (value != null) {
				return value;
			}
		}
		return null;
	}

	private static String nestedText(JsonNode item, String key) {
		return text(item, key);
	}

	private static String nestedText(JsonNode item, String parent, String key) {
		return item == null ? null : text(item.path(parent), key);
	}

	private static String locationText(JsonNode location) {
		if (location == null || location.isNull()) {
			return null;
		}
		if (location.isValueNode() || location.isArray()) {
			return location.isArray() ? textFromArray(location) : location.asText();
		}
		return joinNonEmpty(firstText(location, "name", "city"),
				firstText(location, "region", "state"),
				firstText(location, "country", "country_name"));
	}

	private static String textFromArray(JsonNode values) {
		List<String> parts = new ArrayList<>();
		values.forEach(value -> {
			String text = value.isValueNode() ? value.asText().trim()
					: firstText(value, "name", "label");
			addText(parts, text);
		});
		return parts.isEmpty() ? null : String.join(", ", parts);
	}

	private static List<String> arrayTexts(JsonNode values) {
		if (values == null || !values.isArray()) {
			return Collections.emptyList();
		}
		List<String> result = new ArrayList<>();
		values.forEach(value -> addText(result, value.isValueNode() ? value.asText()
				: firstText(value, "name", "label")));
		return result;
	}

	private static String epochTimestamp(String raw) {
		try {
			long timestamp = Long.parseLong(raw);
			return Math.abs(timestamp) >= 100_000_000_000L
					? Instant.ofEpochMilli(timestamp).toString()
					: Instant.ofEpochSecond(timestamp).toString();
		} catch (NumberFormatException | java.time.DateTimeException exception) {
			return raw;
		}
	}

	private String nextCursor(String response) throws IOException {
		JsonNode root = objectMapper.readTree(response);
		String cursor = text(root, "nextCursor");
		return cursor == null || cursor.trim().isEmpty() ? null : cursor;
	}

	private static String appendQueryParameter(String url, String name, String value) {
		return url + (url.contains("?") ? "&" : "?")
				+ name + "=" + UriUtils.encodeQueryParam(value, StandardCharsets.UTF_8);
	}

	private static String jobDeduplicationKey(JobPosting job) {
		try {
			URI uri = URI.create(job.getUrl());
			String path = uri.getPath() == null ? "" : uri.getPath().replaceAll("/+$", "");
			return uri.getHost().toLowerCase(Locale.ROOT) + path;
		} catch (IllegalArgumentException exception) {
			return safe(job.getUrl()).toLowerCase(Locale.ROOT);
		}
	}

	private static String pathSegment(String url, String prefix) {
		try {
			String path = URI.create(url).getPath();
			int start = path.indexOf(prefix);
			if (start < 0) {
				return null;
			}
			String segment = path.substring(start + prefix.length()).split("/")[0];
			return segment.isEmpty() ? null : segment;
		} catch (IllegalArgumentException exception) {
			return null;
		}
	}

	private static String joinNonEmpty(String... values) {
		List<String> parts = new ArrayList<>();
		for (String value : values) {
			addText(parts, value);
		}
		return parts.isEmpty() ? null : String.join(", ", parts);
	}

	private static String firstNonEmpty(String... values) {
		for (String value : values) {
			if (value != null && !value.trim().isEmpty()) {
				return value.trim();
			}
		}
		return null;
	}

	private List<JobPosting> parseRss(String body, String source, String feedUrl)
			throws IOException, SAXException, ParserConfigurationException {
		DocumentBuilderFactory factory = secureXmlFactory();
		org.w3c.dom.Document document = factory.newDocumentBuilder()
				.parse(new ByteArrayInputStream(body.getBytes(StandardCharsets.UTF_8)));
		NodeList items = document.getElementsByTagName("item");
		List<JobPosting> jobs = new ArrayList<>();
		String base = URI.create(feedUrl).resolve("/").toString();
		for (int i = 0; i < items.getLength(); i++) {
			Node node = items.item(i);
			if (!(node instanceof Element)) {
				continue;
			}
			Element item = (Element) node;
			String titleWithCompany = childText(item, "title");
			String url = absoluteUrl(childText(item, "link"), base);
			if (titleWithCompany == null || url == null) {
				continue;
			}
			int separator = titleWithCompany.indexOf(':');
			String company = separator > 0 ? titleWithCompany.substring(0, separator).trim() : null;
			String title = separator > 0 ? titleWithCompany.substring(separator + 1).trim() : titleWithCompany;
			String location = firstNonEmpty(childText(item, "region"), childText(item, "location"));
			String category = childText(item, "category");
			List<String> tags = category == null ? Collections.emptyList()
					: Collections.singletonList(category);
			jobs.add(posting(url, title, company, location, url,
					childText(item, "description"), firstNonEmpty(childText(item, "pubDate"),
							childText(item, "date")), tags));
		}
		return jobs;
	}

	private static DocumentBuilderFactory secureXmlFactory() throws ParserConfigurationException {
		DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
		factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
		factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
		factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
		factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
		factory.setXIncludeAware(false);
		factory.setExpandEntityReferences(false);
		factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
		factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
		return factory;
	}

	private JobPosting rank(JobPosting job, CandidateProfile profile) {
		String text = String.join(" ", safe(job.getTitle()), safe(job.getCompany()),
				safe(job.getDescription()), String.join(" ", job.getTags())).toLowerCase(Locale.ROOT);
		List<String> matched = profile.getSkills() == null ? Collections.emptyList()
				: Arrays.stream(profile.getSkills().split(","))
						.map(String::trim).filter(skill -> !skill.isEmpty())
						.filter(skill -> containsTerm(text, skill))
						.distinct().collect(Collectors.toList());
		List<RelatedSkillMatch> relatedMatches = profile.getSkills() == null ? Collections.emptyList()
				: Arrays.stream(profile.getSkills().split(","))
						.map(String::trim).filter(skill -> !skill.isEmpty())
						.filter(skill -> matched.stream().noneMatch(skill::equalsIgnoreCase))
						.map(skill -> SkillRelationMatcher.findRelatedEvidence(skill, text)
								.map(keyword -> new RelatedSkillMatch(skill, keyword, keyword))
								.orElse(null))
						.filter(java.util.Objects::nonNull)
						.collect(Collectors.toList());
		int score = Math.min(70, matched.size() * 10 + relatedMatches.size() * 5);
		String title = safe(job.getTitle()).toLowerCase(Locale.ROOT);
		long roleTermsMatched = Arrays.stream(safe(profile.getTargetRole()).toLowerCase(Locale.ROOT)
				.split("[^a-z0-9]+"))
				.filter(term -> term.length() > 2)
				.distinct()
				.filter(term -> containsTerm(title, term))
				.count();
		score += (int) Math.min(20, roleTermsMatched * 5);
		Double requiredExperience = requiredExperienceYears(text);
		if (requiredExperience != null && profile.getExperienceYears() > 0) {
			score += profile.getExperienceYears() >= requiredExperience ? 10 : -10;
		}
		List<String> resumeSkills = profile.getSkills() == null ? Collections.emptyList()
				: Arrays.stream(profile.getSkills().split(","))
						.map(String::trim).filter(skill -> !skill.isEmpty())
						.distinct().collect(Collectors.toList());
		List<String> missingSkills = resumeSkills.stream()
				.filter(skill -> matched.stream().noneMatch(skill::equalsIgnoreCase)
						&& relatedMatches.stream().noneMatch(
								relation -> relation.resumeSkill().equalsIgnoreCase(skill)))
				.collect(Collectors.toList());
		return new JobPosting(job.getId(), job.getTitle(), job.getCompany(), job.getLocation(),
				job.getSource(), job.getUrl(), job.getDescription(), job.getPostedAt(), job.getTags(),
				Math.min(score, 100), matched,
				job.getTags() == null ? Collections.emptyList() : job.getTags(),
				missingSkills, relatedMatches.isEmpty()
						? "Exact skill matching" : "Exact and related skill matching",
				relatedMatches, relatedMatches.isEmpty()
						? "Exact resume skill terms found in the job title, description, or keywords."
						: "Related skill evidence: " + relatedMatches.stream()
								.map(relation -> relation.resumeSkill() + " is relevant to "
										+ relation.jobKeyword())
								.collect(Collectors.joining("; ")) + ".");
	}

	private static Double requiredExperienceYears(String jobText) {
		Matcher matcher = MINIMUM_EXPERIENCE_PATTERN.matcher(jobText);
		if (!matcher.find()) {
			matcher = EXPERIENCE_REQUIREMENT_PATTERN.matcher(jobText);
			if (!matcher.find()) {
				return null;
			}
		}
		try {
			return Double.valueOf(matcher.group(1));
		} catch (NumberFormatException exception) {
			return null;
		}
	}

	private static boolean isAllowedFeed(String value) {
		if (value == null) {
			return false;
		}
		try {
			URI uri = URI.create(value);
			if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getHost() == null) {
				return false;
			}
			String host = uri.getHost().toLowerCase(Locale.ROOT);
			return ALLOWED_FEED_HOSTS.contains(host)
					|| isSingleTenantSubdomain(host, "recruitee.com")
					|| isSingleTenantSubdomain(host, "breezy.hr")
					|| isSingleTenantSubdomain(host, "teamtailor.com")
					|| isSingleTenantSubdomain(host, "jobs.personio.de");
		} catch (IllegalArgumentException exception) {
			return false;
		}
	}

	private static boolean isSingleTenantSubdomain(String host, String domain) {
		if (!host.endsWith("." + domain)) {
			return false;
		}
		String tenant = host.substring(0, host.length() - domain.length() - 1);
		return tenant.matches("[a-z0-9-]+");
	}

	private static String absoluteUrl(String value, String base) {
		if (value == null || value.trim().isEmpty()) {
			return null;
		}
		try {
			URI uri = URI.create(value.trim());
			URI resolved = uri.isAbsolute() ? uri : URI.create(base).resolve(uri);
			if (!"https".equalsIgnoreCase(resolved.getScheme()) || resolved.getHost() == null) {
				return null;
			}
			return resolved.toString();
		} catch (IllegalArgumentException exception) {
			return null;
		}
	}

	private static String text(JsonNode item, String key) {
		JsonNode value = item.get(key);
		if (value == null || value.isNull()) {
			return null;
		}
		if (value.isArray()) {
			List<String> entries = new ArrayList<>();
			value.forEach(entry -> {
				if (entry.isValueNode() && !entry.asText().trim().isEmpty()) {
					entries.add(entry.asText().trim());
				}
			});
			return entries.isEmpty() ? null : String.join(", ", entries);
		}
		if (!value.isValueNode()) {
			return null;
		}
		String text = value.asText().trim();
		return text.isEmpty() ? null : text;
	}

	private static void addText(List<String> values, String value) {
		if (value != null && !value.trim().isEmpty()) {
			values.add(value.trim());
		}
	}

	private static void addArrayText(List<String> values, JsonNode node) {
		if (node != null && node.isArray()) {
			node.forEach(value -> addText(values, value.isValueNode() ? value.asText() : null));
		}
	}

	private static String childText(Element parent, String name) {
		NodeList nodes = parent.getElementsByTagName(name);
		if (nodes.getLength() == 0) {
			return null;
		}
		String value = nodes.item(0).getTextContent();
		return value == null || value.trim().isEmpty() ? null : value.trim();
	}

	private static boolean containsTerm(String text, String term) {
		String normalizedTerm = term.toLowerCase(Locale.ROOT);
		int index = text.indexOf(normalizedTerm);
		while (index >= 0) {
			int end = index + normalizedTerm.length();
			boolean startsAtBoundary = index == 0 || !Character.isLetterOrDigit(text.charAt(index - 1));
			boolean endsAtBoundary = end == text.length() || !Character.isLetterOrDigit(text.charAt(end));
			if (startsAtBoundary && endsAtBoundary) {
				return true;
			}
			index = text.indexOf(normalizedTerm, index + 1);
		}
		return false;
	}

	private static String safe(String value) {
		return value == null ? "" : value;
	}

	private static String resolveFeedUrl(String feedUrl, CandidateProfile profile) {
		if (feedUrl == null) {
			return null;
		}
		String firstSkill = profile.getSkills() == null ? null
				: Arrays.stream(profile.getSkills().split(","))
						.map(String::trim).filter(skill -> !skill.isEmpty()).findFirst().orElse(null);
		String role = safe(profile.getTargetRole()).trim();
		String skillQuery = firstNonEmpty(firstSkill, role);
		String roleQuery = firstNonEmpty(role, firstSkill);
		return feedUrl.replace("{skill}", UriUtils.encodeQueryParam(
						skillQuery == null ? "" : skillQuery, StandardCharsets.UTF_8))
				.replace("{role}", UriUtils.encodeQueryParam(
						roleQuery == null ? "" : roleQuery, StandardCharsets.UTF_8));
	}

	private record BoardDiscovery(Feed feed, Instant expiresAt) {
	}

	private record CachedSearch(LocalDateTime profileUpdatedAt, Instant refreshedAt,
			JobSearchResponse response) {
	}

	private record FeedFetchResult(List<JobPosting> jobs, JobFeedStatus status) {
	}

	private static boolean isRecent(String postedAt, Instant cutoff) {
		if (postedAt == null || postedAt.trim().isEmpty()) {
			return false;
		}
		Instant posted;
		try {
			posted = Instant.parse(postedAt);
		} catch (DateTimeParseException ignored) {
			try {
				posted = OffsetDateTime.parse(postedAt).toInstant();
			} catch (DateTimeParseException offsetException) {
				try {
					posted = java.time.ZonedDateTime.parse(postedAt).toInstant();
				} catch (DateTimeParseException zonedException) {
					try {
						posted = java.time.ZonedDateTime.parse(postedAt, DateTimeFormatter.RFC_1123_DATE_TIME)
								.toInstant();
					} catch (DateTimeParseException rfcException) {
						try {
							posted = java.time.LocalDate.parse(postedAt)
									.atStartOfDay().toInstant(ZoneOffset.UTC);
						} catch (DateTimeParseException dateException) {
							try {
								posted = java.time.LocalDateTime.parse(postedAt)
										.toInstant(ZoneOffset.UTC);
							} catch (DateTimeParseException localDateTimeException) {
								try {
									long epoch = Long.parseLong(postedAt);
									posted = Math.abs(epoch) >= 100_000_000_000L
											? Instant.ofEpochMilli(epoch) : Instant.ofEpochSecond(epoch);
								} catch (NumberFormatException | java.time.DateTimeException epochException) {
									return false;
								}
							}
						}
					}
				}
			}
		}
		Instant now = Instant.now();
		return !posted.isBefore(cutoff) && !posted.isAfter(now);
	}
}
