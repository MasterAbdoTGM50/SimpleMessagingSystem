package model;


import com.google.gson.Gson;
import com.google.gson.JsonObject;

import java.io.*;
import java.net.Socket;
import java.net.SocketException;
import java.time.LocalDateTime;
import java.util.*;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;

public class LeaderBroker {

    private List<PartitionBrokerSocket> partitionBrokers; // List<Socket>
    private static final String LAST_SEEN_MESSAGE_ID_TEXT = "lastSeenMessageId: ";

    private int currentPartitionBrokerTurn;
    private Queue<Socket> partitionQueue;

    private int numberOfAcceptablePartitions;
    public static final int PARTITION_QUEUE_CAPACITY = 10;

    public LeaderBroker() {
        currentPartitionBrokerTurn = 0;
        partitionBrokers = new ArrayList<>();
        partitionQueue = new ArrayBlockingQueue<>(PARTITION_QUEUE_CAPACITY);
    }

    public LeaderBroker(List<PartitionBrokerSocket> partitionBrokers, int numberOfAcceptablePartitions) {
        this.partitionBrokers = partitionBrokers;
        this.numberOfAcceptablePartitions = numberOfAcceptablePartitions;
    }

    public synchronized void addPartitionBroker(Socket partitionSocket, ObjectOutputStream objectOutputStream, ObjectInputStream objectInputStream) {
        partitionBrokers.add(new PartitionBrokerSocket(partitionSocket, objectOutputStream, objectInputStream));
    }

    public synchronized void addPartitionBrokers(int numberOfNewPartitionBrokers) throws IllegalArgumentException {
        if (numberOfNewPartitionBrokers < 0) {
            throw new IllegalArgumentException("Number of new Partitions can only be greater than zero");
        }

        for (int i = 0; i < numberOfNewPartitionBrokers; i++) {
            if (partitionQueue.peek() != null) {
                PartitionBrokerSocket partitionBrokerSocket = new PartitionBrokerSocket(partitionQueue.remove());
                if (partitionBrokerSocket.getSocket().isClosed()) {
                    continue;
                }
                partitionBrokers.add(partitionBrokerSocket);
            }
        }
        this.numberOfAcceptablePartitions += numberOfNewPartitionBrokers;

    }

    public synchronized void addToPartitionQueue(Socket partitionSocket, ObjectOutputStream objectOutputStream, ObjectInputStream objectInputStream) {
        partitionQueue.add(partitionSocket);
    }

    public synchronized int getNumberOfPartitions() {
        return partitionBrokers.size();
    }

    public synchronized int getNumberOfQueuePartitions() {
        return partitionQueue.size();
    }

    public void configureMessage(String text) {
        String messageTimestamp = LocalDateTime.now().toString();
        synchronized (this) {
            String messageUUID = UUID.randomUUID().toString();
            String id = messageTimestamp + '_' + messageUUID + '_' + currentPartitionBrokerTurn;
            Message message = new Message(id, text, currentPartitionBrokerTurn);

            requestToStoreMessageInPartition(message);
            currentPartitionBrokerTurn = (currentPartitionBrokerTurn + 1) % partitionBrokers.size();
        }
    }

