# Importer

Periodically Fetches data from the [OpenWeatherMap API](https://openweathermap.org/api).

## Setup

1. Install importer
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
    |target.host| The host of the service the importer will push it's data to |
    |target.port| The port of the service the importer will push it's data to |
    |target.endpoint| The relative URI of the service the importer will push it's data to |
    |owmApiKey| Your OpenWeatherMap API key |

2. Start the application

    ```
    $ mvn package && java -jar target/importer-fat.jar
    ```

## API

To trigger the fetching of data a `POST` request must be sent to

    {url}/weather
    
containing a JSON body with the data shown in the table below. All values are mandatory.

|Key|Description|
|:--- |:---|
|pipeId| Unique value identifying the job |
|hopsFolder| The hopsworks folder the resulting file will be uploaded to |
|type| Either `bbox` or `location` |
|value| Either the bbox coordinates or a location ID |
|durationInHours| How long the pipeline should run for, in hours. A value < 1 will make the pipeline run exactly once. |
|frequencyInMinutes| The interval at which data should be fetched, in minutes. A value < 1 will make the pipeline not run. |

A frequency higher than the duration (for durations > 0) is not allowed.

A list of currently running pipes can be obtained by sending a `GET` request to 

    {url}/running