#!/bin/bash
. include.sh



function scenario {

	# start server
	$JAVA domotics.DomoticsServer &
	export SERVER=$!
	sleep 2

	#start fridge
	echo "starting fridge"
	$JAVA domotics.SmartFridgeClient &
	export FRIDGE1=$!
	sleep 4

	#start fridge
	echo "starting fridge2"
	$JAVA domotics.SmartFridgeClient &
	export FRIDGE2=$!
	sleep 4

	# start user
	echo "Starting User"
	rm -f /tmp/test.pipe
	mkfifo /tmp/test.pipe 
	tail -f /tmp/test.pipe | $JAVA domotics.UserClient &
	export USER1=$!

	sleep 2
	echo "Sending wait"
	echo WAIT > /tmp/test.pipe

	sleep 2
	echo "Sending eh"
	echo eh > /tmp/test.pipe

	sleep 2
	echo "Sending gc"
	echo gc > /tmp/test.pipe

	sleep 2
	echo "open fridge 1"
	echo of 6790 > /tmp/test.pipe

	sleep 2
	echo "aitf"
	echo aitf cheese > /tmp/test.pipe

	sleep 2
	echo "close fridge"
	echo cf > /tmp/test.pipe

	sleep 2
	echo "sending gc"
	echo gc > /tmp/test.pipe

	sleep 5
	echo Stopping
	Terminator
	
	
}

scenario &> test.log

export COND1=`grep "\[cheese\]" test.log`
if [[ "$COND1" == "" ]] ; then
        exit 1
fi

