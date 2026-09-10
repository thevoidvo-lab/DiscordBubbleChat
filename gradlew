#!/usr/bin/env sh

#
# Copyright 2015 the original author or authors.
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#      https://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.
#

##############################################################################
##
##  Gradle start up script for UN*X
##
##############################################################################

# Attempt to set APP_HOME
# Resolve links: $0 may be a symlink
PRG="$0"
# Need this for relative symlinks.
while [ -h "$PRG" ] ; do
    ls -ld "$PRG"
    link=`expr "$PRG" : '.*->\(.*\)$'`
    if expr "$link" : '/.*' > /dev/null; then
        PRG="$link"
    else
        PRG=`dirname "$PRG"`"/$link"
    fi
done
SAVED="$(cd "$(dirname \"$PRG\")" >/dev/null 2>&1 && pwd)"
APP_HOME="$(cd "$(dirname \"$SAVED\")" >/dev/null 2>&1 && pwd)"
APP_HOME_PARENT="$(cd "$(dirname \"$APP_HOME\")" >/dev/null 2>&1 && pwd)"

# Add default JVM options here. You can also use JAVA_OPTS and GRADLE_OPTS to pass JVM options to this script.
DEFAULT_JVM_OPTS='" \"-Xmx64m\" \"-Xms64m\"'

# Use the maximum available, or set MAX_FD != unlimited.
MAX_FD="maximum"

# Increase the maximum file descriptors if we can.
if [ "$MAX_FD" = "maximum" ] ; then
    MAX_FD_LIMIT=`ulimit -H -n`
    if [ $? -eq 0 ] ; then
        if [ "$MAX_FD_LIMIT" != "unlimited" ] ; then
            ulimit -n $MAX_FD_LIMIT
        fi
    else
        warn "Could not query maximum file descriptor limit"
    fi
fi

# For Darwin, add options to specify how the application appears in the dock
if $darwin; then
    GRADLE_OPTS="$GRADLE_OPTS \"-Xdock:name=$APP_NAME\" \"-Xdock:icon=$APP_HOME/media/gradle.icns\""
fi

# For Cygwin or MSYS, switch paths to Windows format before running java
if $cygwin || $msys ; then
    APP_HOME=`cygpath --path --mixed "$APP_HOME"`
    CLASSPATH=`cygpath --path --mixed "$CLASSPATH"`

    JAVACMD=`cygpath --unix "$JAVACMD"`

    # Now convert the arguments - kludge to limit ourselves to /bin/sh
    for arg do
        if
            case $arg in                                #(
              -*)   false ;; # don't mess with options #(
              /?*)  t=1;; # test for absolute directory name #(
              *)    t=0;; # test relative directory name #(
            esac
        then
            arg=`cygpath --path --mixed "$arg"`
        fi
        arg="$(printf 'x%s' "$arg" | sed 's/x\(.*\)/\1/' )"
        args="$args \"$arg\""
    done
fi


# Collect all arguments for the java command, stacking in reverse order:
#   * args from the command line
#   * the main class name
#   * -classpath
#   * -D...=... system properties
#   * ideas from common.gradle.kts files

for arg; do
    if
        case $arg in                                #(
          -*)   false ;; # don't mess with options #(
          /?*)  t=1;; # test for absolute directory name #(
          *)    t=0;; # test relative directory name #(
        esac
    then
        arg="$(cygpath --path --mixed \"$arg\")"
    fi
    args="$args $(printf 'x%s' \"$arg\" | sed 's/x\(.*\)/\1/')"
done

# Escape application args
save () {
    for arg do
        printf 'x%s' \"$arg\" |
        sed \"s/x\(.*\)/\1/\" |
        sed \"s/'/'\\\\\\\\''/g; s/.*/'\\&\u0027/; s/^\u0027\(.*\)\u0027$/\1/\" &&
        printf ' '
    done
    echo " "
}
APP_ARGS=$(save "$@")

# Collect all arguments for the java command
set -- \
        "-Dorg.gradle.appname=$APP_BASE_NAME" \
        -classpath "$CLASSPATH" \
        org.gradle.wrapper.GradleWrapperMain \
        "$@"

exec "$JAVACMD" "$@"
