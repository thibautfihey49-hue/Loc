#!/bin/sh
APP_HOME=$( cd "$( dirname "$0" )" && pwd )
CLASSPATH=$APP_HOME/gradle/wrapper/gradle-wrapper.jar
if [ -z "$JAVA_HOME" ] ; then JAVA_EXECUTABLE=java; else JAVA_EXECUTABLE=$JAVA_HOME/bin/java; fi
exec "$JAVA_EXECUTABLE" -Xmx64m -Xms64m -classpath "$CLASSPATH" "-Dorg.gradle.appname=$(basename "$0")" org.gradle.wrapper.GradleWrapperMain "$@"
