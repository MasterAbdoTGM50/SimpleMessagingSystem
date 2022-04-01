package model;

import java.io.Serializable;

public class Message implements Serializable {

    private String id;
    private String text;
    private String timestamp;

    public String getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(String timestamp) {
        this.timestamp = timestamp;
    }

    public Message(String id, String text, String timestamp, int partition) {
        this.id = id;
        this.text = text;
        this.timestamp = timestamp;
        this.partition = partition;
    }

    private int partition;

    public Message(String text){
        this.text = text;
    }

    public Message(String id, String text, int partition) {
        this.id = id;
        this.text = text;
        this.partition = partition;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getText() {
        return text;
    }

    public void setText(String text) {
        this.text = text;
    }

    public int getPartition() {
        return partition;
    }

    public void setPartition(int partition) {
        this.partition = partition;
    }
}
