#!/bin/bash

git clone https://github.com/aegisbigdata/hopsworks-data-connector.git
cd hopsworks-data-connector
mvn -Dmaven.test.skip=true install
mvn install:install-file -Dfile=target/hopsworks-data-connector-1.0-SNAPSHOT.jar -DpomFile=pom.xml

cd ..
git clone git@gitlab.fokus.fraunhofer.de:aegis/weather-harvester-demo.git
