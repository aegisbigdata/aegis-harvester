package model;

public class CkanFetchRequest {

    private String pipeId;
    private String hopsFolder;
    private String url;

    private Integer durationInHours;
    private Integer frequencyInMinutes;

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

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
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
                ", hopsFolder='" + hopsFolder + '\'' +
                ", url='" + url + '\'' +
                ", durationInHours=" + durationInHours +
                ", frequencyInMinutes=" + frequencyInMinutes +
                '}';
    }
}
