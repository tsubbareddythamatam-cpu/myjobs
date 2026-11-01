# MyJobs-API â€” resume-first job search

MyJobs-API extracts a private candidate profile from an uploaded resume, lists the requested job boards, and tracks jobs the candidate saves or applies to. It does not use the Excel dataset.

## Run locally

- Java 25 (JDK 25) and Maven 3.9 or later
- MySQL with a database-creation-capable account. Set `DB_PASSWORD` before starting the app; database credentials are not stored in source. Connector/J is configured to create the `naukri` database on first connection. If your MySQL account cannot create databases, run `CREATE DATABASE naukri;` in MySQL first.

From the repository root, run `mvn -f .\Job-API\pom.xml spring-boot:run` on Windows (or `mvn -f Job-API/pom.xml spring-boot:run` on Linux/macOS), then open `http://localhost:8088/`. Set `JWT_SECRET` first; the application refuses to start without a signing secret of at least 32 UTF-8 bytes (see [Accounts and authentication](#accounts-and-authentication)). Maven run enables native access for Netty on Java 25. For IDE or direct JAR launches, add `--enable-native-access=ALL-UNNAMED` to the JVM options (for example, `java --enable-native-access=ALL-UNNAMED -jar Job-API/target/MyJobs-API-0.0.1-SNAPSHOT.jar`). No sample resume or candidate profile is loaded by default: register/sign in, then upload a DOCX or PDF in the dashboard to create that account's profile. The original resume file is stored locally in `data/resumes` under the app's working directory (override with `JOB_RESUME_STORAGE_DIR`); extracted profile details are stored in the configured database. Uploading an identical resume keeps the existing local file; uploading a different resume replaces and deletes the old local file and overwrites all profile fields, so missing values are not carried forward. The dashboard can download the locally stored resume copy for attaching to applications. `JOB_RESUME_PATH` is ignored because a local startup file cannot be safely assigned to an account; upload the resume after signing in.

The 39 original job-board links are configured through the `jobsearch.listofwebsites` property list in `application.properties` and returned by `GET /api/websites`. The expanded source directory is available at `GET /api/websites/catalog` and includes public feeds, key-based providers, and the supplied company slugs. Links target provider/company career pages; LinkedIn, Indeed, and Naukri receive role/location search terms from the candidate profile. Indian options include Naukri, Apna, iimjobs, Hirist, Cutshort, Jora, and Jooble. Search the board grid by name or source type.

After a resume is uploaded, the dashboard extracts candidate skills from the resume's skills/technology sections, plus labeled role, location, and experience details, then searches the configured public job feeds. Skill extraction reads the uploaded resume rather than a fixed skills catalog. Search terms for supported query-based feeds are filled from the extracted role and skills. Configured public feeds are fetched concurrently (up to 12 requests at a time), with a four-second read timeout per request so a slow board does not delay every other feed. Matching job results are cached in Redis for 15 minutes and refreshed on expiry; a new resume upload invalidates the cache immediately, and the next search ranks listings against the newly extracted profile. Configure Redis with `REDIS_HOST` (default `localhost`), `REDIS_PORT` (default `6379`), and optionally `REDIS_PASSWORD`; `JOB_CACHE_REDIS_TTL_MS` controls the cache TTL. If Redis is unavailable, the service logs a warning and falls back to its process-local cache. On later visits, an existing saved profile also triggers a search automatically; **Find jobs** can be used to refresh results at any time. Existing public feeds include Remote OK, five We Work Remotely categories (programming, DevOps, full-stack, back-end, and front-end), Working Nomads, Jobicy, Remotive, Himalayas, Arbeitnow, and FreeHire. India-focused public ATS feeds now also query Meesho and Paytm through their public Lever postings APIs; neither requires API credentials. Himalayas pagination is followed for up to five pages, and overlapping listings are deduplicated by posting URL. FreeHire's public API is keyless; the app sends a project-identifying User-Agent and uses its published `posted_at` field for the seven-day check. The company source catalog's `auto` entries can be checked against supported public ATS endpoints (Greenhouse, Lever, Ashby, Workable, SmartRecruiters, Recruitee, Breezy, Rippling, Personio, and Teamtailor). This exhaustive discovery is disabled by default to avoid slowing job searches; set `JOB_COMPANY_DISCOVERY_ENABLED=true` to enable it. The first endpoint returning jobs is cached for 24 hours and used for subsequent refreshes; companies with no match are retried after six hours. Key-based providers appear in the catalog but require separate provider API credentials and are not queried by the live matcher yet. Detected skills and role are used to rank results; when a listing states an experience requirement, the candidate's extracted years adjust the match score up or down. Results without a valid posting date or older than seven days are excluded. Dashboard filters support location text, remote/hybrid/on-site/offshore work arrangements, minimum annual salary in INR where the posting explicitly states an INR/LPA amount, and a minimum match score. Unknown salary listings are excluded only when a salary minimum is set. Matching results include the source and original job link and can be saved to the tracker. The minimum resume-match filter is selectable from 10% to 100% in 10% increments, defaulting to 10% so more relevant listings are visible. Results are capped at 250 per search. Requests time out and failures are shown per source. Review each original posting and apply on its job board.
The search controller depends on the `JobFeedProvider` interface; source fetching, parsing, matching, and filtering remain behind that boundary. Add/remove sources through the configured feed list and provider adapters without changing the controller.

Jobicy's `/jobs-rss` URL currently responds with an HTML page rather than an RSS document, so the configured integration uses its JSON API instead. Jobvetta was also evaluated as an India-focused source; its API requires a bearer key (create one in the Jobvetta dashboard), and search summaries do not include the posting timestamp. Fetching details per result would consume its small daily request allowance, so it is not enabled as a live feed by default.

The employer ATS endpoints require a company-specific board slug; they are not global job-search APIs. To enable another board, append a feed entry to `application.properties` using the relevant type and a real employer slug, for example:

```properties
jobsearch.feeds[11].name=Example Greenhouse
jobsearch.feeds[11].type=greenhouse-json
jobsearch.feeds[11].url=https://boards-api.greenhouse.io/v1/boards/example/jobs?content=true
```

Supported ATS feed types are `greenhouse-json`, `lever-json`, `ashby-json`, `workable-json`, `smartrecruiters-json`, `recruitee-json`, `breezy-json`, `personio-xml`, `teamtailor-rss`, and `rippling-json`. Replace `example` with the employer's actual board slug. Where the source provides no parseable posting date, its listings are intentionally omitted by the seven-day filter.

## SMTP email configuration

The app sends account-verification and password-reset emails through SMTP. Job-save and tracker-status notifications use the same SMTP connection but remain opt-in. Configure environment variables before starting the app; do not put mailbox passwords in `application.properties`, commit them, or use them as job-board login credentials. Use an SMTP app password where your mail provider requires one.

SMTP defaults to Gmail on port 587 with authentication and STARTTLS enabled. Connection, read, and write timeouts are each five seconds. The SMTP username, host, port, authentication, and STARTTLS settings can be overridden with the environment variables shown below. For Gmail, use a Google App Password generated for the same account as `MAIL_USERNAME` (requires 2-Step Verification), not the normal account password. Enter the App Password without the display spaces Gmail may show.

On Windows PowerShell, for example:

```powershell
$env:MAIL_HOST = "smtp.gmail.com"
$env:MAIL_PORT = "587"
$env:MAIL_USERNAME = "your-address@example.com"
$env:MAIL_PASSWORD = "your-mail-provider-app-password"
$env:APP_BASE_URL = "http://localhost:8089"
# Optional tracker notifications
$env:JOB_NOTIFICATIONS_ENABLED = "true"
$env:JOB_NOTIFICATION_RECIPIENT = "your-address@example.com"
.\mvnw.cmd spring-boot:run
```

When starting from IntelliJ, add `MAIL_USERNAME` and `MAIL_PASSWORD` under **Run â†’ Edit Configurations â†’ Environment variables** for the Spring Boot run configuration, then stop and restart the app. Setting these variables in a separate PowerShell window does not update an already-running IntelliJ process. Never paste the App Password into application properties or commit it.

If Gmail responds with `535-5.7.8 Username and Password not accepted`, verify that the App Password is active, copied without spaces, and belongs to the configured username. Revoke any App Password shared in chat or source control and generate a new one. Restart the application after changing environment variables. A failed verification-email send does not delete the account; use the resend-verification option after fixing SMTP configuration.

`APP_BASE_URL` is used to construct verification and password-reset links. `EMAIL_VERIFICATION_TTL_SECONDS` defaults to 86400 and `PASSWORD_RESET_TTL_SECONDS` defaults to 1800. Both link types are signed JWTs, expire, and can be used once. Registration does not issue an access token until the email is verified. Forgot-password requests return the same response whether or not an account exists. `JOB_NOTIFICATIONS_ENABLED=true` is only needed for tracker notifications. The `Send test email` button or `POST /api/applications/test-email` checks the optional notification configuration. `MAIL_SMTP_AUTH` and `MAIL_SMTP_STARTTLS` default to `true`; change them only if your provider requires different SMTP settings. Job-board login and application submission remain manual.

## Optional job-source API credentials

The source directory includes key-based providers. Configure keys as environment variables (never in source files or the browser) to enable those live searches:

| Provider | Required environment variables |
|---|---|
| Adzuna (all listed countries) | `ADZUNA_APP_ID`, `ADZUNA_APP_KEY` |
| Jooble | `JOOBLE_API_KEY` |
| Careerjet | `CAREERJET_AFFILIATE_ID` |
| Findwork | `FINDWORK_API_KEY` |
| Reed | `REED_API_KEY` |
| USAJOBS | `USAJOBS_API_KEY`, `USAJOBS_USER_AGENT` |

Adzuna credentials are shared across its country-specific endpoints. The live matcher has adapters for these providers, The Muse, Hacker News hiring comments, the configured public feeds, and company ATS discovery. `landingjobs`, `remotewx`, `devitjobs-uk`, `graphql-jobs`, `reddit-forhire`, `reddit-remotejobs`, and `arbeitsamt` are included as source links but are not queried because their public endpoints are unavailable, require a different authentication contract, or could not be verified. Missing API credentials are reported in job-source status without exposing secret values.

## Accounts and authentication

Create an account or sign in from the dashboard. Registration sends an email-verification link, and only verified accounts can sign in. The page supports resending verification email and requesting a password reset; reset links let users choose a new password. Passwords are stored as BCrypt hashes, and successful login returns a short-lived access JWT that the browser keeps for the current tab session. Verification and reset links use separate, expiring, single-use signed JWTs. Each account has its own resume/profile, job ranking cache, and application tracker. Existing legacy profile/application data is not assigned to a new account. All API endpoints other than authentication and the public job-board list require a valid Bearer access token.

Set `JWT_SECRET` to a private, randomly generated secret with at least 32 UTF-8 bytes before starting the application, and optionally set `JWT_TTL_SECONDS` (default 3600). Do not commit the secret. For example, in PowerShell:

```powershell
$env:JWT_SECRET = [Convert]::ToBase64String([Security.Cryptography.RandomNumberGenerator]::GetBytes(48))
.\mvnw.cmd spring-boot:run
```

Registration and login are available at `POST /api/auth/register` and `POST /api/auth/login`, respectively, with JSON `{ "email": "...", "password": "..." }`. Email verification uses `POST /api/auth/verify-email` with `{ "token": "..." }`; verification resend uses `POST /api/auth/resend-verification` with `{ "email": "..." }`. Password recovery uses `POST /api/auth/forgot-password` with `{ "email": "..." }` and `POST /api/auth/reset-password` with `{ "token": "...", "newPassword": "..." }`. Use HTTPS whenever the app is exposed beyond localhost. The application database must allow the `app_user`, `auth_action_token`, and nullable `owner_id` column on `job_application` to be created/updated.

## Job results

Job match scores and highlighted matched skills are derived from the uploaded resume profile. Deterministic matching runs by default for faster job searches. To enable optional Spring AI refinement, set `JOB_AI_MATCHING_ENABLED=true`; it compares resume skills and technologies, tools, platforms, frameworks, and capabilities stated anywhere in the resume context with job descriptions and provider keywords. AI results separate direct matches from related capabilities; newly identified resume skills and related matches must be supported by verbatim resume evidence, and all matches require verbatim job-text evidence. Resume context is sent only to a loopback Ollama endpoint; otherwise, related AI results must satisfy a curated skill relationship. Exact matches found by deterministic comparison are preserved even if the AI omits them. For example, resume skills Java, JPA, Spring, and J2EE remain direct matches when those terms appear among job keywords. Jenkins experience is shown as a **partial related match** when a job requires CI/CD; it is not presented as an exact Jenkins match. Invalid or unavailable AI output falls back to deterministic exact and curated related-skill matching. When AI refinement is enabled, the app checks Ollama's `/api/tags` endpoint before an AI request and, if it is unavailable, skips AI calls for 60 seconds before checking again. Install Ollama and run `ollama pull qwen2.5:7b`; the default service URL is `http://localhost:11434`. Configure `OLLAMA_BASE_URL` or `OLLAMA_MODEL` as needed. AI refinement is bounded to the top 25 preliminary matches per feed refresh; all listings still include deterministic matched, related, missing, and job-keyword comparison lists. The dashboard paginates results and lets you display 10, 15, 20, 25, or 30 jobs per page; changing filters returns to the first page.

The optional `dependency-audit` Maven profile runs OWASP Dependency-Check and fails on any reported CVE. It requires an NVD API key in `NVD_API_KEY`; never commit the key. Run `mvn -f Job-API/pom.xml -Pdependency-audit verify` after setting that environment variable.

## API

| Method | Path | Purpose |
|---|---|---|
| `POST` | `/api/auth/register` | Create an account and send an email-verification link |
| `POST` | `/api/auth/login` | Authenticate and return a JWT |
| `POST` | `/api/auth/verify-email` | Verify an account using the emailed token |
| `POST` | `/api/auth/resend-verification` | Resend an email-verification link |
| `POST` | `/api/auth/forgot-password` | Request a password-reset link |
| `POST` | `/api/auth/reset-password` | Set a new password using the emailed token |
| `POST` | `/api/profile/resume` | Upload and extract a `.docx` or `.pdf` resume |
| `GET` | `/api/profile` | Read the parsed candidate profile |
| `GET` | `/api/profile/resume` | Download the saved resume copy |
| `GET` | `/api/websites` | List configured job-board links |
| `GET` | `/api/websites/catalog` | List public-feed, API-key, and auto-discovered company sources |
| `GET` | `/api/jobs?limit=100&minimumMatchScore=10&postedWithinDays=7` | Fetch recent live public-feed listings and rank against the resume |
| `GET` | `/api/applications` | List saved/tracked jobs |
| `POST` | `/api/applications` | Add a saved job and optionally send a notification |
| `PATCH` | `/api/applications/{id}` | Update status and optionally send a notification |
| `POST` | `/api/applications/test-email` | Send a test notification |
| `DELETE` | `/api/applications/{id}` | Remove a tracked job |

Application status values are `SAVED`, `APPLIED`, `INTERVIEW`, `OFFER`, `REJECTED`, and `WITHDRAWN`. Creating an application requires a non-empty job `description` of up to 4,000 characters; the dashboard shows it in the application tracker. Applicants review roles, sign in, and submit on the destination job board themselves; the app does not collect job-board credentials or automatically submit applications.
