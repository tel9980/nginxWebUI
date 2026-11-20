package com.cym.task;

import java.io.File;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;

import org.noear.solon.annotation.Component;
import org.noear.solon.annotation.Inject;
import org.noear.solon.scheduling.annotation.Scheduled;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.cym.config.HomeConfig;
import com.cym.sqlhelper.config.DataSourceEmbed;
import com.cym.utils.AppSecurityUtil;

import cn.hutool.core.io.FileUtil;

/**
 * Daily backup of the embedded SQLite database.
 *
 * Uses {@code VACUUM INTO} so the copy is taken from within SQLite itself,
 * avoiding an inconsistent copy of a WAL-mode database.
 */
@Component
public class SqliteBackupTask {
	private static final Logger LOG = LoggerFactory.getLogger(SqliteBackupTask.class);

	@Inject
	HomeConfig homeConfig;

	@Inject
	DataSourceEmbed dataSourceEmbed;

	/** Run once a day, one minute after the other midnight tasks. */
	@Scheduled(cron = "0 1 0 * * ?")
	public void backup() {
		if (!AppSecurityUtil.sqliteBackupEnabled()) {
			return;
		}

		File dbFile = new File(homeConfig.home + "sqlite.db");
		if (!dbFile.isFile()) {
			// MySQL/PostgreSQL deployments have no embedded SQLite database.
			return;
		}

		File backupDir = new File(homeConfig.home + "backup");
		if (!backupDir.exists() && !backupDir.mkdirs()) {
			LOG.error("Cannot create SQLite backup directory: " + backupDir.getAbsolutePath());
			return;
		}

		String fileName = "sqlite-" + new SimpleDateFormat("yyyyMMdd-HHmmss").format(new Date()) + ".db";
		File target = new File(backupDir, fileName);
		try (Connection connection = dataSourceEmbed.getDataSource().getConnection();
				PreparedStatement statement = connection.prepareStatement("VACUUM INTO '" + target.getAbsolutePath().replace("'", "''") + "'")) {
			statement.executeUpdate();
			LOG.info("SQLite backup created: " + target.getAbsolutePath());
		} catch (Exception e) {
			LOG.error("Failed to backup SQLite database", e);
			try {
				FileUtil.copy(dbFile, target, true);
				LOG.info("SQLite backup copied with FileUtil: " + target.getAbsolutePath());
			} catch (Exception copyError) {
				LOG.error("Failed to copy SQLite database to backup", copyError);
				return;
			}
		}

		pruneOldBackups(backupDir);
	}

	private void pruneOldBackups(File backupDir) {
		int keep = AppSecurityUtil.sqliteBackupKeep();
		if (keep < 0) {
			return;
		}
		File[] files = backupDir.listFiles((dir, name) -> name.startsWith("sqlite-") && name.endsWith(".db"));
		if (files == null || files.length <= keep) {
			return;
		}
		List<File> sorted = Arrays.stream(files).sorted(Comparator.comparingLong(File::lastModified)).collect(Collectors.toList());
		for (int i = 0; i < sorted.size() - keep; i++) {
			if (!sorted.get(i).delete()) {
				LOG.warn("Cannot remove old SQLite backup: " + sorted.get(i).getAbsolutePath());
			}
		}
	}
}
