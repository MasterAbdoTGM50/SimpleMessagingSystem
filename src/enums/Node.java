package enums;

public enum Node {
    CONSUMER(52400),
    PRODUCER(45200),
    LEADER(69420),
    PARTITION(32500);

    private final int value;

    Node(int value){
        this.value = value;
    }

    public int getValue(){
        return value;
    }

}
