#!/bin/bash

## https://hadoop.apache.org/docs/stable/hadoop-project-dist/hadoop-common/SingleCluster.html#Execution

HADOOP_VER="hadoop-3.1.0"

wget_fail=0
wget http://apache.mirrors.spacedump.net/hadoop/common/$HADOOP_VER/$HADOOP_VER.tar.gz || wget_fail=1

if [[ $sbt_fail -ne 0 ]];
then
	echo "Failed to download Hadoop using wget"
	exit 1
else
	echo "Unpacking $HADOOP_VER.tar.gz"
	tar -xvzf $HADOOP_VER.tar.gz
    echo "See the instructions at https://hadoop.apache.org/docs/stable/hadoop-project-dist/hadoop-common/SingleCluster.html#Execution"
fi


