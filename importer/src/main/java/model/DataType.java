package model;

public enum DataType {

    OWM("message.owm"), CKAN("message.ckan"), CSV("message.csv"), EVENT("message.event");

    private final String eventBusAddress;

    DataType(String eventBusAddress) {
        this.eventBusAddress = eventBusAddress;
    }

    public String getEventBusAddress() {
        return this.eventBusAddress;
    }
}
