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

package objectmodel.client;

import objectmodel.client.generated.Car;
import objectmodel.client.generated.Driver;
import objectmodel.client.generated.MyObjectModel;
import of4gwt.LazyMap;
import of4gwt.misc.Debug;
import of4gwt.misc.Log;
import of4gwt.transports.Client.Callback;
import of4gwt.transports.http.HTTPClient;

import com.google.gwt.core.client.EntryPoint;
import com.google.gwt.user.client.rpc.AsyncCallback;
import com.google.gwt.user.client.ui.HTML;
import com.google.gwt.user.client.ui.RootPanel;

/**
 * Connects to the same server as the Java version of the Hello World example (Check
 * objectfabric.examples/part01.helloworld).
 */
public class Main implements EntryPoint {

    private Car car;

    @SuppressWarnings("unchecked")
    public void onModuleLoad() {
        /*
         * Redirect log to web page.
         */
        Log.add(new Log() {

            @Override
            protected void onWrite(String message) {
                RootPanel.get("log").add(new HTML(message));
            }
        });

        /*
         * Like Java applications, GWT object models must be registered.
         */
        MyObjectModel.register();

        final HTTPClient client = new HTTPClient("http://localhost:8080");

        client.setCallback(new Callback() {

            public void onReceived(Object object) {
                Log.write("Received: " + object);

                if (object instanceof String)
                    Debug.assertAlways("Blah".equals(object));

                if (object instanceof Integer)
                    Debug.assertAlways(42 == (Integer) object);

                if (object instanceof Car) {
                    car = (Car) object;
                    Debug.assertAlways("Joe".equals(car.getDriver().getName()));
                }

                if (object instanceof Driver) {
                    Driver friend = (Driver) object;
                    Debug.assertAlways(5 == car.getSettings().get(friend).getSeatHeight());
                }

                if (object instanceof LazyMap) {
                    final LazyMap<String, Car> map = (LazyMap) object;

                    /*
                     * Fetched only entry with key A.
                     */
                    map.getAsync("A", new AsyncCallback<Car>() {

                        public void onSuccess(final Car a) {
                            Debug.assertAlways("Brand A".equals(a.getBrand()));

                            Log.write("Done!");
                            client.send("Done!");
                        }

                        public void onFailure(Throwable caught) {
                        }
                    });
                }
            }

            public void onDisconnected(Exception e) {
            }
        });

        Log.write("Connecting...");
        client.connectAsync();
    }
}
