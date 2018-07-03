#!/bin/bash
git config --global alias.root 'rev-parse --show-toplevel'
REPO_ROOT=$(git root)

[ -f "$REPO_ROOT"/build/statemanager.jar ] || { echo "Jar file not found, run compile.sh!" ; exit 1; }

java -jar $REPO_ROOT/build/statemanager.jar


