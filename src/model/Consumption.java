package model;

public class Consumption{

    private String consumptionId;
    private String lastSentMessageId;

    public String getLastSentMessageId() {
        return lastSentMessageId;
    }

    public void setLastSentMessageId(String lastSentMessageId) {
        this.lastSentMessageId = lastSentMessageId;
    }

    public String getConsumptionId() {
        return consumptionId;
    }

    public void setConsumptionId(String consumptionId) {
        this.consumptionId = consumptionId;
    }
}
