#!/bin/bash

RED=$(tput setaf 1)
GREEN=$(tput setaf 2)
CYAN=$(tput setaf 6)
BLUE=$(tput setaf 4)
WHITE=$(tput setaf 7)
RESET=$(tput sgr0)


mkdir -p build

function cpy_jars {
    case "$1" in
    "rm")
        cp runtime/rmtarget/scala-2.12/resourcemanager.jar build/resourcemanager.jar
        ;;
    "tm")
        cp runtime/tmtarget/scala-2.12/taskmanager.jar build/taskmanager.jar
        ;;
    "driver")
        cp runtime/drivertarget/scala-2.12/driver.jar build/driver.jar
        ;;
    "all")
        cp runtime/drivertarget/scala-2.12/driver.jar build/driver.jar
        cp runtime/rmtarget/scala-2.12/resourcemanager.jar build/resourcemanager.jar
        cp runtime/tmtarget/scala-2.12/taskmanager.jar build/taskmanager.jar
        ;;
esac
}


case "$1" in
    "driver")
        echo $GREEN"Compiling Driver..."
        sbt_fail=0
        (sbt driver/assembly) || sbt_fail=1
        if [[ $sbt_fail -ne 0 ]];
        then
            echo $RED"Failed to compile driver"
            exit 1
        else
            echo $GREEN"driver.jar is being moved to build/"
            cpy_jars "driver"
        fi
        ;;
    "taskmanager"|"tm")
        echo $CYAN"Compiling TaskManager..."
        sbt_fail=0
        (sbt taskmanager/assembly) || sbt_fail=1
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
        (sbt resourcemanager/assembly) || sbt_fail=1
        if [[ $sbt_fail -ne 0 ]];
        then
            echo $RED"Failed to compile ResourecManager"
            exit 1
        else
            echo $BLUE"resourcemanager.jar is being moved to build/"
            cpy_jars "rm"
        fi
        ;;  
    *)
        echo $WHITE"Compiling everything..."
        sbt_fail=0
        (sbt clean assembly) || sbt_fail=1
        if [[ $sbt_fail -ne 0 ]];
        then
            echo $RED"Failed to compile"
            exit 1
        else
            echo $WHITE"Moving driver, taskmanager and resourcemanager jars to build/"
            cpy_jars "all"
        fi
        ;;
esac