    public void requestToStoreMessageInPartition(Message message) {

        try {
            Gson gson = new Gson();
            ObjectOutputStream objectOutputStream = partitionBrokers
                    .get(currentPartitionBrokerTurn).getObjectOutputStream();
            Map<String, Message> request = new HashMap<>();
            request.put("messages", message);
            String requestInString = gson.toJson(request);
            objectOutputStream.writeUTF(requestInString);
            objectOutputStream.flush();
        } catch (SocketException e) {
            closeConnection(partitionBrokers.get(currentPartitionBrokerTurn));
            partitionBrokers.remove(currentPartitionBrokerTurn);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void requestMessagesFromPartition(int partitionIndex) {
        try {
            Gson gson = new Gson();
            ObjectOutputStream objectOutputStream = partitionBrokers
                    .get(partitionIndex).getObjectOutputStream();
            Map<String, Boolean> request = new HashMap<>();
            request.put("more", true);
            String requestInString = gson.toJson(request);
            objectOutputStream.writeUTF(requestInString);
            objectOutputStream.flush();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void requestMessagesFromPartition(PartitionBrokerSocket partitionBrokerSocket) {
        try {
            Gson gson = new Gson();
            ObjectOutputStream objectOutputStream = partitionBrokerSocket.getObjectOutputStream();
            Map<String, Boolean> request = new HashMap<>();
            request.put("more", true);
            String requestInString = gson.toJson(request);
            objectOutputStream.writeUTF(requestInString);
            objectOutputStream.flush();
        } catch (SocketException e) {
            partitionBrokers.remove(partitionBrokerSocket);
            closeConnection(partitionBrokerSocket);
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    synchronized public Message[] listenForMessagesFromPartition(int partitionIndex) {
        Gson gson = new Gson();
        try {
            ObjectOutputStream objectOutputStream = partitionBrokers.get(partitionIndex).getObjectOutputStream();
            ObjectInputStream objectInputStream = partitionBrokers.get(partitionIndex).getObjectInputStream();
            return getMessages(gson, partitionBrokers.get(partitionIndex).getSocket(), objectInputStream);
        } catch (IOException e) {
            e.printStackTrace();
        }
        return new Message[0];
    }

    synchronized public Message[] listenForMessagesFromPartition(PartitionBrokerSocket partitionBrokerSocket) {
        Gson gson = new Gson();
        try {
            ObjectOutputStream objectOutputStream = partitionBrokerSocket.getObjectOutputStream();
            ObjectInputStream objectInputStream = partitionBrokerSocket.getObjectInputStream();

            return getMessages(gson, partitionBrokerSocket.getSocket(), objectInputStream);
        } catch (IOException e) {
            e.printStackTrace();
        }
        return new Message[0];
    }

    synchronized private Message[] getMessages(Gson gson, Socket partitionSocket, ObjectInputStream objectInputStream) throws IOException {
        String response = "";
        while (!partitionSocket.isClosed()) {
            if (objectInputStream.available() != 0) {
                response = objectInputStream.readUTF();
                break;
            }
        }
        JsonObject responseJson = gson.fromJson(response, JsonObject.class);
        Message[] messages;
        if (responseJson.get("messages").isJsonArray()) {
            messages = gson.fromJson(responseJson.getAsJsonArray("messages"), Message[].class);
        } else {
            messages = new Message[1];
            messages[0] = gson.fromJson(responseJson.get("messages"), Message.class);
        }
        return messages;
    }

    public boolean findConsumptionIdFile(Consumption consumption) {
        File consumptionFile = new File(consumption.getConsumptionId() + ".txt");
        return consumptionFile.exists();
    }

    public void serveConsumerConsumption(Consumption consumption, ObjectOutputStream objectOutputStream) throws IOException {
        if (consumption.getConsumptionId().isBlank()) {
            sendAllMessages(objectOutputStream);
            return;
        }

        if (!findConsumptionIdFile(consumption)) {
            throw new FileNotFoundException();
        }

        File consumptionFile = new File(consumption.getConsumptionId() + ".txt");
        Scanner scanner = new Scanner(consumptionFile);
        StringBuilder lastSeenMessageId = new StringBuilder(
                scanner.skip(LAST_SEEN_MESSAGE_ID_TEXT).nextLine());

        int lastSeenMessagePartition =
                Integer.parseInt(lastSeenMessageId.substring(lastSeenMessageId.lastIndexOf("_") + 1));
        String lastSeenMessageTimestamp = lastSeenMessageId.substring(0, lastSeenMessageId.indexOf("_"));


        requestMessagesFromPartition(lastSeenMessagePartition);
        Message[] lastSeenPartitionMessages = listenForMessagesFromPartition(lastSeenMessagePartition);
        List<Message> messages = Arrays.asList(lastSeenPartitionMessages);
        Comparator<Message> c = (o1, o2) -> {
            if (o1.getId().equals(o2.getId())) {
                return 0;
            } else if (isAfterTimestamps(o1, lastSeenMessageTimestamp)) {
                return 1;
            } else {
                return -1;
            }
        };


        int indexOfMessageInPartition = Collections.binarySearch(messages, new Message(lastSeenMessageId.toString(), "", lastSeenMessageTimestamp, lastSeenMessagePartition), c);
        List<Message> messagesToBeSent = new ArrayList<>();

        for (int i = 0; i < partitionBrokers.size(); i++) {
            int startingIndex = (lastSeenMessagePartition == i) ? indexOfMessageInPartition + 1 : 0;
            int sizeOfMessagesInPartition = requestAndListenForSizeOfMessagesInPartition(partitionBrokers.get(i));
            if (sizeOfMessagesInPartition == -1 || sizeOfMessagesInPartition == 0) {
                continue;
            }
            Message[] messagesReceivedFromPartition;
            if (lastSeenMessagePartition == i) {
                messagesReceivedFromPartition = lastSeenPartitionMessages;
            } else {
                requestMessagesFromPartition(partitionBrokers.get(i));
                messagesReceivedFromPartition = listenForMessagesFromPartition(partitionBrokers.get(i));
            }

            for (int j = startingIndex; j < sizeOfMessagesInPartition; j++) {
                Message msg = messagesReceivedFromPartition[j];
                if (!isAfterTimestamps(msg, lastSeenMessageTimestamp)) {
                    continue;
                }
                messagesToBeSent.add(msg);
            }
        }

        Gson gson = new Gson();

        if (messagesToBeSent.isEmpty()) {
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

    }

    public Integer requestAndListenForSizeOfMessagesInPartition(PartitionBrokerSocket partitionBrokerSocket) {
        try {
            ObjectOutputStream objectOutputStream = partitionBrokerSocket.getObjectOutputStream();
            ObjectInputStream objectInputStream = partitionBrokerSocket.getObjectInputStream();
            Gson gson = new Gson();
            Map<String, Object> request = new HashMap<>();
            request.put("sizeOfMessages", true);
            String requestAsString = gson.toJson(request);
            objectOutputStream.writeUTF(requestAsString);
            objectOutputStream.flush();
            while (!partitionBrokerSocket.getSocket().isClosed()) {
                if (objectInputStream.available() != 0) {
                    String response = objectInputStream.readUTF();
                    JsonObject responseAsJsonObject = gson.fromJson(response, JsonObject.class);
                    return gson.fromJson(responseAsJsonObject.get("size"), int.class);
                }
            }


        } catch (IOException e) {
            e.printStackTrace();
        }

        return -1;
    }

    private boolean isAfterTimestamps(Message firstMessage, String olderMessage) {
        return
                LocalDateTime.parse(parseTimeStamp(firstMessage)).
                        isAfter(LocalDateTime
                                .parse(olderMessage));
    }

    private String parseTimeStamp(Message message) {
        return message.getId().substring(0, message.getId().indexOf('_'));
    }

    public int getNumberOfAcceptablePartitions() {
        return numberOfAcceptablePartitions;
    }

    public void setNumberOfAcceptablePartitions(int numberOfAcceptablePartitions) {
        this.numberOfAcceptablePartitions = numberOfAcceptablePartitions;
    }

    public void sendAllMessages(ObjectOutputStream objectOutputStream) throws IOException {
        Gson gson = new Gson();
        List<Message> messagesToBeSent = new ArrayList<>();

        for (PartitionBrokerSocket partitionBrokerSocket : partitionBrokers) {
            requestMessagesFromPartition(partitionBrokerSocket);
            Message[] messages = listenForMessagesFromPartition(partitionBrokerSocket);
            messagesToBeSent.addAll(Arrays.asList(messages));
        }

        Map<String, Object> map = new HashMap<>();

        if (messagesToBeSent.isEmpty()) {
            map.put("empty", "");
            objectOutputStream.writeUTF(gson.toJson(map));
            objectOutputStream.flush();
            return;
        }

        String lastSeenMessageId = messagesToBeSent.get(messagesToBeSent.size() - 1).getId();
        String consumptionId = UUID.randomUUID().toString();

        map = new HashMap<>();
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

    public void removeDisconnectedSocket(Socket socketToBeRemoved) {
        for (int i = 0; i < partitionBrokers.size(); i++) {
            if (partitionBrokers.get(i).getSocket() == socketToBeRemoved && socketToBeRemoved.isClosed()) {
                partitionBrokers.remove(i);
            }
        }
    }

    private void closeConnection(PartitionBrokerSocket partitionBrokerSocket) {
        try {
            partitionBrokerSocket.getObjectInputStream().close();
            partitionBrokerSocket.getObjectOutputStream().close();
            partitionBrokerSocket.getSocket().close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
