#!/bin/sh

./ci/lein clean
./ci/lein compile
./ci/lein test2junit
