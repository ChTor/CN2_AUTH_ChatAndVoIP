package com.cn2.communication;

import java.io.*;
import java.net.*;
import javax.swing.*;
import java.awt.*;
import java.awt.event.*;

public class Tcp extends Frame implements WindowListener, ActionListener {

    // GUI components
    static JTextField inputTextField;        
    static JTextArea textArea;              
    static JFrame frame;                    
    static JButton sendButton;              
    static JButton connectButton;           
    static JTextField ipTextField;          
    static JTextField portTextField;        
    static ServerSocket serverSocket;       
    static Socket socket;                   
    static BufferedReader reader;           
    static PrintWriter writer;              
    static Thread serverThread;             
    final static String newline = "\n";    

    public Tcp(String title) {

        super(title);
        setLayout(new FlowLayout());
        addWindowListener(this);

        // GUI components
        textArea = new JTextArea(10, 40);
        textArea.setLineWrap(true);
        textArea.setEditable(false);
        JScrollPane scrollPane = new JScrollPane(textArea);
        scrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_ALWAYS);

        inputTextField = new JTextField(20);
        sendButton = new JButton("Send");
        connectButton = new JButton("Connect");
        ipTextField = new JTextField("192.168.1.16", 10);
        portTextField = new JTextField("5000", 5);

        add(new JLabel("IP:"));
        add(ipTextField);
        add(new JLabel("Port:"));
        add(portTextField);
        add(connectButton);
        add(scrollPane);
        add(inputTextField);
        add(sendButton);

        sendButton.addActionListener(this);
        connectButton.addActionListener(this);
    }

    public static void main(String[] args) {
        Tcp app = new Tcp("Peer-to-Peer Chat");
        app.setSize(500, 300);
        app.setVisible(true);

        // Start server thread
        startServer();
    }

    /**
     * Start a server thread to listen for incoming connections.
     */
    public static void startServer() {
        serverThread = new Thread(() -> {
            try {
                serverSocket = new ServerSocket(Integer.parseInt(portTextField.getText()));
                textArea.append("Server started. Waiting for connections...\n");
    
                while (true) {
                    socket = serverSocket.accept();
                    textArea.append("Peer connected." + newline);
                    reader = new BufferedReader(new InputStreamReader(socket.getInputStream()));
    
                    String message;
                    while ((message = reader.readLine()) != null) {
                        textArea.append("Peer: " + message + newline);
                        // Handle peer disconnection message
                        if (message.equalsIgnoreCase("Peer has disconnected.")) {
                            textArea.append("Peer disconnected. Closing connection.\n");
                            break;
                        }
                    }
    
                    // Cleanup after peer disconnects
                    if (socket != null && !socket.isClosed()) {
                        socket.close();
                    }
                    textArea.append("Connection closed.\n");
                }
            } catch (IOException e) {
                textArea.append("Error in server: " + e.getMessage() + newline);
            }
        });
        serverThread.start();
    }
    

    /**
     * Handle button clicks.
     */
    @Override
    public void actionPerformed(ActionEvent e) {
        if (e.getSource() == connectButton) {
            try {
                socket = new Socket(ipTextField.getText(), Integer.parseInt(portTextField.getText()));
                writer = new PrintWriter(socket.getOutputStream(), true);
                textArea.append("Connected to " + ipTextField.getText() + ":" + portTextField.getText() + newline);
            } catch (IOException ex) {
                textArea.append("Connection failed: " + ex.getMessage() + newline);
            }
        } else if (e.getSource() == sendButton) {
            String message = inputTextField.getText();
            if (writer != null && !message.isEmpty()) {
                writer.println(message);
                textArea.append("Me: " + message + newline);
                inputTextField.setText("");
            } else {
                textArea.append("No connection or empty message.\n");
            }
        }
    }

    // Window listener methods
    @Override
    public void windowClosing(WindowEvent e) {
        try {
            // Send a disconnect message to the peer
            if (writer != null) {
                writer.println("Peer has disconnected."); // Notify the peer
            }

            // Close resources gracefully
            if (writer != null) writer.close();
            if (reader != null) reader.close();
            if (socket != null && !socket.isClosed()) socket.close();
            if (serverSocket != null && !serverSocket.isClosed()) serverSocket.close();

            // Stop the server thread
            if (serverThread != null && serverThread.isAlive()) {
                serverThread.interrupt();
            }

            textArea.append("Application closed.\n");
        } catch (IOException ex) {
            textArea.append("Error during closure: " + ex.getMessage() + "\n");
        }

        // Close the GUI
        dispose();
        System.exit(0);
    }
    @Override
    public void windowActivated(WindowEvent e) {}
    @Override
    public void windowClosed(WindowEvent e) {}
    @Override
    public void windowDeactivated(WindowEvent e) {}
    @Override
    public void windowDeiconified(WindowEvent e) {}
    @Override
    public void windowIconified(WindowEvent e) {}
    @Override
    public void windowOpened(WindowEvent e) {}
}
