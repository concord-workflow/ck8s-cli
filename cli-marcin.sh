#!/usr/bin/env bash

mvn clean install

java -jar target/quarkus-app/quarkus-run.jar \
--target-root /tmp/ck8s \
--ck8s-root /Users/marcin.karpezo/projects/ck8s-systems/ck8s \
--ck8s-ext-root /Users/marcin.karpezo/projects/ck8s-systems/ck8s-ext \
--flow-executor concord-cli \
--verbose \
$@