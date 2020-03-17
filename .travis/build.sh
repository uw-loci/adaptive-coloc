#!/bin/sh
curl -fsLO https://raw.githubusercontent.com/scijava/scijava-scripts/master/travis-build.sh
sh travis-build.sh $encrypted_bfc1455e5e2a_key $encrypted_bfc1455e5e2a_iv
