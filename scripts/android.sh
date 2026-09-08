#!/usr/bin/env bash
# Build, install, and debug the Lectern Android app on an emulator or a
# device over adb.  Resolves an SDK/JDK on its own and, when the host is
# missing a usable JDK or adb, re-runs itself inside the flake's devshell.
#
#   scripts/android.sh                 build debug, install, launch
#   scripts/android.sh debug -e        same on an emulator, following logcat
#   scripts/android.sh build --release assemble the release APK
#   scripts/android.sh doctor          show what the script resolved
set -euo pipefail

REPO="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
ANDROID_DIR="$REPO/android"
GRADLE_MODULE="app"

# ---------------------------------------------------------------- output ---

if [ -t 2 ]; then
  C_RESET=$'\033[0m'; C_DIM=$'\033[2m'; C_RED=$'\033[31m'
  C_YELLOW=$'\033[33m'; C_BLUE=$'\033[34m'
else
  C_RESET=; C_DIM=; C_RED=; C_YELLOW=; C_BLUE=
fi

say()  { printf '%s==>%s %s\n' "$C_BLUE" "$C_RESET" "$*" >&2; }
warn() { printf '%swarn:%s %s\n' "$C_YELLOW" "$C_RESET" "$*" >&2; }
die()  { printf '%serror:%s %s\n' "$C_RED" "$C_RESET" "$*" >&2; exit 1; }
run()  { printf '%s$ %s%s\n' "$C_DIM" "$*" "$C_RESET" >&2; "$@"; }

usage() {
  cat >&2 <<'USAGE'
usage: scripts/android.sh [command] [options]

commands
  run                build the debug APK, install it, launch it   (default)
  debug              run, then follow logcat; --wait to attach a debugger
  build              assemble an APK without installing
  install            build and install, but do not launch
  apk                print the APK path; --out DIR copies it there
  test               run the JVM unit tests
  emulator           boot an AVD (--avd NAME, or the only/first one)
  avds               list the AVDs this SDK knows about
  devices            list attached emulators and devices
  logcat             follow the app's logs on the chosen target
  stop | uninstall   force-stop or uninstall the app
  clean              gradle clean
  doctor             report the resolved SDK, JDK, AVDs, and targets

target selection
  -s, --serial ID    use this adb serial
  -e, --emulator     use a running emulator, booting one if none is running
  -d, --device       use a USB/wifi device
      --avd NAME     AVD to boot with -e or `emulator`
                     (with no flag: the only attached target, or you pick)

build options
      --release      release variant instead of debug
      --no-build     skip gradle, use the APK already on disk
      --stacktrace   pass --stacktrace to gradle
  -G, --gradle-arg A extra gradle argument (repeatable)

other
  -l, --logcat       follow logcat after launching
      --wait         launch stopped, waiting for a JDWP debugger to attach
      --sdk DIR      force this Android SDK
      --no-nix       never re-run inside `nix develop`
  -h, --help         this text
USAGE
}

# ------------------------------------------------------------ arg parsing ---

ORIGINAL_ARGS=("$@")
CMD=""
VARIANT="debug"
TARGET_MODE="auto"   # auto | emulator | device | serial
SERIAL=""
AVD_NAME=""
DO_BUILD=1
FOLLOW_LOGCAT=0
WAIT_DEBUGGER=0
OUT_DIR=""
SDK_OVERRIDE=""
USE_NIX=1
GRADLE_ARGS=()

