#!/bin/sh

echo $JAVA_HOME
export PATH=$JAVA_HOME/bin:$PATH

test() {
  ./ci/lein clean
  ./ci/lein compile
  ./ci/lein test2junit
}

uberjar() {
  set -x
  ./ci/lein clean
  echo ${TRAVIS_BUILD_NUMBER} > ./resources/build.txt
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
