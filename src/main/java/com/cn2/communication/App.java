package com.cn2.communication;

import java.io.*;
import java.net.*;
import javax.swing.*;
import javax.sound.sampled.*;
import java.awt.*;
import java.awt.event.*;

public class App extends Frame implements WindowListener, ActionListener {

    static JTextField inputTextField;
    static JTextArea textArea;
    static JButton sendButton;
    static JButton callButton;
    public static Color gray;
    final static String newline = "\n";

    // Text communication variables
    private DatagramSocket textSocket;
    private int textPort; // Port for text messages

    // VoIP communication variables
    private DatagramSocket voiceSocket;
    private int voicePort; // Port for VoIP communication
    private InetAddress remoteAddress;
    private boolean isCalling = false;
    private boolean peerCalling = false;

    // Audio components for playback
    private SourceDataLine speaker;

    public App(String title) {
        super(title);
        gray = new Color(254, 254, 254);
        setBackground(gray);
        setLayout(new BorderLayout());
        addWindowListener(this);

        inputTextField = new JTextField(20);
        textArea = new JTextArea(10, 40);
        textArea.setLineWrap(true);
        textArea.setEditable(false);
        JScrollPane scrollPane = new JScrollPane(textArea);
        scrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_ALWAYS);

        sendButton = new JButton("Send");
        callButton = new JButton("Call");

        add(scrollPane, BorderLayout.CENTER);
        JPanel panel = new JPanel(new FlowLayout());
        panel.add(inputTextField);
        panel.add(sendButton);
        panel.add(callButton);
        add(panel, BorderLayout.SOUTH);