while [ $# -gt 0 ]; do
  case "$1" in
    run|debug|build|install|apk|test|emulator|emu|avds|devices|logcat|stop|uninstall|clean|doctor)
      [ -z "$CMD" ] || die "more than one command given: $CMD and $1"
      CMD="$1" ;;
    -s|--serial)   SERIAL="${2:?--serial needs an adb serial}"; TARGET_MODE="serial"; shift ;;
    -e|--emulator) TARGET_MODE="emulator" ;;
    -d|--device)   TARGET_MODE="device" ;;
    --avd)         AVD_NAME="${2:?--avd needs a name}"; shift ;;
    --release)     VARIANT="release" ;;
    --no-build)    DO_BUILD=0 ;;
    --stacktrace)  GRADLE_ARGS+=(--stacktrace) ;;
    -G|--gradle-arg) GRADLE_ARGS+=("${2:?--gradle-arg needs a value}"); shift ;;
    -l|--logcat)   FOLLOW_LOGCAT=1 ;;
    --wait)        WAIT_DEBUGGER=1 ;;
    --out)         OUT_DIR="${2:?--out needs a directory}"; shift ;;
    --sdk)         SDK_OVERRIDE="${2:?--sdk needs a directory}"; shift ;;
    --no-nix)      USE_NIX=0 ;;
    -h|--help)     usage; exit 0 ;;
    --)            shift; break ;;
    -*)            usage; die "unknown option: $1" ;;
    *)             usage; die "unknown argument: $1" ;;
  esac
  shift
done
CMD="${CMD:-run}"
[ "$CMD" = "emu" ] && CMD="emulator"
[ "$CMD" = "debug" ] && { VARIANT="debug"; FOLLOW_LOGCAT=1; }

# --------------------------------------------------- project introspection ---

APP_GRADLE="$ANDROID_DIR/$GRADLE_MODULE/build.gradle.kts"
MANIFEST="$ANDROID_DIR/$GRADLE_MODULE/src/main/AndroidManifest.xml"
[ -f "$APP_GRADLE" ] || die "no android module at $APP_GRADLE"

gradle_value() { # gradle_value <key> <default>
  local v
  v="$(sed -nE "s/^[[:space:]]*$1[[:space:]]*=[[:space:]]*\"?([^\"]+)\"?[[:space:]]*$/\1/p" "$APP_GRADLE" | head -1)"
  printf '%s' "${v:-$2}"
}

