package com.cym.utils;

import java.io.File;
import java.io.IOException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import cn.hutool.core.util.StrUtil;

/**
 * Docker/personal-deployment safe process helpers.
 *
 * <p>Upstream nginxWebUI scans every Java process listed by {@code ps} and
 * kills anything whose command line looks like nginxWebUI. Inside a container
 * that can kill a sibling service whenever they share a PID namespace. The
 * optimized image keeps PID isolation and confines restarts to the JVM itself.</p>
 */
public final class ContainerSafeProcessUtils {

	private static final Logger logger = LoggerFactory.getLogger(ContainerSafeProcessUtils.class);

	private ContainerSafeProcessUtils() {
	}

	/** Returns true when the application is configured for container-safe startup. */
	public static boolean isContainerSafe(String[] args) {
		if (hasArg(args, "--project.containerSafe=true")) {
			return true;
		}
		// Configuration is supplied as CLI args in the optimized image. If a
		// deployment prefers a JVM property, it can set project.containerSafe too.
		String prop = System.getProperty("project.containerSafe");
		return prop != null && Boolean.parseBoolean(prop);
	}

	private static boolean hasArg(String[] args, String expected) {
		if (args == null) {
			return false;
		}
		for (String arg : args) {
			if (expected.equals(arg)) {
				return true;
			}
		}
		return false;
	}

	/**
	 * Restart only when the configured jar path looks like this application's
	 * own jar, and only via a stored PID file that belongs to a live JVM.
	 */
	public static void stopPreviousOwnJar(String jarPath) {
		if (StrUtil.isBlank(jarPath)) {
			return;
		}

		File jar = new File(jarPath);
		File pidFile = new File(jar.getAbsoluteFile().getParentFile(), "nginxWebUI.pid");
		if (!pidFile.isFile()) {
			return;
		}

		String pidText;
		try {
			pidText = new String(java.nio.file.Files.readAllBytes(pidFile.toPath()), java.nio.charset.StandardCharsets.UTF_8).trim();
		} catch (IOException e) {
			logger.warn("unable to read pid file {}", pidFile, e);
			return;
		}
		if (!pidText.matches("\\d+")) {
			logger.warn("invalid pid file content: {}", pidText);
			return;
		}

		String ownPid = java.lang.management.ManagementFactory.getRuntimeMXBean().getName().split("@")[0];
		if (pidText.equals(ownPid)) {
			return;
		}

		if (!isLiveOwnJvm(pidText, jar.getName())) {
			logger.warn("pid {} is not an nginxWebUI JVM; refusing to stop it", pidText);
			return;
		}

		logger.info("stopping previous nginxWebUI process {}", pidText);
		try {
			new ProcessBuilder("/bin/sh", "-c", "kill " + pidText).start().waitFor();
		} catch (Exception e) {
			logger.warn("failed to stop previous process {}", pidText, e);
		}
	}

	/** Write the current JVM pid so the next start can stop only this application. */
	public static void writeOwnPid(String jarPath) {
		if (StrUtil.isBlank(jarPath)) {
			return;
		}
		File jar = new File(jarPath);
		File pidFile = new File(jar.getAbsoluteFile().getParentFile(), "nginxWebUI.pid");
		try {
			java.nio.file.Files.write(pidFile.toPath(),
					java.lang.management.ManagementFactory.getRuntimeMXBean().getName().split("@")[0]
							.getBytes(java.nio.charset.StandardCharsets.UTF_8));
		} catch (IOException e) {
			logger.warn("unable to write pid file {}", pidFile, e);
		}
	}

	/** Remove stale nginxWebUI jars only in container-safe mode and only beside the current jar. */
	public static void removeStaleJars(String jarPath) {
		if (StrUtil.isBlank(jarPath)) {
			return;
		}
		File current = new File(jarPath).getAbsoluteFile();
		File[] files = current.getParentFile().listFiles();
		if (files == null) {
			return;
		}
		for (File file : files) {
			if (!file.getName().startsWith("nginxWebUI-") || !file.getName().endsWith(".jar")) {
				continue;
			}
			if (file.getAbsolutePath().equals(current.getAbsolutePath())) {
				continue;
			}
			logger.info("removing stale jar {}", file);
			cn.hutool.core.io.FileUtil.del(file);
		}
	}

	private static boolean isLiveOwnJvm(String pid, String jarName) {
		try {
			Process process = new ProcessBuilder("/bin/sh", "-c", "ps -p " + pid + " -o args=")
					.redirectErrorStream(true).start();
			String output = new String(readAll(process.getInputStream()), java.nio.charset.StandardCharsets.UTF_8);
			process.waitFor();
			return output.contains(jarName) && output.contains("java");
		} catch (Exception e) {
			logger.warn("unable to inspect pid {}", pid, e);
			return false;
		}
	}

	private static byte[] readAll(java.io.InputStream in) throws IOException {
		java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
		byte[] buffer = new byte[8192];
		int n;
		while ((n = in.read(buffer)) != -1) {
			out.write(buffer, 0, n);
		}
		return out.toByteArray();
	}
}
