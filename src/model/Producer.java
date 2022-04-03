package model;

import com.google.gson.Gson;
import enums.Node;

import javax.swing.*;
import javax.swing.plaf.nimbus.NimbusLookAndFeel;
import java.awt.*;
import java.io.IOException;
import java.io.ObjectOutputStream;
import java.net.Socket;
import java.util.HashMap;
import java.util.Map;

public class Producer{

    private Socket producerSocket;
    private ObjectOutputStream objectOutputStream;

    public void connect(String ip, int port) {
        try {
            producerSocket = new Socket(ip, port);
            objectOutputStream = new ObjectOutputStream(producerSocket.getOutputStream());
            objectOutputStream.writeInt(Node.PRODUCER.getValue());
            objectOutputStream.flush();

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void close(){
        try {
            producerSocket.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public boolean isConnected(){
        return producerSocket.isConnected();
    }

    public void produce(String message) throws IOException {
        if (producerSocket.isConnected()) {
            Map<String,String> map = new HashMap<>();
            map.put("text", message);
            String serializedMessage = new Gson()
                    .toJson(map);
            objectOutputStream.writeUTF(serializedMessage);
            objectOutputStream.flush();
        }
    }

    public Socket getProducerSocket(){
        return producerSocket;
    }

    public static void main(String[] args) throws UnsupportedLookAndFeelException{

        UIManager.setLookAndFeel(new NimbusLookAndFeel());
        Producer producer = new Producer();
        JFrame jFrame = new JFrame("Producer");
        jFrame.setSize(650, 650);
        jFrame.setMaximumSize(new Dimension(1080, 720));
        jFrame.setMinimumSize(new Dimension(420, 420));

        CardLayout cardLayout = new CardLayout();

        JPanel panelCards = new JPanel(cardLayout);

        JPanel panel = new JPanel();
        panel.setBackground(Color.DARK_GRAY);
        JLabel messageLabel = new JLabel("Message: ");
        messageLabel.setForeground(Color.magenta);


        JTextField messageField = new JTextField(15);
        messageField.setSize(new Dimension(200, 200));
        JButton submitButton = new JButton("SEND");
        JLabel errorLabel = new JLabel("PLEASE, ENTER A MESSAGE");
        errorLabel.setForeground(Color.RED);
        errorLabel.setVisible(false);

        submitButton.addActionListener(e -> {
            String message = messageField.getText();
            if (message.isEmpty()){
                errorLabel.setVisible(true);
                return;
            }

            errorLabel.setVisible(false);
            try {
                producer.produce(message);
            } catch (IOException ex) {
                ex.printStackTrace();
            }
        });

        panel.add(messageLabel);
        panel.add(messageField);

        panel.add(submitButton);
        panel.add(errorLabel);

        jFrame.addWindowListener(new java.awt.event.WindowAdapter() {
            @Override
            public void windowClosing(java.awt.event.WindowEvent windowEvent) {
                if (JOptionPane.showConfirmDialog(jFrame,
                        "Are you sure you want to close this window?", "Close Window?",
                        JOptionPane.YES_NO_OPTION,
                        JOptionPane.QUESTION_MESSAGE) == JOptionPane.YES_OPTION){

                    if (producer.getProducerSocket() != null && producer.isConnected()){
                        producer.close();
                    }

                    System.exit(0);
                }
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
            producer.connect(ip, Integer.parseInt(port));
            if (producer.getProducerSocket() != null && producer.isConnected()){
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

        jFrame.add(connectionPanel);

        panelCards.add(connectionPanel, "CONNECTION_CARD");
        panelCards.add(panel, "MESSAGE_CARD");

        jFrame.add(panelCards);
        jFrame.setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);

        jFrame.setVisible(true);

    }

}
