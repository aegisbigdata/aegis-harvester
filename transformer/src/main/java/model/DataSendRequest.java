package model;

public class DataSendRequest {

    private String pipeId;
    private String hopsFolder;
    private String location;
    private String csvHeaders;
    private String csvPayload;

    public DataSendRequest() {}

    public DataSendRequest(String pipeId, String hopsFolder, String location, String csvHeaders, String csvPayload) {
        this.pipeId = pipeId;
        this.hopsFolder = hopsFolder;
        this.location = location;
        this.csvHeaders = csvHeaders;
        this.csvPayload = csvPayload;
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

    public String getCsvPayload() {
        return csvPayload;
    }

    public void setCsvPayload(String csvPayload) {
        this.csvPayload = csvPayload;
    }

    @Override
    public String toString() {
        return "DataSendRequest{" +
                "pipeId='" + pipeId + '\'' +
                ", hopsFolder='" + hopsFolder + '\'' +
                ", location='" + location + '\'' +
                ", csvHeaders='" + csvHeaders + '\'' +
                ", csvPayload='" + csvPayload + '\'' +
                '}';
    }
}
