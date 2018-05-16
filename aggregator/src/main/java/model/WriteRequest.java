package model;

public class WriteRequest {

    private String pipeId;
    private String hopsFolder;
    private String filePath;
    private String csvHeaders;
    private String csvData;

    public WriteRequest() {
    }

    public WriteRequest(String pipeId, String hopsFolder, String filePath, String csvHeaders, String csvData) {
        this.pipeId = pipeId;
        this.hopsFolder = hopsFolder;
        this.filePath = filePath;
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

    public String getFilePath() {
        return filePath;
    }

    public void setFilePath(String filePath) {
        this.filePath = filePath;
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
                ", filePath='" + filePath + '\'' +
                ", csvHeaders='" + csvHeaders + '\'' +
                ", data='" + csvData + '\'' +
                '}';
    }
}
