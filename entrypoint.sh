#!/bin/sh
set -eu

cd /home

JAVA_OPTS="${JAVA_OPTS:--Xms64m -Xmx256m}"
NGINXWEBUI_OPTS="${BOOT_OPTIONS:---project.containerSafe=true --project.filesystemRoot=/home/nginxWebUI/ --project.home=/home/nginxWebUI/}"

echo "nginxWebUI starting: JAVA_OPTS=${JAVA_OPTS} BOOT_OPTIONS=${NGINXWEBUI_OPTS}"

# Keep JVM logs on stdout/stderr so Dockge/docker logs works. Do not redirect to /dev/null.
# shellcheck disable=SC2086
exec java $JAVA_OPTS -Dfile.encoding=UTF-8 -jar nginxWebUI.jar $NGINXWEBUI_OPTS
