#!/bin/sh
set -eu
umask 077

if [ ! -f /dsh-home/settings.yaml ]; then
  cp /opt/dsh-seed/settings.yaml /dsh-home/settings.yaml
fi
mkdir -p /dsh-home/profiles/web
if [ ! -f /dsh-home/profiles/web/cordis.patch.yml ]; then
  cp /opt/dsh-seed/profiles/web/cordis.patch.yml /dsh-home/profiles/web/cordis.patch.yml
fi

rm -f /dsh-home/launch-token
fifo=/dsh-home/proxy-output.fifo
rm -f "$fifo"
mkfifo "$fifo"

cleanup() {
  trap - EXIT INT TERM
  kill "$proxy_pid" "$dsh_pid" "$reader_pid" 2>/dev/null || true
  wait "$proxy_pid" "$dsh_pid" "$reader_pid" 2>/dev/null || true
  rm -f "$fifo"
}

node /opt/dsh-seed/auth-proxy.mjs &
proxy_pid=$!

while IFS= read -r line; do
    token=$(printf '%s\n' "$line" | sed -n 's/.*[?&]token=\([^[:space:]]*\).*/\1/p')
    if [ -n "$token" ] && [ ! -f /dsh-home/launch-token ]; then
      printf '%s' "$token" > /dsh-home/launch-token
    fi
    printf '%s\n' "$line" | sed -e 's#http://127.0.0.1:3081#http://127.0.0.1:3080#g' -e 's/[?&]token=[^[:space:]]*/?token=REDACTED/g'
done < "$fifo" &
reader_pid=$!

dsh web --no-open --port 3081 \
  --trusted-host "localhost:3080" \
  --trusted-host "127.0.0.1:3080" > "$fifo" 2>&1 &
dsh_pid=$!
trap cleanup EXIT
trap 'exit 130' INT
trap 'exit 143' TERM

# 任一服务退出即结束容器，让 restart 策略重启完整服务。
while kill -0 "$proxy_pid" 2>/dev/null && kill -0 "$dsh_pid" 2>/dev/null; do
  sleep 1
done
exit 1
