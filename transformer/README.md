# Transformer

Transforms OpenWeatherMap JSON data into CSV format

## Setup

1. Install transformer
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
        |target.host| The host of the service the transformer will push it's data to |
        |target.port| The port of the service the transformer will push it's data to |
        |target.endpoint| The relative URI of the service the transformer will push it's data to |
 

3. Start the application

* Vanilla

    ```
    $ mvn clean package && java -jar target/transformer-fat.jar
    ```

* Docker

    1. Start your docker daemon 
    2. Build the application: `mvn clean package`
    3. Adjust the port number (`EXPOSE` in the `Dockerfile`)
    4. Build the image: `docker build -t aegis/transformer .`
    5. Run the image, adjusting the port number as set in step _iii_: `docker run -i -p 8124:8124 aegis/transformer`