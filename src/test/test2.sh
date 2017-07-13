#!/bin/bash
. include.sh

function scenario {
	# start server
	$JAVA domotics.DomoticsServer &
	export SERVER=$!
	sleep 2

	echo Starting fridge1...
	$JAVA domotics.SmartFridgeClient &
	export FRIDGE1=$!
	sleep 3	#required for getting different ip's

	echo Starting fridge2...
	$JAVA domotics.SmartFridgeClient &
	export FRIDGE2=$!
	sleep $SLEEPER

	echo Starting fridge3...
	$JAVA domotics.SmartFridgeClient &
	export FRIDGE3=$!
	sleep 3	#required for getting different ip's


	echo Killing server $SERVER...
	kill -s SIGTERM $SERVER

	sleep $SLEEPER

	# start server
	echo Starting server again...
	$JAVA domotics.DomoticsServer &
	export SERVER=$!
	sleep $SLEEPER

	echo Killing server $SERVER...
	kill -s SIGTERM $SERVER
	sleep $SLEEPER

	echo Killing fridge2 $FRIDGE2...
	kill -s SIGTERM $FRIDGE2
	sleep 10
	
	echo Killing fridge3 $FRIDGE3...
	kill -s SIGTERM $FRIDGE3
	sleep 10

	# start server
	echo Starting server again...
	$JAVA domotics.DomoticsServer &
	export SERVER=$!
	sleep 7

	echo Stopping
	kill -s SIGTERM $FRIDGE1 $SERVER
}

scenario &> test.log

export COND1=`grep "You have won" test.log`
export COND2=`grep "not a server anymore" test.log`
if [[ $COND1 == "" || $COND2 == "" ]] ; then
        exit 1
fi

