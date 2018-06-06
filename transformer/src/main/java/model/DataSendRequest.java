package model;

public class DataSendRequest {

    private String pipeId;
    private String hopsFolder;
    private String location;
    private String csvPayload;

    public DataSendRequest() {}

    public DataSendRequest(String pipeId, String hopsFolder, String location, String csvPayload) {
        this.pipeId = pipeId;
        this.hopsFolder = hopsFolder;
        this.location = location;
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
                ", csvPayload='" + csvPayload + '\'' +
                '}';
    }
}
