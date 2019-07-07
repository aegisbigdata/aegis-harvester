package model;

public class WriteRequest {

    private String pipeId;
    private Integer hopsProjectId;
    private String hopsDataset;
    private String baseFileName;
    private String csvHeaders;
    private String csvData;

    private String user;
    private String password;

    private String metadata;

    private String targetFileName;



    // entire contents of request are written to file and exported when set to false
    private Boolean aggregate;

    public WriteRequest() {
    }

    public WriteRequest(String pipeId, Integer hopsProjectId, String hopsDataset, String baseFileName, String csvHeaders, String csvData, Boolean aggregate, String user, String password, String metadata, String targetFileName) {
        this.pipeId = pipeId;
        this.hopsProjectId = hopsProjectId;
        this.hopsDataset = hopsDataset;
        this.baseFileName = baseFileName;
        this.csvHeaders = csvHeaders;
        this.csvData = csvData;
        this.aggregate = aggregate;
        this.user = user;
        this.password = password;
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

    public String getCsvData() {
        return csvData;
    }

    public void setCsvData(String csvData) {
        this.csvData = csvData;
    }

    public Boolean getAggregate() {
        return aggregate;
    }

    public void setAggregate(Boolean aggregate) {
        this.aggregate = aggregate;
    }

    public String getHopsUserName() {
        return user;
    }

    public void setHopsUserName(String user) {
        this.user = user;
    }

    public String getHopsPassword() {
        return password;
    }

    public void setHopsPassword(String password) {
        this.password = password;
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
        return "WriteRequest{" +
                "pipeId='" + pipeId + '\'' +
                ", hopsDataset='" + hopsDataset + '\'' +
                ", hopsProjectId=" + hopsProjectId +
                ", baseFileName='" + baseFileName + '\'' +
                ", csvHeaders='" + csvHeaders + '\'' +
                ", csvData='" + csvData + '\'' +
                ", user='" + user + '\'' +
                ", password='" + password + '\'' +
                ", aggregate=" + aggregate + '\'' +
                ", metadata=" + metadata +
                '}';
    }
}
