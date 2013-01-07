/**
 * This file is part of ObjectFabric (http://objectfabric.org).
 *
 * ObjectFabric is licensed under the Apache License, Version 2.0, the terms
 * of which may be found at http://www.apache.org/licenses/LICENSE-2.0.html.
 * 
 * Copyright ObjectFabric Inc.
 * 
 * This file is provided AS IS with NO WARRANTY OF ANY KIND, INCLUDING THE
 * WARRANTY OF DESIGN, MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE.
 */

package sample_images;

import java.awt.Button;
import java.awt.Dimension;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.util.Random;

import javax.swing.BoxLayout;
import javax.swing.Icon;
import javax.swing.ImageIcon;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.Timer;
import javax.swing.WindowConstants;

import org.objectfabric.AsyncCallback;
import org.objectfabric.IndexListener;
import org.objectfabric.KeyListener;
import org.objectfabric.Netty;
import org.objectfabric.Remote;
import org.objectfabric.Resource;
import org.objectfabric.SQLite;
import org.objectfabric.SwingWorkspace;
import org.objectfabric.TArrayDouble;
import org.objectfabric.TSet;
import org.objectfabric.Workspace;

/**
 * This sample replicates image positions.
 */
public class Images {

    private TSet<TArrayDouble> positions;

    private JPanel imagePanel;

    // Sync status

    private JLabel disconnected, ongoing, complete, label;

    public static void main(String[] args) throws Exception {
        Images images = new Images();
        images.run(args);
    }

    @SuppressWarnings("unchecked")
    public void run(String[] args) throws Exception {
        final JFrame frame = buildWindow();

        // Configure a workspace for a Swing application.
        final Workspace workspace = new SwingWorkspace();

        if (args.length > 0) {
            // Add a file based cache to demo off-line support
            workspace.addCache(new SQLite("temp/" + args[0], true));
        }

        workspace.addURIHandler(new Netty());

        workspace.openAsync("ws://localhost:8888/images", new AsyncCallback<Resource>() {

            @Override
            public void onSuccess(Resource result) {
                positions = (TSet<TArrayDouble>) result.get();
                onReceivedImages();
            }

            @Override
            public void onFailure(Exception e) {
            }
        });

        frame.addWindowListener(new WindowAdapter() {

            @Override
            public void windowClosing(WindowEvent e) {
                workspace.close();
            }
        });

        Timer timer = new Timer(100, new ActionListener() {

            public void actionPerformed(ActionEvent evt) {
                disconnected.setVisible(false);
                ongoing.setVisible(false);
                complete.setVisible(false);

                if (positions != null) {
                    switch (((Remote) positions.resource().origin()).status()) {
                        case DISCONNECTED:
                            disconnected.setVisible(true);
                            label.setText("Disconnected");
                            break;
                        case CONNECTING:
                            ongoing.setVisible(true);
                            label.setText("Connecting...");
                            break;
                        case WAITING_RETRY:
                            disconnected.setVisible(true);
                            label.setText("Waiting retry...");
                            break;
                        case SYNCHRONIZING:
                            ongoing.setVisible(true);
                            label.setText("Synchronizing...");
                            break;
                        case UP_TO_DATE:
                            complete.setVisible(true);
                            label.setText("Up to date");
                            break;
                    }
                }
            }
        });

        timer.start();
    }

