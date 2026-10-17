package com.ihealthpharm.config;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import javax.annotation.PostConstruct;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import io.jsonwebtoken.impl.TextCodec;
import lombok.extern.slf4j.Slf4j;

/**
 * Checks every secret the application is configured with before it starts
 * serving, and refuses to start on one that cannot do its job.
 *
 * <p>Secrets reach the application from the environment rather than from
 * {@code application.properties}, which is tracked in git. That move is only
 * half the protection: a deployment that forgets to set a variable would
 * otherwise fall back to a default and come up looking healthy. Each rule
 * below says what happens in that case.
 *
 * <p><b>Adding a secret:</b> add one {@link SecretRule} to {@link #RULES}.
 * Nothing else changes — reading, checking, severity and wording are shared.
 *
 * <p>See {@code AUTH-HARDENING-PLAN.md} section 0.
 */
@Component
@Slf4j
public class StartupSecretsValidator {

	/**
	 * What to do about a secret that fails its rule.
	 *
	 * <p>{@code FATAL} is for a secret whose weakness silently defeats
	 * authentication, so running without it is worse than not running.
	 * {@code WARN} is for one whose absence breaks a feature but must not stop
	 * the pharmacy trading.
	 */
	private enum Severity {
		FATAL, WARN
	}

	/**
	 * Values seen in this codebase's history or shipped by the tutorials it was
	 * built from. Compared case-insensitively, so they are listed lower-case.
	 */
	private static final List<String> KNOWN_WEAK_VALUES = Arrays.asList(
			"mysecret", "secret", "changeme", "change-me", "password", "passwd",
			"ihealthpharm", "mykey", "test", "topsecret", "root", "admin");

	/**
	 * One rule per configured secret. This list is the whole specification —
	 * add a line when a new secret is introduced.
	 */
	private static final List<SecretRule> RULES = Arrays.asList(

			new SecretRule("jwt.signing.key.secret", "JWT_SIGNING_SECRET", Severity.FATAL,
					32, 64, true,
					"the key alone is enough to forge a session token for any user,"
							+ " bypassing login and two-factor entirely"),

			new SecretRule("spring.mail.password", "MAIL_PASSWORD", Severity.WARN,
					8, 16, false,
					"outgoing mail will fail, and two-factor codes are delivered by mail"),

			new SecretRule("spring.datasource.password", "DB_PASSWORD", Severity.WARN,
					8, 16, false,
					"the database is reachable with a guessable password"));

	private final Environment environment;

	@Autowired
	public StartupSecretsValidator(Environment environment) {
		this.environment = environment;
	}

	@PostConstruct
	void verifyConfiguredSecrets() {
		List<String> fatals = new ArrayList<>();

		for (SecretRule rule : RULES) {
			String value = environment.getProperty(rule.property);
			String problem = rule.findProblem(value);

			if (problem != null) {
				if (rule.severity == Severity.FATAL) {
					fatals.add(rule.explain(problem));
				} else {
					log.warn("{} Starting anyway.", rule.explain(problem));
				}
				continue;
			}

			Integer shortfall = rule.bytesIfBelowRecommended(value);
			if (shortfall != null) {
				log.warn("{} is {} bytes; {} is the matching length for its algorithm."
						+ " Regenerate with: openssl rand -base64 64",
						rule.envVar, shortfall, rule.recommendedBytes);
			} else {
				log.info("Secret {} accepted.", rule.envVar);
			}
		}

		if (!fatals.isEmpty()) {
			// Reported together so one restart surfaces every misconfiguration.
			throw new IllegalStateException(String.join(" | ", fatals));
		}
	}

	/**
	 * A secret, where its value comes from, and what makes that value
	 * unacceptable. Immutable; carries its own checking and wording so
	 * {@link #verifyConfiguredSecrets()} stays a loop.
	 */
	private static final class SecretRule {

		private final String property;
		private final String envVar;
		private final Severity severity;
		private final int minimumBytes;
		private final int recommendedBytes;

		/**
		 * True when the configured value is Base64 and the key is the bytes it
		 * decodes to — jjwt 0.9.1 reads signing keys that way, so length has to
		 * be measured after decoding.
		 */
		private final boolean base64Encoded;

		/** The consequence of leaving this secret weak, as a sentence clause. */
		private final String consequence;

		private SecretRule(String property, String envVar, Severity severity, int minimumBytes,
				int recommendedBytes, boolean base64Encoded, String consequence) {
			this.property = property;
			this.envVar = envVar;
			this.severity = severity;
			this.minimumBytes = minimumBytes;
			this.recommendedBytes = recommendedBytes;
			this.base64Encoded = base64Encoded;
			this.consequence = consequence;
		}

		/**
		 * @return why {@code value} is unusable, or {@code null} if it passes.
		 *         Phrased to follow the variable name: "… is not set".
		 */
		private String findProblem(String value) {
			if (value == null || value.trim().isEmpty()) {
				return "is not set";
			}
			String candidate = value.trim();
			if (KNOWN_WEAK_VALUES.contains(candidate.toLowerCase())) {
				return "is set to a known weak value";
			}
			int bytes = keyBytes(candidate);
			if (bytes < minimumBytes) {
				return "is only " + bytes + " bytes; at least " + minimumBytes + " are required";
			}
			return null;
		}

		/**
		 * @return the value's length when it is usable but shorter than ideal,
		 *         or {@code null} when it is at or above the recommended length.
		 *         Only meaningful for a value that already passed
		 *         {@link #findProblem(String)}.
		 */
		private Integer bytesIfBelowRecommended(String value) {
			int bytes = keyBytes(value.trim());
			return bytes < recommendedBytes ? Integer.valueOf(bytes) : null;
		}

		/** Length in bytes of the value as it will actually be used. */
		private int keyBytes(String candidate) {
			if (!base64Encoded) {
				return candidate.getBytes().length;
			}
			try {
				return TextCodec.BASE64.decode(candidate).length;
			} catch (RuntimeException e) {
				// Not Base64 — a hand-typed secret is used as its own bytes.
				return candidate.getBytes().length;
			}
		}

		/** The one place any of this is worded for a human. */
		private String explain(String problem) {
			return envVar + " " + problem + ", so " + consequence + "."
					+ " Set it in the environment"
					+ (minimumBytes >= 32 ? " (generate with: openssl rand -base64 64)" : "")
					+ ". See AUTH-HARDENING-PLAN.md section 0.";
		}
	}
}
