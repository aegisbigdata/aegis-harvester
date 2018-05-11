package model;

public class WriteRequest {

    private String pipeId;
    private String filePath;
    private String data;

    public WriteRequest() {}

    public WriteRequest(String pipeId, String filePath, String data) {
        this.pipeId = pipeId;
        this.filePath = filePath;
        this.data = data;
    }

    public String getPipeId() {
        return pipeId;
    }

    public void setPipeId(String pipeId) {
        this.pipeId = pipeId;
    }

    public String getFilePath() {
        return filePath;
    }

    public void setFilePath(String filePath) {
        this.filePath = filePath;
    }

    public String getData() {
        return data;
    }

    public void setData(String data) {
        this.data = data;
    }

    @Override
    public String toString() {
        return "WriteRequest{" +
                "pipeId='" + pipeId + '\'' +
                ", filePath='" + filePath + '\'' +
                ", data='" + data + '\'' +
                '}';
    }
}
