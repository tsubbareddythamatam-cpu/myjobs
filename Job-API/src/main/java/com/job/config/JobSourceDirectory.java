package com.job.config;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.springframework.stereotype.Component;

@Component
public class JobSourceDirectory {
	private static final String SOURCES = """
			feed,remotive
			feed,remoteok
			feed,weworkremotely
			feed,jobicy
			feed,himalayas
			feed,arbeitnow
			feed,arbeitnow-uk
			feed,workingnomads
			feed,themuse
			feed,meesho
			feed,paytm
			feed,landingjobs
			feed,remotewx
			feed,devitjobs-uk
			feed,graphql-jobs
			feed,hn-whoishiring
			feed,reddit-forhire
			feed,reddit-remotejobs
			feed,arbeitsamt
			key,adzuna-in
			key,adzuna-gb
			key,adzuna-us
			key,adzuna-au
			key,adzuna-ca
			key,adzuna-de
			key,adzuna-fr
			key,adzuna-nl
			key,adzuna-sg
			key,adzuna-za
			key,jooble
			key,careerjet
			key,findwork
			key,reed
			key,usajobs
			auto,stripe
			auto,airbnb
			auto,databricks
			auto,cloudflare
			auto,coinbase
			auto,figma
			auto,discord
			auto,dropbox
			auto,instacart
			auto,lyft
			auto,pinterest
			auto,reddit
			auto,robinhood
			auto,twilio
			auto,okta
			auto,datadog
			auto,mongodb
			auto,elastic
			auto,gitlab
			auto,asana
			auto,brex
			auto,gusto
			auto,hubspot
			auto,squarespace
			auto,postman
			auto,razorpay
			auto,samsara
			auto,toast
			auto,duolingo
			auto,airtable
			auto,webflow
			auto,fivetran
			auto,anthropic
			auto,openai
			auto,notion
			auto,ramp
			auto,linear
			auto,deel
			auto,vercel
			auto,spotify
			auto,palantir
			auto,plaid
			auto,zoox
			auto,huggingface
			auto,bunq
			auto,channable
			auto,zego
			auto,rippling
			auto,nvidia
			auto,adobe
			auto,nike
			auto,walmart
			auto,salesforce
			auto,capitalone
			auto,mastercard
			auto,visa
			auto,paypal
			auto,intuit
			auto,workday
			auto,servicenow
			auto,atlassian
			auto,shopify
			auto,uber
			auto,meesho
			auto,cred
			auto,groww
			auto,phonepe
			auto,swiggy
			auto,eternal
			auto,flipkart
			auto,freshworks
			auto,zoho
			auto,juspay
			auto,dream11
			auto,myntra
			auto,paytm
			auto,cleartax
			auto,zerodha
			auto,upstox
			auto,slice
			auto,navi
			auto,jupiter
			auto,lenskart
			auto,urbancompany
			auto,delhivery
			auto,olacabs
			auto,bharatpe
			auto,chargebee
			auto,browserstack
			auto,hasura
			auto,clevertap
			auto,whatfix
			auto,sprinklr
			auto,druva
			auto,thoughtspot
			auto,mindtickle
			auto,grab
			auto,gojek
			auto,sea
			auto,bosch
			""";

