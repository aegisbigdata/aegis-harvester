# Exporter

Uploads files to Hopsworks.

## Setup

1. Install dependency Hopsworks Data Connector

    ```
    $ git clone https://github.com/aegisbigdata/hopsworks-data-connector.git
    $ cd hopsworks-data-connector
    $ mvn install
    $ mvn install:install-file -Dfile=target/hopsworks-data-connector-1.0-SNAPSHOT.jar -DpomFile=pom.xml 
    ```

2. Install exporter
    * Clone repository
    * Navigate into the cloned directory
    * Copy the sample configuration

        ```
        $ cp conf/config.sample.json conf/config.json
        ```

    * Edit the configuration to your liking:

        |Key|Description|
        |:--- |:---|
        |http.port| The port this service will run on |
        |aegis.*| Configuration keys specifying the Hops endpoint |

3. Start the application

    ```
    $ mvn package && java -jar target/exporter-fat.jar
    ```