#!/bin/sh

lein clean
lein compile
lein test2junit
