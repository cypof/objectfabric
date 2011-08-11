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

import java.util.ArrayList;

import junit.framework.Assert;

import org.junit.Test;

import com.objectfabric.OF.AutoCommitPolicy;
import com.objectfabric.OF.Config;
import com.objectfabric.generated.PersistenceClass;
import com.objectfabric.generated.PersistenceObjectModel;
import com.objectfabric.misc.Log;
import com.objectfabric.misc.PlatformAdapter;
import com.objectfabric.misc.PlatformFile;
import com.objectfabric.misc.SeparateClassLoader;

public class KeyedAppendTest extends TestsHelper {

    private static final int COUNT = 1000;

    public static void main(String[] args) throws Exception {
        KeyedAppendTest test = new KeyedAppendTest();

        for (int i = 0; i < 100; i++) {
            test.before();
            test.test();
            test.after();
        }
    }

    @Test
    public void test() {
        PlatformFile.mkdir(JdbmTest.TEMP);
        PersistenceObjectModel.register();

        PlatformFile.deleteFileIfExists(JdbmTest.FILE);
        PlatformFile.deleteFileIfExists(JdbmTest.FILE + FileStore.LOG_EXTENSION);

        SeparateClassLoader test = new SeparateClassLoader(TestWrite.class.getName());
        test.run();

        SeparateClassLoader update = new SeparateClassLoader(TestUpdate.class.getName());
        update.run();

        SeparateClassLoader read = new SeparateClassLoader(TestRead.class.getName());
        read.run();
    }

    public static final class TestWrite {

        public static void main(String[] args) {
            PersistenceObjectModel.register();

            OF.setConfig(new Config() {

                @Override
                public AutoCommitPolicy getAutoCommitPolicy() {
                    return AutoCommitPolicy.DELAYED_MANUAL;
                }
            });

            FileStore store = new FileStore(JdbmTest.FILE);
            Transaction trunk = Site.getLocal().createTrunk(store);
            Transaction.setDefaultTrunk(trunk);

            TMap<String, TSet<PersistenceClass>> map = new TMap<String, TSet<PersistenceClass>>();
            ArrayList<TSet<PersistenceClass>> list = new ArrayList<TSet<PersistenceClass>>();

            for (int i = 0; i < COUNT; i++) {
                TSet<PersistenceClass> set = new TSet<PersistenceClass>();
                list.add(set);
                map.put("set" + i, set);
                PersistenceClass object = new PersistenceClass();
                object.setInt(i);
                set.add(object);
            }

            Assert.assertNull(store.getRoot());
            store.setRoot(map);

            OF.update();
            Assert.assertEquals(COUNT, map.size());
            Log.write("Inserted " + COUNT + " sets.");
            long mainWriteLength = 0;

            if (Stats.ENABLED) {
                mainWriteLength = Stats.getInstance().FileTotalWritten.get();
                Stats.getInstance().writeAndReset();
            }

            TSet<PersistenceClass> set = new TSet<PersistenceClass>();
            PersistenceClass object = new PersistenceClass();
            object.setInt(COUNT);
            set.add(object);
            map.put("set" + list.size(), set);

            OF.update();

            Log.write("Inserted 1 more set.");

            if (Stats.ENABLED)
                Assert.assertTrue(Stats.getInstance().FileTotalWritten.get() < mainWriteLength / 10);

            store.close();

            if (Stats.ENABLED)
                Stats.getInstance().reset();

            // Re open

            FileStore store2 = new FileStore(JdbmTest.FILE);

            @SuppressWarnings("unchecked")
            TMap<String, TSet<PersistenceClass>> map2 = (TMap) store2.getRoot();
            Assert.assertTrue(map2 == map);

            for (int i = 0; i < COUNT; i++) {
                TSet<PersistenceClass> set2 = map.get("set" + i);
                Assert.assertTrue(set2 == list.get(i));
                Assert.assertEquals(1, set2.size());

                for (PersistenceClass current : set2)
                    Assert.assertTrue(current.getInt() == i);
            }

            Log.write("Reopened and read all.");

            store2.close();

            if (Stats.ENABLED) {
                // 4 for 2 log file initializations
                Assert.assertEquals(4, Stats.getInstance().FileTotalWritten.get());
                Stats.getInstance().writeAndReset();
            }

            PlatformAdapter.shutdown();
        }
    }

    public static final class TestUpdate {

        public static void main(String[] args) {
            PersistenceObjectModel.register();

            FileStore store = new FileStore(JdbmTest.FILE);

            @SuppressWarnings("unchecked")
            final TMap<String, TSet<PersistenceClass>> map = (TMap) store.getRoot();
            Transaction.setDefaultTrunk(map.getTrunk());
            Assert.assertEquals(COUNT + 1, map.size());

            Transaction.run(new Runnable() {

                public void run() {
                    for (int i = 0; i < COUNT + 1; i++) {
                        TSet<PersistenceClass> set = map.get("set" + i);
                        Assert.assertEquals(1, set.size());

                        for (PersistenceClass current : set)
                            Assert.assertTrue(current.getInt() == i);
                    }
                }
            });

            if (Stats.ENABLED) {
                // 2 for log file initialization
                Assert.assertEquals(2, Stats.getInstance().FileTotalWritten.get());
            }

            TSet<PersistenceClass> set = new TSet<PersistenceClass>();
            PersistenceClass object = new PersistenceClass();
            object.setInt(COUNT + 1);
            set.add(object);
            map.put("set" + (COUNT + 1), set);

            store.flush();

            Log.write("Inserted 1 more set.");

            if (Stats.ENABLED) {
                Assert.assertTrue(Stats.getInstance().FileTotalWritten.get() < Stats.getInstance().FileTotalRead.get() / 10);
                Stats.getInstance().writeAndReset();
            }

            store.close();
            PlatformAdapter.shutdown();
        }
    }

    public static final class TestRead {

        public static void main(String[] args) {
            PersistenceObjectModel.register();

            FileStore store = new FileStore(JdbmTest.FILE);

            @SuppressWarnings("unchecked")
            final TMap<String, TSet<PersistenceClass>> map = (TMap) store.getRoot();
            Transaction.setDefaultTrunk(map.getTrunk());

            Assert.assertEquals(COUNT + 2, map.size());

            for (int i = 0; i < COUNT + 2; i++) {
                final TSet<PersistenceClass> set = map.get("set" + i);
                Assert.assertEquals(1, set.size());
                final int i_ = i;

                Transaction.run(new Runnable() {

                    public void run() {
                        for (PersistenceClass current : set)
                            Assert.assertTrue(current.getInt() == i_);
                    }
                });
            }

            Log.write("Reopened and read all.");

            if (Stats.ENABLED) {
                // 2 for log file initialization
                Assert.assertEquals(2, Stats.getInstance().FileTotalWritten.get());
                Stats.getInstance().writeAndReset();
            }

            store.close();
            PlatformAdapter.shutdown();
        }
    }
}