APP_ID="$(gradle_value applicationId com.lectern)"
COMPILE_SDK="$(gradle_value compileSdk '')"
# The launcher activity, as `.Name` or a fully qualified class.
ACTIVITY="$(tr '\n' ' ' < "$MANIFEST" \
  | sed -nE 's/.*<activity[[:space:]]+android:name="([^"]+)".*/\1/p' | head -1)"
ACTIVITY="${ACTIVITY:-.MainActivity}"
COMPONENT="$APP_ID/$ACTIVITY"

# ----------------------------------------------------------- sdk resolution ---

sdk_has_platform() { # sdk_has_platform <sdk> — does it have the compileSdk platform?
  [ -n "$COMPILE_SDK" ] || return 0
  compgen -G "$1/platforms/android-$COMPILE_SDK" >/dev/null && return 0
  compgen -G "$1/platforms/android-$COMPILE_SDK.*" >/dev/null && return 0
  return 1
}

local_properties_sdk() {
  [ -f "$ANDROID_DIR/local.properties" ] || return 0
  sed -nE 's/^[[:space:]]*sdk\.dir[[:space:]]*=[[:space:]]*(.+)$/\1/p' \
    "$ANDROID_DIR/local.properties" | head -1
}

resolve_sdk() {
  local candidates=() c first=""
  [ -n "$SDK_OVERRIDE" ] && candidates+=("$SDK_OVERRIDE")
  [ -n "${ANDROID_HOME:-}" ] && candidates+=("$ANDROID_HOME")
  [ -n "${ANDROID_SDK_ROOT:-}" ] && candidates+=("$ANDROID_SDK_ROOT")
  c="$(local_properties_sdk)"; [ -n "$c" ] && candidates+=("$c")
  candidates+=("$HOME/Library/Android/sdk" "$HOME/Android/Sdk")

  for c in "${candidates[@]}"; do
    [ -d "$c" ] || continue
    [ -n "$first" ] || first="$c"
    if sdk_has_platform "$c"; then SDK="$c"; return 0; fi
  done

  if [ -n "$first" ]; then
    # Every SDK we found is missing the platform the build compiles against —
    # most likely the flake's android-sdk, which pins its platforms explicitly.
    warn "no SDK found with platforms/android-$COMPILE_SDK; using $first"
    warn "add platforms-android-$COMPILE_SDK to the android-sdk list in flake.nix,"
    warn "or lower compileSdk in android/app/build.gradle.kts"
    SDK="$first"; return 0
  fi
  die "no Android SDK found — set ANDROID_HOME, pass --sdk DIR, or enter \`nix develop\`"
}

ENV_SDK="${ANDROID_HOME:-}"
resolve_sdk
# In the devshell ANDROID_HOME is the flake's android-sdk; say so when the
# build needs a different one (it pins platforms, so it can lag compileSdk).
if [ -n "$ENV_SDK" ] && [ "$ENV_SDK" != "$SDK" ]; then
  say "using $SDK instead of ANDROID_HOME=$ENV_SDK (it has platforms/android-$COMPILE_SDK)"
fi
export ANDROID_HOME="$SDK" ANDROID_SDK_ROOT="$SDK"
PATH="$SDK/platform-tools:$SDK/emulator:$SDK/cmdline-tools/latest/bin:$SDK/tools/bin:$PATH"
export PATH

# Gradle reads sdk.dir from local.properties in preference to the environment,
# so keep the two in step.  The file is gitignored and machine-local.
sync_local_properties() {
  local current; current="$(local_properties_sdk)"
  [ "$current" = "$SDK" ] && return 0
  say "pointing android/local.properties at $SDK"
  printf 'sdk.dir=%s\n' "$SDK" > "$ANDROID_DIR/local.properties"
}

# ------------------------------------------------------ jdk / nix devshell ---

java_major() { # java_major <java binary>
  "$1" -version 2>&1 | sed -nE 's/.*version "([0-9]+).*/\1/p' | head -1
}

find_java() {
  local candidates=() c major
  [ -n "${JAVA_HOME:-}" ] && candidates+=("$JAVA_HOME/bin/java")
  command -v java >/dev/null 2>&1 && candidates+=("$(command -v java)")
  if [ -x /usr/libexec/java_home ]; then
    c="$(/usr/libexec/java_home -v 17+ 2>/dev/null || true)"
    [ -n "$c" ] && candidates+=("$c/bin/java")
  fi
  # Android Studio ships a JDK; it is the same one the IDE builds with.
  candidates+=(
    "/Applications/Android Studio.app/Contents/jbr/Contents/Home/bin/java"
    "$HOME/Applications/Android Studio.app/Contents/jbr/Contents/Home/bin/java"
    "/opt/android-studio/jbr/bin/java"
  )
  for c in "${candidates[@]}"; do
    [ -x "$c" ] || continue
    major="$(java_major "$c")"
    if [ -n "$major" ] && [ "$major" -ge 17 ]; then
      JAVA_BIN="$c"
      export JAVA_HOME="$(cd "$(dirname "$c")/.." && pwd)"
      return 0
    fi
  done
  return 1
}

# The Gradle wrapper needs a JDK 17+ and everything else needs adb; when the
# host has neither, the flake's devshell has both.  Re-exec there once.
maybe_reexec_nix() {
  [ "${LECTERN_ANDROID_NIX:-0}" = "1" ] && return 0
  [ "$USE_NIX" = "1" ] || return 0
  [ -f "$REPO/flake.nix" ] || return 0
  command -v nix >/dev/null 2>&1 || return 0
  # Nix evaluates a flake in a git repo from the *tracked* tree, so an
  # untracked devshell.nix would make `nix develop .` fail; fall back to a
  # path: flakeref, which sees the working tree as it is.
  local flakeref="$REPO"
  if git -C "$REPO" rev-parse --git-dir >/dev/null 2>&1; then
    local f untracked=""
    for f in flake.nix devshell.nix; do
      [ -e "$REPO/$f" ] || continue
      git -C "$REPO" ls-files --error-unmatch "$f" >/dev/null 2>&1 || untracked="$untracked $f"
    done
    if [ -n "$untracked" ]; then
      warn "untracked flake files:$untracked — \`git add\` them so nix can evaluate the flake from git"
      flakeref="path:$REPO"
    fi
  fi
  say "no JDK 17+ or adb on PATH — re-running inside \`nix develop\`"
  export LECTERN_ANDROID_NIX=1
  if [ "${#ORIGINAL_ARGS[@]}" -gt 0 ]; then
    exec nix develop "$flakeref" --command bash "${BASH_SOURCE[0]}" "${ORIGINAL_ARGS[@]}"
  fi
  exec nix develop "$flakeref" --command bash "${BASH_SOURCE[0]}"
}

# need_toolchain [java]  — ensure adb, and a JDK 17+ when gradle will run.
need_toolchain() {
  local want_java="${1:-}" missing=0
  command -v adb >/dev/null 2>&1 || missing=1
  if [ "$want_java" = "java" ]; then find_java || missing=1; fi
  if [ "$missing" = "1" ]; then
    maybe_reexec_nix
    command -v adb >/dev/null 2>&1 || die "adb not found in $SDK/platform-tools"
    if [ "$want_java" = "java" ]; then
      find_java || die "no JDK 17+ found; this Gradle build needs one (\`nix develop\` provides it)"
    fi
  fi
}

# ------------------------------------------------------------------- adb ----

adb_() { adb ${SERIAL:+-s "$SERIAL"} "$@"; }

list_targets() { # serial<TAB>state<TAB>description
  adb devices -l 2>/dev/null | awk 'NR>1 && NF {print}' | while read -r line; do
    local_serial="${line%% *}"
    state="$(printf '%s' "$line" | awk '{print $2}')"
    desc="$(printf '%s' "$line" | sed -E 's/^[^ ]+ +[^ ]+ +//')"
    printf '%s\t%s\t%s\n' "$local_serial" "$state" "$desc"
  done
}

online_targets() { list_targets | awk -F'\t' '$2=="device" {print}'; }

count_lines() { printf '%s\n' "$1" | awk 'NF {n++} END {print n+0}'; }

avd_list() { emulator -list-avds 2>/dev/null | sed '/^$/d'; }

pick_avd() {
  local avds count
  if [ -n "$AVD_NAME" ]; then printf '%s' "$AVD_NAME"; return 0; fi
  avds="$(avd_list)"
  count="$(count_lines "$avds")"
  [ "$count" = "0" ] && die "no AVDs — create one with \`avdmanager create avd\` (system images come from flake.nix)"
  printf '%s' "$(printf '%s\n' "$avds" | head -1)"
}

boot_emulator() {
  local avd log
  avd="$(pick_avd)"
  log="${TMPDIR:-/tmp}/lectern-emulator-$avd.log"
  say "booting AVD $avd (log: $log)"
  nohup emulator -avd "$avd" -netdelay none -netspeed full >"$log" 2>&1 &
  disown || true
  wait_for_boot
}

wait_for_boot() {
  local waited=0 serial
  say "waiting for the emulator to come up"
  until serial="$(online_targets | awk -F'\t' '$1 ~ /^emulator-/ {print $1; exit}')"; [ -n "$serial" ]; do
    [ "$waited" -ge 300 ] && die "emulator did not appear in adb after 5 minutes"
    sleep 2; waited=$((waited + 2))
  done
  SERIAL="$serial"
  until [ "$(adb_ shell getprop sys.boot_completed 2>/dev/null | tr -d '\r')" = "1" ]; do
    [ "$waited" -ge 300 ] && die "emulator $SERIAL booted too slowly"
    sleep 2; waited=$((waited + 2))
  done
  say "emulator ready: $SERIAL"
}

# Sets SERIAL to the target this run should talk to.
select_target() {
  [ -n "$SERIAL" ] && return 0
  adb start-server >/dev/null 2>&1 || true
  local rows count row

  case "$TARGET_MODE" in
    emulator) rows="$(online_targets | awk -F'\t' '$1 ~ /^emulator-/')" ;;
    device)   rows="$(online_targets | awk -F'\t' '$1 !~ /^emulator-/')" ;;
    *)        rows="$(online_targets)" ;;
  esac

  count="$(count_lines "$rows")"

  if [ "$count" = "0" ]; then
    case "$TARGET_MODE" in
      device)
        die "no device attached — plug one in with USB debugging on, or use -e" ;;
      emulator)
        boot_emulator; return 0 ;;
      *)
        if [ -n "$(avd_list)" ]; then
          say "nothing attached; booting an emulator"
          boot_emulator; return 0
        fi
        die "no emulator or device attached (and no AVDs to boot)" ;;
    esac
  fi

  if [ "$count" = "1" ]; then
    SERIAL="$(printf '%s' "$rows" | awk -F'\t' '{print $1; exit}')"
    return 0
  fi

  if [ ! -t 0 ]; then
    SERIAL="$(printf '%s' "$rows" | awk -F'\t' '{print $1; exit}')"
    warn "several targets attached; using $SERIAL (pass -s to choose)"
    return 0
  fi

  say "several targets attached:"
  local i=1
  while IFS=$'\t' read -r s _ desc; do
    printf '  %d) %s %s%s%s\n' "$i" "$s" "$C_DIM" "$desc" "$C_RESET" >&2
    i=$((i + 1))
  done <<< "$rows"
  printf 'target [1]: ' >&2
  read -r choice || choice=1
  choice="${choice:-1}"
  SERIAL="$(printf '%s' "$rows" | awk -F'\t' -v n="$choice" 'NR==n {print $1; exit}')"
  [ -n "$SERIAL" ] || die "no such target: $choice"
}

