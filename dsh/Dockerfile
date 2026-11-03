FROM node:22-bookworm-slim

# 系统依赖单独成层，调整 DSH 或 npm 依赖时无需重复执行 apt-get。
RUN apt-get update \
    && apt-get install --no-install-recommends --yes ca-certificates git \
    && rm -rf /var/lib/apt/lists/* \
    && mkdir --parents /dsh-home \
    && chown --recursive node:node /dsh-home

# dsh 版本固定：brand-patch.mjs 精确匹配该版本的第三方源码，升级前必须同步核对补丁。
RUN npm install --global @deepseek-ai/dsh@0.2.0-rc.2 pnpm

COPY --chown=node:node docker-entrypoint.sh /usr/local/bin/docker-entrypoint.sh
COPY --chown=node:node seed /opt/dsh-seed
COPY --chown=node:node brand-patch.mjs /opt/dsh-seed/brand-patch.mjs
COPY --chown=node:node auth-proxy.mjs agent-bindings.mjs session-store.mjs mcp-forwarder.mjs migrate-profile.mjs /opt/dsh-seed/
COPY --chown=node:node hr-mcp/package.json hr-mcp/index.mjs hr-mcp/core.mjs /opt/dsh-seed/hr-mcp/
RUN node /opt/dsh-seed/brand-patch.mjs \
    && chmod +x /usr/local/bin/docker-entrypoint.sh

USER node
ENV DSH_HOME=/dsh-home
EXPOSE 3080
ENTRYPOINT ["docker-entrypoint.sh"]
