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

package part09.images;

import java.awt.Button;
import java.awt.Dimension;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseMotionListener;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.IOException;

import javax.swing.BoxLayout;
import javax.swing.Icon;
import javax.swing.ImageIcon;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;

import part09.images.generated.ImageInfo;
import part09.images.generated.ImagesObjectModel;

import com.objectfabric.FieldListener;
import com.objectfabric.KeyListener;
import com.objectfabric.OF;
import com.objectfabric.SwingConfig;
import com.objectfabric.TSet;
import com.objectfabric.Transaction;
import com.objectfabric.misc.Debug;
import com.objectfabric.misc.Log;
import com.objectfabric.misc.PlatformAdapter;
import com.objectfabric.transports.Client;
import com.objectfabric.transports.Server;
import com.objectfabric.transports.http.HTTP;
import com.objectfabric.transports.socket.SocketClient;
import com.objectfabric.transports.socket.SocketConnection;
import com.objectfabric.transports.socket.SocketServer;

/**
 * This sample replicates image positions through a socket connection.
 */
public class Images {

    private static final String HOST = "localhost";

    private static final int PORT = 4444;

    private TSet<ImageInfo> images;

    private JPanel imagePanel;

    private int imageIndex;

    public static void main(String[] args) throws Exception {
        Images images = new Images();
        images.run();
    }

    @SuppressWarnings("unchecked")
    public void run() throws Exception {
        JFrame frame = buildWindow();
        frame.setVisible(true);

        /*
         * Configure ObjectFabric for a Swing application. This auto-commits changes made
         * by the UI thread, and raises events and listeners on it.
         */
        OF.setConfig(new SwingConfig());

        /*
         * Register the object model specific to this application.
         */
        ImagesObjectModel.register();

        /*
         * Try to start a socket server. Share a list of images. Repeat the generic type
         * as argument for the .NET version of this demo. .NET generics are typed at
         * runtime, so this is necessary to make sure the runtime will instantiate the
         * right generic TSet.
         */
        images = new TSet<ImageInfo>(ImageInfo.TYPE);
        final SocketServer server = new SocketServer(PORT);

        /*
         * This sample is multi-platform, allow connection over http.
         */
        server.addFilter(new HTTP());

        server.setCallback(new Server.Callback<SocketConnection>() {

            public void onConnection(SocketConnection session) {
                Log.write("Connection from " + session.getRemoteAddress());

                // Send shared image set to client
                session.send(images);
            }

            public void onDisconnection(SocketConnection session, Throwable t) {
                Log.write("Disconnection from " + session.getRemoteAddress());
            }

            public void onReceived(SocketConnection session, Object object) {
            }
        });

        try {
            server.start();
            Log.write("Started a socket server on port " + server.getPort());
        } catch (IOException e) {
            Log.write("Could not start a socket server on port " + server.getPort() + " (" + e.toString() + ")");
            images = null;
        }

        final Client client;

        if (images != null) {
            client = null;
            onReceivedImages();
        } else {
            /*
             * If server failed to start, this demo assumes there is one already running
             * on localhost. Connect to it and retrieve the list from the server.
             */
            client = new SocketClient(HOST, PORT);

            client.setCallback(new Client.Callback() {

                public void onReceived(Object object) {
                    images = (TSet<ImageInfo>) object;

                    /*
                     * Make servers's trunk the default so we do not need to pass it as
                     * argument every time we create and object or start a transaction.
                     */
                    Transaction.setDefaultTrunk(images.getTrunk());

                    onReceivedImages();
                }

                public void onDisconnected(Throwable t) {
                    Log.write("Disconnection");
                }
            });

            client.connectAsync();
            Log.write("Started a socket client to " + HOST + ":" + PORT);
        }

        frame.addWindowListener(new WindowAdapter() {

            @Override
            public void windowClosing(WindowEvent e) {
                /*
                 * This block is not required, testing purposes only.
                 */
                if (Debug.TESTING) {
                    OF.reset();

                    if (server.isStarted())
                        server.stop();

                    if (client != null)
                        client.close();

                    PlatformAdapter.shutdown();
                }

                System.exit(0);
            }
        });
    }

