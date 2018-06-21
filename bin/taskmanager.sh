#!/bin/bash
git config --global alias.root 'rev-parse --show-toplevel'
REPO_ROOT=$(git root)

[ -f "$REPO_ROOT"/build/taskmanager.jar ] || { echo "Jar file not found, run compile.sh!" ; exit 1; }

if [ -z "$1" ]
then
    java -jar $REPO_ROOT/build/taskmanager.jar
else
    java -jar -Dakka.remote.netty.tcp.port=$1 $REPO_ROOT/build/taskmanager.jar
fi


