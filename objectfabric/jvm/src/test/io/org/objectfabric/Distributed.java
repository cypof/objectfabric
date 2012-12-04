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

package org.objectfabric;

import java.util.ArrayList;
import java.util.Map.Entry;

import org.junit.Assert;
import org.junit.Ignore;
import org.junit.Test;
import org.objectfabric.CloseCounter.Callback;
import org.objectfabric.generated.Limit32;
import org.objectfabric.generated.LimitsObjectModel;

@Ignore
@SuppressWarnings("unchecked")
public class Distributed {

    static final int FLAG_SERVER_PERSIST = 1 << 2;

    static final int FLAG_CLIENT_PERSIST = 1 << 3;

    static {
        JVMPlatform.loadClass();
    }

    final int[] _last = new int[3];

    final PlatformConcurrentMap<SeparateCL, VMConnection> _clients = new PlatformConcurrentMap<SeparateCL, VMConnection>();

    @Test
    public void run1() throws Exception {
        run(1, 1, 0);
    }

    TObject create(int test, Resource resource, final int clients, int flags) {
        if (test == 1) {
            final Limit32 object = new Limit32(resource);

            if (clients == 1) {
                object.addListener(new IndexListener() {

                    public void onSet(int field) {
                        int current = (Integer) object.getField(field);

                        if (clients == 1 && (field == 0 || field == 1))
                            Assert.assertTrue(current >= _last[field]);

                        _last[field] = current;
                    }
                });
            }

            return object;
        }

        All all = new All(resource, clients, flags);
        return all.Root;
    }

    boolean step(int test, Separate separate, TObject object, final int flags) {
        if (test == 1) {
            final Limit32 limit = (Limit32) object;

            if (separate.getProgress() == DistributedClient.GO) {
                object.atomic(new Runnable() {

                    public void run() {
                        if (limit.int0() < DistributedClient.LIMIT)
                            limit.int0(limit.int0() + 1);

                        for (int i = 3; i < Limit32.FIELD_COUNT; i++)
                            limit.setField(i, Platform.get().randomInt());
                    }
                });

                object.atomic(new Runnable() {

                    public void run() {
                        if (limit.int2() < DistributedClient.LIMIT)
                            limit.int2(limit.int2() + 1);
                    }
                });
            }

            return limit.int0() == DistributedClient.LIMIT && limit.int2() == DistributedClient.LIMIT;
        }

        All.check((TMap) object, flags);
        return true;
    }

    void end(int test, TObject object) {
        if (test == 1) {
            final Limit32 limit = (Limit32) object;
            Assert.assertTrue(limit.int0() == DistributedClient.LIMIT && limit.int1() == DistributedClient.LIMIT && limit.int2() == DistributedClient.LIMIT);
            Assert.assertTrue(_last[0] == DistributedClient.LIMIT && _last[1] == DistributedClient.LIMIT && _last[2] == DistributedClient.LIMIT);

            for (int i = 0; i < Limit32.FIELD_COUNT; i++)
                Assert.assertEquals(limit.getField(i), _last[i]);

            ArrayList<int[]> list = new ArrayList<int[]>();

            for (Entry<SeparateCL, VMConnection> entry : _clients.entrySet()) {
                VMConnection connection = entry.getValue();
                String c = DistributedClient.class.getName();
                list.add((int[]) connection.getClassLoader().invoke(c, "getEndValues"));
            }

            for (int[] values : list)
                for (int i = 0; i < values.length; i++)
                    Assert.assertEquals(values[i], _last[i]);
        } else {
        }
    }

    void run(int test, int clients, int flags) throws Exception {
        if (Debug.ENABLED)
            Helper.instance().ProcessName = "Server";

        writeStart(clients, flags);
        LimitsObjectModel.register();

        final Workspace workspace = Platform.newTestWorkspace();
        Memory memory = new Memory(false);
        workspace.addURIHandler(memory);
        Resource resource = workspace.open("/object");
        TObject object = create(test, resource, clients, flags);
        resource.set(object);
        workspace.flush();

        Server server = Platform.get().newTestServer();
        server.addURIHandler(memory);
        connect(server, clients, flags);

        // Wait for Remote actors to create connections
        Platform.get().sleep(10);

        byte[] buffer = new byte[5000];

        for (;;) {
            boolean idle = true;
            boolean done = true;

            for (Entry<SeparateCL, VMConnection> entry : _clients.entrySet()) {
                VMConnection connection = entry.getValue();
                idle &= connection.serverTransfer(buffer);
                connection.getClassLoader().invoke(DistributedClient.class.getName(), "step");
                idle &= step(test, entry.getKey(), object, flags);
                done &= entry.getKey().getProgress() == DistributedClient.DONE;
            }

            if (done)
                break;

            if (idle)
                Platform.get().sleep(1);
        }

        workspace.flushNotifications();
        end(test, object);

        for (Separate separate : _clients.keySet())
            separate.setProgress(DistributedClient.CLOSING);

        for (Separate separate : _clients.keySet())
            separate.waitForEnd();

        while (_clients.size() > 0)
            Platform.get().sleep(1);

        workspace.close();
    }

    void connect(Server server, int clients, int flags) {
        for (int i = 0; i < clients; i++) {
            final SeparateCL client = new SeparateCL(DistributedClient.class.getName(), true);
            client.setArgTypes(int.class, int.class, int.class);
            client.setArgs(clients, i, flags);
            client.run(false);

            VMConnection connection = new VMConnection(server) {

                @Override
                void onClose(Callback callback) {
                    super.onClose(callback);

                    _clients.remove(client);
                    client.close();
                }
            };

            connection.setClassLoader(client);
            _clients.put(client, connection);
        }
    }

    public static Workspace createServerWorkspace(int flags) {
        Workspace workspace = Platform.newTestWorkspace();

        if ((flags & FLAG_SERVER_PERSIST) == 0)
            workspace.addURIHandler(new Memory(false));
        else {
            String folder = "temp/server";
            PlatformGenerator.clearFolder(folder);
            workspace.addURIHandler(Platform.get().newTestStore(folder));
        }

        return workspace;
    }

    public static Workspace createClientWorkspace(int flags) {
        Workspace workspace = Platform.newTestWorkspace();

        if ((flags & FLAG_CLIENT_PERSIST) != 0) {
            String folder = "temp/client";
            PlatformGenerator.clearFolder(folder);
            workspace.addURIHandler(Platform.get().newTestStore(folder));
        }

        return workspace;
    }

    static void writeStart(int clients, int flags) {
        String s = "";

        if ((flags & FLAG_SERVER_PERSIST) != 0)
            s += "SERVER_PERSIST, ";

        if ((flags & FLAG_CLIENT_PERSIST) != 0)
            s += "CLIENT_PERSIST, ";

        Log.write("");
        Log.write("Starting clients: " + clients + ", flags: " + s);
    }
}
