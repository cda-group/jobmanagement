#!/bin/bash

MAP_EXPR="map(v, |a:i32| a + i32(5))"
MAP_VEC="1 2 3 4"

FILTER_EXPR="filter(v, |a:i32| a > 2)"
FILTER_VEC="1 2 3 4"

REST_API="localhost:5050"


case "$1" in
    "status")
        curl "$REST_API"/api/v1/jobs/status
        ;;
    "jobmetric")
        curl "$REST_API"/api/v1/jobs/metrics/"$2"
        ;;
    *)
        echo -e "Adding Weld job with expr $MAP_EXPR and vector $MAP_VEC"
        echo -e "Adding Weld Job with expr $FILTER_EXPR and vector $FILTER_VEC"
        curl -H "Content-Type: application/json" -X POST \
        -d '{"priority": 1, "locality" : false, "tasks":[{"name": "mapper", "cores" : 1, "memory": 1024, "expr": "'"$MAP_EXPR"'", "vec": "'"$MAP_VEC"'"}, 
        {"name": "filter", "cores": 1, "memory": 1024, "expr": "'"$FILTER_EXPR"'", "vec":"'"$FILTER_VEC"'"}]}' "$REST_API"/api/v1/jobs/deploy
        ;;
esac


