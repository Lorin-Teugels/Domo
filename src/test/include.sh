#!/bin/bash
export ROOT=~/dev/eclipseworkspace/Domo
export JAVA="java -cp $ROOT/bin:$ROOT/lib/avro-1.7.7.jar:$ROOT/lib/avro-ipc-1.7.7.jar:$ROOT/lib/slf4j-api-1.7.7.jar:$ROOT/lib/slf4j-simple-1.7.7.jar:$ROOT/lib/jackson-core-asl-1.9.13.jar:$ROOT/lib/jackson-mapper-asl-1.9.13.jar:$ROOT/lib/asg.cliche-110413.jar"
export SLEEPER=6
#killing rogue processes
function Terminator {
ps waux | grep domotics | awk '{print $2}' | xargs kill -s SIGTERM
}
Terminator
Terminator
Terminator

