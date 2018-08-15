package model;

public class DataSendRequest {

    private String pipeId;
    private Integer hopsProjectId;
    private String hopsDataset;
    private DataType dataType;
    private String baseFileName;
    private String payload;
    private String user;
    private String password;

    public DataSendRequest() {}

    public DataSendRequest(String pipeId, Integer hopsProjectId, String hopsDataset, DataType dataType, String baseFileName, String payload, String user, String password) {
        this.pipeId = pipeId;
        this.hopsProjectId = hopsProjectId;
        this.hopsDataset = hopsDataset;
        this.dataType = dataType;
        this.baseFileName = baseFileName;
        this.payload = payload;
        this.user = user;
        this.password = password;
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

    public String getBaseFileName() {
        return baseFileName;
    }

    public void setBaseFileName(String baseFileName) {
        this.baseFileName = baseFileName;
    }

    public String getPayload() {
        return payload;
    }

    public void setPayload(String payload) {
        this.payload = payload;
    }

    public String getUser() {
        return user;
    }

    public void setUser(String user) {
        this.user = user;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    @Override
    public String toString() {
        return "DataSendRequest{" +
                "pipeId='" + pipeId + '\'' +
                ", hopsProjectId=" + hopsProjectId +
                ", hopsDataset='" + hopsDataset + '\'' +
                ", dataType=" + dataType +
                ", baseFileName='" + baseFileName + '\'' +
                ", payload='" + payload + '\'' +
                ", user='" + user + '\'' +
                ", password='" + password + '\'' +
                '}';
    }
}
