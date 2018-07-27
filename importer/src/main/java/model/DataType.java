package model;

public enum DataType {

    OWM("message.owm"), CKAN("message.ckan"), CSV("message.csv"), CUSTOM("message.custom");

    private final String eventBusAddress;

    DataType(String eventBusAddress) {
        this.eventBusAddress = eventBusAddress;
    }

    public String getEventBusAddress() {
        return this.eventBusAddress;
    }
}
