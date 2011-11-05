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

package chat;

import chat.generated.ObjectModel;
import chat.generated.User;

import com.objectfabric.FieldListener;
import com.objectfabric.KeyListener;
import com.objectfabric.ListListener;
import com.objectfabric.TList;
import com.objectfabric.TMap;
import com.objectfabric.Transaction;
import com.objectfabric.misc.PlatformConsole;
import com.objectfabric.misc.PlatformThread;
import com.objectfabric.transports.Client.Callback;
import com.objectfabric.transports.socket.SocketClient;

public class Client {

    private TList<String> messageList;

    private TMap<String, User> users;

    public void run() throws Exception {
        ObjectModel.register();

        /*
         * Connect to the server through a socket connection.
         */
        SocketClient client = new SocketClient("localhost", 4444);

        client.setCallback(new Callback() {

            /*
             * Update objects received from server, changes will be replicated and invoke
             * listeners on the server.
             */
            @SuppressWarnings("unchecked")
            public void onReceived(Object object) {
                users = (TMap<String, User>) object;

                Transaction.setDefaultTrunk(users.getTrunk());
                messageList = new TList<String>();

                Transaction.run(new Runnable() {

                    public void run() {
                        System.out.println("Users:");
                        for (String s : users.keySet()) {
                            System.out.println(s);
                        }
                    }
                });

                messageList.addListener(new ListListener() {

                    public void onRemoved(int index) {
                    }

                    public void onCleared() {
                    }

                    public void onAdded(int index) {
                        // print all users again, since there is a new user;
                        Transaction.run(new Runnable() {

                            public void run() {
                                System.out.println("my message: ");

                                for (String s : messageList) {
                                    System.out.println(s);
                                }
                            }
                        });

                    }
                });

                users.addListener(new KeyListener<String>() {

                    public void onRemoved(String key) {
                        // TODO Auto-generated method stub

                    }

                    public void onPut(String key) {
                        // print all users again, since there is a new user
                        System.out.println("Users:");
                        Transaction.run(new Runnable() {

                            public void run() {
                                System.out.println("Users:");

                                for (String s : users.keySet()) {
                                    System.out.println(s);
                                }
                            }
                        });
                    }

                    public void onCleared() {
                        // TODO Auto-generated method stub

                    }
                });

            }

            public void onDisconnected(Exception e) {
            }
        });

        client.connect();

        // Wait for another user
        while (users == null) {
            PlatformThread.sleep(0);
        }

        messageList = new TList<String>();

        System.out.println("User Name: ");
        String username = PlatformConsole.readLine();
        final User me = new User();

        me.setName(username);
        users.put(username, me);
        me.setConversations(messageList);
        me.addListener(new FieldListener() {

            public void onFieldChanged(int fieldIndex) {
                System.out.println("changed!!");

                System.out.print(messageList == me.getConversations());

                me.getConversations().addListener(new ListListener() {

                    public void onRemoved(int index) {
                    }

                    public void onCleared() {
                    }

                    public void onAdded(int index) {
                        // print all users again, since there is a new user;
                        Transaction.run(new Runnable() {

                            public void run() {
                                System.out.println("> ");

                                for (String s : me.getConversations()) {
                                    System.out.println(s);
                                }
                            }
                        });

                    }
                });

            }
        });

        User otherUser;

        // Connect to another user
        if (users.size() > 1) {
            System.out.println("Connect to: ");
            String otherUsername = PlatformConsole.readLine();

            otherUser = users.get(otherUsername);
            otherUser.setConversations(messageList);
        } else {
            System.out.println("no one else online");
        }

        while (true) {
            // try {
            // System.out.println("Enter the line :- ");
            // System.out.println(object.getText());
            // String s = br.readLine();
            System.out.println("me: ");
            String s = PlatformConsole.readLine();
            messageList.add(s);
            // object.setText(object.getText() + s);
            // System.out.println("You have Entered :- " + s);

            // } catch (IOException e) {
            // e.printStackTrace();
            // }
        }
    }

    public static void main(String[] args) throws Exception {
        Client test = new Client();
        test.run();
    }

    /**
     * Creates a separate ClassLoader to read back data from the store. The class loader
     * separation ensures data cannot be read from memory and does reflect the file
     * content.
     */
    // private void executeInSimulatedProcess(Class class_) {
    // store.close();
    //
    // // Run read
    // SeparateClassLoader test = new SeparateClassLoader(class_.getName());
    // test.run();
    // }
}
