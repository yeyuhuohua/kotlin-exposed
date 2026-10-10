#!/bin/sh
set -eu

if [ ! -f /dsh-home/settings.yaml ]; then
  cp /opt/dsh-seed/settings.yaml /dsh-home/settings.yaml
fi
mkdir -p /dsh-home/profiles/web
if [ ! -f /dsh-home/profiles/web/cordis.patch.yml ]; then
  cp /opt/dsh-seed/profiles/web/cordis.patch.yml /dsh-home/profiles/web/cordis.patch.yml
fi

rm -f /dsh-home/launch-token
node /opt/dsh-seed/auth-proxy.mjs &
proxy_pid=$!
trap 'kill "$proxy_pid" 2>/dev/null || true' EXIT INT TERM

# dsh 自己监听 3081。对外的 3080 由代理换成登录态，并改写启动地址。
dsh web --no-open --port 3081 \
  --trusted-host "localhost:3080" \
  --trusted-host "127.0.0.1:3080" \
  2>&1 | while IFS= read -r line; do
    printf '%s\n' "$line" | sed 's#http://127.0.0.1:3081#http://127.0.0.1:3080#g'
    token=$(printf '%s\n' "$line" | sed -n 's/.*[?&]token=\([^[:space:]]*\).*/\1/p')
    if [ -n "$token" ] && [ ! -f /dsh-home/launch-token ]; then
      printf '%s' "$token" > /dsh-home/launch-token
    fi
  done
