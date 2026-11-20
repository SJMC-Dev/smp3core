#!/bin/bash

tar -zxpvf output.tar.gz

mv -v luckperms.json.gz mods/luckperms/
cp -af overlay/. ./ && rm -rf overlay/

echo "Remember to execute /lp import luckperms.json.gz --replace"

