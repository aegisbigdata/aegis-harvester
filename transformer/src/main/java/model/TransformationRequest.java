package model;

public class TransformationRequest {

    private String pipeId;
    private DataType dataType;
    private String hopsFolder;
    private String payload;

    public String getPipeId() {
        return pipeId;
    }

    public void setPipeId(String pipeId) {
        this.pipeId = pipeId;
    }

    public DataType getDataType() {
        return dataType;
    }

    public void setDataType(DataType dataType) {
        this.dataType = dataType;
    }

    public String getHopsFolder() {
        return hopsFolder;
    }

    public void setHopsFolder(String hopsFolder) {
        this.hopsFolder = hopsFolder;
    }

    public String getPayload() {
        return payload;
    }

    public void setPayload(String payload) {
        this.payload = payload;
    }
}
