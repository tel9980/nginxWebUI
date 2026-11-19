package com.cym.utils;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Small in-memory sliding-window limiter for authentication endpoints.
 * Entries are pruned lazily; this is per-process and intentionally simple.
 */
public final class LoginRateLimitUtil {
	private static final Map<String, Window> WINDOWS = new ConcurrentHashMap<>();

	private LoginRateLimitUtil() {
	}

	public static boolean allow(String key, int maxAttempts, long windowMs) {
		long now = System.currentTimeMillis();
		Window w = WINDOWS.computeIfAbsent(key, k -> new Window(now, 0));
		synchronized (w) {
			if (now - w.start >= windowMs) {
				w.start = now;
				w.count = 0;
			}
			if (w.count >= maxAttempts) {
				return false;
			}
			w.count++;
			return true;
		}
	}

	public static void reset(String key) {
		WINDOWS.remove(key);
	}

	private static final class Window {
		long start;
		int count;

		Window(long start, int count) {
			this.start = start;
			this.count = count;
		}
	}
}
