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

package images.client;

import images.client.generated.ImageInfo;
import images.client.generated.ImagesObjectModel;
import of4gwt.FieldListener;
import of4gwt.KeyListener;
import of4gwt.TSet;
import of4gwt.Transaction;
import of4gwt.transports.Client.Callback;
import of4gwt.transports.http.HTTPClient;

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
import com.google.gwt.user.client.ui.Button;
import com.google.gwt.user.client.ui.Image;
import com.google.gwt.user.client.ui.RootPanel;
import com.google.gwt.user.client.ui.Widget;

/**
 * This client is designed to connect to the Java version of the Images demo. First launch
 * /objectfabric.examples/samples/images/generator/Main.java, then this Web application.
 */
public class Main implements EntryPoint {

    private TSet<ImageInfo> images;

    private int imageIndex;

    private boolean _dragging;

    private int _left, _top;

    public void onModuleLoad() {
        ImagesObjectModel.register();

        final Button sendButton = new Button("New Image");
        sendButton.addStyleName("sendButton");
        sendButton.setWidth("200px");
        RootPanel.get("sendButtonContainer").add(sendButton);
        sendButton.setEnabled(false);

        // HTTPClient client = new HTTPClient("http://192.168.1.68:4444");
        HTTPClient client = new HTTPClient("http://localhost:4444");

        client.setCallback(new Callback() {

            @SuppressWarnings("unchecked")
            public void onReceived(Object object) {
                images = (TSet<ImageInfo>) object;

                /*
                 * Make servers's trunk the default so we do not need to pass it as
                 * argument every time we create and object or start a transaction.
                 */
                Transaction.setDefaultTrunk(images.getTrunk());

                for (ImageInfo image : images)
                    if (image != null)
                        addImage(image);

                /*
                 * Register a listener on the shared object to be notified when an
                 * ImageInfo is shared. When this happen, add an image to the UI.
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

                sendButton.setEnabled(true);
            }

            public void onDisconnected(Exception e) {
            }
        });

        client.connectAsync();

        sendButton.addClickHandler(new ClickHandler() {

            public void onClick(ClickEvent event) {
                /*
                 * Add an image info to the share.
                 */
                ImageInfo image = new ImageInfo();

                image.setUrl("image" + imageIndex++ + ".jpg");
                image.setLeft(Random.nextInt(100) + 50);
                image.setTop(Random.nextInt(100) + 50);

                if (imageIndex > 1)
                    imageIndex = 0;

                images.add(image);
            }
        });
    }

    private void addImage(final ImageInfo info) {
        final Image image = new Image(info.getUrl());

        RootPanel.get().add(image, info.getLeft(), info.getTop());

        // Listen to image info events

        info.addListener(new FieldListener() {

            public void onFieldChanged(int fieldIndex) {
                if (fieldIndex == ImageInfo.LEFT_INDEX || fieldIndex == ImageInfo.TOP_INDEX) {
                    RootPanel.get().setWidgetPosition(image, info.getLeft(), info.getTop());
                }
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

                    if (info.getLeft() != xAbs - _left || info.getTop() != yAbs - _top) {
                        info.setLeft(xAbs - _left);
                        info.setTop(yAbs - _top);
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
