#!/usr/bin/env bash

LEIN=$(command -v lein)
if [[ "${LEIN}" == "" ]]; then
  LEIN="./ci/lein"
fi

test() {
  ./ci/lein clean
  ./ci/lein compile
  ./ci/lein test2junit
}

uberjar() {
  set -x
  ./ci/lein clean
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
