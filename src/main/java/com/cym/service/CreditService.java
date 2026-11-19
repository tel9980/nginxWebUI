package com.cym.service;

import java.util.List;

import org.noear.solon.annotation.Component;
import org.noear.solon.annotation.Inject;

import com.cym.model.Credit;
import com.cym.sqlhelper.utils.ConditionAndWrapper;
import com.cym.sqlhelper.utils.SqlHelper;
import com.cym.utils.AppSecurityUtil;

import cn.hutool.core.lang.UUID;
import cn.hutool.core.util.StrUtil;

@Component
public class CreditService {
	@Inject
	SqlHelper sqlHelper;
	
	public String make(String adminId) {
		if (!AppSecurityUtil.creditKeyEnabled()) {
			return "";
		}
		Credit credit = new Credit();
		credit.setKey(UUID.randomUUID().toString());
		credit.setAdminId(adminId);
		credit.setRevoked(false);
		Long ttl = AppSecurityUtil.creditKeyTtlMs();
		credit.setExpiresAt(ttl > 0 ? System.currentTimeMillis() + ttl : 0L);
		sqlHelper.insertOrUpdate(credit);
		
		return credit.getKey();
	}

	public Credit getValid(String key) {
		if (!AppSecurityUtil.creditKeyEnabled() || StrUtil.isEmpty(key)) {
			return null;
		}
		Credit credit = sqlHelper.findOneByQuery(new ConditionAndWrapper().eq("key", key), Credit.class);
		if (credit == null || Boolean.TRUE.equals(credit.getRevoked())) {
			return null;
		}
		Long expiresAt = credit.getExpiresAt();
		if (expiresAt != null && expiresAt > 0 && expiresAt <= System.currentTimeMillis()) {
			return null;
		}
		credit.setLastUsedTime(System.currentTimeMillis());
		sqlHelper.updateById(credit);
		return credit;
	}

	public boolean check(String key) {
		return getValid(key) != null;
	}

	public void revoke(String adminId) {
		List<Credit> credits = sqlHelper.findListByQuery(new ConditionAndWrapper().eq(Credit::getAdminId, adminId), Credit.class);
		for (Credit credit : credits) {
			credit.setRevoked(true);
			sqlHelper.updateById(credit);
		}
	}
}
