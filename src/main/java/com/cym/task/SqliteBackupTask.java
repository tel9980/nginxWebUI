package com.cym.task;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Date;

import org.noear.solon.annotation.Component;
import org.noear.solon.annotation.Inject;
import org.noear.solon.scheduling.annotation.Scheduled;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.cym.config.HomeConfig;
import com.cym.sqlhelper.config.DataSourceEmbed;
import com.cym.utils.AppSecurityUtil;
import com.cym.utils.SqliteBackupUtil;

import cn.hutool.core.io.FileUtil;

/**
 * Daily backup of the embedded SQLite database.
 *
 * Uses {@code VACUUM INTO} so the copy is taken from within SQLite itself,
 * avoiding an inconsistent copy of a WAL-mode database. Every created backup
 * is verified with a fresh SQLite connection before it is retained.
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
			LOG.error("SQLITE_BACKUP_FAILURE cannot create backup directory: " + backupDir.getAbsolutePath());
			return;
		}

		String fileName = "sqlite-" + new SimpleDateFormat("yyyyMMdd-HHmmss").format(new Date()) + ".db";
		File target = new File(backupDir, fileName);
		boolean created = false;
		try (java.sql.Connection connection = dataSourceEmbed.getDataSource().getConnection();
				java.sql.PreparedStatement statement = connection.prepareStatement(
						"VACUUM INTO '" + target.getAbsolutePath().replace("'", "''") + "'")) {
			statement.executeUpdate();
			created = true;
		} catch (Exception e) {
			LOG.error("SQLITE_BACKUP_FAILURE VACUUM INTO failed; trying file copy", e);
			try {
				FileUtil.copy(dbFile, target, true);
				created = true;
			} catch (Exception copyError) {
				LOG.error("SQLITE_BACKUP_FAILURE file copy failed", copyError);
				return;
			}
		}

		if (!created) {
			LOG.error("SQLITE_BACKUP_FAILURE no backup was created");
			return;
		}
		String problem = SqliteBackupUtil.verifyBackup(target);
		if (problem != null) {
			LOG.error("SQLITE_BACKUP_FAILURE verification failed: " + problem + "; deleting " + target.getAbsolutePath());
			if (!target.delete()) {
				target.deleteOnExit();
			}
			return;
		}
		LOG.info("SQLite backup created and verified: " + target.getAbsolutePath());

		int deleted = SqliteBackupUtil.pruneBackups(backupDir, AppSecurityUtil.sqliteBackupKeep(),
				AppSecurityUtil.sqliteBackupRetainDays(), System.currentTimeMillis());
		if (deleted > 0) {
			LOG.info("Pruned " + deleted + " old SQLite backup(s)");
		}
	}
}
