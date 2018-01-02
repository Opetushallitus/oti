#!/bin/sh

export JAVA_HOME="${bamboo_capability_system_jdk_JDK_1_8}"
export PATH=$JAVA_HOME/bin:$PATH

test() {
  ./ci/lein clean
  ./ci/lein compile
  ./ci/lein test2junit
}

uberjar() {
  set -x
  ./ci/lein clean
  echo ${bamboo_buildResultKey} > ./resources/build.txt
  git rev-parse HEAD > ./resources/git-rev.txt
  ./ci/lein uberjar
}

command="$1"

case "$command" in
    "test" )
        test
        ;;
    "uberjar" )
        uberjar
        ;;
    *)
        echo "Unknown command $command"
        ;;
esac
