#!/bin/sh

# enter script directory
script_directory=$(cd $(dirname "${BASH_SOURCE[0]}") && pwd)
cd $script_directory

java -server -Xmx256m -Xms256m -Xmn128m -cp ".:./nettyProxyServer.jar:./lib/*:" cn.lihua.MainServerStart &
