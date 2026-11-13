#!/usr/bin/env bash
# Memory regression check (milestone M1b). Generates the 11e-sized synthetic dataset, then loads it with the
# streaming loader under small heap limits, on the JVM and compiled to JavaScript in Node. Fails if either runs out
# of memory or if the typed model's counts change.
#
# The limits are about 30% above what the load needs today (JVM about 50 MB, Node about 100 MB) and far below the
# 300 MB browser budget, so a regression shows up here long before it matters. Raise them deliberately, with a note
# in docs/engine-design.md, when a milestone legitimately needs more.
set -euo pipefail
cd "$(dirname "$0")/.."

JVM_HEAP_MB="${JVM_HEAP_MB:-64}"
NODE_HEAP_MB="${NODE_HEAP_MB:-128}"
EXPECTED='model catalogues=46 entries=12111 groups=4449 costed=5221 links=17869 unresolvedLinks=0 forceEntries=3'

./gradlew -q :engine-jvm:installDist :engine-web:generateJavaScript
dir="$(mktemp -d)"
trap 'rm -rf "$dir"' EXIT
cli=engine-jvm/build/install/engine-jvm/bin/engine-jvm

"$cli" generate "$dir/data" 1.0 1 > /dev/null

echo "JVM, -Xmx${JVM_HEAP_MB}m"
JAVA_OPTS="-XX:+UseSerialGC -Xmx${JVM_HEAP_MB}m" "$cli" load "$dir/data" stream | tee "$dir/jvm.txt"
grep -qxF "$EXPECTED" "$dir/jvm.txt" || { echo "unexpected model counts on the JVM" >&2; exit 1; }

echo "Node, --max-old-space-size=${NODE_HEAP_MB}"
node "--max-old-space-size=${NODE_HEAP_MB}" scripts/run-engine.cjs \
    engine-web/build/generated/teavm/js/engine.js "load:$dir/data:stream" | tee "$dir/node.txt"
grep -qxF "$EXPECTED" "$dir/node.txt" || { echo "unexpected model counts in Node" >&2; exit 1; }

echo "memory check passed"
