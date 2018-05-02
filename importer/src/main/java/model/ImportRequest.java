package model;

public class ImportRequest {

    private String pipeId;
    private String type;
    private String value;

    private Integer durationInHours;
    private Integer frequencyInMinutes;

    public String getPipeId() {
        return pipeId;
    }

    public void setPipeId(String pipeId) {
        this.pipeId = pipeId;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getValue() {
        return value;
    }

    public void setValue(String value) {
        this.value = value;
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
        return "ImportRequest{" +
                "pipeId='" + pipeId + '\'' +
                ", type='" + type + '\'' +
                ", value='" + value + '\'' +
                ", durationInHours=" + durationInHours +
                ", frequencyInMinutes=" + frequencyInMinutes +
                '}';
    }
}
