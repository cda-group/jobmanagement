#!/bin/bash

# TaskManager
sudo mkdir -p /sys/fs/cgroup/cpu/taskmanager
sudo mkdir -p /sys/fs/cgroup/memory/taskmanager

# Containers
sudo mkdir -p /sys/fs/cgroup/cpu/containers
sudo mkdir -p /sys/fs/cgroup/memory/containers


# Set User rights to the directories

sudo chown -R ${USER}: /sys/fs/cgroup/cpu/taskmanager
sudo chown -R ${USER}: /sys/fs/cgroup/cpu/containers

sudo chown -R ${USER}: /sys/fs/cgroup/memory/taskmanager
sudo chown -R ${USER}: /sys/fs/cgroup/memory/containers
