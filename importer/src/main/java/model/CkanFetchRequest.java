package model;

public class CkanFetchRequest {

    private String pipeId;
    private Integer hopsProjectId;
    private String hopsDataset;
    private CkanFetchType fetchType;
    private String url;
    private String resourceId;

    private Integer durationInHours;
    private Integer frequencyInMinutes;

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

    public CkanFetchType getFetchType() {
        return fetchType;
    }

    public void setFetchType(CkanFetchType fetchType) {
        this.fetchType = fetchType;
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public String getResourceId() {
        return resourceId;
    }

    public void setResourceId(String resourceId) {
        this.resourceId = resourceId;
    }

    public Integer getDurationInHours() {
        return durationInHours;
    }

    public void setDurationInHours(Integer durationInHours) {
        this.durationInHours = durationInHours;
    }

    public Integer getFrequencyInMinutes() {
        return frequencyInMinutes;
    }

    public void setFrequencyInMinutes(Integer frequencyInMinutes) {
        this.frequencyInMinutes = frequencyInMinutes;
    }

    @Override
    public String toString() {
        return "CkanFetchRequest{" +
                "pipeId='" + pipeId + '\'' +
                ", hopsProjectId=" + hopsProjectId +
                ", hopsDataset='" + hopsDataset + '\'' +
                ", fetchType=" + fetchType +
                ", url='" + url + '\'' +
                ", resourceId='" + resourceId + '\'' +
                ", durationInHours=" + durationInHours +
                ", frequencyInMinutes=" + frequencyInMinutes +
                '}';
    }
}
