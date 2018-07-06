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
    - Clone repository
    - Navigate into the cloned directory
    - Copy the sample configuration: `$ cp conf/config.sample.json conf/config.json`
    - Edit the configuration to your liking:

        |Key|Description|
        |:--- |:---|
        |http.port| The port this service will run on |
        |aegis.*| Configuration keys specifying the Hops endpoint |

3. Start the application

- Vanilla

    ```
    $ mvn clean package && java -jar target/exporter-fat.jar
    ```

- Docker
    1. Start your docker daemon 
    2. Build the application: `mvn clean package`
    3. Adjust the port number (`EXPOSE` in the `Dockerfile`)
    4. Build the image: `docker build -t aegis/exporter .`
    5. Run the image, adjusting the port number as set in step _iii_: `docker run -i -p 8126:8126 aegis/exporter`