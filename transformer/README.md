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

    ```
    $ mvn package && java -jar target/transformer-fat.jar
    ```