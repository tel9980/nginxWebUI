package com.cym.utils;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.regex.Pattern;

import org.noear.solon.Solon;
import org.noear.solon.core.handle.Context;

import cn.hutool.core.util.StrUtil;

/**
 * Resolves the request authority (host[:port]) used when building context
 * paths and remote-management URLs.
 *
 * <p>By default no proxy header is trusted: the authority is read from the
 * request URL only. X-Forwarded-Host / X-Forwarded-Port are consulted only
 * when project.trustedProxyHeader is true AND the resulting host is present
 * in project.allowedHosts. An empty allow-list therefore also means
 * "never trust forwarded headers".</p>
 */
public final class ProxyTrustUtil {
	private static final String HEADER_X_FORWARDED_HOST = "X-Forwarded-Host";
	private static final String HEADER_X_FORWARDED_PORT = "X-Forwarded-Port";
	private static final Pattern AUTHORITY_PATTERN = Pattern.compile("^[A-Za-z0-9._\\[\\]-]+(?::\\d{1,5})?$");

	private ProxyTrustUtil() {
	}

	public static boolean trustProxyHeaders() {
		return Solon.cfg().getBool("project.trustedProxyHeader", false);
	}

	public static Set<String> allowedHosts() {
		Set<String> hosts = new LinkedHashSet<>();
		String configured = Solon.cfg().get("project.allowedHosts");
		if (StrUtil.isEmpty(configured)) {
			return hosts;
		}
		for (String item : configured.split(",")) {
			String host = item.trim().toLowerCase();
			if (StrUtil.isNotEmpty(host)) {
				hosts.add(host);
			}
		}
		return hosts;
	}

	/** Extracts host[:port] from an absolute request URL using URI parsing. */
	public static String authorityFromUrl(String url) {
		if (StrUtil.isEmpty(url)) {
			return "";
		}
		try {
			URI uri = new URI(url);
			String host = uri.getHost();
			if (StrUtil.isEmpty(host)) {
				return "";
			}
			int port = uri.getPort();
			return port > 0 ? host + ":" + port : host;
		} catch (URISyntaxException e) {
			return "";
		}
	}

	/** A host is accepted when no allow-list is configured or when it is listed. */
	public static boolean isHostAllowed(String authority) {
		if (StrUtil.isEmpty(authority) || !AUTHORITY_PATTERN.matcher(authority).matches()) {
			return false;
		}
		Set<String> allowed = allowedHosts();
		if (allowed.isEmpty()) {
			return true;
		}
		String host = stripPort(authority).toLowerCase();
		return allowed.contains(host) || allowed.contains("[" + host + "]");
	}

	/**
	 * Returns the trusted authority, e.g. "nginx.example.com:8080". Returns an
	 * empty string when the request host is rejected by the allow-list.
	 */
	public static String resolveTrustedAuthority(Context ctx) {
		String urlAuthority = authorityFromUrl(ctx.url());
		if (!isHostAllowed(urlAuthority)) {
			// Never fall back to a client-controlled header.
			return "";
		}
		Set<String> allowed = allowedHosts();
		if (!trustProxyHeaders() || allowed.isEmpty()) {
			return urlAuthority;
		}

		String forwarded = ctx.header(HEADER_X_FORWARDED_HOST);
		if (StrUtil.isEmpty(forwarded)) {
			return urlAuthority;
		}
		if (forwarded.indexOf(',') >= 0) {
			forwarded = forwarded.substring(0, forwarded.indexOf(',')).trim();
		}

		String candidate = forwarded;
		if (!hasPort(candidate)) {
			String forwardedPort = ctx.header(HEADER_X_FORWARDED_PORT);
			if (StrUtil.isNotEmpty(forwardedPort)) {
				String port = forwardedPort.trim();
				if (!port.matches("\\d{1,5}")) {
					return urlAuthority;
				}
				int portNumber = Integer.parseInt(port);
				if (portNumber < 1 || portNumber > 65535) {
					return urlAuthority;
				}
				candidate = candidate + ":" + portNumber;
			}
		}
		if (!isHostAllowed(candidate)) {
			return urlAuthority;
		}
		return candidate;
	}

	private static boolean hasPort(String authority) {
		return !authority.equals(stripPort(authority));
	}

	/** Removes a trailing ":port" from a host:port authority (IPv6 aware). */
	private static String stripPort(String authority) {
		int colon = authority.lastIndexOf(':');
		if (colon > 0 && authority.substring(colon + 1).matches("\\d{1,5}")) {
			return authority.substring(0, colon);
		}
		return authority;
	}
}
