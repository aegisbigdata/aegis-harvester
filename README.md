# Weather Harvester Demo

A pipeline consisting of four micro services performing the following steps:
1. Fetch data from [OpenWeatherMap](https://openweathermap.org/)
2. Transform JSON to CSV
3. Aggregate results in a file for a specified duration
4. Export the file to [Hops](http://www.hops.io/)

## Prerequisites

* Java JDK 1.8
* Maven

## Setup

Install the following services. Specific instructions can be found in their respective directories.
* Importer
* Transformer
* Aggregator (optional)
* Exporter
