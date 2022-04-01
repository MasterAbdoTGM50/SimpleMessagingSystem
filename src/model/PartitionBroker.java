package model;

import java.util.List;

public class PartitionBroker {

    private List<Message> messages;

    public PartitionBroker(List<Message> messages) {
        this.messages = messages;
    }

    public List<Message> getMessages() {
        return messages;
    }

    public void setMessages(List<Message> messages) {
        this.messages = messages;
    }
}
