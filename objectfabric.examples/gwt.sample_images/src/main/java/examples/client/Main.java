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

package examples.client;

import org.objectfabric.AbstractKeyListener;
import org.objectfabric.AsyncCallback;
import org.objectfabric.GWTWorkspace;
import org.objectfabric.IndexListener;
import org.objectfabric.IndexedDB;
import org.objectfabric.Remote;
import org.objectfabric.Resource;
import org.objectfabric.TArrayDouble;
import org.objectfabric.TSet;
import org.objectfabric.WebSocketURIHandler;
import org.objectfabric.Workspace;

import com.google.gwt.core.client.EntryPoint;
import com.google.gwt.event.dom.client.ClickEvent;
import com.google.gwt.event.dom.client.ClickHandler;
import com.google.gwt.event.dom.client.MouseDownEvent;
import com.google.gwt.event.dom.client.MouseDownHandler;
import com.google.gwt.event.dom.client.MouseMoveEvent;
import com.google.gwt.event.dom.client.MouseMoveHandler;
import com.google.gwt.event.dom.client.MouseUpEvent;
import com.google.gwt.event.dom.client.MouseUpHandler;
import com.google.gwt.user.client.DOM;
import com.google.gwt.user.client.Random;
import com.google.gwt.user.client.Timer;
import com.google.gwt.user.client.ui.Button;
import com.google.gwt.user.client.ui.Image;
import com.google.gwt.user.client.ui.Label;
import com.google.gwt.user.client.ui.RootPanel;
import com.google.gwt.user.client.ui.Widget;

/**
 * This client is designed to connect to the Java version of the Images demo. First launch
 * /objectfabric.examples/samples/images/generator/Main.java, then this Web application.
 */
@SuppressWarnings("unchecked")
public class Main implements EntryPoint {

    private TSet<TArrayDouble> positions;

    private Button button;

    private boolean _dragging;

    private int _left, _top;

    public void onModuleLoad() {
        Workspace workspace = new GWTWorkspace();

        if (WebSocketURIHandler.isSupported())
            workspace.addURIHandler(new WebSocketURIHandler());
        // else // TODO
        // workspace.addURIHandler(new CometURIHandler());

        if (IndexedDB.isSupported())
            workspace.addCache(new IndexedDB());

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

        button = new Button("New Image");
        button.setWidth("200px");
        button.setEnabled(false);
        RootPanel.get().add(button, 20, 100);

        button.addClickHandler(new ClickHandler() {

            public void onClick(ClickEvent event) {
                TArrayDouble position = new TArrayDouble(positions.resource(), 2);
                position.set(0, Random.nextInt(100) + 50);
                position.set(1, Random.nextInt(100) + 50);
                positions.add(position);
            }
        });
    }

    private void onReceivedImages() {
        button.setEnabled(true);

        /*
         * Register a listener on the shared object to be notified when an ImageInfo is
         * shared. When this happen, add an image to the UI.
         */
        positions.addListener(new AbstractKeyListener<TArrayDouble>() {

            @Override
            public void onPut(TArrayDouble key) {
                addImage(key);
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

        final Image disconnected = new Image("sync-disconnected-24.png");
        final Image ongoing = new Image("sync-24.gif");
        final Image complete = new Image("sync-complete-24.png");
        final Label label = new Label();
        RootPanel.get().add(disconnected, 20, 130);
        RootPanel.get().add(ongoing, 20, 130);
        RootPanel.get().add(complete, 20, 130);
        RootPanel.get().add(label, 50, 130);

        new Timer() {

            @Override
            public void run() {
                disconnected.setVisible(false);
                ongoing.setVisible(false);
                complete.setVisible(false);

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
        }.scheduleRepeating(100);
    }

    private void addImage(final TArrayDouble position) {
        final Image image = new Image("image.png");
        RootPanel.get().add(image, (int) position.get(0), (int) position.get(1));

        // Listen to image info events

        position.addListener(new IndexListener() {

            public void onSet(int i) {
                RootPanel.get().setWidgetPosition(image, (int) position.get(0), (int) position.get(1));
            }
        });

        // Listen to image mouse events

        image.addMouseDownHandler(new MouseDownHandler() {

            public void onMouseDown(MouseDownEvent event) {
                DOM.setCapture(((Widget) event.getSource()).getElement());
                _left = event.getX();
                _top = event.getY();
                _dragging = true;

                // Prevent browser from starting a drag and drop (E.g. Chrome)
                event.preventDefault();
            }
        });

        image.addMouseMoveHandler(new MouseMoveHandler() {

            public void onMouseMove(MouseMoveEvent event) {
                if (_dragging) {
                    // convert from local to global coordinates
                    int xAbs = event.getX() + ((Widget) event.getSource()).getAbsoluteLeft();
                    int yAbs = event.getY() + ((Widget) event.getSource()).getAbsoluteTop();

                    if ((int) position.get(0) != xAbs - _left || (int) position.get(1) != yAbs - _top) {
                        position.set(0, xAbs - _left);
                        position.set(1, yAbs - _top);
                    }
                }
            }
        });

        image.addMouseUpHandler(new MouseUpHandler() {

            public void onMouseUp(MouseUpEvent event) {
                DOM.releaseCapture(((Widget) event.getSource()).getElement());
                _dragging = false;
            }
        });
    }
}
