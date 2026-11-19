package com.cym;

import java.io.File;

import org.noear.solon.Solon;
import org.noear.solon.annotation.SolonMain;
import org.noear.solon.scheduling.annotation.EnableScheduling;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.cym.utils.ContainerSafeProcessUtils;
import com.cym.utils.JarUtil;

@EnableScheduling
@SolonMain
public class NginxWebUI {
	static Logger logger = LoggerFactory.getLogger(NginxWebUI.class);

	public static void main(String[] args) {
		boolean findPass = false;
		if (args != null) {
			for (String arg : args) {
				if (arg.equals("--project.findPass=true")) {
					findPass = true;
				}
			}
		}

		if (!findPass) {
			String jarPath = JarUtil.getCurrentFilePath();
			if (ContainerSafeProcessUtils.isContainerSafe(args)) {
				// Container-optimized mode: never scan or kill unrelated JVMs.
				ContainerSafeProcessUtils.stopPreviousOwnJar(jarPath);
				ContainerSafeProcessUtils.removeStaleJars(jarPath);
				ContainerSafeProcessUtils.writeOwnPid(jarPath);
			} else {
				// Legacy compatibility for bare-metal installs that rely on the
				// old broad process scan. Not used by the optimized image.
				killSelf(args);
				removeJar();
			}
		}

		Solon.start(NginxWebUI.class, args);
	}

	/** Legacy process cleanup kept for non-container deployments. */
	public static void killSelf(String[] args) {
		if (ContainerSafeProcessUtils.isContainerSafe(args)) {
			return;
		}
		String myPid = java.lang.management.ManagementFactory.getRuntimeMXBean().getName().split("@")[0];
		for (String pid : getProcessId()) {
			if (!pid.equals(myPid)) {
				logger.info("杀掉旧进程:" + pid);
				try {
					if (com.cym.utils.SystemTool.isWindows()) {
						new ProcessBuilder("taskkill", "/F", "/PID", pid).start().waitFor();
					} else if (com.cym.utils.SystemTool.isLinux()) {
						new ProcessBuilder("/bin/sh", "-c", "kill " + pid).start().waitFor();
					}
				} catch (Exception e) {
					logger.warn("failed to stop legacy process {}", pid, e);
				}
			}
		}
	}

	private static java.util.List<String> getProcessId() {
		java.util.List<String> pids = new java.util.ArrayList<>();
		try {
			java.util.List<String> list;
			if (com.cym.utils.SystemTool.isWindows()) {
				list = cn.hutool.core.util.RuntimeUtil.execForLines(
						"wmic process where \"CommandLine like '%nginxWebUI%'\" get ProcessId,CommandLine");
			} else {
				list = cn.hutool.core.util.RuntimeUtil.execForLines("/bin/sh", "-c", "ps -ef | grep nginxWebUI");
			}
			for (String line : list) {
				if (line.contains(".jar") && !line.contains("nginxWebUI-Website")) {
					String[] parts = line.trim().split("\\s+");
					String pid = com.cym.utils.SystemTool.isWindows() ? parts[parts.length - 1] : parts[1];
					if (pid != null && pid.matches("\\d+")) {
						pids.add(pid);
					}
				}
			}
		} catch (Exception e) {
			logger.warn("failed to enumerate legacy processes", e);
		}
		return pids;
	}

	private static void removeJar() {
		String jarPath = JarUtil.getCurrentFilePath();
		if (jarPath == null || jarPath.trim().isEmpty()) {
			return;
		}
		File[] list = new File(jarPath).getAbsoluteFile().getParentFile().listFiles();
		if (list == null) {
			return;
		}
		for (File file : list) {
			if (file.getName().startsWith("nginxWebUI") && file.getName().endsWith(".jar")
					&& !file.getAbsolutePath().equals(new File(jarPath).getAbsolutePath())) {
				logger.info("删除旧文件:" + file);
				cn.hutool.core.io.FileUtil.del(file);
			}
		}
	}
}
