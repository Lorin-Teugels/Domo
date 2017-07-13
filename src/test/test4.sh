#!/bin/bash
. include.sh

function scenario {
	# start server
	$JAVA domotics.DomoticsServer &
	export SERVER=$!
	sleep 2

	# start user
	echo Starting User 
($JAVA domotics.UserClient<<TERMINATOR
invalid command
eh
WAIT
gc
WAIT
lh
WAIT
eh
TERMINATOR
 )&
	export USER1=$!
	sleep 6


	echo Stopping
	kill -s SIGTERM $SERVER $USER1
}

scenario &> test.log

#export COND1=`grep "You have won" test.log`
#export COND2=`grep "not a server anymore" test.log`
#if [[ $COND1 == "" || $COND2 == "" ]] ; then
#        exit 1
#fi

