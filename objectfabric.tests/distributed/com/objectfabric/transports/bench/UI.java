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

package com.objectfabric.transports.bench;

import java.awt.Button;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.TextArea;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.IOException;
import java.text.DecimalFormat;
import java.text.NumberFormat;

import javax.swing.BoxLayout;
import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.Timer;

import com.objectfabric.misc.AsyncCallback;
import com.objectfabric.misc.NIOConnection;
import com.objectfabric.misc.NIOListener;
import com.objectfabric.misc.NIOManager;
import com.objectfabric.misc.Utils;

final class UI extends Data {

    private static final String DEFAULT_HOST = "localhost";

    // private static final String DEFAULT_HOST = "192.168.145.202";

    private static final int DEFAULT_PORT = 4444;

    protected JPanel _panel;

    protected JTextArea _label;

    private TextArea _log;

    public static void main(String[] args) {
        final UI ui = new UI();
        final JFrame frame = ui.buildWindow();
        frame.setVisible(true);
    }

    @Override
    protected void log(String text) {
        _log.append(text + "\n\r");
        _log.setCaretPosition(_log.getText().length());
    }

    protected JFrame buildWindow() {
        final JFrame frame = new JFrame("Bench Demo (Java & Swing)");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);

        final JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));

        // Main panel

        _panel = new JPanel();
        _panel.setLayout(null);
        _panel.setPreferredSize(new Dimension(640, 400));
        panel.add(_panel);

        _label = new JTextArea();
        _label.setLocation(140, 120);
        _label.setSize(new Dimension(1000, 1000));
        _label.setText("");
        _label.setBackground(_panel.getBackground());
        final Font font = new Font("Courier New", Font.BOLD, 13);
        _label.setFont(font);
        _panel.add(_label);

        Button button;

        // Connections

        button = new Button("Start Server");
        button.setLocation(10, 40);
        button.setSize(new Dimension(100, 23));
        _panel.add(button);

        button.addActionListener(new ActionListener() {

            public void actionPerformed(ActionEvent e) {
                NIOListener listener = new NIOListener() {

                    @Override
                    public NIOConnection createConnection() {
                        return new TestConnection();
                    }
                };

                try {
                    listener.start(DEFAULT_PORT);

                    log("Listening on port " + DEFAULT_PORT);
                } catch (final IOException ex) {
                    log(ex.toString());
                }
            }
        });

        final JTextField host = new JTextField();
        host.setLocation(140, 80);
        host.setSize(new Dimension(300, 23));
        host.setText(DEFAULT_HOST);

        _panel.add(host);

        button = new Button("Start Client");
        button.setLocation(10, 80);
        button.setSize(new Dimension(100, 23));
        _panel.add(button);

        button.addActionListener(new ActionListener() {

            public void actionPerformed(ActionEvent e) {
                final NIOManager manager = NIOManager.getInstance();
                final TestConnection connection = new TestConnection();

                manager.connect(connection, host.getText(), DEFAULT_PORT, new AsyncCallback<Void>() {

                    public void onSuccess(Void _) {
                        log("Connected to " + connection.getChannel().socket().getRemoteSocketAddress());
                    }

                    public void onFailure(Exception ex) {
                        log("Connection failed (" + ex + ")");
                    }
                }, null);
            }
        });

        // Create the start updates button

        button = new Button("Start Updates");
        button.setLocation(10, 120);
        button.setSize(new Dimension(100, 23));
        _panel.add(button);

        button.addActionListener(new ActionListener() {

            public void actionPerformed(ActionEvent e) {
                sendData();
            }
        });

        // Create the stop updates button

        button = new Button("Stop Updates");
        button.setLocation(10, 160);
        button.setSize(new Dimension(100, 23));
        _panel.add(button);

        button.addActionListener(new ActionListener() {

            public void actionPerformed(ActionEvent e) {
                stopData();
            }
        });

        // Create the clear button

        button = new Button("Reset Stats");
        button.setLocation(10, 200);
        button.setSize(new Dimension(100, 23));
        _panel.add(button);

        button.addActionListener(new ActionListener() {

            public void actionPerformed(ActionEvent e) {
                _latencySum.set(0);
                _latencyCount.set(0);
            }
        });

        // Create the shudown button

        button = new Button("Shutdown");
        button.setLocation(10, 240);
        button.setSize(new Dimension(100, 23));
        _panel.add(button);

        button.addActionListener(new ActionListener() {

            public void actionPerformed(ActionEvent e) {
                NIOManager.getInstance().close();
            }
        });

        // A panel for logs

        _log = new TextArea();
        _log.setEditable(false);
        panel.add(_log);

        final Timer timer = new Timer(1000, new ActionListener() {

            int _lastTotal;

            public void actionPerformed(ActionEvent e) {
                final StringBuilder sb = new StringBuilder();
                int total = 0;

                for (final TestConnection connection : _connections) {
                    String address = connection.getChannel().socket().getRemoteSocketAddress().toString();
                    address = Utils.padRight(address + ": ", 25);
                    sb.append(address.substring(0, 25));

                    final String reads = writeInt(connection._reads - connection._lastReads);
                    final String writes = writeInt(connection._writes - connection._lastWrites);

                    sb.append(Utils.padLeft(reads + " reads/s ", 20));
                    sb.append(Utils.padLeft(writes + " writes/s ", 20));

                    connection._lastReads = connection._reads;
                    connection._lastWrites = connection._writes;

                    sb.append(Utils.NEW_LINE);

                    total += connection._reads + connection._writes;
                }

                sb.append(Utils.NEW_LINE + Utils.padRight("Total: ", 25));
                final String s = writeInt(total - _lastTotal);
                sb.append(Utils.padLeft(s + " msgs/s ", 20));
                _lastTotal = total;

                double latency = 0;

                if (_latencyCount.get() > 0)
                    latency = (double) _latencySum.get() / _latencyCount.get();

                sb.append(Utils.NEW_LINE + Utils.padRight("Roundtrip: ", 25));
                sb.append(Utils.padLeft(latency / 1e3 + " ms ", 20));

                _label.setText(sb.toString());
            }
        });

        timer.start();

        // Window

        frame.getContentPane().add(panel);
        frame.setSize(700, 500);

        return frame;
    }

    private static final NumberFormat _format = new DecimalFormat("#,###");

    private static String writeInt(int value) {
        return _format.format(value);
    }
}
