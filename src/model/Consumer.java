package model;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import enums.Node;

import javax.swing.*;
import javax.swing.event.AncestorEvent;
import javax.swing.event.AncestorListener;
import javax.swing.plaf.nimbus.NimbusLookAndFeel;
import java.awt.*;
import java.io.*;
import java.net.Socket;
import java.util.*;

public class Consumer{

    private Socket consumerSocket;
    private String consumptionId;
    private ObjectInputStream objectInputStream;
    private ObjectOutputStream objectOutputStream;

    public Consumer() {
        consumptionId = "";
    }

    public Consumer(String consumptionId) {
        this.consumptionId = consumptionId;
    }

    public void connect(String ip, int port){
        try {
            consumerSocket = new Socket(ip, port);
            objectOutputStream = new ObjectOutputStream(consumerSocket.getOutputStream());
            objectInputStream = new ObjectInputStream(consumerSocket.getInputStream());
            objectOutputStream.writeInt(Node.CONSUMER.getValue());
            objectOutputStream.flush();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public Boolean isConnected(){
        return consumerSocket.isConnected();
    }

    public void close(){
        try {
            consumerSocket.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void consume() {
        Gson gson = new Gson();
        Map<String, String> request = new HashMap<>();
        request.put("consumptionId", consumptionId);
        String serializedRequest = gson.toJson(request);
        try {
            objectOutputStream.writeUTF(serializedRequest);
            objectOutputStream.flush();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public Socket getConsumerSocket() {
        return consumerSocket;
    }

    public String listenForResponse() throws IOException {
         return objectInputStream.readUTF();
    }

    public void receiveAndProcessMessages(JTextArea inbox){
        Gson gson = new Gson();
        String response;
        try {
            response = listenForResponse();
            JsonObject responseJson = gson.fromJson(response, JsonObject.class);
            String responseConsumptionId = responseJson.get("consumptionId").getAsString();
            if (!consumptionId.equals(responseConsumptionId)){
                Message[] messages = gson.fromJson(responseJson.getAsJsonArray("messages"), Message[].class);
                Arrays.stream(messages)
                        .forEach(msg -> inbox.append(msg.getText() + "\n"));
//                        .forEach(msg -> inbox.setText(inbox.getText() + "\n" + msg.getText()));
                consumptionId = responseConsumptionId;
            }

        } catch (IOException e) {
            e.printStackTrace();
        }

    }

    public String getConsumptionId() {
        return consumptionId;
    }

    public void setConsumptionId(String consumptionId) {
        this.consumptionId = consumptionId;
    }

    public static void main(String[] args) throws UnsupportedLookAndFeelException {

        UIManager.setLookAndFeel(new NimbusLookAndFeel());
        Consumer consumer = new Consumer();
        JFrame frame = new JFrame("Consumer");
        frame.setSize(650, 650);
        frame.setMaximumSize(new Dimension(1080, 720));
        frame.setMinimumSize(new Dimension(420, 420));

        CardLayout cardLayout = new CardLayout();

        JPanel panelCards = new JPanel(cardLayout);

        JPanel panel = new JPanel();
        panel.setBackground(Color.DARK_GRAY);
        JLabel inboxLabel = new JLabel("Inbox: ");
        inboxLabel.setForeground(Color.magenta);


        JTextArea inbox = new JTextArea(10, 40);
        inbox.setEditable(false);

        JScrollPane inboxScrollPane = new JScrollPane(inbox);
        inboxScrollPane.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_ALWAYS);
        inboxScrollPane.setVerticalScrollBarPolicy(ScrollPaneConstants.VERTICAL_SCROLLBAR_ALWAYS);

        JButton consumeButton = new JButton("CONSUME");

        consumeButton.addActionListener(e -> {
                    consumer.consume();
                    Thread listenForResponsesThread = new Thread(() -> consumer.receiveAndProcessMessages(inbox));
                    listenForResponsesThread.setName("listenForResponsesThread");
                    listenForResponsesThread.start();
        });


        panel.add(inboxLabel);
        panel.add(inboxScrollPane);
        panel.add(consumeButton);

        frame.addWindowListener(new java.awt.event.WindowAdapter() {
            @Override
            public void windowClosing(java.awt.event.WindowEvent windowEvent) {
                if (JOptionPane.showConfirmDialog(frame,
                        "Are you sure you want to close this window?", "Close Window?",
                        JOptionPane.YES_NO_OPTION,
                        JOptionPane.QUESTION_MESSAGE) == JOptionPane.YES_OPTION){

                    if (consumer.getConsumerSocket() != null && consumer.isConnected()){
                        consumer.close();
                    }

                    System.exit(0);
                }
            }
        });
//
        inbox.addAncestorListener(new AncestorListener() {
            @Override
            public void ancestorAdded(AncestorEvent event) {
                java.util.Timer timer = new java.util.Timer();
                timer.schedule(new TimerTask() {
                    @Override
                    public void run() {
                        consumeButton.doClick();
                    }
                }, 0, 30*1000);

            }

            @Override
            public void ancestorRemoved(AncestorEvent event) {
                System.out.println("rem");

            }

            @Override
            public void ancestorMoved(AncestorEvent event) {
            }
        });

        JPanel connectionPanel = new JPanel();

        JLabel connectToLabel = new JLabel("Connect to: ");

        JLabel ipLabel = new JLabel("ip");
        ipLabel.setForeground(Color.magenta);
        JLabel portLabel = new JLabel("port");
        portLabel.setForeground(Color.MAGENTA);

        JTextField ipField = new JTextField(15);
        JTextField portField = new JTextField(10);

        ipField.setText("localhost");
        portField.setText("5050");

        JLabel connectionErrorLabel = new JLabel();
        connectionErrorLabel.setVisible(false);

        JButton connectButton = new JButton("connect");

        connectButton.addActionListener(e -> {
            String ip = ipField.getText();
            String port = portField.getText();

            if (ip.isBlank() || port.isBlank() || port.matches(".*[Aa-zZ].*")){
                connectionErrorLabel.setText("Fill the fields with valid info");
                connectionErrorLabel.setForeground(Color.RED);
                connectionErrorLabel.setVisible(true);
                return;
            }

            connectionErrorLabel.setVisible(false);
            cardLayout.show(panelCards, "MESSAGE_CARD");
            consumer.connect(ip, Integer.parseInt(port));
            if (consumer.getConsumerSocket() != null && consumer.isConnected()){
                connectionPanel.setVisible(false);
            }
        });

        connectionPanel.add(connectToLabel);
        connectionPanel.add(ipLabel);
        connectionPanel.add(ipField);
        connectionPanel.add(portLabel);
        connectionPanel.add(portField);
        connectionPanel.add(connectionErrorLabel);
        connectionPanel.add(connectButton);

        connectionPanel.setBackground(Color.GREEN);
        connectionPanel.setVisible(true);

        frame.add(connectionPanel);

        panelCards.add(connectionPanel, "CONNECTION_CARD");
        panelCards.add(panel, "MESSAGE_CARD");

        frame.add(panelCards);
        frame.setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);

        frame.setVisible(true);


    }

}
