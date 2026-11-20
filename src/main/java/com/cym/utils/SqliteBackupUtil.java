package com.cym.utils;

import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Helpers for verifying and pruning embedded SQLite backups. Kept as static
 * pure-ish methods so they can be unit tested with a real temporary SQLite file.
 */
public final class SqliteBackupUtil {

	private SqliteBackupUtil() {
	}

	/** Opens the backup with a fresh connection and runs integrity_check. */
	public static String verifyBackup(File file) {
		if (file == null || !file.isFile()) {
			return "backup file missing: " + (file == null ? "null" : file.getAbsolutePath());
		}
		String url = "jdbc:sqlite:" + file.getAbsolutePath();
		try (Connection connection = DriverManager.getConnection(url);
				Statement statement = connection.createStatement()) {
			try (ResultSet rs = statement.executeQuery("PRAGMA integrity_check")) {
				if (!rs.next() || !"ok".equalsIgnoreCase(rs.getString(1))) {
					return "integrity_check failed";
				}
			}
			try (ResultSet rs = statement.executeQuery("SELECT count(*) FROM sqlite_master")) {
				if (!rs.next() || rs.getInt(1) <= 0) {
					return "backup has no schema";
				}
			}
			return null;
		} catch (Exception e) {
			return "cannot verify backup: " + e.getMessage();
		}
	}

	/**
	 * Deletes backups beyond the count limit or older than the day limit.
	 * Only files named sqlite-*.db are considered.
	 *
	 * @return number of files deleted
	 */
	public static int pruneBackups(File backupDir, int keep, int retainDays, long now) {
		if (backupDir == null || !backupDir.isDirectory()) {
			return 0;
		}
		File[] files = backupDir.listFiles((dir, name) -> name.startsWith("sqlite-") && name.endsWith(".db"));
		if (files == null || files.length == 0) {
			return 0;
		}
		List<File> sorted = Arrays.stream(files).sorted(Comparator.comparingLong(File::lastModified)).collect(Collectors.toList());
		long cutoff = retainDays > 0 ? now - retainDays * 24L * 60L * 60L * 1000L : Long.MIN_VALUE;
		int toDelete = Math.max(0, sorted.size() - Math.max(0, keep));
		int deleted = 0;
		for (int i = 0; i < sorted.size(); i++) {
			File file = sorted.get(i);
			boolean tooMany = i < toDelete;
			boolean tooOld = file.lastModified() < cutoff;
			if ((tooMany || tooOld) && file.delete()) {
				deleted++;
			}
		}
		return deleted;
	}
}
