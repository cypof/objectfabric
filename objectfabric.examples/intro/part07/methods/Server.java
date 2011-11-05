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

package part07.methods;

import org.junit.Assert;
import org.junit.Test;

import part07.methods.generated.MyClass;
import part07.methods.generated.ObjectModel;

import com.objectfabric.misc.Log;
import com.objectfabric.misc.PlatformAdapter;
import com.objectfabric.misc.SeparateClassLoader;
import com.objectfabric.transports.Server.Callback;
import com.objectfabric.transports.socket.SocketServer;
import com.objectfabric.transports.socket.SocketServer.Session;

/**
 * This example invokes a method on a generated object. If the object is local, the
 * behavior is the same as a regular method. If object is a replica of an object created
 * on a remote site, then the method is executed remotely like a RPC or service call.
 */
public class Server {

    public static SocketServer run(boolean isTest) throws Exception {
        ObjectModel.register();

        /*
         * Create an instance of MyClassImpl, which implements methods declared on
         * MyClass. MyClassImpl is written manually, whereas MyClass is generated.
         */
        final MyClass myObject = new MyClassImpl();

        /*
         * If a method is invoked on a local object, it is executed locally as a regular
         * method.
         */
        myObject.start("Local object");

        /*
         * Start a server to share object with a client.
         */
        SocketServer server = new SocketServer(4444);

        server.setCallback(new Callback<Session>() {

            public void onConnection(Session session) {
                System.out.println("Connection from " + session.getRemoteAddress());

                /*
                 * The instance that will be created on the client will be of the base
                 * type (MyClass). Clients do not need to have the implementation
                 * (MyClassImpl) in their classpath. When clients invoke methods, they
                 * will be executed on the server as the object has been created here.
                 */
                session.send(myObject);
            }

            public void onDisconnection(Session session, Exception e) {
                System.out.println("Disconnection from " + session.getRemoteAddress());
            }

            public void onReceived(Session session, Object object) {
                Assert.assertEquals("Done!", object);

                if (_client != null)
                    _client.interrupt();
            }
        });

        server.start();

        if (!isTest) {
            Log.write("Started a server on port " + server.getPort());
            Thread.sleep(Long.MAX_VALUE);
        }

        return server;
    }

    public static void main(String[] args) throws Exception {
        run(false);
    }

    // Testing purposes

    private static SeparateClassLoader _client;

    @Test
    public void asTest() throws Exception {
        SocketServer server = run(true);

        _client = new SeparateClassLoader(Client.class.getName());
        _client.start();
        _client.join();

        server.stop();
        PlatformAdapter.reset();
    }
}
