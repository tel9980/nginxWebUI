package com.cym.utils;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ConfigPathValidatorTest {

	@TempDir
	Path tempDir;

	private Path home() throws IOException {
		Path home = tempDir.resolve("home");
		Files.createDirectories(home.resolve("conf.d"));
		return home.toAbsolutePath().normalize();
	}

	@Test
	void allowsRelativeIncludesInsideHome() throws IOException {
		Path home = home();
		String conf = "http {\n  include mime.types;\n  include conf.d/*.conf;\n}\n";
		assertDoesNotThrow(() -> ConfigPathValidator.validate(conf, home.toFile(), Collections.singletonList(home)));
	}

	@Test
	void rejectsAbsoluteIncludeOutsideHome() throws IOException {
		Path home = home();
		String conf = "http { include /etc/nginx/mime.types; }";
		IOException e = assertThrows(IOException.class,
				() -> ConfigPathValidator.validate(conf, home.toFile(), Collections.singletonList(home)));
		assertTrue(e.getMessage().contains("/etc/nginx/mime.types"));
	}

	@Test
	void rejectsTraversalViaRelativePath() throws IOException {
		Path home = home();
		String conf = "http { ssl_certificate ../outside.pem; }";
		assertThrows(IOException.class,
				() -> ConfigPathValidator.validate(conf, home.toFile(), Collections.singletonList(home)));
	}

	@Test
	void ignoresNetworkProxyPassButChecksUnixSocket() throws IOException {
		Path home = home();
		String conf = "location / { proxy_pass http://127.0.0.1:8080; }\n"
				+ "location /sock { proxy_pass unix:/var/run/external.sock; }";
		assertThrows(IOException.class,
				() -> ConfigPathValidator.validate(conf, home.toFile(), Collections.singletonList(home)));
		String safe = "location /sock { proxy_pass unix:conf.d/app.sock; }";
		assertDoesNotThrow(() -> ConfigPathValidator.validate(safe, home.toFile(), Collections.singletonList(home)));
	}

	@Test
	void ignoresCommentsAndHonoursExtraRoot() throws IOException {
		Path home = home();
		Path extra = Files.createDirectories(tempDir.resolve("extra")).toAbsolutePath().normalize();
		String conf = "# include " + extra.resolve("blocked.conf") + ";\n"
				+ "include " + extra.resolve("allowed.conf") + ";\n";
		assertThrows(IOException.class,
				() -> ConfigPathValidator.validate(conf, home.toFile(), Collections.singletonList(home)));
		assertDoesNotThrow(() -> ConfigPathValidator.validate(conf, home.toFile(), java.util.Arrays.asList(home, extra)));
	}

	@Test
	void rejectsUnsafeChildNames() {
		assertDoesNotThrow(() -> ConfigPathValidator.validateChildName("site.conf"));
		assertThrows(IOException.class, () -> ConfigPathValidator.validateChildName("../site.conf"));
		assertThrows(IOException.class, () -> ConfigPathValidator.validateChildName("/etc/passwd"));
		assertThrows(IOException.class, () -> ConfigPathValidator.validateChildName("a/b.conf"));
	}

	@Test
	void detectsVariablesInFilePaths() throws IOException {
		Path home = home();
		String conf = "http { ssl_certificate $ssl_path; }";
		IOException e = assertThrows(IOException.class,
				() -> ConfigPathValidator.validate(conf, home.toFile(), Collections.singletonList(home)));
		assertTrue(e.getMessage().contains("variables"));
	}

	@Test
	void allowsQuotedPathsInsideHome() throws IOException {
		Path home = home();
		String conf = "http { ssl_certificate \"" + home.resolve("cert.pem") + "\"; }";
		assertDoesNotThrow(() -> ConfigPathValidator.validate(conf, home.toFile(), Collections.singletonList(home)));
	}

	@Test
	void keepsOriginalDirectiveNames() throws IOException {
		Path home = home();
		String conf = "http { auth_basic_user_file /etc/nginx/.htpasswd; }";
		IOException e = assertThrows(IOException.class,
				() -> ConfigPathValidator.validate(conf, home.toFile(), Collections.singletonList(home)));
		assertTrue(e.getMessage().contains("auth_basic_user_file"));
		assertFalse(e.getMessage().isEmpty());
		assertEquals(true, e.getMessage().startsWith("unsafe config path"));
	}
}
