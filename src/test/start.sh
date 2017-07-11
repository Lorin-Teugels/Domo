#!/bin/bash
. include.sh

function scenario {
	# start server
	$JAVA domotics.DomoticsServer &
	export SERVER=$!
	sleep 2

	echo Starting fridge...
	$JAVA domotics.SmartFridgeClient &
	export FRIDGE1=$!

	sleep 3
	echo Killing server $SERVER...
	kill -s SIGTERM $SERVER

	sleep 2

	# start server
	echo Starting server again...
	$JAVA domotics.DomoticsServer &
	export SERVER=$!
	sleep 12

	echo Stopping
	kill -s SIGTERM $SERVER $FRIDGE1
}

scenario &> test.log

export COND1=`grep "You have won" test.log`
export COND2=`grep "not a server anymore" test.log`
if [[ $COND1 == "" || $COND2 == "" ]] ; then
        exit 1
fi

