$(function() {
	
});

function dExport() {
	if (confirm(exportStr.exportConfirm)) {
		window.open(ctx + "/adminPage/export/dataExport")
	}
}


function dImport() {
	$("#file").click();
}

function dImportOver() {
	var files = $('#file').prop('files');// 获取到文件列表
	if (files.length == 0) {
		layer.alert(exportStr.selectFile);
	} else {
		if (confirm(exportStr.confirm)) {
			$("#dataImport").submit();
		}
	}
}

function finisheUpload(){
	layer.msg(exportStr.importSuccess);
}

function importFailed(message) {
	layer.alert(exportStr.importFailed + ": " + (message || ""));
}

function lExport() {
	window.open(ctx + "/adminPage/export/logExport")
}
