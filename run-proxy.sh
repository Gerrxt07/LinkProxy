#!/usr/bin/env sh
set -eu

ROOT_DIR=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
VERSION=$(sed -n 's/^version=//p' "$ROOT_DIR/gradle.properties" | head -n 1)
JAR="$ROOT_DIR/proxy/build/libs/link-proxy-$VERSION-all.jar"
RUN_DIR="${LINK_RUN_DIR:-$ROOT_DIR/run}"

if [ ! -f "$JAR" ]; then
  "$ROOT_DIR/gradlew" :link-proxy:shadowJar
fi

mkdir -p "$RUN_DIR"
cd "$RUN_DIR"

exec java --enable-native-access=ALL-UNNAMED ${JAVA_OPTS:-} -jar "$JAR" "$@"
