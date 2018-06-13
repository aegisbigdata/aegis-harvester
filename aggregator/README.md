# Aggregator

Aggregates CSV data in a file for a specified amount of time 

## Setup

1. Install aggregator
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
        |target.host| The host of the service the aggregator will push it's data to |
        |target.port| The port of the service the aggregator will push it's data to |
        |target.endpoint| The relative URI of the service the aggregator will push it's data to |
        |fileDir| The directory in which the aggregation files will be stored |
        |frequencyInMinutes| Amount of time for which data is to be aggregated, in minutes |
 
2. Start the application

    ```
    $ mvn package && java -jar target/aggregator-fat.jar
    ```