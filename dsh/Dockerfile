FROM node:22-bookworm-slim

# dsh 版本固定：brand-patch.mjs 精确匹配该版本的第三方源码，升级前必须同步核对补丁。
RUN apt-get update \
    && apt-get install --no-install-recommends --yes ca-certificates git socat \
    && npm install --global @deepseek-ai/dsh@0.2.0-rc.2 pnpm \
    && rm -rf /var/lib/apt/lists/* \
    && mkdir --parents /dsh-home \
    && chown --recursive node:node /dsh-home

COPY --chown=node:node docker-entrypoint.sh /usr/local/bin/docker-entrypoint.sh
COPY --chown=node:node seed /opt/dsh-seed
COPY --chown=node:node brand-patch.mjs /opt/dsh-seed/brand-patch.mjs
COPY --chown=node:node auth-proxy.mjs /opt/dsh-seed/auth-proxy.mjs
RUN node /opt/dsh-seed/brand-patch.mjs \
    && chmod +x /usr/local/bin/docker-entrypoint.sh

USER node
ENV DSH_HOME=/dsh-home
EXPOSE 3080
ENTRYPOINT ["docker-entrypoint.sh"]
