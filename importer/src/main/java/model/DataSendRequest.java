package model;

public class DataSendRequest {

    private String pipeId;
    private String hopsFolder;
    private DataType dataType;
    private String payload;

    public DataSendRequest() {}

    public DataSendRequest(String pipeId, String hopsFolder, DataType dataType, String payload) {
        this.pipeId = pipeId;
        this.hopsFolder = hopsFolder;
        this.dataType = dataType;
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

    public DataType getDataType() {
        return dataType;
    }

    public void setDataType(DataType dataType) {
        this.dataType = dataType;
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
                ", dataType=" + dataType +
                ", payload='" + payload + '\'' +
                '}';
    }
}