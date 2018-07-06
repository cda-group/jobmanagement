# jobmanagement

In early stage of development


## Building

ResourceManager
```
$ ./compile.sh resourcemanager
```

TaskManager
```
$ ./compile.sh taskmanager
```

AppManager
```
$ ./compile.sh appmanager
```

StateManager
```
$ ./compile.sh statemanager
```

Complete Runtime
```
$ ./compile.sh
```

## Running

Resource Manager
```
$ ./bin/resourcemanager.sh
```

Task Manager
```
$ ./bin/taskmanager.sh
```

AppManager
```
$ ./bin/appmanager.sh
```

StateManager
```
$ ./bin/statemanager.sh
```
## Testing


General
```
$ sbt test
```

Cluster Multi-JVM tests
```
$ sbt multi-jvm:test
```

# License

TBD