# ----------------------------------------------------------------- gradle ---

gradlew() {
  sync_local_properties
  # Gradle logs on stdout; keep that clear of callers capturing an APK path.
  ( cd "$ANDROID_DIR" && run ./gradlew ${GRADLE_ARGS[@]+"${GRADLE_ARGS[@]}"} "$@" >&2 )
}

capitalize() { printf '%s%s' "$(printf '%s' "${1:0:1}" | tr '[:lower:]' '[:upper:]')" "${1:1}"; }

apk_path() {
  local dir="$ANDROID_DIR/$GRADLE_MODULE/build/outputs/apk/$VARIANT"
  ls -t "$dir"/*.apk 2>/dev/null | head -1 || true
}

build_apk() {
  if [ "$DO_BUILD" = "1" ]; then
    gradlew ":$GRADLE_MODULE:assemble$(capitalize "$VARIANT")" || die "gradle build failed"
  fi
  local apk; apk="$(apk_path)"
  [ -n "$apk" ] || die "no $VARIANT APK in $ANDROID_DIR/$GRADLE_MODULE/build/outputs/apk/$VARIANT"
  printf '%s' "$apk"
}

install_apk() {
  local apk="$1"
  case "$apk" in
    *unsigned*)
      die "$(basename "$apk") is unsigned — the release build type has no signingConfig, so it cannot be installed; use the debug variant or add one" ;;
  esac
  say "installing $(basename "$apk") on $SERIAL"
  run adb -s "$SERIAL" install -r -d "$apk"
}

launch_app() {
  say "launching $COMPONENT"
  if [ "$WAIT_DEBUGGER" = "1" ]; then
    adb_ shell am start -D -n "$COMPONENT" >&2
    say "stopped, waiting for a debugger — attach to $APP_ID (Run > Attach Debugger in Studio)"
  else
    adb_ shell am start -n "$COMPONENT" >&2
  fi
}

follow_logcat() {
  local pid waited=0
  while :; do
    # pidof exits non-zero until the process shows up; that is not an error.
    pid="$(adb_ shell pidof "$APP_ID" 2>/dev/null | tr -d '\r' | awk '{print $1}' || true)"
    [ -n "$pid" ] && break
    [ "$waited" -ge 20 ] && break
    sleep 1; waited=$((waited + 1))
  done
  if [ -n "$pid" ]; then
    say "logcat for $APP_ID (pid $pid) — ctrl-c to stop"
    adb_ logcat --pid="$pid"
  else
    warn "$APP_ID is not running; showing the unfiltered log instead"
    adb_ logcat
  fi
}

# --------------------------------------------------------------- commands ---

cmd_build() {
  need_toolchain java
  local apk
  apk="$(build_apk)" || exit 1
  [ -n "$apk" ] || exit 1
  say "APK: $apk ($(du -h "$apk" | awk '{print $1}'))"
  [ -n "$OUT_DIR" ] && { mkdir -p "$OUT_DIR"; cp "$apk" "$OUT_DIR/"; say "copied to $OUT_DIR/$(basename "$apk")"; }
  [ "$VARIANT" = "release" ] && case "$apk" in
    *unsigned*) warn "this APK is unsigned; add a signingConfig to install it on a device" ;;
  esac
  return 0
}

cmd_run() {
  if [ "$DO_BUILD" = "1" ]; then need_toolchain java; else need_toolchain; fi
  local apk
  apk="$(build_apk)" || exit 1
  [ -n "$apk" ] || exit 1
  select_target
  install_apk "$apk"
  [ "$CMD" = "install" ] && return 0
  launch_app
  [ "$FOLLOW_LOGCAT" = "1" ] && follow_logcat
  return 0
}

cmd_doctor() {
  printf 'repo          %s\n' "$REPO"
  printf 'app id        %s\n' "$APP_ID"
  printf 'launch        %s\n' "$COMPONENT"
  printf 'compileSdk    %s\n' "${COMPILE_SDK:-?}"
  printf 'sdk           %s%s\n' "$SDK" \
    "$(sdk_has_platform "$SDK" && printf '' || printf '  (missing platforms/android-%s)' "$COMPILE_SDK")"
  printf 'local.props   %s\n' "$(local_properties_sdk || true)"
  printf 'in nix shell  %s\n' "$([ "${LECTERN_ANDROID_NIX:-0}" = 1 ] && echo yes || echo no)"
  if find_java; then
    printf 'java          %s (%s)\n' "$JAVA_BIN" "$(java_major "$JAVA_BIN")"
  else
    printf 'java          %s\n' "none with major >= 17 — the script would use \`nix develop\`"
  fi
  printf 'adb           %s\n' "$(command -v adb || echo 'not on PATH')"
  printf 'emulator      %s\n' "$(command -v emulator || echo 'not on PATH')"
  printf 'avds          %s\n' "$(avd_list | tr '\n' ' ')"
  printf 'targets\n'
  list_targets | sed 's/\t/  /g; s/^/  /' || true
  local apk
  for VARIANT in debug release; do
    apk="$(apk_path)"
    printf '%-13s %s\n' "$VARIANT apk" "${apk:-not built}"
  done
}

case "$CMD" in
  run|install)  cmd_run ;;
  debug)        cmd_run ;;
  build)        cmd_build ;;
  apk)
    apk="$(apk_path)"
    [ -n "$apk" ] || die "no $VARIANT APK built yet — run \`scripts/android.sh build\`"
    [ -n "$OUT_DIR" ] && { mkdir -p "$OUT_DIR"; cp "$apk" "$OUT_DIR/"; apk="$OUT_DIR/$(basename "$apk")"; }
    printf '%s\n' "$apk" ;;
  test)         need_toolchain java; gradlew ":$GRADLE_MODULE:test$(capitalize "$VARIANT")UnitTest" ;;
  clean)        need_toolchain java; gradlew clean ;;
  emulator)     need_toolchain; boot_emulator ;;
  avds)         avd_list ;;
  devices)      need_toolchain; list_targets | sed 's/\t/  /g' ;;
  logcat)       need_toolchain; select_target; follow_logcat ;;
  stop)         need_toolchain; select_target; run adb -s "$SERIAL" shell am force-stop "$APP_ID" ;;
  uninstall)    need_toolchain; select_target; run adb -s "$SERIAL" uninstall "$APP_ID" ;;
  doctor)       cmd_doctor ;;
  *)            usage; die "unknown command: $CMD" ;;
esac
