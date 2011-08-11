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

package com.objectfabric;

import org.junit.Assert;
import org.junit.Test;

import com.objectfabric.Transaction.Granularity;
import com.objectfabric.generated.SimpleClass;
import com.objectfabric.generated.SimpleObjectModel;
import com.objectfabric.misc.Debug;
import com.objectfabric.misc.PlatformAdapter;
import com.objectfabric.misc.PlatformFile;
import com.objectfabric.misc.SeparateClassLoader;
import com.objectfabric.transports.VMConnection;
import com.objectfabric.transports.VMServer;

public class ShareStoredObject extends TestsHelper {

    public static void main(String[] args) throws Exception {
        ShareStoredObject test = new ShareStoredObject();

        for (int i = 0; i < 100; i++) {
            test.before();
            test.test1();
            test.after();
        }
    }

    @Test
    public void test1() {
        run(Granularity.COALESCE);
    }

    public void test2() {
        run(Granularity.ALL);
    }

    private void run(Granularity granularity) {
        PlatformFile.mkdir(JdbmTest.TEMP);
        SimpleObjectModel.register();

        PlatformFile.deleteFileIfExists(JdbmTest.FILE);
        PlatformFile.deleteFileIfExists(JdbmTest.FILE + FileStore.LOG_EXTENSION);

        SeparateClassLoader test = new SeparateClassLoader(TestWrite.class.getName());
        test.setArgTypes(int.class);
        test.setArgs(granularity.ordinal());
        test.run();

        SeparateClassLoader read = new SeparateClassLoader(TestShare.class.getName());
        read.setArgTypes(int.class);
        read.setArgs(granularity.ordinal());
        read.run();
    }

    public static final class TestWrite {

        public static void main(int granularity) {
            SimpleObjectModel.register();
            FileStore store = new FileStore(JdbmTest.FILE);
            Transaction trunk = Site.getLocal().createTrunk(Transaction.DEFAULT_CONFLICT_DETECTION, Transaction.DEFAULT_CONSISTENCY, Granularity.values()[granularity], store);
            SimpleClass object = new SimpleClass(trunk);
            object.setInt0(42);
            store.setRoot(object);
            store.close();
            PlatformAdapter.shutdown();
        }
    }

    public static final class TestShare {

        public static void main(int granularity) {
            Debug.ProcessName = "Server";
            SimpleObjectModel.register();

            FileStore store = new FileStore(JdbmTest.FILE);
            SimpleClass object = (SimpleClass) store.getRoot();
            Assert.assertEquals(Granularity.values()[granularity], object.getTrunk().getGranularity());
            Assert.assertEquals(42, object.getInt0());

            VMServer server = new VMServer(object);

            SeparateClassLoader client = new SeparateClassLoader(ShareStoredObjectClient.class.getName());
            client.run();

            VMConnection connection = server.createConnection();
            connection.setClassLoader(client);
            boolean connecting = true;

            for (;;) {
                boolean connected = server.getSessions().size() > 0;

                if (connected)
                    connecting = false;
                else if (!connecting)
                    break;

                if (connection.length() != VMConnection.EXIT) {
                    connection.setLength(connection.transfer(connection.getBuffer(), connection.length()));
                    connection.setLength((Integer) client.invoke("transfer", new Class[] { byte[].class, int.class }, connection.getBuffer(), connection.length()));

                    if (connection.length() == VMConnection.EXIT)
                        connection.close(new RuntimeException());
                }
            }

            Assert.assertEquals(12, object.getInt1());

            server.stop();

            Debug.ProcessName = "";
            Debug.AssertNoConflict = false;

            client.close();
            store.close();

            Transaction.setDefaultTrunk(Site.getLocal().getTrunk());
            PlatformAdapter.shutdown();
        }
    }
}
