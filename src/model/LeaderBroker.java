package model;


import com.google.gson.Gson;

import javax.swing.*;
import java.awt.*;
import java.io.*;
import java.time.LocalDateTime;
import java.util.*;
import java.util.List;

public class LeaderBroker {

    private PrintWriter out;
    private List<PartitionBroker> partitionBrokers;
    private int currentPartitionBrokerTurn;

    public LeaderBroker(){
        currentPartitionBrokerTurn = 0;
        partitionBrokers = new ArrayList<>();
        partitionBrokers.add(new PartitionBroker());
        partitionBrokers.add(new PartitionBroker());
        partitionBrokers.add(new PartitionBroker());
        partitionBrokers.add(new PartitionBroker());
    }

    public LeaderBroker(List<PartitionBroker> partitionBrokers) {
        this.partitionBrokers = partitionBrokers;
    }

    public void addPartitionBroker(PartitionBroker singlePartitionBroker){
        partitionBrokers.add(singlePartitionBroker);
    }

    public void configureMessage(String text){
        System.out.println("MESSAGE_TEXT: " + text);
        String messageTimestamp = LocalDateTime.now().toString();
        synchronized (this){
            String messageUUID = UUID.randomUUID().toString();
            String id = messageTimestamp + '_' + messageUUID + '_' + currentPartitionBrokerTurn;
            Message message = new Message(id, text, currentPartitionBrokerTurn);
            partitionBrokers
                    .get(currentPartitionBrokerTurn)
                    .getMessages()
                    .add(message);
            currentPartitionBrokerTurn = (currentPartitionBrokerTurn + 1) % partitionBrokers.size();
            System.out.println("MESSAGE_ID: " + id);
        }
    }

    public boolean findConsumptionIdFile(Consumption consumption){
        File consumptionFile = new File(consumption.getConsumptionId() + ".txt");
        return consumptionFile.exists();
    }

//    public void serveConsumerConsumption(Consumption consumption) throws IOException {
    public void serveConsumerConsumption(Consumption consumption, ObjectOutputStream objectOutputStream) throws IOException {
        if (consumption.getConsumptionId().isBlank()){
//            sendAllMessages();
            sendAllMessages(objectOutputStream);
            return;
        }

        if (!findConsumptionIdFile(consumption)){
            throw new FileNotFoundException();
        }

        File consumptionFile = new File(consumption.getConsumptionId() + ".txt");
        Scanner scanner = new Scanner(consumptionFile);
        StringBuilder lastSeenMessageId = new StringBuilder(
                scanner.skip("lastSeenMessageId: ").nextLine());
        System.out.println("FILE: " + lastSeenMessageId);

        int lastSeenMessagePartition =
                Integer.parseInt(lastSeenMessageId.substring(lastSeenMessageId.lastIndexOf("_") + 1 ));
        String lastSeenMessageTimestamp = lastSeenMessageId.substring(0, lastSeenMessageId.indexOf("_"));

        List messages = partitionBrokers
                .get(lastSeenMessagePartition)
                .getMessages();
//        int indexOfMessageInPartition = Collections.binarySearch(messages, lastSeenMessageId);
        Comparator<Message> c = new Comparator<Message>() {
            int counter = 0;
            @Override
            public int compare(Message o1, Message o2) {
                System.out.println(o1.getId());
                System.out.println(o2.getId());
                System.out.println("counter: " + (counter++));
                if (o1.getId().equals(o2.getId())){
                    return 0;
                }else if (isAfterTimestamps(o1, lastSeenMessageTimestamp)){
                    return 1;
                }else {
                    return -1;
                }
            }
        };

        int indexOfMessageInPartition = Collections.binarySearch(messages, new Message(lastSeenMessageId.toString(), "", lastSeenMessageTimestamp, lastSeenMessagePartition), c);
        System.out.println("INDEX: " + indexOfMessageInPartition);
        List<Message> messagesToBeSent = new ArrayList<>();

        System.out.println("Partition ID: " + lastSeenMessagePartition);

        for (int i = 0; i < partitionBrokers.size(); i++){
            int startingIndex = (lastSeenMessagePartition == i) ? indexOfMessageInPartition + 1 : 0;
            System.out.println("starting index: " + startingIndex);
            System.out.println("i: " + i + "Size: " + partitionBrokers.get(i).getMessages().size());
            for (int j = startingIndex; j < partitionBrokers.get(i).getMessages().size(); j++){
                System.out.println("J: " + j);
                Message msg = partitionBrokers.get(i).getMessages().get(j);
                System.out.println("messageLoop: " + msg.getText());
                if (!isAfterTimestamps(msg, lastSeenMessageTimestamp)){continue;}
                messagesToBeSent.add(msg);
                System.out.println("added: " + msg.getText());
            }
        }

        Gson gson = new Gson();

        if (messagesToBeSent.isEmpty()){
//            out.print(gson.toJson(consumption));
            objectOutputStream.writeUTF(gson.toJson(consumption));
            scanner.close();
            return;
        }

        String consumptionId = UUID.randomUUID().toString();

        Map<String, Object> map = new HashMap<>();
        map.put("lastSeenMessageId", messagesToBeSent.get(messagesToBeSent.size() - 1).getId());

        File consumptionfile = new File(consumptionId + ".txt");
        consumptionfile.createNewFile();

        FileWriter fileWriter = new FileWriter(consumptionfile);
        fileWriter.write("lastSeenMessageId: " + messagesToBeSent.get(messagesToBeSent.size() - 1).getId());
        fileWriter.close();

        map.put("messages", messagesToBeSent);
        map.put("consumptionId", consumptionId);
        String serialized = gson.toJson(map);
//        out.print(serialized);
        System.out.println("newSerialized: " + serialized);
        objectOutputStream.writeUTF(serialized);
        objectOutputStream.flush();
        System.out.println("After writing UTF");

        scanner.close();
    }

