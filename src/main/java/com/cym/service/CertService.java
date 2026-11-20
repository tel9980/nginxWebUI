package com.cym.service;

import java.io.File;
import java.util.List;

import com.cym.model.Stream;
import com.cym.sqlhelper.bean.Sort;
import org.noear.solon.annotation.Component;
import org.noear.solon.annotation.Inject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.cym.config.HomeConfig;
import com.cym.model.Cert;
import com.cym.model.CertCode;
import com.cym.sqlhelper.bean.Page;
import com.cym.sqlhelper.utils.ConditionAndWrapper;
import com.cym.sqlhelper.utils.SqlHelper;

import cn.hutool.core.codec.Base64;
import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.core.util.ZipUtil;

@Component
public class CertService {
	Logger logger = LoggerFactory.getLogger(this.getClass());

	@Inject
	SqlHelper sqlHelper;
	@Inject
	HomeConfig homeConfig;

	public boolean hasSame(Cert cert) {
		if (StrUtil.isEmpty(cert.getId())) {
			// 添加
			if (sqlHelper.findCountByQuery(new ConditionAndWrapper().eq("domain", cert.getDomain()), Cert.class) > 0) {
				return true;
			}
		} else {
			// 编辑
			if (sqlHelper.findCountByQuery(new ConditionAndWrapper().eq("domain", cert.getDomain()).ne("id", cert.getId()), Cert.class) > 0) {
				return true;
			}
		}

		return false;
	}

	public List<CertCode> getCertCodes(String certId) {

		return sqlHelper.findListByQuery(new ConditionAndWrapper().eq(CertCode::getCertId, certId), CertCode.class);
	}

	public void insertOrUpdate(Cert cert, String[] domain, String[] type, String[] value) {
		sqlHelper.insertOrUpdate(cert);

		sqlHelper.deleteByQuery(new ConditionAndWrapper().eq(CertCode::getCertId, cert.getId()), CertCode.class);

		if (domain != null && type != null && value != null) {
			for (int i = 0; i < domain.length; i++) {
				CertCode certCode = new CertCode();
				certCode.setCertId(cert.getId());
				certCode.setDomain(domain[i]);
				certCode.setType(type[i]);
				certCode.setValue(value[i]);
				sqlHelper.insert(certCode);
			}
		}

	}

	public List<Cert> findAll() {
		return sqlHelper.findAll(new Sort(Cert::getId, Sort.Direction.ASC), Cert.class);
	}

	public Page getPage(String keywords, Page page) {
		ConditionAndWrapper conditionAndWrapper = new ConditionAndWrapper();
		if (StrUtil.isNotEmpty(keywords)) {
			conditionAndWrapper.like(Cert::getDomain, keywords);
		}

		return sqlHelper.findPage(conditionAndWrapper, page, Cert.class);
	}

	public void saveCertCode(String certId, List<CertCode> mapList) {
		sqlHelper.deleteByQuery(new ConditionAndWrapper().eq(CertCode::getCertId, certId), CertCode.class);
		for (CertCode certCode : mapList) {
			certCode.setCertId(certId);
			sqlHelper.insert(certCode);
		}

	}

	public boolean hasCode(String certId) {
		return sqlHelper.findCountByQuery(new ConditionAndWrapper().eq(CertCode::getCertId, certId), CertCode.class) > 0;
	}

	public String getAcmeZipBase64() {
		try {
			File file = ZipUtil.zip(homeConfig.home + ".acme.sh", homeConfig.home + "temp" + File.separator + "acme.zip");
			String str = Base64.encode(file);
			file.delete();
			return str;
		} catch (Exception e) {
			logger.error(e.getMessage(), e);
		}
		return null;
	}
	
	public String getCertZipBase64() {
		try {
			File file = ZipUtil.zip(homeConfig.home + "cert", homeConfig.home + "temp" + File.separator + "cert.zip");
			String str = Base64.encode(file);
			file.delete();
			return str;
		} catch (Exception e) {
			logger.error(e.getMessage(), e);
		}
		return null;
	}

	public void writeAcmeZipBase64(String acmeZip) {
		replaceFromZipBase64(acmeZip, homeConfig.home + ".acme.sh", "acme.zip");
	}
	

	public void writeCertZipBase64(String certZip) {
		replaceFromZipBase64(certZip, homeConfig.home + "cert", "cert.zip");
	}

	private void replaceFromZipBase64(String zipBase64, String targetPath, String tempZipName) {
		if (StrUtil.isEmpty(zipBase64)) {
			return;
		}
		File tempZip = new File(homeConfig.home + "temp" + File.separator + tempZipName);
		File tempDir = new File(homeConfig.home + "cert-import-" + System.nanoTime());
		File backupDir = new File(targetPath + ".bak-" + System.nanoTime());
		File target = new File(targetPath);
		FileUtil.mkdir(tempZip.getParentFile());
		try {
			Base64.decodeToFile(zipBase64, tempZip);
			FileUtil.mkdir(tempDir);
			ZipUtil.unzip(tempZip, tempDir);

			if (target.exists()) {
				FileUtil.rename(target, backupDir.getName(), true);
			}
			try {
				FileUtil.rename(tempDir, target.getName(), true);
			} catch (RuntimeException e) {
				if (backupDir.exists()) {
					FileUtil.rename(backupDir, target.getName(), true);
				}
				throw e;
			}
			tempDir = null;
		} catch (Exception e) {
			logger.error("Failed to import certificate archive into {}", targetPath, e);
			throw new RuntimeException("证书压缩包导入失败: " + e.getMessage(), e);
		} finally {
			FileUtil.del(tempZip);
			if (tempDir != null) {
				FileUtil.del(tempDir);
			}
		}
	}

	public boolean hasName(Cert cert) {
		if (StrUtil.isEmpty(cert.getId())) {
			Long count = sqlHelper.findCountByQuery(new ConditionAndWrapper().eq(Cert::getDomain, cert.getDomain()), Cert.class);
			if (count > 0) {
				return true;
			}
		} else {
			Long count = sqlHelper.findCountByQuery(new ConditionAndWrapper().eq(Cert::getDomain, cert.getDomain()).ne(Cert::getId, cert.getId()), Cert.class);
			if (count > 0) {
				return true;
			}
		}

		return false;
	}


	

}
