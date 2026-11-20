package com.cym.controller.adminPage;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import org.noear.solon.annotation.Controller;
import org.noear.solon.annotation.Inject;
import org.noear.solon.annotation.Mapping;
import org.noear.solon.annotation.Produces;
import org.noear.solon.core.handle.Context;
import org.noear.solon.core.handle.DownloadedFile;
import org.noear.solon.core.handle.ModelAndView;
import org.noear.solon.core.handle.UploadedFile;
import org.noear.solon.core.util.MimeType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.cym.ext.AsycPack;
import com.cym.model.Cert;
import com.cym.model.Password;
import com.cym.model.Server;
import com.cym.service.CertService;
import com.cym.service.ConfService;
import com.cym.utils.BaseController;

import cn.hutool.core.date.DateUtil;
import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;

@Controller
@Mapping("/adminPage/export")
public class ExportController extends BaseController {
	Logger logger = LoggerFactory.getLogger(this.getClass());
	@Inject
	ConfService confService;
	@Inject
	CertService certService;

	@Mapping("")
	public ModelAndView index(ModelAndView modelAndView) {

		modelAndView.view("/adminPage/export/index.html");
		return modelAndView;
	}

	@Mapping("dataExport")
	public DownloadedFile dataExport(Context context) {
		String includeSecrets = context.param("includeSecrets");
		boolean include = Boolean.parseBoolean(includeSecrets);
		AsycPack asycPack = confService.getAsycPack(new String[] { "all" });
		List<String> omitNotes = new ArrayList<>();
		if (!include) {
			omitNotes = sanitize(asycPack);
		}
		
		String json = JSONUtil.toJsonPrettyStr(asycPack);

		String date = DateUtil.format(new Date(), "yyyy-MM-dd_HH-mm-ss");
		String fileName = include ? date + ".json" : date + "_safe.json";
		String notes = omitNotes.isEmpty() ? "" : "// " + String.join("\n// ", omitNotes) + "\n";
		byte[] exportBytes = (notes + json).getBytes(StandardCharsets.UTF_8);
		DownloadedFile downloadedFile = new DownloadedFile("application/octet-stream", new ByteArrayInputStream(exportBytes), fileName);
		return downloadedFile;
	}

	private List<String> sanitize(AsycPack asycPack) {
		List<String> notes = new ArrayList<>();
		if (asycPack.getServerList() != null) {
			for (Server server : asycPack.getServerList()) {
				if (StrUtil.isNotEmpty(server.getPemStr()) || StrUtil.isNotEmpty(server.getKeyStr())) {
					notes.add("TLS private key content for server " + server.getId() + " omitted; restore manually.");
					server.setPemStr(null);
					server.setKeyStr(null);
				}
			}
		}
		if (asycPack.getPasswordList() != null) {
			for (Password password : asycPack.getPasswordList()) {
				if (StrUtil.isNotEmpty(password.getPathStr())) {
					notes.add("Password file content for " + password.getPath() + " omitted; restore manually.");
					password.setPathStr("(omitted by safe export; restore manually from backup)");
				}
			}
		}
		if (asycPack.getCertList() != null) {
			for (Cert cert : asycPack.getCertList()) {
				if (sanitizeCert(cert)) {
					notes.add("DNS credentials for cert " + cert.getId() + " omitted; re-enter them after import.");
				}
			}
		}
		if (asycPack.getAcmeZip() != null || asycPack.getCertZip() != null) {
			notes.add("ACME/certificate archives omitted; restore manually from backup.");
			asycPack.setAcmeZip(null);
			asycPack.setCertZip(null);
		}
		return notes;
	}

	private boolean sanitizeCert(Cert cert) {
		boolean changed = false;
		changed |= clear(cert.getDpKey(), () -> cert.setDpKey(null));
		changed |= clear(cert.getTencentSecretId(), () -> cert.setTencentSecretId(null));
		changed |= clear(cert.getTencentSecretKey(), () -> cert.setTencentSecretKey(null));
		changed |= clear(cert.getAliKey(), () -> cert.setAliKey(null));
		changed |= clear(cert.getAliSecret(), () -> cert.setAliSecret(null));
		changed |= clear(cert.getCfKey(), () -> cert.setCfKey(null));
		changed |= clear(cert.getCfToken(), () -> cert.setCfToken(null));
		changed |= clear(cert.getGdKey(), () -> cert.setGdKey(null));
		changed |= clear(cert.getGdSecret(), () -> cert.setGdSecret(null));
		changed |= clear(cert.getHwPassword(), () -> cert.setHwPassword(null));
		changed |= clear(cert.getAwsAccessKeyId(), () -> cert.setAwsAccessKeyId(null));
		changed |= clear(cert.getAwsSecretAccessKey(), () -> cert.setAwsSecretAccessKey(null));
		changed |= clear(cert.getIpv64Token(), () -> cert.setIpv64Token(null));
		return changed;
	}

	private boolean clear(String value, Runnable setter) {
		if (StrUtil.isEmpty(value)) {
			return false;
		}
		setter.run();
		return true;
	}
	
	@Produces(MimeType.TEXT_HTML_VALUE)
	@Mapping(value = "dataImport")
	public String dataImport(UploadedFile file, Context context) throws IOException {
		if (file != null) {
			File tempFile = new File(homeConfig.home + "temp" + File.separator + file.getName().replace("..", ""));
			FileUtil.mkdir(tempFile.getParentFile());
			file.transferTo(tempFile);
			String json = FileUtil.readString(tempFile, StandardCharsets.UTF_8);
			tempFile.delete();

			try {
				// Safe exports prepend human-readable "// " notes; strip them before parsing.
				StringBuilder cleaned = new StringBuilder();
				for (String line : json.split("\\R")) {
					if (line.trim().startsWith("// ")) {
						continue;
					}
					if (cleaned.length() > 0) {
						cleaned.append('\n');
					}
					cleaned.append(line);
				}
				AsycPack asycPack = JSONUtil.toBean(cleaned.toString(), AsycPack.class);
				if (asycPack == null) {
					return "<script>window.parent.importFailed(" + JSONUtil.toJsonStr("Invalid backup file") + ");</script> ";
				}
				confService.setAsycPack(asycPack);
			} catch (Exception e) {
				logger.error("Data import failed", e);
				return "<script>window.parent.importFailed(" + JSONUtil.toJsonStr(e.getMessage()) + ");</script> ";
			}
		}
		return "<script>window.parent.finisheUpload();</script> ";
	}

	@Mapping("logExport")
	public DownloadedFile logExport(Context context) throws IOException {
		File file = new File(homeConfig.home + "log/nginxWebUI.log");
		if (file.exists()) {
			DownloadedFile downloadedFile = new DownloadedFile("application/octet-stream", Files.newInputStream(file.toPath()), file.getName());
			return downloadedFile;
		}

		return null;
	}

}
