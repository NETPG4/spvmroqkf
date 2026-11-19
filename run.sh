#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")"
mkdir -p build/classes
find src/main/java src/test/java -name '*.java' > build/sources.txt
javac -encoding UTF-8 --release 17 -d build/classes @build/sources.txt
mode="${1:-server}"
if [ "$#" -gt 0 ]; then shift; fi
case "$mode" in
  server) java -cp build/classes kr.ac.moyeotalk.server.ChatServer "$@" ;;
  client) java -cp build/classes kr.ac.moyeotalk.client.ChatClient "$@" ;;
  test|test-local)
    java -ea -cp build/classes kr.ac.moyeotalk.StarterTest codec
    java -ea -cp build/classes kr.ac.moyeotalk.client.ConsoleInputTest
    java -ea -cp build/classes kr.ac.moyeotalk.server.ReliabilityTest
    if [ "$mode" = "test" ]; then java -ea -cp build/classes kr.ac.moyeotalk.StarterTest; fi
    ;;
  *) echo '사용법: ./run.sh server [port] | client [host] [port] | test | test-local'; exit 1 ;;
esac
