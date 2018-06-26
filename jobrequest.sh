#!/bin/bash

MAP_EXPR="map(v, |a:i32| a + i32(5))"
MAP_VEC="1 2 3 4"

FILTER_EXPR="filter(v, |a:i32| a > 2)"
FILTER_VEC="1 2 3 4"


case "$1" in
    "status")
        curl localhost:5050/api/v1/job/status
        ;;
    *)
        echo -e "Adding Weld job with expr $MAP_EXPR and vector $MAP_VEC"
        echo -e "Adding Weld Job with expr $FILTER_EXPR and vector $FILTER_VEC"
        curl -H "Content-Type: application/json" -X POST \
        -d '{"tasks":[{"expr": "'"$MAP_EXPR"'", "vec": "'"$MAP_VEC"'"}, {"expr": "'"$FILTER_EXPR"'", "vec":"'"$FILTER_VEC"'"}]}' localhost:5050/api/v1/job/submit
        ;;
esac


