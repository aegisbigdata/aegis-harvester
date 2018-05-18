package model;

public class WriteRequest {

    private String pipeId;
    private String hopsFolder;
    private String location;
    private String csvHeaders;
    private String csvData;

    public WriteRequest() {
    }

    public WriteRequest(String pipeId, String hopsFolder, String location, String csvHeaders, String csvData) {
        this.pipeId = pipeId;
        this.hopsFolder = hopsFolder;
        this.location = location;
        this.csvHeaders = csvHeaders;
        this.csvData = csvData;
    }

    public String getPipeId() {
        return pipeId;
    }

    public void setPipeId(String pipeId) {
        this.pipeId = pipeId;
    }

    public String getHopsFolder() {
        return hopsFolder;
    }

    public void setHopsFolder(String hopsFolder) {
        this.hopsFolder = hopsFolder;
    }

    public String getLocation() {
        return location;
    }

    public void setLocation(String location) {
        this.location = location;
    }

    public String getCsvHeaders() {
        return csvHeaders;
    }

    public void setCsvHeaders(String csvHeaders) {
        this.csvHeaders = csvHeaders;
    }

    public String getCsvData() {
        return csvData;
    }

    public void setCsvData(String csvData) {
        this.csvData = csvData;
    }

    @Override
    public String toString() {
        return "WriteRequest{" +
                "pipeId='" + pipeId + '\'' +
                ", hopsFolder='" + hopsFolder + '\'' +
                ", location='" + location + '\'' +
                ", csvHeaders='" + csvHeaders + '\'' +
                ", csvData='" + csvData + '\'' +
                '}';
    }
}
