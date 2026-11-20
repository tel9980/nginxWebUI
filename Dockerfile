FROM alpine:3.22

LABEL org.opencontainers.image.title="nginxWebUI (optimized)" \
      org.opencontainers.image.description="Dockge-friendly nginxWebUI with safe startup, visible logs and slim nginx modules" \
      org.opencontainers.image.source="https://github.com/tel9980/nginxWebUI"

ENV LANG=zh_CN.UTF-8 \
    TZ=Asia/Shanghai \
    PS1="\u@\h:\w \$ " \
    JAVA_OPTS="-Xms64m -Xmx256m" \
    BOOT_OPTIONS="--project.containerSafe=true --project.filesystemRoot=/home/nginxWebUI/ --project.home=/home/nginxWebUI/"

RUN apk add --no-cache \
      nginx \
      nginx-mod-stream \
      nginx-mod-http-headers-more \
      nginx-mod-http-lua \
      openjdk17-jre-headless \
      curl \
      tzdata \
      fontconfig \
      ttf-dejavu \
      tini \
      su-exec \
    && apk upgrade --no-cache \
    && fc-cache -f \
    && ln -snf /usr/share/zoneinfo/${TZ} /etc/localtime \
    && echo "${TZ}" > /etc/timezone \
    && mkdir -p /home/nginxWebUI/log \
    && chown -R nobody:nogroup /home/nginxWebUI

COPY target/nginxWebUI-*.jar /home/nginxWebUI.jar
COPY entrypoint.sh /usr/local/bin/entrypoint.sh
RUN chmod +x /usr/local/bin/entrypoint.sh

WORKDIR /home
VOLUME ["/home/nginxWebUI"]
EXPOSE 8080 80 443

HEALTHCHECK --interval=30s --timeout=5s --start-period=90s --retries=3 \
  CMD curl -fsS http://127.0.0.1:8080/ >/dev/null 2>&1 || exit 1

ENTRYPOINT ["tini", "--", "/usr/local/bin/entrypoint.sh"]
