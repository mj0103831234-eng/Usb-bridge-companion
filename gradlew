#!/bin/sh

GRADLE_HOME="${GRADLE_USER_HOME:-$HOME/.gradle}"

die() {
    echo "$*" >&2
    exit 1
}

APP_BASE_NAME=$(basename "$0")
APP_HOME=$(cd "$(dirname "$0")" && pwd -P)

CLASSPATH="$APP_HOME/gradle/wrapper/gradle-wrapper.jar"

exec java -classpath "$CLASSPATH" org.gradle.wrapper.GradleWrapperMain "$@"