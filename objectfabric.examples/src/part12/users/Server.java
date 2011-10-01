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

package part12.users;

import part12.users.generated.ObjectModel;
import part12.users.generated.PrivateData;
import part12.users.generated.PublicData;

import com.objectfabric.LazyMap;
import com.objectfabric.TList;
import com.objectfabric.TObject;
import com.objectfabric.Validator;
import com.objectfabric.misc.PlatformAdapter;
import com.objectfabric.misc.PlatformConsole;
import com.objectfabric.misc.SeparateClassLoader;
import com.objectfabric.security.shiro.ShiroObjectModel;
import com.objectfabric.security.shiro.ShiroStore;
import com.objectfabric.security.shiro.UsernamePasswordSession;
import com.objectfabric.security.shiro.UsernamePasswordUserManager;
import com.objectfabric.transports.Server.Callback;
import com.objectfabric.transports.socket.SocketServer;

/**
 * This example implements simple user management, authentication and authorization.
 * Authentication is done using Apache Shiro. Authorization is done through a callback
 * from ObjectFabric (Validator) which allows user code to reject client transactions and
 * method calls before they are executed (Validator has separate callbacks for an object
 * read, object write, and method execution).
 * <nl>
 * This sample uses two data maps, a public and a private one. Code is used in the
 * validator to make sure private data is only accessible (Reads) to its owner while
 * public data can be seen by anyone. Public data can also only be modified by its owner
 * (Writes). This is just one example of securing an application. Shiro features other
 * forms of authentication, and user code can implement other forms of authorization,
 * using the Validator as a basic block.
 */
public class Server {

    public static SocketServer run(boolean waitForEnter) throws Exception {
        ObjectModel.register();
        ShiroObjectModel.register();

        // In memory store, this can easily be persisted using one of OF stores
        ShiroStore shiroStore = new ShiroStore();

        final UsernamePasswordUserManager userManager = new UsernamePasswordUserManager(shiroStore);

        userManager.createAccount("user1", "password1", null);
        userManager.createAccount("user2", "password2", null);

        final LazyMap<String, PublicData> publicData = new LazyMap<String, PublicData>();
        final LazyMap<String, PrivateData> privateData = new LazyMap<String, PrivateData>();

        publicData.put("user1", new PublicData());
        publicData.put("user2", new PublicData());

        privateData.put("user1", new PrivateData());
        privateData.put("user2", new PrivateData());

        SocketServer server = new SocketServer(8080) {

            /*
             * Create a custom session which contains a reference to a shiro session.
             */
            @Override
            protected Session createSession() {
                return new SessionWithShiro(this, userManager);
            }
        };

        /*
         * Send the shiro session when a client connects.
         */
        server.setCallback(new Callback<SessionWithShiro>() {

            @SuppressWarnings("unchecked")
            public void onConnection(SessionWithShiro session) {
                TList list = new TList();
                list.add(session.getUsernamePasswordSession());
                list.add(publicData);
                list.add(privateData);
                session.send(list);
            }

            public void onDisconnection(SessionWithShiro session, Exception e) {
            }

            public void onReceived(SessionWithShiro session, Object object) {
            }
        });

        /*
         * Register a validator which makes sure private data is only accessible to its
         * owner.
         */
        server.setValidator(new Validator<SessionWithShiro>() {

            public void validateRead(SessionWithShiro session, TObject object) {
                UsernamePasswordSession shiro = session.getUsernamePasswordSession();

                if (object instanceof PrivateData) {
                    String user = shiro.getUsername();

                    if (object != privateData.get(user)) {
                        String from = session.getRemoteAddress().toString();
                        throw new SecurityException(user + " is not allowed to read this object (" + from + ").");
                    }
                }
            }

            public void validateWrite(SessionWithShiro session, TObject object) {
                UsernamePasswordSession shiro = session.getUsernamePasswordSession();

                if (object instanceof PublicData || object instanceof PrivateData) {
                    String user = shiro.getUsername();

                    if (object != privateData.get(user) && object != publicData.get(user)) {
                        String from = session.getRemoteAddress().toString();
                        throw new SecurityException(user + " is not allowed to write to this object (" + from + ").");
                    }
                }
            }

            public void validateMethodCall(SessionWithShiro session, TObject object, String methodName) {
            }
        });

        server.start();

        if (waitForEnter) {
            System.out.println("Started socket server, press enter to exit.");
            PlatformConsole.readLine();
        }

        return server;
    }

    public static void main(String[] args) throws Exception {
        run(true);
    }

    // TODO Broken
    //@Test
    public void asTest() throws Exception {
        int i;
        SocketServer server = run(false);

        SeparateClassLoader client = new SeparateClassLoader(Client.class.getName());
        client.start();
        client.join();

        server.stop();
        PlatformAdapter.reset();
    }

    private static final class SessionWithShiro extends SocketServer.Session {

        private final UsernamePasswordSession _session;

        public SessionWithShiro(SocketServer server, UsernamePasswordUserManager userManager) {
            super(server);

            _session = new UsernamePasswordSession(userManager);
        }

        public UsernamePasswordSession getUsernamePasswordSession() {
            return _session;
        }
    }
}
