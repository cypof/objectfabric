/**
 * Copyright (c) ObjectFabric Inc. All rights reserved.
 *
 * This file is part of ObjectFabric (objectfabric.com).
 *
 * ObjectFabric is licensed under the Apache License, Version 2.0, the terms
 * of which may be found at http://www.apache.org/licenses/LICENSE-2.0.html.
 *
 * This file is provided AS IS with NO WARRANTY OF ANY KIND, INCLUDING THE
 * WARRANTY OF DESIGN, MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE.
 */

package part11.bench;

import java.awt.Button;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.IOException;
import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.util.concurrent.CopyOnWriteArrayList;

import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.Timer;

import part11.bench.generated.MyClass;

import com.objectfabric.misc.Utils;
import com.objectfabric.transports.Client;
import com.objectfabric.transports.Server.Callback;
import com.objectfabric.transports.socket.SocketClient;
import com.objectfabric.transports.socket.SocketConnection;
import com.objectfabric.transports.socket.SocketServer;

public class UI extends Data {

    private static final String DEFAULT_HOST = "localhost";

    // private static final String DEFAULT_HOST = "192.168.145.202";

    private static final int DEFAULT_PORT = 4444;

    protected final CopyOnWriteArrayList<SocketConnection> _connections = new CopyOnWriteArrayList<SocketConnection>();

    protected JTextArea label;

    public static void main(String[] args) {
        final UI ui = new UI();
        final JFrame frame = ui.buildWindow();
        frame.setVisible(true);
    }

    protected void log(String text) {
        System.out.println(text);
    }

    protected JFrame buildWindow() {
        final JFrame frame = new JFrame("Bench Demo (Java & Swing)");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);

        JPanel panel = new JPanel();
        panel.setLayout(null);
        // panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setPreferredSize(new Dimension(640, 400));

        label = new JTextArea();
        label.setLocation(140, 120);
        label.setSize(new Dimension(1000, 1000));
        label.setText("");
        label.setBackground(panel.getBackground());
        final Font font = new Font("Courier New", Font.BOLD, 13);
        label.setFont(font);
        panel.add(label);

        Button button;

        /*
         * Server
         */

        button = new Button("Start Server");
        button.setLocation(10, 40);
        button.setSize(new Dimension(100, 23));
        panel.add(button);

        button.addActionListener(new ActionListener() {

            public void actionPerformed(ActionEvent e) {
                final MyClass myClass = new MyClass();
                SocketServer server = new SocketServer(DEFAULT_PORT);

                server.setCallback(new Callback<SocketConnection>() {

                    public void onConnection(SocketConnection session) {
                        session.send(myClass);
                    }

                    public void onReceived(SocketConnection session, Object object) {
                        log("Connection from " + session.getRemoteAddress());
                        _connections.add(session);
                    }

                    public void onDisconnection(SocketConnection session, Throwable t) {
                        log("Disconnected from " + session.getRemoteAddress());
                        _connections.remove(session);
                    }
                });

                setMyClass(myClass);

                try {
                    server.start();

                    log("Listening to port " + server.getPort());
                } catch (IOException ex) {
                    log(ex.toString());
                }
            }
        });

        /*
         * Client
         */

        final JTextField host = new JTextField();
        host.setLocation(140, 80);
        host.setSize(new Dimension(200, 23));
        host.setText(DEFAULT_HOST);
        panel.add(host);

        button = new Button("Start Client");
        button.setLocation(10, 80);
        button.setSize(new Dimension(100, 23));
        panel.add(button);

        button.addActionListener(new ActionListener() {

            public void actionPerformed(ActionEvent e) {
                try {
                    SocketClient client = new SocketClient(host.getText(), DEFAULT_PORT);

                    client.setCallback(new Client.Callback() {

                        public void onReceived(Object object) {
                            MyClass myClass = (MyClass) object;
                            setMyClass(myClass);
                        }

                        public void onDisconnected(Throwable t) {
                        }
                    });

                    client.connect();
                    log("Connected to " + client.getSocketChannel().socket().getInetAddress());

                    _connections.add(client);
                } catch (IOException ex) {
                    log("Could not connect to " + host.getText());
                }
            }
        });

        /*
         * Updates
         */

        button = new Button("Start Updates");
        button.setLocation(10, 120);
        button.setSize(new Dimension(100, 23));
        panel.add(button);

        button.addActionListener(new ActionListener() {

            public void actionPerformed(ActionEvent e) {
                sendData();
            }
        });

        button = new Button("Stop Updates");
        button.setLocation(10, 160);
        button.setSize(new Dimension(100, 23));
        panel.add(button);

        button.addActionListener(new ActionListener() {

            public void actionPerformed(ActionEvent e) {
                stopData();
            }
        });

        /*
         * Reset
         */

        button = new Button("Reset Stats");
        button.setLocation(10, 200);
        button.setSize(new Dimension(100, 23));
        panel.add(button);

        button.addActionListener(new ActionListener() {

            public void actionPerformed(ActionEvent e) {
            }
        });

        final Timer timer = new Timer(1000, new ActionListener() {

            int _lastWrites, _lastLastValue;

            public void actionPerformed(ActionEvent e) {
                StringBuilder sb = new StringBuilder();

                for (SocketConnection connection : _connections) {
                    String address = connection.getRemoteAddress().toString();
                    int port = connection.getSocketChannel().socket().getPort();
                    sb.append(address + ":" + port);
                    sb.append(Utils.NEW_LINE);
                }

                String speed = writeInt(writes - _lastWrites);
                _lastWrites = writes;
                sb.append("Writes: " + writeInt(writes) + " (" + speed + " transaction/s)");
                sb.append(Utils.NEW_LINE);

                MyClass c = getMyClass();
                int lastValue = c != null ? c.getMyField() : 0;
                speed = writeInt(lastValue - _lastLastValue);
                _lastLastValue = lastValue;
                sb.append("Value: " + writeInt(lastValue) + " (" + speed + " writes/s (Writes are aggregated))");
                sb.append(Utils.NEW_LINE);

                label.setText(sb.toString());
            }
        });

        timer.start();

        /*
         * Window
         */

        frame.getContentPane().add(panel);
        frame.setSize(700, 500);

        return frame;
    }

    private static final NumberFormat _format = new DecimalFormat("#,###");

    private static String writeInt(int value) {
        return _format.format(value);
    }
}
