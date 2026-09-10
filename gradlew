#!/usr/bin/env sh

##############################################################################
##
##  Gradle start up script for UN*X
##
##############################################################################

# Attempt to set APP_HOME
PRG="$0"
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

DEFAULT_JVM_OPTS=''

MAX_FD="maximum"

if [ "$MAX_FD" = "maximum" ] ; then
    MAX_FD_LIMIT=`ulimit -H -n`
    if [ $? -eq 0 ] ; then
        if [ "$MAX_FD_LIMIT" != "unlimited" ] ; then
            ulimit -n $MAX_FD_LIMIT
        fi
    fi
fi

JAVA_OPTS=\"$JAVA_OPTS -Xmx64m -Xms64m\"

CLASSPATH=$APP_HOME/gradle/wrapper/gradle-wrapper.jar

JAVACMD=`which java`

if [ ! -x \"$JAVACMD\" ] ; then
    echo \"Error: JAVA_HOME is not defined correctly.\" >&2
    exit 1
fi

if [ -z \"$JAVA_HOME\" ] ; then
    JAVA_HOME=`dirname $JAVACMD`
    JAVA_HOME=`cd \"$JAVA_HOME/..\" >/dev/null 2>&1 && pwd`
fi

exec \"$JAVACMD\" $DEFAULT_JVM_OPTS $JAVA_OPTS -classpath \"$CLASSPATH\" org.gradle.wrapper.GradleWrapperMain \"$@\"
