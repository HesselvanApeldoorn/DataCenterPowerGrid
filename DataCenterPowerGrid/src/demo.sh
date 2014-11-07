#!/bin/bash

for i in $(seq 1 5); do
	xterm -e java Main &
done
