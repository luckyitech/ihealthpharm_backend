package com.ihealthpharm.config.jwt;

import java.util.regex.Pattern;

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * BCrypt-backed {@link PasswordEncoder} that also recognises the plaintext
 * passwords already present in employee_credentials from before encryption
 * was introduced, so existing accounts keep working after import instead of
 * being locked out. Every password written going forward is always a BCrypt
 * hash; {@link #isBCryptHash(String)} lets callers detect and upgrade any
 * legacy plaintext value they encounter.
 */
public class LegacyAwarePasswordEncoder implements PasswordEncoder {

	private static final Pattern BCRYPT_PATTERN = Pattern.compile("^\\$2[aby]\\$\\d{2}\\$.{53}$");

	private final BCryptPasswordEncoder delegate = new BCryptPasswordEncoder();

	@Override
	public String encode(CharSequence rawPassword) {
		return delegate.encode(rawPassword);
	}

	@Override
	public boolean matches(CharSequence rawPassword, String encodedPassword) {
		if (isBCryptHash(encodedPassword)) {
			return delegate.matches(rawPassword, encodedPassword);
		}
		// Legacy plaintext row: compare directly instead of failing the login.
		return rawPassword != null && rawPassword.toString().equals(encodedPassword);
	}

	public static boolean isBCryptHash(String value) {
		return value != null && BCRYPT_PATTERN.matcher(value).matches();
	}
}
