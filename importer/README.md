# Importer

Prepares weather data for further processing by either fetching data periodically from the [OpenWeatherMap API](https://openweathermap.org/api)
or providing a file upload for CSV data.

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

### OpenWeatherMap

To trigger the fetching of data a `POST` request must be sent to

    {url}/owm
    
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

### Push CSV 

In order to push weather data from another source a `POST` request must be sent to

    {url}/custom

containing multipart/form-data with key-value pairs containing the data shown in the table below. All values are mandatory.

|Key|Description|
|:--- |:---|
|pipeId| Unique value identifying the job |
|hopsFolder| The hopsworks folder the resulting file will be uploaded to |
|upload| Path to a local file |
|mapping| Instructions for transforming csv data. See below for a more thorough explanation |

##### Transform CSV

The supplied CSV file can be transformed in various ways.    
Each transformation needs to be specified in a mapping script. 
The transformations will be executed in the order they are listed.
A sample JSON structure for each transformation type is shown below. 
Numbers indicate the indices of the columns to be transformed.
All transformations must be contained within the root node `mapping`.

1. Rename headers
        
        {
            "renameHeaders" : [
                {   
                    "old": "oldHeader",
                    "new": "newHeader
                }
            ]   
        }
    
2. Merge columns
    
        {
            "mergeColumns" : [
                {
                    "header" : "newHeader"
                    "columns" : [ 1, 2, 3 ],
                }
                [ 4, 5, 6 ]               
            ]
        }
        
3. Convert timestamps
        
        {
            "convertTimestamps" : [ 1, 5, 8 ]   
        }
        

##### Sample call

An example using curl is shown below:

    curl -X POST {url}/custom -F pipeId=csvPipe -F hopsFolder=myFolder -F mapping='{"renameHeaders":[{"old":"trip_id","new":"tripId"}],"mergeColumns":[[2, 3]],"convertTimeStamp":[5]}' -F upload=@/path/to/file.csv

### Status

A list of currently running pipes can be obtained by sending a `GET` request to 

    {url}/running