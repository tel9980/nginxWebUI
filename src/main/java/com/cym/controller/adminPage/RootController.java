package com.cym.controller.adminPage;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import org.noear.solon.annotation.Controller;
import org.noear.solon.annotation.Inject;
import org.noear.solon.annotation.Mapping;

import com.cym.ext.TreeNode;
import com.cym.utils.BaseController;
import com.cym.utils.JsonResult;
import com.cym.utils.PathSafetyUtil;
import com.cym.utils.SystemTool;

import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.StrUtil;

@Controller
@Mapping("/adminPage/root")
public class RootController extends BaseController {

	@Inject("${project.filesystemRoot:}")
	String filesystemRoot;

	private volatile PathSafetyUtil pathSafety;

	private PathSafetyUtil pathSafety() {
		if (pathSafety == null) {
			synchronized (this) {
				if (pathSafety == null) {
					PathSafetyUtil util = new PathSafetyUtil();
					util.setRoot(filesystemRoot);
					pathSafety = util;
				}
			}
		}
		return pathSafety;
	}

	@Mapping("getList")
	public List<TreeNode> getList(String id) {
		List<TreeNode> list = new ArrayList<TreeNode>();
		File[] fileList;
		try {
			if (pathSafety().isEnabled() && StrUtil.isEmpty(id)) {
				fileList = new File(filesystemRoot).getAbsoluteFile().listFiles();
			} else {
				File resolved = pathSafety().resolve(id);
				if (SystemTool.isWindows() && "/".equals(id) && !pathSafety().isEnabled()) {
					fileList = File.listRoots();
				} else {
					fileList = resolved.listFiles();
				}
			}
		} catch (IOException e) {
			return list;
		}
		if (fileList == null) {
			return list;
		}

		for (File temp : fileList) {
			TreeNode treeNode = new TreeNode();
			treeNode.setId(temp.getPath());
			if (FileUtil.isDirectory(treeNode.getId())) {
				treeNode.setId(treeNode.getId() + "/");
			}
			treeNode.setName(StrUtil.isNotEmpty(temp.getName()) ? temp.getName() : temp.getPath());
			treeNode.setIsParent(temp.isDirectory() ? "true" : "false");
			list.add(treeNode);
		}

		list.sort((o1, o2) -> {
			if (o1.getIsParent().equals("true") && o2.getIsParent().equals("false")) {
				return -1;
			}
			if (o1.getIsParent().equals("false") && o2.getIsParent().equals("true")) {
				return 1;
			}
			return o1.getName().compareToIgnoreCase(o2.getName());
		});
		return list;
	}

	@Mapping("mkdir")
	public JsonResult mkdir(String dir, String name) {
		try {
			File target = pathSafety().resolveChild(dir, name);
			FileUtil.mkdir(target);
			return renderSuccess();
		} catch (IOException e) {
			return renderError(e.getMessage());
		}
	}
}
