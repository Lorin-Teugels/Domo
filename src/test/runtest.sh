#!/bin/bash
# Parameter 1 is script name

for i in `seq 1 10`; do
    ./$1 
    if [[ $? != 0 ]]; then
        echo TEST $i - FAIL
        cat test.log 
	exit 1
    fi 
    echo TEST $i - PASS
done

