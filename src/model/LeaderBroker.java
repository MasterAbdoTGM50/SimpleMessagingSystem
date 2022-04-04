package model;


import com.google.gson.Gson;

import java.io.*;
import java.time.LocalDateTime;
import java.util.*;
import java.util.List;

public class LeaderBroker {

    private List<PartitionBroker> partitionBrokers;
    private static final String LAST_SEEN_MESSAGE_ID_TEXT = "lastSeenMessageId: ";
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
        }
    }

    public boolean findConsumptionIdFile(Consumption consumption){
        File consumptionFile = new File(consumption.getConsumptionId() + ".txt");
        return consumptionFile.exists();
    }

    public void serveConsumerConsumption(Consumption consumption, ObjectOutputStream objectOutputStream) throws IOException {
        if (consumption.getConsumptionId().isBlank()){
            sendAllMessages(objectOutputStream);
            return;
        }

        if (!findConsumptionIdFile(consumption)){
            throw new FileNotFoundException();
        }

        File consumptionFile = new File(consumption.getConsumptionId() + ".txt");
        Scanner scanner = new Scanner(consumptionFile);
        StringBuilder lastSeenMessageId = new StringBuilder(
                scanner.skip(LAST_SEEN_MESSAGE_ID_TEXT).nextLine());

        int lastSeenMessagePartition =
                Integer.parseInt(lastSeenMessageId.substring(lastSeenMessageId.lastIndexOf("_") + 1 ));
        String lastSeenMessageTimestamp = lastSeenMessageId.substring(0, lastSeenMessageId.indexOf("_"));

        List messages = partitionBrokers
                .get(lastSeenMessagePartition)
                .getMessages();
        Comparator<Message> c = new Comparator<Message>() {
            int counter = 0;
            @Override
            public int compare(Message o1, Message o2) {
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
        List<Message> messagesToBeSent = new ArrayList<>();

        for (int i = 0; i < partitionBrokers.size(); i++){
            int startingIndex = (lastSeenMessagePartition == i) ? indexOfMessageInPartition + 1 : 0;
            for (int j = startingIndex; j < partitionBrokers.get(i).getMessages().size(); j++){
                Message msg = partitionBrokers.get(i).getMessages().get(j);
                if (!isAfterTimestamps(msg, lastSeenMessageTimestamp)){continue;}
                messagesToBeSent.add(msg);
            }
        }

        Gson gson = new Gson();

        if (messagesToBeSent.isEmpty()){
            objectOutputStream.writeUTF(gson.toJson(consumption));
            objectOutputStream.flush();
            scanner.close();
            return;
        }

        String consumptionId = UUID.randomUUID().toString();

        Map<String, Object> map = new HashMap<>();
        map.put("lastSeenMessageId", messagesToBeSent.get(messagesToBeSent.size() - 1).getId());

        File consumptionfile = new File(consumptionId + ".txt");
        consumptionfile.createNewFile();

        try (FileWriter fileWriter = new FileWriter(consumptionfile)) {
            fileWriter.write(LAST_SEEN_MESSAGE_ID_TEXT + messagesToBeSent.get(messagesToBeSent.size() - 1).getId());
        }

        map.put("messages", messagesToBeSent);
        map.put("consumptionId", consumptionId);
        String serialized = gson.toJson(map);
        objectOutputStream.writeUTF(serialized);
        objectOutputStream.flush();

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

        if (messagesToBeSent.size() == 0){
            System.out.println("IN");
            return;
        }

        String lastSeenMessageId = messagesToBeSent.get(messagesToBeSent.size() - 1).getId();
        String consumptionId = UUID.randomUUID().toString();

        Map<String, Object> map = new HashMap<String, Object>();
        map.put("lastSeenMessageId", lastSeenMessageId);

        File consumptionFile = new File(consumptionId + ".txt");
        consumptionFile.createNewFile();

        map.put("consumptionId", consumptionId);
        map.put("messages", messagesToBeSent);

        String serializedMessages = gson.toJson(map);
        objectOutputStream.writeUTF(serializedMessages);
        objectOutputStream.flush();

        try (FileWriter fileWriter = new FileWriter(consumptionFile)) {
            fileWriter.write(LAST_SEEN_MESSAGE_ID_TEXT + lastSeenMessageId + "\nconsumptionId: " + consumptionId);
        }

    }
}