        sendButton.addActionListener(this);
        callButton.addActionListener(this);
    }

    public static void main(String[] args) {
        App app = new App("CN2 - AUTH");
        app.setSize(500, 250);
        app.setVisible(true);

        try {
            app.remoteAddress = InetAddress.getByName("192.168.1.14");
            app.textPort = 50000;
            app.voicePort = 50001;
            app.textSocket = new DatagramSocket(app.textPort);
            app.voiceSocket = new DatagramSocket(app.voicePort);
        } catch (SocketException | UnknownHostException ex) {
            SwingUtilities.invokeLater(() -> textArea.append("Error initializing sockets: " + ex.getMessage() + newline));
        }

        // Listener thread for text messages
        new Thread(() -> {
            do {
                try {
                    byte[] buffer = new byte[1024];
                    DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                    app.textSocket.receive(packet);
                    String receivedData = new String(packet.getData(), 0, packet.getLength()).trim();

                    if (receivedData.equals("CALL_START")) {
                        SwingUtilities.invokeLater(() -> {
                            if (!app.isCalling) {
                                app.peerCalling = true;
                                app.callButton.setText("Accept");
                                textArea.append("Peer is calling..." + newline);
                            }
                        });
                        continue;
                    } else if (receivedData.equals("CALL_END")) {
                        SwingUtilities.invokeLater(() -> app.endCall());
                        continue;
                    }
                    // Attempt to decrypt regular messages
                    try {
                        String message = EncryptionUtil.decrypt(receivedData);
                        SwingUtilities.invokeLater(() -> textArea.append("Peer: " + message + newline));
                    } catch (Exception ex) {
                        SwingUtilities.invokeLater(() -> textArea.append("Error decrypting message: " + ex.getMessage() + newline));
                    }
                } catch (IOException ex) {
                    SwingUtilities.invokeLater(() -> textArea.append("Error receiving message: " + ex.getMessage() + newline));
                }
            } while (true);
        }).start();

        // Listener thread for voice data
        new Thread(() -> {
            do {
                try {
                    byte[] buffer = new byte[1024];
                    DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                    app.voiceSocket.receive(packet);

                    if (app.isCalling) {
                        if (app.speaker == null) {
                            AudioFormat format = new AudioFormat(8000.0f, 16, 1, true, true);
                            app.speaker = AudioSystem.getSourceDataLine(format);
                            app.speaker.open(format);
                            app.speaker.start();
                        }
                        app.speaker.write(packet.getData(), 0, packet.getLength());
                    }
                } catch (IOException | LineUnavailableException ex) {
                    SwingUtilities.invokeLater(() -> textArea.append("Error receiving audio: " + ex.getMessage() + newline));
                }
            } while (true);
        }).start();
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        if (e.getSource() == sendButton) {
            try {
                String message = inputTextField.getText().trim();
                if (!message.isEmpty()) {
                    // Encrypt the message
                    String encryptedMessage = EncryptionUtil.encrypt(message);

                    // Send the encrypted message
                    byte[] buffer = encryptedMessage.getBytes();
                    DatagramPacket packet = new DatagramPacket(buffer, buffer.length, remoteAddress, textPort);
                    textSocket.send(packet);

                    // Display the original message locally
                    SwingUtilities.invokeLater(() -> textArea.append("You: " + message + newline));
                    inputTextField.setText("");
                }
            } catch (Exception ex) {
                SwingUtilities.invokeLater(() -> textArea.append("Error sending message: " + ex.getMessage() + newline));
            }
        } else if (e.getSource() == callButton) {
            if (callButton.getText().equals("Call")) {
                startCall();
            } else if (callButton.getText().equals("Accept")) {
                acceptCall();
            } else if (callButton.getText().equals("End Call")) {
                endCall();
            }
        }
    }

    private void startCall() {
        isCalling = true;
        callButton.setText("End Call");
        SwingUtilities.invokeLater(() -> textArea.append("Starting VoIP call..." + newline));

        // Send the "CALL_START" signal
        try {
            byte[] signalBuffer = "CALL_START".getBytes();
            DatagramPacket signalPacket = new DatagramPacket(signalBuffer, signalBuffer.length, remoteAddress, textPort);
            textSocket.send(signalPacket);
        } catch (IOException ex) {
            SwingUtilities.invokeLater(() -> textArea.append("Error sending call signal: " + ex.getMessage() + newline));
        }

        startMicrophone();
    }

    private void acceptCall() {
        isCalling = true;
        peerCalling = false;
        callButton.setText("End Call");
        SwingUtilities.invokeLater(() -> textArea.append("Call accepted." + newline));
        startMicrophone();
    }

    private void startMicrophone() {
        new Thread(() -> {
            try {
                AudioFormat format = new AudioFormat(8000.0f, 16, 1, true, true);
                TargetDataLine microphone = AudioSystem.getTargetDataLine(format);
                microphone.open(format);
                microphone.start();

                byte[] buffer = new byte[1024];
                while (isCalling) {
                    int bytesRead = microphone.read(buffer, 0, buffer.length);
                    if (bytesRead > 0) {
                        DatagramPacket packet = new DatagramPacket(buffer, bytesRead, remoteAddress, voicePort);
                        voiceSocket.send(packet);
                    }
                }
                microphone.close();
            } catch (LineUnavailableException | IOException ex) {
                SwingUtilities.invokeLater(() -> textArea.append("Error sending audio: " + ex.getMessage() + newline));
            }
        }).start();
    }

    private void endCall() {
        if (!isCalling) return; // Avoid multiple "End Call" updates

        isCalling = false;
        peerCalling = false;
        callButton.setText("Call");
        SwingUtilities.invokeLater(() -> textArea.append("Call ended." + newline));

        // Send the "CALL_END" signal
        try {
            byte[] signalBuffer = "CALL_END".getBytes();
            DatagramPacket signalPacket = new DatagramPacket(signalBuffer, signalBuffer.length, remoteAddress, textPort);
            textSocket.send(signalPacket);
        } catch (IOException ex) {
            SwingUtilities.invokeLater(() -> textArea.append("Error sending end call signal: " + ex.getMessage() + newline));
        }

        if (speaker != null && speaker.isOpen()) {
            speaker.close();
            speaker = null;
        }
    }

    @Override
    public void windowClosing(WindowEvent e) {
        if (textSocket != null && !textSocket.isClosed()) {
            textSocket.close();
        }
        if (voiceSocket != null && !voiceSocket.isClosed()) {
            voiceSocket.close();
        }
        if (speaker != null && speaker.isOpen()) {
            speaker.close();
        }
        dispose();
        System.exit(0);
    }

    @Override public void windowActivated(WindowEvent e) {}
    @Override public void windowClosed(WindowEvent e) {}
    @Override public void windowDeactivated(WindowEvent e) {}
    @Override public void windowDeiconified(WindowEvent e) {}
    @Override public void windowIconified(WindowEvent e) {}
    @Override public void windowOpened(WindowEvent e) {}
}