    private JFrame buildWindow() {
        JFrame frame = new JFrame("Images Sample (Java & Swing)");
        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));

        // A panel for images

        imagePanel = new JPanel();
        imagePanel.setLayout(null);
        imagePanel.setPreferredSize(new Dimension(640, 400));
        panel.add(imagePanel);

        // Window

        frame.getContentPane().add(panel);
        frame.setSize(640, 480);

        return frame;
    }

    private void onReceivedImages() {
        /*
         * Register a listener on the shared object to be notified when an ImageInfo is
         * shared. When this happen, add an image to the UI.
         */
        images.addListener(new KeyListener<ImageInfo>() {

            public void onPut(ImageInfo key) {
                addImage(key);
            }

            public void onRemoved(ImageInfo key) {
            }

            public void onCleared() {
            }
        });

        // Some images might already be shared, show them

        /*
         * A transaction is required to ensure the iterator runs in a stable view of the
         * collection. (SwingConfig makes it automatic on the UI thread, but this is still
         * running on the startup thread).
         */
        Transaction transaction = Transaction.start();

        for (ImageInfo image : images)
            addImage(image);

        transaction.abort();

        // Create the new image button

        Button button = new Button("New Image");

        imagePanel.add(button);

        button.setLocation(30, 80);
        button.setSize(new Dimension(77, 23));

        button.addActionListener(new ActionListener() {

            /**
             * Add an ImageInfo to the share when button is clicked.
             */
            public void actionPerformed(ActionEvent e) {
                ImageInfo image = new ImageInfo();

                image.setUrl("image" + imageIndex++ + ".jpg");
                image.setLeft(PlatformAdapter.getRandomInt(100) + 50);
                image.setTop(PlatformAdapter.getRandomInt(100) + 100);

                if (imageIndex > 1)
                    imageIndex = 0;

                images.add(image);
            }
        });
    }

    /**
     * Creates an image corresponding to an ImageInfo, and adds listeners to the image and
     * the ImageInfo to be notified of events on both sides.
     */
    private void addImage(final ImageInfo info) {
        String url = info.getUrl();
        System.out.println("Loading image " + url);
        Icon icon = new ImageIcon(getClass().getResource(url));
        final JLabel label1 = new JLabel(icon);
        imagePanel.add(label1);
        imagePanel.setComponentZOrder(label1, 0);
        label1.setLocation(info.getLeft(), info.getTop());
        label1.setSize(icon.getIconWidth(), icon.getIconHeight());

        // Listen to the ImageInfo changes, and move image to new position.

        info.addListener(new FieldListener() {

            public void onFieldChanged(int fieldIndex) {
                if (fieldIndex == ImageInfo.LEFT_INDEX || fieldIndex == ImageInfo.TOP_INDEX)
                    label1.setLocation(info.getLeft(), info.getTop());
            }
        });

        // Listen to mouse and change ImageInfo when image is dragged.

        Listener listener = new Listener(info);
        label1.addMouseListener(listener);
        label1.addMouseMotionListener(listener);
    }

    /**
     * Listens mouse events. If an image is dragged, update the fields of the
     * corresponding ImageInfo.
     */
    private class Listener extends MouseAdapter implements MouseMotionListener {

        private final ImageInfo _info;

        private int _left, _top;

        private boolean _dragging;

        public Listener(ImageInfo info) {
            _info = info;
        }

        @Override
        public void mousePressed(MouseEvent e) {
            super.mousePressed(e);
            _left = e.getX();
            _top = e.getY();
            _dragging = true;
        }

        @Override
        public void mouseDragged(MouseEvent e) {
            if (_dragging) {
                _info.setLeft(e.getX() + ((JLabel) e.getSource()).getX() - _left);
                _info.setTop(e.getY() + ((JLabel) e.getSource()).getY() - _top);
            }
        }

        @Override
        public void mouseMoved(MouseEvent e) {
        }

        @Override
        public void mouseReleased(MouseEvent e) {
            super.mouseReleased(e);
            _dragging = false;
        }
    }
}
