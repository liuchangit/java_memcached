#!/bin/bash

echo
echo "#############     Memcached Server stopping     #############"
echo

MC_HOME=$(cd "$(dirname $0)"; pwd)
echo "memcached.home = $MC_HOME"
cd $MC_HOME

conf=./conf
CONF_DIR=$(cd "$conf"; pwd)
echo "config.dir = $CONF_DIR"

ps -ef | grep java | grep "memcached.StartServer" | awk '{print $2}' | xargs kill -9

echo
echo "#############     Memcached Server stopped     #############"
echo

