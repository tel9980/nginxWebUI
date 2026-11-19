package com.cym.utils;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;

/**
 * Restricts arbitrary filesystem browsing/mkdir to a configured root.
 *
 * <p>Default is permissive for backward compatibility with bare-metal installs.
 * The optimized Docker image sets {@code project.filesystemRoot=/home/nginxWebUI}
 * so the admin file picker cannot walk the whole container filesystem.</p>
 */
public final class PathSafetyUtil {
	private Path root = null;

	public void setRoot(String configured) {
		if (configured == null || configured.trim().isEmpty()) {
			root = null;
			return;
		}
		root = new File(configured).getAbsoluteFile().toPath().normalize();
	}

	public boolean isEnabled() {
		return root != null;
	}

	public File resolve(String path) throws IOException {
		if (path == null || path.isEmpty()) {
			path = "/";
		}
		File candidate = new File(path).getAbsoluteFile();
		if (root == null) {
			return candidate;
		}
		Path normalized = candidate.toPath().normalize();
		if (!normalized.startsWith(root)) {
			throw new IOException("path outside project.filesystemRoot: " + path);
		}
		return normalized.toFile();
	}

	public File resolveChild(String dir, String name) throws IOException {
		if (name == null || name.contains("/") || name.contains("\\") || name.contains("..")) {
			throw new IOException("invalid directory name: " + name);
		}
		File parent = resolve(dir == null || dir.isEmpty() ? root == null ? "/" : root.toString() : dir);
		File target = new File(parent, name).getAbsoluteFile().toPath().normalize().toFile();
		if (root != null && !target.toPath().startsWith(root)) {
			throw new IOException("path outside project.filesystemRoot: " + target);
		}
		return target;
	}
}
