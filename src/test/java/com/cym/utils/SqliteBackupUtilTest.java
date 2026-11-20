package com.cym.utils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SqliteBackupUtilTest {

	@TempDir
	Path tempDir;

	@Test
	void verifiesAValidSqliteBackup() throws Exception {
		Path db = tempDir.resolve("backup.db");
		try (Connection connection = DriverManager.getConnection("jdbc:sqlite:" + db.toAbsolutePath());
				Statement statement = connection.createStatement()) {
			statement.execute("CREATE TABLE demo(id INTEGER PRIMARY KEY, value TEXT)");
			statement.execute("INSERT INTO demo(value) VALUES ('ok')");
		}
		assertNull(SqliteBackupUtil.verifyBackup(db.toFile()));
	}

	@Test
	void rejectsMissingOrNonDatabaseFiles() throws Exception {
		Path notDb = tempDir.resolve("junk.db");
		Files.writeString(notDb, "not a sqlite database");
		String problem = SqliteBackupUtil.verifyBackup(notDb.toFile());
		assertTrue(problem != null && problem.contains("cannot verify"));
		assertTrue(SqliteBackupUtil.verifyBackup(tempDir.resolve("absent.db").toFile()).contains("missing"));
	}

	@Test
	void prunesByCountAndAge() throws Exception {
		File dir = tempDir.resolve("backup").toFile();
		assertTrue(dir.mkdirs());
		long now = System.currentTimeMillis();
		long day = 24L * 60L * 60L * 1000L;
		createFile(dir, "sqlite-old.db", now - 40 * day);
		createFile(dir, "sqlite-mid.db", now - 10 * day);
		createFile(dir, "sqlite-new1.db", now - 2 * day);
		createFile(dir, "sqlite-new2.db", now - day);
		createFile(dir, "unrelated.txt", now - 100 * day);

		// keep=2, retainDays=30: old (age) and mid (count + age) go; two newest stay.
		int deleted = SqliteBackupUtil.pruneBackups(dir, 2, 30, now);
		assertEquals(2, deleted);
		assertFalse(new File(dir, "sqlite-old.db").exists());
		assertFalse(new File(dir, "sqlite-mid.db").exists());
		assertTrue(new File(dir, "sqlite-new1.db").exists());
		assertTrue(new File(dir, "sqlite-new2.db").exists());
		assertTrue(new File(dir, "unrelated.txt").exists());
	}

	private void createFile(File dir, String name, long lastModified) throws Exception {
		File file = new File(dir, name);
		Files.writeString(file.toPath(), "x");
		assertTrue(file.setLastModified(lastModified));
	}
}
