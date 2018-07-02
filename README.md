# jobmanagement

In early stage of development


## Building

ResourceManager
```
$ ./compile.sh rm
```

TaskManager
```
$ ./compile.sh tm
```

Driver
```
$ ./compile.sh driver
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

Driver
```
$ ./bin/driver.sh
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

