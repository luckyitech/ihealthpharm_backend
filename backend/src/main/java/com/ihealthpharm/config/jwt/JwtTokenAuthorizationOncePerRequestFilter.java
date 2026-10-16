package com.ihealthpharm.config.jwt;

import java.io.IOException;

import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;

@Component
public class JwtTokenAuthorizationOncePerRequestFilter extends OncePerRequestFilter {

	private final Logger logger = LoggerFactory.getLogger(this.getClass());

	@Autowired
	private UserDetailsService jwtInMemoryUserDetailsService;

	@Autowired
	private JwtTokenUtil jwtTokenUtil;

	@Value("${jwt.http.request.header}")
	private String tokenHeader;

	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
			throws ServletException, IOException {
		logger.debug("Authentication Request For '{}'", request.getRequestURL());

		final String requestTokenHeader = request.getHeader(this.tokenHeader);
		String username = null;
		String jwtToken = null;
		// The token is NOT logged. It is a bearer credential: anyone holding it
		// is the user until it expires, and logs are read by more people than
		// should be able to act as that user.
		if (requestTokenHeader != null && requestTokenHeader.startsWith("Bearer ")) {
			jwtToken = requestTokenHeader.substring(7);
			try {
				username = jwtTokenUtil.getUsernameFromToken(jwtToken);
			} catch (ExpiredJwtException e) {
				logger.debug("Token expired; continuing unauthenticated.");
			} catch (JwtException | IllegalArgumentException e) {
				// An unusable token is treated exactly like no token at all.
				//
				// It must not fail the request. This filter is a @Component, so
				// Spring Boot registers it for every request - including the
				// paths WebSecurity.ignoring() exempts, /authenticate among
				// them. Letting the exception escape therefore breaks LOGIN for
				// anyone whose browser is holding a token the server will not
				// accept.
				//
				// That is not hypothetical: rotating the signing key invalidates
				// every token in circulation at once, and the Angular
				// interceptor attaches the stale one to the login request. An
				// uncaught SignatureException here would lock out every user on
				// the deploy that rotates the key, with no way back except
				// clearing browser storage by hand.
				//
				// A request carrying a bad token simply arrives unauthenticated:
				// public endpoints serve it, protected ones answer 401.
				logger.debug("Unusable token ignored: {}", e.getClass().getSimpleName());
			}
		}

		logger.debug("JWT_TOKEN_USERNAME_VALUE '{}'", username);
		if (username != null && SecurityContextHolder.getContext().getAuthentication() == null) {

			UserDetails userDetails = this.jwtInMemoryUserDetailsService.loadUserByUsername(username);

			if (jwtTokenUtil.validateToken(jwtToken, userDetails)) {
				UsernamePasswordAuthenticationToken usernamePasswordAuthenticationToken = new UsernamePasswordAuthenticationToken(
						userDetails, null, userDetails.getAuthorities());
				usernamePasswordAuthenticationToken
						.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
				SecurityContextHolder.getContext().setAuthentication(usernamePasswordAuthenticationToken);
			}
		}

		chain.doFilter(request, response);
	}
}
