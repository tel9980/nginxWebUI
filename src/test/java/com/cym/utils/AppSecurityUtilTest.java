package com.cym.utils;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class AppSecurityUtilTest {

	@Test
	void protectsSensitiveSettings() {
		assertTrue(AppSecurityUtil.isSensitiveSetting("nginxExe"));
		assertTrue(AppSecurityUtil.isSensitiveSetting("mail_pass"));
		assertTrue(AppSecurityUtil.isSensitiveSetting("creditKey"));
		assertFalse(AppSecurityUtil.isSensitiveSetting("serverName"));
		assertFalse(AppSecurityUtil.isSensitiveSetting(null));
	}
}
