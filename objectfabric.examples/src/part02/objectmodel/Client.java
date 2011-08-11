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

package part02.objectmodel;

import java.io.IOException;

import junit.framework.Assert;
import part02.objectmodel.generated.Car;
import part02.objectmodel.generated.Driver;
import part02.objectmodel.generated.MyObjectModel;

import com.objectfabric.LazyMap;
import com.objectfabric.misc.PlatformConsole;
import com.objectfabric.misc.SeparateClassLoader;
import com.objectfabric.transports.Client.Callback;
import com.objectfabric.transports.socket.SocketClient;

@SuppressWarnings("unchecked")
public class Client {

    private static Car car;

    public static void main(String[] args) throws IOException {
        /*
         * Like on the server, your generated object model must be registered on the
         * client so ObjectFabric can deserialize your objects.
         */
        MyObjectModel.register();

        final SocketClient client = new SocketClient("localhost", 8080);

        client.setCallback(new Callback() {

            public void onReceived(Object object) {
                System.out.println("Received: " + object);

                if (object instanceof String)
                    Assert.assertEquals("Blah", object);

                if (object instanceof Integer)
                    Assert.assertEquals(42, object);

                if (object instanceof Car) {
                    car = (Car) object;
                    Assert.assertEquals("Joe", car.getDriver().getName());
                }

                if (object instanceof Driver) {
                    Driver friend = (Driver) object;
                    Assert.assertEquals(5, car.getSettings().get(friend).getSeatHeight());
                }

                if (object instanceof LazyMap) {
                    LazyMap<String, Car> map = (LazyMap) object;
                    // Fetched only entry with key A
                    Car a = map.get("A");
                    Assert.assertEquals("Brand A", a.getBrand());
                    // Instances are unique, every call must return the same reference
                    Assert.assertTrue(map.get("A") == a);

                    client.send("Done!");
                }
            }

            public void onDisconnected(Throwable t) {
            }
        });

        client.connect();

        if (SeparateClassLoader.isTest()) // Testing purposes
            SeparateClassLoader.waitForInterruption(client);
        else {
            System.out.println("Connected, press enter to exit.");
            PlatformConsole.readLine();
        }
    }
}
