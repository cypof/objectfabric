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

package examples.android;

import java.io.IOException;

import android.app.Activity;
import android.os.Bundle;
import android.view.MotionEvent;
import android.view.View;
import android.view.View.OnTouchListener;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.RelativeLayout;

import com.objectfabric.FieldListener;
import com.objectfabric.KeyListener;
import com.objectfabric.OF;
import com.objectfabric.TSet;
import com.objectfabric.Transaction;
import com.objectfabric.examples.android.R;
import com.objectfabric.misc.PlatformAdapter;
import com.objectfabric.transports.Client.Callback;
import com.objectfabric.transports.socket.SocketClient;

import examples.android.generated.ImageInfo;
import examples.android.generated.ImagesObjectModel;

/**
 * This demo is the Android version of the Images sample. This version can only connect as
 * a client, you need to launch a Java or .NET version first to be the server. Once
 * connected to a server, you can add images and drag them around, and their position will
 * sync between all instances.
 */
public class Activity1 extends Activity {

    /*
     * Connects to developer machine from emulator.
     */
    // private static final String HOST = "10.0.2.2";

    private static final String HOST = "24.6.134.122";

    private static final int PORT = 4444;

    private TSet<ImageInfo> images;

    private int imageIndex;

    @SuppressWarnings("unchecked")
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main);

        /*
         * Configure ObjectFabric for an Android application. This raises events and
         * listeners on the UI thread, and auto-commits its writes.
         */
        OF.setConfig(new AndroidConfig(findViewById(R.id.layout), true));

        /*
         * Register the object model specific to this application.
         */
        ImagesObjectModel.register();

        SocketClient client;

        try {
            client = new SocketClient(HOST, PORT);
        } catch (IOException ex) {
            throw new RuntimeException(ex);
        }

        client.setCallback(new Callback() {

            public void onReceived(Object object) {
                images = (TSet<ImageInfo>) object;

                /*
                 * Make servers's trunk the default so we do not need to pass it as
                 * argument every time we create and object or start a transaction.
                 */
                Transaction.setDefaultTrunk(images.getTrunk());

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

                for (ImageInfo image : images)
                    addImage(image);

                Button button = (Button) findViewById(R.id.button1);

                button.setOnClickListener(new View.OnClickListener() {

                    public void onClick(View v) {
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

            public void onDisconnected(Exception e) {
            }
        });

        client.connectAsync();
    }

    /**
     * Creates an image corresponding to an ImageInfo, and adds listeners to the image and
     * the ImageInfo to be notified of events on both sides.
     */
    private void addImage(final ImageInfo info) {
        Button button = (Button) findViewById(R.id.button1);
        final ImageView image = new ImageView(button.getContext());

        if (info.getUrl().equals("image0.jpg"))
            image.setImageDrawable(getResources().getDrawable(R.drawable.image0));
        else
            image.setImageDrawable(getResources().getDrawable(R.drawable.image1));

        final RelativeLayout layout = (RelativeLayout) findViewById(R.id.layout);
        RelativeLayout.LayoutParams params = new RelativeLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.leftMargin = info.getLeft();
        params.topMargin = info.getTop();
        image.setLayoutParams(params);
        layout.addView(image);

        // Listen to the ImageInfo changes, and move image to new position.

        info.addListener(new FieldListener() {

            public void onFieldChanged(int fieldIndex) {
                if (fieldIndex == ImageInfo.LEFT_INDEX || fieldIndex == ImageInfo.TOP_INDEX) {
                    RelativeLayout.LayoutParams params2 = new RelativeLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                    params2.leftMargin = info.getLeft();
                    params2.topMargin = info.getTop();
                    image.setLayoutParams(params2);
                    layout.invalidate();
                }
            }
        });

        // Listen to mouse and change ImageInfo when image is dragged.

        image.setOnTouchListener(new OnTouchListener() {

            private int _left, _top;

            public boolean onTouch(View v, MotionEvent event) {
                int x = (int) event.getX();
                int y = (int) event.getY();

                switch (event.getAction()) {
                    case MotionEvent.ACTION_DOWN: {
                        _left = x;
                        _top = y;
                        break;
                    }
                    case MotionEvent.ACTION_MOVE: {
                        RelativeLayout.LayoutParams p = (RelativeLayout.LayoutParams) image.getLayoutParams();
                        info.setLeft(x + p.leftMargin - _left);
                        info.setTop(y + p.topMargin - _top);
                        break;
                    }
                }

                return true;
            }
        });
    }
}
