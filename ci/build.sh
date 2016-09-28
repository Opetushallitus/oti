#!/bin/sh

export JAVA_HOME="${bamboo_capability_system_jdk_JDK_1_8}"
export LEIN_JAVA_CMD="${bamboo_capability_system_jdk_JDK_1_8}/bin/java"
export JAVA_CMD="$LEIN_JAVA_CMD"

./ci/lein clean
./ci/lein compile
./ci/lein test2junit