	private static final Map<String, String> FEED_URLS = Map.ofEntries(
			Map.entry("remotive", "https://remotive.com/remote-jobs"),
			Map.entry("remoteok", "https://remoteok.com"),
			Map.entry("weworkremotely", "https://weworkremotely.com"),
			Map.entry("jobicy", "https://jobicy.com/jobs"),
			Map.entry("himalayas", "https://himalayas.app/jobs"),
			Map.entry("arbeitnow", "https://www.arbeitnow.com"),
			Map.entry("arbeitnow-uk", "https://www.arbeitnow.com"),
			Map.entry("workingnomads", "https://www.workingnomads.com/jobs"),
			Map.entry("themuse", "https://www.themuse.com/jobs"),
			Map.entry("meesho", "https://www.meesho.com"),
			Map.entry("paytm", "https://paytm.com"),
			Map.entry("landingjobs", "https://landing.jobs"),
			Map.entry("remotewx", "https://remotewx.com"),
			Map.entry("devitjobs-uk", "https://devitjobs.uk"),
			Map.entry("graphql-jobs", "https://graphql.jobs"),
			Map.entry("hn-whoishiring", "https://news.ycombinator.com"),
			Map.entry("reddit-forhire", "https://www.reddit.com/r/forhire"),
			Map.entry("reddit-remotejobs", "https://www.reddit.com/r/remotejobs"),
			Map.entry("arbeitsamt", "https://www.arbeitsagentur.de/jobsuche"));
	private static final Map<String, String> KEY_SOURCE_URLS = Map.ofEntries(
			Map.entry("adzuna", "https://www.adzuna.com"),
			Map.entry("jooble", "https://jooble.org"),
			Map.entry("careerjet", "https://www.careerjet.com"),
			Map.entry("findwork", "https://findwork.dev"),
			Map.entry("reed", "https://www.reed.co.uk"),
			Map.entry("usajobs", "https://www.usajobs.gov"));
	private static final Map<String, String> COMPANY_DOMAINS = Map.ofEntries(
			Map.entry("airbnb", "airbnb.com"),
			Map.entry("datadog", "datadoghq.com"),
			Map.entry("huggingface", "huggingface.co"),
			Map.entry("capitalone", "capitalone.com"),
			Map.entry("olacabs", "olacabs.com"),
			Map.entry("sea", "sea.com"),
			Map.entry("grab", "grab.com"),
			Map.entry("gojek", "gojek.com"),
			Map.entry("nvidia", "nvidia.com"),
			Map.entry("bosch", "bosch.com"),
			Map.entry("zoox", "zoox.com"),
			Map.entry("eternal", "eternal.com"));
	private static final Map<String, String> COMPANY_CAREERS_PATHS = Map.ofEntries(
			Map.entry("airbnb", "/careers"),
			Map.entry("stripe", "/jobs"),
			Map.entry("discord", "/jobs"),
			Map.entry("dropbox", "/jobs"),
			Map.entry("pinterest", "/careers"),
			Map.entry("huggingface", "/jobs"),
			Map.entry("nvidia", "/careers"),
			Map.entry("nike", "/careers"),
			Map.entry("walmart", "/careers"),
			Map.entry("salesforce", "/careers"),
			Map.entry("capitalone", "/careers"),
			Map.entry("visa", "/careers"),
			Map.entry("zoox", "/careers"),
			Map.entry("grab", "/careers"),
			Map.entry("gojek", "/careers"));

	public List<Source> getSources() {
		Map<String, Source> uniqueSources = new LinkedHashMap<>();
		for (String row : SOURCES.strip().split("\\R")) {
			String[] columns = row.split(",", 2);
			String kind = columns[0].trim();
			String slug = columns[1].trim();
			Source source = new Source(kind, slug, displayName(slug), sourceUrl(kind, slug),
					sourceStatus(kind, slug));
			uniqueSources.putIfAbsent(kind + ":" + slug, source);
		}
		return new ArrayList<>(uniqueSources.values());
	}

	public List<String> getAutoCompanySlugs() {
		return getSources().stream().filter(source -> "auto".equals(source.kind()))
				.map(Source::slug).toList();
	}

	public List<Source> getSourcesByKind(String kind) {
		return getSources().stream().filter(source -> kind.equals(source.kind())).toList();
	}

	private static String sourceUrl(String kind, String slug) {
		if ("feed".equals(kind)) {
			return FEED_URLS.get(slug);
		}
		if ("key".equals(kind)) {
			String provider = slug.startsWith("adzuna-") ? "adzuna" : slug;
			return KEY_SOURCE_URLS.get(provider);
		}
		String domain = COMPANY_DOMAINS.getOrDefault(slug, slug.replace("-", "") + ".com");
		String path = COMPANY_CAREERS_PATHS.getOrDefault(slug, "/careers");
		return "https://" + domain + path;
	}

	private static String sourceStatus(String kind, String slug) {
		if ("key".equals(kind)) {
			return "API key required";
		}
		if ("auto".equals(kind)) {
			return "ATS auto-discovery enabled; company careers link";
		}
		if (List.of("remotive", "remoteok", "weworkremotely", "jobicy", "himalayas",
				"arbeitnow", "arbeitnow-uk", "workingnomads", "themuse", "meesho", "paytm",
				"hn-whoishiring").contains(slug)) {
			return "Public feed configured";
		}
		return "Source link; live feed not configured";
	}

	private static String displayName(String slug) {
		String[] words = slug.split("-");
		StringBuilder name = new StringBuilder();
		for (String word : words) {
			if (name.length() > 0) {
				name.append(' ');
			}
			if ("uk".equals(word) || "us".equals(word) || "in".equals(word)
					|| "gb".equals(word) || "au".equals(word) || "ca".equals(word)
					|| "de".equals(word) || "fr".equals(word) || "nl".equals(word)
					|| "sg".equals(word) || "za".equals(word)) {
				name.append(word.toUpperCase(Locale.ROOT));
			} else {
				name.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1));
			}
		}
		return name.toString();
	}

	public record Source(String kind, String slug, String name, String url, String status) {
	}
}
