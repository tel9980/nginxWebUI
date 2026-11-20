package com.cym.utils;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;

import cn.hutool.core.util.StrUtil;

/**
 * Validates file-referencing nginx directives before configuration is written
 * or tested. The goal is to keep generated/published configuration inside the
 * project home directory, so a compromised admin session cannot use
 * {@code include /etc/...} or similar directives to read arbitrary files.
 *
 * <p>Network targets ({@code proxy_pass http://...}, upstream names) are not
 * file paths and are deliberately left alone. Only {@code unix:} sockets are
 * treated as paths.</p>
 */
public final class ConfigPathValidator {

	private static final List<String> PATH_DIRECTIVES = Arrays.asList(
			"include", "auth_basic_user_file",
			"ssl_certificate", "ssl_certificate_key",
			"root", "alias",
			"access_log", "error_log",
			"client_body_temp_path", "proxy_temp_path",
			"fastcgi_temp_path", "uwsgi_temp_path", "scgi_temp_path");

	private static final String DIRECTIVE_PATTERN = "\\b(" + String.join("|", PATH_DIRECTIVES)
			+ "|proxy_pass)\\s+([^;{}]+);";

	private ConfigPathValidator() {
	}

	/**
	 * Validates the final rendered configuration text.
	 *
	 * @param content       main config or a conf.d fragment
	 * @param configBaseDir directory relative include paths resolve against
	 * @param roots         normalized absolute paths that are allowed (project home
	 *                      plus any explicitly configured extra roots)
	 * @throws IOException when a directive points outside the allowed roots
	 */
	public static void validate(String content, File configBaseDir, List<Path> roots) throws IOException {
		if (StrUtil.isEmpty(content) || roots == null || roots.isEmpty()) {
			return;
		}
		java.util.regex.Matcher matcher = java.util.regex.Pattern.compile(DIRECTIVE_PATTERN).matcher(stripComments(content));
		while (matcher.find()) {
			String directive = matcher.group(1);
			String rawValue = matcher.group(2).trim();
			if (StrUtil.isEmpty(rawValue)) {
				continue;
			}
			String value = unquote(firstToken(rawValue));
			if (StrUtil.isEmpty(value)) {
				continue;
			}

			if ("proxy_pass".equals(directive)) {
				if (!value.startsWith("unix:")) {
					continue;
				}
				value = value.substring("unix:".length());
			}
			if (value.indexOf('$') >= 0) {
				throw violation(directive, value, roots, "variables are not allowed in file paths");
			}
			checkPath(directive, value, configBaseDir, roots);
		}
	}

	/** Validates a conf.d file name supplied by the caller. */
	public static void validateChildName(String name) throws IOException {
		if (StrUtil.isEmpty(name)) {
			throw new IOException("unsafe conf.d file name: empty");
		}
		if (name.contains("/") || name.contains("\\") || name.contains("..")
				|| name.startsWith(".") || new File(name).isAbsolute()) {
			throw new IOException("unsafe conf.d file name: " + name);
		}
	}

	private static void checkPath(String directive, String value, File baseDir, List<Path> roots) throws IOException {
		String candidate = value;
		int wildcard = indexOfWildcard(candidate);
		if (wildcard >= 0) {
			candidate = candidate.substring(0, wildcard);
			if (StrUtil.isEmpty(candidate)) {
				throw violation(directive, value, roots, "wildcard without a literal parent directory");
			}
			if (!candidate.endsWith("/") && !candidate.endsWith("\\")) {
				int slash = Math.max(candidate.lastIndexOf('/'), candidate.lastIndexOf('\\'));
				candidate = slash >= 0 ? candidate.substring(0, slash) : ".";
			}
		}

		File file = new File(candidate);
		if (!file.isAbsolute()) {
			file = new File(baseDir == null ? new File(".") : baseDir, candidate);
		}
		Path resolved;
		try {
			resolved = file.getCanonicalFile().toPath().normalize();
		} catch (IOException e) {
			throw violation(directive, value, roots, "cannot resolve path");
		}
		for (Path root : roots) {
			if (resolved.startsWith(root)) {
				return;
			}
		}
		throw violation(directive, value, roots, "path outside allowed roots");
	}

	private static int indexOfWildcard(String value) {
		int star = value.indexOf('*');
		int question = value.indexOf('?');
		int bracket = value.indexOf('[');
		int result = -1;
		for (int index : new int[] { star, question, bracket }) {
			if (index >= 0 && (result < 0 || index < result)) {
				result = index;
			}
		}
		return result;
	}

	private static String firstToken(String value) {
		String trimmed = value.trim();
		int space = trimmed.indexOf(' ');
		if (space > 0) {
			return trimmed.substring(0, space);
		}
		int tab = trimmed.indexOf('\t');
		return tab > 0 ? trimmed.substring(0, tab) : trimmed;
	}

	private static String unquote(String value) {
		String result = value.trim();
		if (result.length() >= 2
				&& ((result.startsWith("\"") && result.endsWith("\""))
				|| (result.startsWith("'") && result.endsWith("'")))) {
			result = result.substring(1, result.length() - 1);
		}
		return result;
	}

	private static String stripComments(String content) {
		StringBuilder sb = new StringBuilder(content.length());
		for (String line : content.split("\\R")) {
			int comment = -1;
			boolean inSingle = false;
			boolean inDouble = false;
			for (int i = 0; i < line.length(); i++) {
				char c = line.charAt(i);
				if (c == '\'' && !inDouble) {
					inSingle = !inSingle;
				} else if (c == '"' && !inSingle) {
					inDouble = !inDouble;
				} else if (c == '#' && !inSingle && !inDouble && (i == 0 || Character.isWhitespace(line.charAt(i - 1)))) {
					comment = i;
					break;
				}
			}
			sb.append(comment >= 0 ? line.substring(0, comment) : line).append('\n');
		}
		return sb.toString();
	}

	private static IOException violation(String directive, String value, List<Path> roots, String reason) {
		return new IOException("unsafe config path: directive=" + directive + ", path=" + value
				+ ", reason=" + reason + ", allowedRoots=" + roots);
	}
}
