package model;

public class DataSendRequest {

    private String pipeId;
    private Integer hopsProjectId;
    private String hopsDataset;
    private DataType dataType;
    private String payload;

    public DataSendRequest() {}

    public DataSendRequest(String pipeId, Integer hopsProjectId, String hopsDataset, DataType dataType, String payload) {
        this.pipeId = pipeId;
        this.hopsProjectId = hopsProjectId;
        this.hopsDataset = hopsDataset;
        this.dataType = dataType;
        this.payload = payload;
    }

    public String getPipeId() {
        return pipeId;
    }

    public void setPipeId(String pipeId) {
        this.pipeId = pipeId;
    }

    public Integer getHopsProjectId() {
        return hopsProjectId;
    }

    public void setHopsProjectId(Integer hopsProjectId) {
        this.hopsProjectId = hopsProjectId;
    }

    public String getHopsDataset() {
        return hopsDataset;
    }

    public void setHopsDataset(String hopsDataset) {
        this.hopsDataset = hopsDataset;
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
                ", hopsProjectId=" + hopsProjectId +
                ", hopsDataset='" + hopsDataset + '\'' +
                ", dataType=" + dataType +
                ", payload='" + payload + '\'' +
                '}';
    }
}
