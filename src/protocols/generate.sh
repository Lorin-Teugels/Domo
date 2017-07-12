#!/bin/bash
export TOOLS=../../lib/avro-tools-1.7.7.jar
java -jar $TOOLS compile protocol avpr/DomServer.avpr 		../
java -jar $TOOLS compile protocol avpr/SmartFridge.avpr 	../
java -jar $TOOLS compile protocol avpr/User.avpr 		../
java -jar $TOOLS compile protocol avpr/Electable.avpr		../
java -jar $TOOLS compile protocol avpr/Thermostat.avpr 		../
java -jar $TOOLS compile protocol avpr/Light.avpr 		../

