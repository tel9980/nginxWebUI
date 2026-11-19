package com.cym.controller.adminPage;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.noear.solon.annotation.Controller;
import org.noear.solon.annotation.Inject;
import org.noear.solon.annotation.Mapping;
import org.noear.solon.core.handle.ModelAndView;

import com.cym.model.Bak;
import com.cym.model.BakSub;
import com.cym.service.BakService;
import com.cym.service.ConfService;
import com.cym.service.SettingService;
import com.cym.sqlhelper.bean.Page;
import com.cym.utils.BaseController;
import com.cym.utils.JsonResult;

import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.StrUtil;

@Controller
@Mapping("/adminPage/bak")
public class BakController extends BaseController {
	@Inject
	SettingService settingService;
	@Inject
	ConfService confService;
	@Inject
	BakService bakService;

	@Mapping("")
	public ModelAndView index( ModelAndView modelAndView, Page page) {
		setPage(page);
		page = bakService.getList(page);

		modelAndView.put("page", page);
		modelAndView.view("/adminPage/bak/index.html");
		return modelAndView;
	}

	@Mapping("getCompare")
	public JsonResult getCompare(String id) {
		Bak bak = sqlHelper.findById(id, Bak.class);

		Bak pre = bakService.getPre(id);
		if (pre == null) {
			return renderError("没有更早的备份文件");
		}

		Map map = new HashMap<>();
		map.put("bak", bak);
		map.put("pre", pre);

		return renderSuccess(map);
	}

	@Mapping("content")
	public JsonResult content(String id) {
		Bak bak = sqlHelper.findById(id, Bak.class);
		return renderSuccess(bak);
	}

	@Mapping("replace")
	public JsonResult replace(String id) {
		Bak bak = sqlHelper.findById(id, Bak.class);

		String nginxPath = settingService.get("nginxPath");

		if (StrUtil.isNotEmpty(nginxPath)) {
			List<BakSub> subList = bakService.getSubList(bak.getId());
			List<String> subContent = subList.stream().map(BakSub::getContent).collect(java.util.stream.Collectors.toList());
			List<String> subName = subList.stream().map(BakSub::getName).collect(java.util.stream.Collectors.toList());
			String fileTemp = confService.renderToTemp(bak.getContent(), subContent, subName);

			String rs;
			String cmd = settingService.get("nginxExe") + " -t -c " + fileTemp;
			if (StrUtil.isNotEmpty(settingService.get("nginxDir"))) {
				cmd += " -p " + settingService.get("nginxDir");
			}
			try {
				rs = cn.hutool.core.util.RuntimeUtil.execForStr("/bin/sh", "-c", cmd);
			} catch (Exception e) {
				return renderError(m.get("confStr.verifyFail") + "<br>" + e.getMessage());
			}
			if (!rs.contains("successful") && !rs.contains("test is successful")) {
				return renderError(m.get("confStr.verifyFail") + "<br>" + rs.replace("\n", "<br>"));
			}

			try {
				confService.replace(nginxPath, bak.getContent(), subContent, subName, true, getAdmin().getName());
			} catch (java.io.IOException e) {
				return renderError(m.get("bakStr.replaceFail") + "<br>" + e.getMessage());
			}
			return renderSuccess();
		} else {
			return renderError(m.get("bakStr.pathNotice"));
		}

	}

	
	@Mapping("del")
	public JsonResult del(String id) {
		bakService.del(id);
		return renderSuccess();
	}

	@Mapping("delAll")
	public JsonResult delAll() {
		bakService.delAll();

		return renderSuccess();
	}

}
