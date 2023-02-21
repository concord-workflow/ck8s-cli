#!/usr/bin/env bash

. "${1}"

echo "1" ${1}
echo "2" ${2}
echo "3" "${@:2}"

"${2}"