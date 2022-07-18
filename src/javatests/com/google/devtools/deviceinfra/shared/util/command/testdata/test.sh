#!/bin/bash
#
# Sleep $1 time, echo "Hello", sleep $2 time (killable) and echo "Bye".

sleep "$1"

echo "Hello"

trap 'kill ${pid}; wait ${pid}; exit $?' SIGTERM SIGINT
sleep "$2" &
pid=$!
wait ${pid}
wait ${pid}

echo "Bye"