#!/bin/bash

# TaskManager
mkdir -p /sys/fs/cgroup/cpu/taskmanager
mkdir -p /sys/fs/cgroup/memory/taskmanager

# Containers
mkdir -p /sys/fs/cgroup/cpu/containers
mkdir -p /sys/fs/cgroup/memory/containers


# Set User rights to the directories

chown -R ${USER}: /sys/fs/cgroup/cpu/taskmanager
chown -R ${USER}: /sys/fs/cgroup/cpu/containers

chown -R ${USER}: /sys/fs/cgroup/memory/taskmanager
chown -R ${USER}: /sys/fs/cgroup/memory/containers