    private boolean isAfterTimestamps(Message firstMessage, String olderMessage){
        return
                LocalDateTime.parse(parseTimeStamp(firstMessage)).
                        isAfter(LocalDateTime
                                .parse(olderMessage));
    }

    private boolean isAfterTimestamps(Message firstMessage, Message olderMessage){
        return
                LocalDateTime.parse(parseTimeStamp(firstMessage)).
                        isAfter(LocalDateTime
                                .parse(parseTimeStamp(olderMessage)));
    }

    private String parseTimeStamp(Message message){
        return message.getId().substring(0, message.getId().indexOf('_'));
    }

    public void sendAllMessages(ObjectOutputStream objectOutputStream) throws IOException {
        Gson gson = new Gson();
        List<Message> messagesToBeSent = new ArrayList<>();
        for (PartitionBroker partition : partitionBrokers){
            messagesToBeSent.addAll(partition.getMessages());
        }

        String lastSeenMessageId = messagesToBeSent.get(messagesToBeSent.size() - 1).getId();
        String consumptionId = UUID.randomUUID().toString();

        Map<String, Object> map = new HashMap();
        map.put("lastSeenMessageId", lastSeenMessageId);

        File consumptionFile = new File(consumptionId + ".txt");
        consumptionFile.createNewFile();

        map.put("consumptionId", consumptionId);
        map.put("messages", messagesToBeSent);

        String serializedMessages = gson.toJson(map);
//        out.print(serializedMessages);
        System.out.println("Serialized: " + serializedMessages);
        objectOutputStream.writeUTF(serializedMessages);
        objectOutputStream.flush();

        System.out.println("AFTER");
        FileWriter fileWriter = new FileWriter(consumptionFile);
        fileWriter.write("lastSeenMessageId: " + lastSeenMessageId + "\nconsumptionId: " + consumptionId);
        fileWriter.close();

    }

//    public static void main(String[] args) {
//        JFrame jFrame = new JFrame("Leader");
//        jFrame.setSize(650, 650);
//        jFrame.setMaximumSize(new Dimension(1080, 720));
//        jFrame.setMinimumSize(new Dimension(420, 420));
//
//        JPanel jPanel = new JPanel();
//        jPanel.setBackground(Color.DARK_GRAY);
//        jFrame.add(jPanel);
//        jFrame.setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
//        jFrame.setVisible(true);

//    }


}
