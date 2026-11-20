#!/bin/bash

SCRIPT_DIR=$(dirname $(readlink -f $0))

tar -zcpvf data_snapshot.tar.gz smp3-rewards.json.gz config/ world/dimensions/minecraft/overworld/data/smp3/sign-mod.dat world/players/

