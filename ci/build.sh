#!/bin/sh

./bin/lein clean
./bin/lein compile
./bin/lein test2junit
