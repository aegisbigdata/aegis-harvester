package model;

public class UploadRequest {

    private String pipeId;
    private Integer hopsProjectId;
    private String hopsDataset;
    private String filePath;
    private String metadata;
    private String url;
    private String url_metadata;
    private String user;
    private String password;

    public UploadRequest() {
    }

    public UploadRequest(String pipeId, Integer hopsProjectId, String hopsDataset, String filePath, String metadata, String url, String url_metadata, String user, String password) {
        this.pipeId = pipeId;
        this.hopsProjectId = hopsProjectId;
        this.hopsDataset = hopsDataset;
        this.filePath = filePath;
        this.metadata = metadata;
        this.url = url;
        this.url_metadata = url_metadata;
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

    public String getFilePath() {
        return filePath;
    }

    public void setFilePath(String filePath) {
        this.filePath = filePath;
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public String getUrl_metadata() {
        return url_metadata;
    }

    public void setUrl_metadata(String url_metadata) {
        this.url_metadata = url_metadata;
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

    public String getMetadata() {
        return metadata;
    }

    public void setMetadata(String metadata) {
        this.metadata = metadata;
    }

    @Override
    public String toString() {
        return "WriteRequest{" +
                "pipeId='" + pipeId + '\'' +
                ", hopsDataset='" + hopsDataset + '\'' +
                ", hopsProjectId=" + hopsProjectId +
                ", baseFileName='" + filePath + '\'' +
                ", metadata='" + metadata + '\'' +
                ", url='" + url + '\'' +
                ", url_metadata='" + url_metadata + '\'' +
                ", user='" + user + '\'' +
                ", password='" + password +
                '}';
    }
}
