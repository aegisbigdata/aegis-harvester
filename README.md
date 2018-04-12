## Prerequisites

* Java JDK 1.8
* Maven

## Setup

* Install dependency Hopsworks Data Connector
```
$ git clone https://github.com/aegisbigdata/hopsworks-data-connector.git
$ cd hopsworks-data-connector
$ mvn install
$ mvn install:install-file -Dfile=target/hopsworks-data-connector-1.0-SNAPSHOT.jar -DpomFile=pom.xml 
```

* Clone repository
* Navigate into the cloned directory
* Create configuration
```
$ cp conf/config.sample.json conf/config.json
```
* Edit config.conf
    * Set `opwApiKey` to our OpenWeatherMap API-Key
    * Set `tempFilePath` to a directory with read-write access
    * Set `ckan` to your CKAN installation
* Start the application
```
$ mvn package exec:java
```
* Browse to http://localhost:8080