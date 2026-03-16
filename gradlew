#!/bin/sh

APP_NAME="Gradle"
APP_BASE_NAME=${0##*/}
APP_HOME=$(cd "${0%/*}" && pwd -P)

DEFAULT_JVM_OPTS="-Xmx64m -Xms64m"

# Use the maximum available, or set MAX_FD != -1 to use that value.
MAX_FD=maximum

warn () {
    echo "$*"
}

die () {
    echo
    echo "$*"
    echo
    exit 1
}

# OS specific support.
cygwin=false
msys=false
darwin=false
case "$(uname)" in
  CYGWIN*) cygwin=true ;;
  Darwin*) darwin=true ;;
  MSYS*) msys=true ;;
esac

# Determine the Java command to use.
if [ -n "$JAVA_HOME" ]; then
    if [ -x "$JAVA_HOME/jre/sh/java" ]; then
        JAVACMD="$JAVA_HOME/jre/sh/java"
    else
        JAVACMD="$JAVA_HOME/bin/java"
    fi
    if [ ! -x "$JAVACMD" ]; then
        die "ERROR: JAVA_HOME is set to an invalid directory: $JAVA_HOME"
    fi
else
    JAVACMD="java"
    which java >/dev/null 2>&1 || die "ERROR: JAVA_HOME is not set and no 'java' command could be found in your PATH."
fi

# Increase the maximum file descriptors if we can.
if ! $cygwin; then
    if [ "$MAX_FD" != "maximum" ]; then
        MAX_FD_LIMIT=$(ulimit -H -n)
        if [ $MAX_FD -le $MAX_FD_LIMIT ]; then
            ulimit -n $MAX_FD
        fi
    fi
fi

# The Gradle wrapper JAR.
CLASSPATH="$APP_HOME/gradle/wrapper/gradle-wrapper.jar"

# Collect all arguments.
GRADLE_ARGS=""
while [ "$#" -gt 0 ]; do
    case "$1" in
        -D*)
            JAVA_OPTS="$JAVA_OPTS $1"
            ;;
        *)
            GRADLE_ARGS="$GRADLE_ARGS $1"
            ;;
    esac
    shift
done

exec "$JAVACMD" $JAVA_OPTS $GRADLE_OPTS $DEFAULT_JVM_OPTS \
    -classpath "$CLASSPATH" \
    org.gradle.wrapper.GradleWrapperMain \
    $GRADLE_ARGS
