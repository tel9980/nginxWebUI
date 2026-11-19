package com.cym.utils;

import java.util.Set;

import org.noear.solon.Solon;

/**
 * Central place for hardening switches and sensitive-setting protections.
 *
 * <p>All flags can be overridden via Solon config (app.yml / BOOT_OPTIONS),
 * so bare-metal users can restore legacy behavior if needed.</p>
 */
public final class AppSecurityUtil {
	private static final Set<String> SENSITIVE_SETTING_KEYS = Set.of(
			"nginxExe", "nginxPath", "nginxDir",
			"mail_pass", "mail_user", "mail_host", "mail_from", "mail_port",
			"username", "password", "fulldomain", "subdomain",
			"remoteCode", "aes", "token", "autoKey", "creditKey");

	private AppSecurityUtil() {
	}

	public static boolean allowInAppUpdate() {
		return Solon.cfg().getBool("project.allowInAppUpdate", false);
	}

	public static boolean csrfEnabled() {
		return Solon.cfg().getBool("project.csrfProtection", true);
	}

	public static boolean isSensitiveSetting(String key) {
		return key != null && SENSITIVE_SETTING_KEYS.contains(key);
	}
}
