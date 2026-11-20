package com.cym.utils;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class ProxyTrustUtilTest {

	@Test
	void extractsAuthorityByUriParsing() {
		assertEquals("example.com", ProxyTrustUtil.authorityFromUrl("http://example.com/path"));
		assertEquals("example.com:8443", ProxyTrustUtil.authorityFromUrl("https://example.com:8443/x"));
		assertEquals("", ProxyTrustUtil.authorityFromUrl("not a url"));
	}
}
