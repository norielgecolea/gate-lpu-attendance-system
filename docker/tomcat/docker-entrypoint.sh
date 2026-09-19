#!/bin/sh
set -e

WAR_SRC="${WAR_SRC:-/opt/deploy/attendance-system.war}"
WAR_DEST="/usr/local/tomcat/webapps/attendance-system.war"

if [ ! -f "$WAR_SRC" ]; then
  echo "ERROR: WAR is missing or not a file at $WAR_SRC"
  echo "Build and copy:"
  echo "  mvn -f lpu-attendance-system clean package"
  echo "  cp lpu-attendance-system/target/attendance-system.war data/tomcat/webapps/"
  ls -la "$(dirname "$WAR_SRC")" || true
  exit 1
fi

# Each replica needs its own exploded webapps tree — never share it across JVMs.
rm -rf /usr/local/tomcat/webapps/*
cp "$WAR_SRC" "$WAR_DEST"

exec catalina.sh run
