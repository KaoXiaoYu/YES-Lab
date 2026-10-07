#!/usr/bin/env bash
# 临时 MySQL 演练实例的统一入口。
#
# 存在理由（2026-09-21 事故）：上一个会话手工起了两个 mysqld 跑同一个 datadir，
# 收尾没关，`--log-error` 追加写满 146 GB；数据目录后来被删掉，句柄仍被持有，
# `du` 与 Finder 完全看不见这块占用，系统盘一度只剩 12 GB。
#
# 因此本脚本把三个约束固化成代码：单实例（pid 文件互斥）、日志有界（看门狗）、
# 生命期有界（TTL 看门狗无条件 kill）。**不要再手工 `mysqld ... &`。**
#
#   scripts/temp-mysql.sh init     [--dir DIR] [--port N]
#   scripts/temp-mysql.sh start    [--dir DIR] [--port N] [--ttl 分钟] [--log-cap MB]
#   scripts/temp-mysql.sh status   [--dir DIR]
#   scripts/temp-mysql.sh stop     [--dir DIR]        # 关停并删除整个目录
#
# 默认：DIR=/tmp/openlims-rehearsal，端口 3399，TTL 60 分钟，run.log 上限 200 MB。

set -uo pipefail

DIR=${DIR:-/tmp/openlims-rehearsal}
PORT=${PORT:-3399}
TTL_MIN=${TTL_MIN:-60}
LOG_CAP_MB=${LOG_CAP_MB:-200}
MYSQLD=${MYSQLD:-/usr/local/opt/mysql/bin/mysqld}
WATCH_INTERVAL=10

die() { echo "错误：$*" >&2; exit 1; }

# 解析 --key value 形式的参数
while [ $# -gt 0 ]; do
  case "$1" in
    --dir) DIR=$2; shift 2 ;;
    --port) PORT=$2; shift 2 ;;
    --ttl) TTL_MIN=$2; shift 2 ;;
    --log-cap) LOG_CAP_MB=$2; shift 2 ;;
    -*) die "未知参数 $1" ;;
    *) break ;;
  esac
done
CMD=${1:-}
[ -n "$CMD" ] || die "用法：$0 {init|start|status|stop} [--dir DIR] [--port N] [--ttl 分钟] [--log-cap MB]"

PIDFILE="$DIR/mysqld.pid"
WATCH_PIDFILE="$DIR/watchdog.pid"
LOGFILE="$DIR/run.log"
LOGCAP_BYTES=$(( LOG_CAP_MB * 1024 * 1024 ))

running_pid() { # 输出仍在运行的 mysqld pid，没有则空
  local pid
  pid=$(cat "$PIDFILE" 2>/dev/null || true)
  if [ -n "${pid:-}" ] && kill -0 "$pid" 2>/dev/null; then echo "$pid"; fi
}

do_init() {
  [ -x "$MYSQLD" ] || die "找不到 mysqld：${MYSQLD}（可用 MYSQLD=/path/to/mysqld 覆盖）"
  if [ -d "$DIR/data/mysql" ]; then echo "已初始化，跳过：$DIR/data"; return 0; fi
  pkill -f "mysqld.*--datadir=$DIR/data" 2>/dev/null && die "已有同 datadir 的 mysqld 在跑，拒绝重复初始化"
  mkdir -p "$DIR"
  echo "初始化 datadir：$DIR/data"
  "$MYSQLD" --initialize-insecure --datadir="$DIR/data" --log-error="$DIR/init.log" >/dev/null 2>&1 \
    || die "初始化失败，见 $DIR/init.log"
  echo "完成。"
}

do_start() {
  [ -d "$DIR/data/mysql" ] || die "尚未初始化，先跑：$0 init --dir $DIR"
  local pid; pid=$(running_pid)
  [ -n "$pid" ] && die "已有实例在跑（pid=${pid}）。要重来先 stop。禁止同 datadir 起第二个进程。"
  mkdir -p "$DIR"
  : > "$LOGFILE"
  nohup "$MYSQLD" \
    --datadir="$DIR/data" \
    --socket="$DIR/m.sock" \
    --port="$PORT" \
    --bind-address=127.0.0.1 \
    --pid-file="$PIDFILE" \
    --log-error="$LOGFILE" \
    >>"$DIR/mysqld.stdout" 2>&1 &
  local i
  for i in $(seq 1 60); do
    sleep 1
    pid=$(running_pid)
    [ -n "$pid" ] && break
  done
  [ -n "${pid:-}" ] || die "mysqld 未能在 60 秒内起来，见 $LOGFILE"
  echo "已启动：pid=$pid port=$PORT datadir=$DIR/data"
  echo "TTL=${TTL_MIN} 分钟，run.log 上限=${LOG_CAP_MB} MB，看门狗每 ${WATCH_INTERVAL} 秒检查一次。"

  # 看门狗：TTL 到期或日志超限即无条件关停，避免「没人管的后台进程」重演
  nohup bash -c '
    DIR="$1"; PIDFILE="$2"; LOGFILE="$3"; DEADLINE="$4"; CAP="$5"; INTERVAL="$6"
    while :; do
      sleep "$INTERVAL"
      pid=$(cat "$PIDFILE" 2>/dev/null || true)
      if [ -z "${pid:-}" ] || ! kill -0 "$pid" 2>/dev/null; then
        echo "$(date "+%F %T") mysqld 已自行退出，看门狗收工"; exit 0
      fi
      now=$(date +%s)
      size=$(stat -f %z "$LOGFILE" 2>/dev/null || echo 0)
      if [ "$now" -ge "$DEADLINE" ]; then reason="TTL 到期"; break; fi
      if [ "$size" -gt "$CAP" ]; then reason="run.log 超过上限（$((size/1048576)) MB）"; break; fi
    done
    echo "$(date "+%F %T") 兜底关停：${reason}（pid=${pid}）。数据保留在 ${DIR}，需要清理请跑 temp-mysql.sh stop"
    kill "$pid" 2>/dev/null; sleep 5; kill -9 "$pid" 2>/dev/null
  ' _ "$DIR" "$PIDFILE" "$LOGFILE" "$(( $(date +%s) + TTL_MIN * 60 ))" "$LOGCAP_BYTES" "$WATCH_INTERVAL" \
    >>"$DIR/watchdog.log" 2>&1 &
  echo $! > "$WATCH_PIDFILE"
}

do_status() {
  local pid; pid=$(running_pid)
  if [ -n "$pid" ]; then
    echo "运行中：pid=$pid port=$PORT"
    echo "run.log: $(du -h "$LOGFILE" 2>/dev/null | cut -f1)"
  else
    echo "未运行（datadir：$DIR/data）"
  fi
}

do_stop() {
  local pid; pid=$(running_pid)
  local wpid; wpid=$(cat "$WATCH_PIDFILE" 2>/dev/null || true)
  [ -n "${wpid:-}" ] && kill "$wpid" 2>/dev/null
  if [ -n "$pid" ]; then
    kill "$pid" 2>/dev/null
    local i
    for i in $(seq 1 20); do sleep 1; kill -0 "$pid" 2>/dev/null || break; done
    kill -0 "$pid" 2>/dev/null && { echo "优雅关停超时，强杀 pid=$pid"; kill -9 "$pid" 2>/dev/null; sleep 2; }
  fi
  rm -rf "$DIR"
  echo "已关停并删除 ${DIR}。"
}

case "$CMD" in
  init) do_init ;;
  start) do_start ;;
  status) do_status ;;
  stop) do_stop ;;
  *) die "未知子命令 $CMD" ;;
esac