    private JFrame buildWindow() {
        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));

        // A panel for images

        imagePanel = new JPanel();
        imagePanel.setLayout(null);
        imagePanel.setPreferredSize(new Dimension(640, 400));
        panel.add(imagePanel);

        // Sync status

        Icon icon1 = new ImageIcon(getClass().getResource("sync-disconnected-24.png"));
        Icon icon2 = new ImageIcon(getClass().getResource("sync-ongoing-24.png"));
        Icon icon3 = new ImageIcon(getClass().getResource("sync-complete-24.png"));

        disconnected = new JLabel(icon1);
        imagePanel.add(disconnected);
        disconnected.setSize(icon1.getIconWidth(), icon1.getIconHeight());
        disconnected.setLocation(10, 10);
        disconnected.setVisible(false);

        ongoing = new JLabel(icon2);
        imagePanel.add(ongoing);
        ongoing.setSize(icon2.getIconWidth(), icon2.getIconHeight());
        ongoing.setLocation(10, 10);
        ongoing.setVisible(false);

        complete = new JLabel(icon3);
        imagePanel.add(complete);
        complete.setSize(icon3.getIconWidth(), icon3.getIconHeight());
        complete.setLocation(10, 10);
        complete.setVisible(false);

        label = new JLabel();
        imagePanel.add(label);
        label.setLocation(50, 10);
        label.setSize(120, 24);

        JFrame frame = new JFrame("Images Sample (Java & Swing)");
        frame.getContentPane().add(panel);
        frame.setSize(640, 480);
        frame.setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
        frame.setVisible(true);
        return frame;
    }

    private void onReceivedImages() {
        /*
         * Register a listener on the shared object to be notified when an ImageInfo is
         * shared. When this happen, add an image to the UI.
         */
        positions.addListener(new KeyListener<TArrayDouble>() {

            public void onPut(TArrayDouble key) {
                addImage(key);
            }

            public void onRemove(TArrayDouble key) {
            }

            public void onClear() {
            }
        });

        /*
         * Some images might already be shared, show them. Iterators on transactional
         * collections provide a stable view of the collection.
         */
        positions.atomicRead(new Runnable() {

            public void run() {
                for (TArrayDouble position : positions)
                    addImage(position);
            }
        });

        // Create the new image button

        Button button = new Button("New Image");

        imagePanel.add(button);

        button.setLocation(10, 50);
        button.setSize(new Dimension(77, 23));

        button.addActionListener(new ActionListener() {

            /**
             * Add an ImageInfo to the share when button is clicked.
             */
            public void actionPerformed(ActionEvent e) {
                TArrayDouble position = new TArrayDouble(positions.resource(), 2);
                Random rand = new Random();
                position.set(0, rand.nextInt(100) + 50);
                position.set(1, rand.nextInt(100) + 50);
                positions.add(position);
            }
        });
    }

    /**
     * Creates an image corresponding to an ImageInfo, and adds listeners to the image and
     * the ImageInfo to be notified of events on both sides.
     */
    private void addImage(final TArrayDouble position) {
        System.out.println("Loading image");
        Icon icon = new ImageIcon(getClass().getResource("image.png"));
        final JLabel label1 = new JLabel(icon);
        imagePanel.add(label1);
        imagePanel.setComponentZOrder(label1, 0);
        label1.setLocation((int) position.get(0), (int) position.get(1));
        label1.setSize(icon.getIconWidth(), icon.getIconHeight());

        // Listen to the ImageInfo changes, and move image to new position.

        position.addListener(new IndexListener() {

            public void onSet(int field) {
                label1.setLocation((int) position.get(0), (int) position.get(1));
            }
        });

        // Listen to mouse and change ImageInfo when image is dragged.

        Listener listener = new Listener(position);
        label1.addMouseListener(listener);
        label1.addMouseMotionListener(listener);
    }

    /**
     * Listens mouse events. If an image is dragged, update the fields of the
     * corresponding ImageInfo.
     */
    private class Listener extends MouseAdapter {

        private final TArrayDouble position;

        private int left, top;

        private boolean dragging;

        public Listener(TArrayDouble position) {
            this.position = position;
        }

        @Override
        public void mousePressed(MouseEvent e) {
            super.mousePressed(e);
            left = e.getX();
            top = e.getY();
            dragging = true;
        }

        @Override
        public void mouseDragged(MouseEvent e) {
            if (dragging) {
                position.set(0, e.getX() + ((JLabel) e.getSource()).getX() - left);
                position.set(1, e.getY() + ((JLabel) e.getSource()).getY() - top);
            }
        }

        @Override
        public void mouseMoved(MouseEvent e) {
        }

        @Override
        public void mouseReleased(MouseEvent e) {
            super.mouseReleased(e);
            dragging = false;
        }
    }
}
