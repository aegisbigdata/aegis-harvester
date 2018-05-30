package model;

public class DataSendRequest {

    private String pipeId;
    private String hopsFolder;
    private String url;
    private String payload;

    public DataSendRequest() {}

    public DataSendRequest(String pipeId, String hopsFolder, String url, String payload) {
        this.pipeId = pipeId;
        this.hopsFolder = hopsFolder;
        this.url = url;
        this.payload = payload;
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

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public String getPayload() {
        return payload;
    }

    public void setPayload(String payload) {
        this.payload = payload;
    }

    @Override
    public String toString() {
        return "DataSendRequest{" +
                "pipeId='" + pipeId + '\'' +
                ", hopsFolder='" + hopsFolder + '\'' +
                ", url='" + url + '\'' +
                ", payload='" + payload + '\'' +
                '}';
    }
}
