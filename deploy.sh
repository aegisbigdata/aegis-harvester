#!/bin/bash

cd importer && mvn clean package
cd ../transformer && mvn clean package
cd ../aggregator && mvn clean package
cd ../exporter && mvn clean package

sudo docker-compose build --no-cache && sudo docker-compose up