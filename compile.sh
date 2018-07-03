#!/bin/bash

RED=$(tput setaf 1)
GREEN=$(tput setaf 2)
CYAN=$(tput setaf 6)
BLUE=$(tput setaf 4)
WHITE=$(tput setaf 7)
RESET=$(tput sgr0)

SCALA_VER="2.12"


mkdir -p build

function cpy_jars {
    case "$1" in
    "rm")
        cp runtime/target/scala-"$SCALA_VER"/resourcemanager.jar build/resourcemanager.jar
        ;;
    "tm")
        cp runtime/target/scala-"$SCALA_VER"/taskmanager.jar build/taskmanager.jar
        ;;
    "sm")
        cp runtime/target/scala-"$SCALA_VER"/statemanager.jar build/statemanager.jar
        ;;
    "am")
        cp runtime/target/scala-"$SCALA_VER"/appmanager.jar build/appmanager.jar
        ;;
    "all")
        cp runtime/target/scala-"$SCALA_VER"/appmanager.jar build/appmanager.jar
        cp runtime/target/scala-"$SCALA_VER"/resourcemanager.jar build/resourcemanager.jar
        cp runtime/target/scala-"$SCALA_VER"/statemanager.jar build/statemanager.jar
        cp runtime/target/scala-"$SCALA_VER"/taskmanager.jar build/taskmanager.jar
        ;;
esac
}


case "$1" in
    "am"|"appmanager")
        echo $GREEN"Compiling AppManager..."
        sbt_fail=0
        (sbt -DruntimeClass=runtime.appmanager.AmSystem -DruntimeJar=appmanager.jar runtime/assembly) || sbt_fail=1
        if [[ $sbt_fail -ne 0 ]];
        then
            echo $RED"Failed to compile AppManager"
            exit 1
        else
            echo $GREEN"appmanager.jar is being moved to build/"
            cpy_jars "am"
        fi
        ;;
    "taskmanager"|"tm")
        echo $CYAN"Compiling TaskManager..."
        sbt_fail=0
        (sbt -DruntimeClass=runtime.taskmanager.TaskManagerSystem -DruntimeJar=taskmanager.jar runtime/assembly) || sbt_fail=1
        if [[ $sbt_fail -ne 0 ]];
        then
            echo $RED"Failed to compile TaskManager"
            exit 1
        else
            echo $CYAN"taskmanager.jar is being moved to build/"
            cpy_jars "tm"
        fi
        ;;
    "resourcemanager"|"rm")
        echo $BLUE"Compiling ResourceManager..."
        sbt_fail=0
        (sbt -DruntimeClass=runtime.resourcemanager.RmSystem -DruntimeJar=resourcemanager.jar runtime/assembly) || sbt_fail=1
        if [[ $sbt_fail -ne 0 ]];
        then
            echo $RED"Failed to compile ResourecManager"
            exit 1
        else
            echo $BLUE"resourcemanager.jar is being moved to build/"
            cpy_jars "rm"
        fi
        ;;  
    "statemanager"|"sm")
        echo $BLUE"Compiling StateManager..."
        sbt_fail=0
        (sbt -DruntimeClass=runtime.statemanager.SmSystem -DruntimeJar=statemanager.jar runtime/assembly) || sbt_fail=1
        if [[ $sbt_fail -ne 0 ]];
        then
            echo $RED"Failed to compile StateManager"
            exit 1
        else
            echo $BLUE"statemanager.jar is being moved to build/"
            cpy_jars "sm"
        fi
        ;;  
    *)
        echo $WHITE"Compiling everything..."
        sbt_fail=0
        (sbt -DruntimeClass=runtime.resourcemanager.RmSystem -DruntimeJar=resourcemanager.jar runtime/assembly);
        (sbt -DruntimeClass=runtime.taskmanager.TaskManagerSystem -DruntimeJar=taskmanager.jar runtime/assembly);
        (sbt -DruntimeClass=runtime.statemanager.SmSystem -DruntimeJar=statemanager.jar runtime/assembly);
        (sbt -DruntimeClass=runtime.appmanager.AmSystem -DruntimeJar=appmanager.jar runtime/assembly) || sbt_fail=1
        if [[ $sbt_fail -ne 0 ]];
        then
            echo $RED"Failed to compile"
            exit 1
        else
            echo $WHITE"Moving appmanager, taskmanager, statemanager and resourcemanager jars to build/"
            cpy_jars "all"
        fi
        ;;
esac


