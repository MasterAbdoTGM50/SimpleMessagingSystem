package model;


import com.google.gson.Gson;

import javax.swing.*;
import java.awt.*;
import java.io.*;
import java.time.LocalDateTime;
import java.util.*;
import java.util.List;

public class LeaderBroker {

//    private Socket leaderSocket;
    private BufferedReader in;
    private PrintWriter out;
    private List<PartitionBroker> partitionBrokers;
    private int currentPartitionBrokerTurn;

    public LeaderBroker(){
        partitionBrokers = new ArrayList<>();
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

    public void serveConsumerConsumption(Consumption consumption) throws IOException {
        if (consumption.getConsumptionId().isBlank()){
            sendAllMessages();
            return;
        }

        if (!findConsumptionIdFile(consumption)){
            throw new FileNotFoundException();
        }

        File consumptionFile = new File(consumption.getConsumptionId() + ".txt");
        Scanner scanner = new Scanner(consumptionFile);
        StringBuilder lastSeenMessageId = new StringBuilder(
                scanner.findInLine("lastSeenMessageId:"));

        int lastSeenMessagePartition =
                Integer.parseInt(lastSeenMessageId.substring(lastSeenMessageId.lastIndexOf("_") + 1 ));
        String lastSeenMessageTimestamp = lastSeenMessageId.substring(0, lastSeenMessageId.indexOf("_"));

        List messages = partitionBrokers
                .get(lastSeenMessagePartition)
                .getMessages();
        int indexOfMessageInPartition = Collections.binarySearch(messages, lastSeenMessageId);

        List<Message> messagesToBeSent = new ArrayList<>();

        for (int i = 0; i < partitionBrokers.size(); i++){
            int startingIndex = lastSeenMessagePartition == i ? indexOfMessageInPartition : 0;
            for (int j = startingIndex; j < partitionBrokers.get(i).getMessages().size(); j++){
                Message msg = partitionBrokers.get(i).getMessages().get(j);
                if (!isAfterTimestamps(msg, lastSeenMessageTimestamp)){continue;}
                messagesToBeSent.add(msg);
            }
        }

        String consumptionId = UUID.randomUUID().toString();

        Gson gson = new Gson();
        Map<String, Object> map = new HashMap<>();
        map.put("lastSeenMessageId", messagesToBeSent.get(messagesToBeSent.size() - 1).getId());

        File consumptionfile = new File(consumptionId);
        consumptionfile.createNewFile();

        FileWriter fileWriter = new FileWriter(consumptionfile);
        fileWriter.write("lastSeenMessageId: " + messagesToBeSent.get(messagesToBeSent.size() - 1).getId());
        fileWriter.close();

        map.put("messages", messagesToBeSent);
        map.put("consumptionId", consumptionId);
        String serialized = gson.toJson(map);
        out.print(serialized);

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

    public void sendAllMessages() throws IOException {
        Gson gson = new Gson();
        List<Message> messagesToBeSent = new ArrayList<>();
        for (PartitionBroker partition : partitionBrokers){
            for (Message msg : partition.getMessages()){
                messagesToBeSent.add(msg);
            }
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
        out.print(serializedMessages);

        FileWriter fileWriter = new FileWriter(consumptionFile);
        fileWriter.write("lastSeenMessageId: " + lastSeenMessageId + "\nconsumptionId: " + consumptionId);
        fileWriter.close();

    }

    /*
    public void connect(String ip, int port) {
        try {
            leaderSocket = new Socket(ip, port);
            in = new BufferedReader(new InputStreamReader(leaderSocket.getInputStream()));
            out = new PrintWriter(leaderSocket.getOutputStream(), true);
            out.write(Node.LEADER.getValue());
            out.flush();

            while (leaderSocket.isConnected()) {

            }

        } catch (IOException e) {
            e.printStackTrace();
        }
    }
    */

    public static void main(String[] args) {
        JFrame jFrame = new JFrame("Leader");
        jFrame.setSize(650, 650);
        jFrame.setMaximumSize(new Dimension(1080, 720));
        jFrame.setMinimumSize(new Dimension(420, 420));

        JPanel jPanel = new JPanel();
        jPanel.setBackground(Color.DARK_GRAY);
        jFrame.add(jPanel);
        jFrame.setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
        jFrame.setVisible(true);

    }


}
