#!/bin/bash
#
# Sleep for $1 time and ignore SIGTERM, SIGINT and SIGHUP.

trap '' SIGTERM SIGINT SIGHUP
sleep "$1"