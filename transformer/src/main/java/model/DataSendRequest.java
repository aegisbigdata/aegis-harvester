package model;

public class DataSendRequest {

    private String pipeId;
    private Integer hopsProjectId;
    private String hopsDataset;
    private String baseFileName;
    private String csvHeaders;
    private String csvPayload;

    private String hopsUserName;
    private String hopsPassword;

    private String metadata;

    private String targetFileName;


    // entire contents of request are written to file and exported when set to false
    private Boolean aggregate;

    public DataSendRequest() {}

    public DataSendRequest(String pipeId, Integer hopsProjectId, String hopsDataset, String baseFileName, String csvHeaders, String csvPayload, Boolean aggregate, String hopsUserName, String hopsPassword, String metadata, String targetFileName) {
        this.pipeId = pipeId;
        this.hopsProjectId = hopsProjectId;
        this.hopsDataset = hopsDataset;
        this.baseFileName = baseFileName;
        this.csvHeaders = csvHeaders;
        this.csvPayload = csvPayload;
        this.aggregate = aggregate;
        this.hopsUserName = hopsUserName;
        this.hopsPassword = hopsPassword;
        this.metadata = metadata;
        this.targetFileName = targetFileName;
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

    public String getBaseFileName() {
        return baseFileName;
    }

    public void setBaseFileName(String baseFileName) {
        this.baseFileName = baseFileName;
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

    public Boolean getAggregate() {
        return aggregate;
    }

    public void setAggregate(Boolean aggregate) {
        this.aggregate = aggregate;
    }

    public String getHopsUserName() {
        return hopsUserName;
    }

    public void setHopsUserName(String hopsUserName) {
        this.hopsUserName = hopsUserName;
    }

    public String getHopsPassword() {
        return hopsPassword;
    }

    public void setHopsPassword(String hopsPassword) {
        this.hopsPassword = hopsPassword;
    }

    public String getMetadata() {
        return metadata;
    }

    public void setMetadata(String metadata) {
        this.metadata = metadata;
    }

    public String getTargetFileName() {
        return targetFileName;
    }

    public void setTargetFileName(String targetFileName) {
        this.targetFileName = targetFileName;
    }

    @Override
    public String toString() {
        return "DataSendRequest{" +
                "pipeId='" + pipeId + '\'' +
                ", hopsProjectId=" + hopsProjectId +
                ", hopsDataset='" + hopsDataset + '\'' +
                ", baseFileName='" + baseFileName + '\'' +
                ", csvHeaders='" + csvHeaders + '\'' +
                ", csvPayload='" + csvPayload + '\'' +
                ", hopsUserName='" + hopsUserName + '\'' +
                ", hopsPassword='" + hopsPassword + '\'' +
                ", aggregate=" + aggregate + '\'' +
                ", metadata=" + metadata +
                '}';
    }
}
