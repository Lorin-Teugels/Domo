#!/bin/bash
. include.sh



function scenario {
	# start server
	$JAVA domotics.DomoticsServer &
	export SERVER=$!
	sleep 2

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
	echo "Sending exit"
	echo exit > /tmp/test.pipe

	sleep 5
	echo Stopping
	kill -s SIGTERM $SERVER $USER1
}

scenario &> test.log

#export COND1=`grep "You have won" test.log`
#export COND2=`grep "not a server anymore" test.log`
#if [[ $COND1 == "" || $COND2 == "" ]] ; then
#        exit 1
#fi

