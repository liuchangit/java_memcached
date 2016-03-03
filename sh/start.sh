#!/bin/bash

echo
echo "#############     Memcached Server starting     #############"
echo

MC_HOME=$(cd "$(dirname $0)"; pwd)
echo "memcached.home = $MC_HOME"
cd $MC_HOME

conf=./conf
CONF_DIR=$(cd "$conf"; pwd)
echo "config.dir = $CONF_DIR"

LIB=$(cd lib; pwd)
CLASSPATH=$HOME:$CONF_DIR
for f in $LIB/*.jar; do
  CLASSPATH=${CLASSPATH}:$f;
done
#echo "CLASSPATH = $CLASSPATH"

LOG4J_DIR=$(grep log.dir= $CONF_DIR/log4j.properties | cut -d'=' -f2)
mkdir -p $LOG4J_DIR

LOG_FILE=memcached.log
if [ -f "$LOG_FILE" ]; then
   mv $LOG_FILE ${LOG_FILE}.bak
fi
if [ -f "gc.log" ]; then
   mv "gc.log"  "gc.log.bak"
fi

nohup java -server -Xloggc:gc.log -XX:+PrintGCDetails -XX:+PrintGCDateStamps -XX:+PrintGCApplicationStoppedTime -XX:+PrintGCApplicationConcurrentTime -XX:+UseConcMarkSweepGC -Xmx2g -Xms2g -Xmn512m -classpath $CLASSPATH com.liuchangit.memcached.StartServer > $LOG_FILE 2>&1 &

IP=`/sbin/ifconfig eth0 | grep "inet addr" | cut -d':' -f2 | cut -d' ' -f1`
PORT=$(grep PORT $CONF_DIR/config.properties | cut -d'=' -f2)
echo
echo "memcached listening at => $IP:$PORT"

echo
echo "#############     Memcached Server started     #############"
echo

