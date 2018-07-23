# jobmanagement

In early stage of development


## Building

Standalone Cluster Manager
```
$ ./compile.sh standalone
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
$ ./bin/standalone --resourcemanager
```

Task Manager
```
$ ./bin/standalone --taskmanager
```

AppManager (Yarn)
```
$ ./bin/appmanager --manager=yarn
```

AppManager (Standalone)
```
$ ./bin/appmanager
```

StateManager
```
$ ./bin/statemanager
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

