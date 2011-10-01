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

import java.io.IOException;

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

public class LazyMapTest extends TestsHelper {

    private static final int COUNT = 1000;

    public static void main(String[] args) throws Exception {
        LazyMapTest test = new LazyMapTest();

        for (int i = 0; i < 100; i++) {
            test.before();
            test.test();
            test.after();
        }
    }

    @Test
    public void test() {
        PlatformFile.mkdir(JdbmTest.TEMP);

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

        public static void main(String[] args) throws IOException {
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

            LazyMap<Object, PersistenceClass> map = new LazyMap<Object, PersistenceClass>();

            for (int i = 0; i < COUNT; i++) {
                PersistenceClass object = new PersistenceClass();
                map.put("object" + i, object);
                object.setInt(i);
            }

            Assert.assertNull(store.getRoot());
            store.setRoot(map);

            OF.update();
            Log.write("Inserted " + COUNT + " objects.");
            long mainWriteLength = 0;

            if (Stats.ENABLED) {
                mainWriteLength = Stats.getInstance().FileTotalWritten.get();
                Stats.getInstance().writeAndReset();
            }

            map.get("object0").setLong(mainWriteLength);
            Log.write("Read back 1 object.");

            PersistenceClass object = new PersistenceClass();
            object.setInt(COUNT);
            map.put("object" + COUNT, object);

            OF.update();
            Log.write("Inserted 1 more object.");

            if (Stats.ENABLED)
                Assert.assertTrue(Stats.getInstance().FileTotalWritten.get() < mainWriteLength / 10);

            // Test with TObject as key
            map.put(object, new PersistenceClass());
            map.put(new PersistenceClass(), new PersistenceClass());
            OF.update();

            store.close();

            if (Stats.ENABLED)
                Stats.getInstance().writeAndReset();

            // Re open

            FileStore store2 = new FileStore(JdbmTest.FILE);

            @SuppressWarnings("unchecked")
            LazyMap<Object, PersistenceClass> map2 = (LazyMap) store2.getRoot();
            Assert.assertTrue(map2 == map);

            // 2 for log file initialization
            Log.write("Reopened store and read back lazy map.");
            long lastRead = 0;

            if (Stats.ENABLED) {
                Assert.assertEquals(2, Stats.getInstance().FileTotalWritten.get());
                Assert.assertTrue(Stats.getInstance().FileTotalRead.get() < mainWriteLength / 10);
                Stats.getInstance().writeAndReset();
                lastRead = Stats.getInstance().FileTotalRead.get();
            }

            for (int i = 0; i < COUNT; i++) {
                PersistenceClass object2 = map.get("object" + i);
                Assert.assertTrue(object2.getInt() == i);

                if (Stats.ENABLED) {
                    Assert.assertTrue((Stats.getInstance().FileTotalRead.get() - lastRead) < mainWriteLength / 10);
                    lastRead = Stats.getInstance().FileTotalRead.get();
                }
            }

            Log.write("Read back objects from map.");

            if (Stats.ENABLED)
                Stats.getInstance().writeAndReset();

            store2.close();
            PlatformAdapter.shutdown();
        }
    }

    public static final class TestUpdate {

        public static void main(String[] args) throws IOException {
            PersistenceObjectModel.register();

            FileStore store = new FileStore(JdbmTest.FILE);

            @SuppressWarnings("unchecked")
            final LazyMap<Object, PersistenceClass> map = (LazyMap) store.getRoot();
            Transaction.setDefaultTrunk(map.getTrunk());

            Log.write("Reloaded store and read back lazy map.");

            final long mainWriteLength = map.get("object0").getLong();

            if (Stats.ENABLED) {
                Assert.assertTrue(Stats.getInstance().FileTotalRead.get() < mainWriteLength / 10);
                Stats.getInstance().writeAndReset();
            }

            map.get("object0");

            if (Stats.ENABLED)
                Assert.assertEquals(0, Stats.getInstance().FileTotalRead.get());

            Transaction.run(new Runnable() {

                public void run() {
                    long lastRead = 0;

                    if (Stats.ENABLED)
                        Stats.getInstance().FileTotalRead.get();

                    for (int i = 0; i < COUNT + 1; i++) {
                        PersistenceClass object = map.get("object" + i);
                        Assert.assertTrue(object.getInt() == i);

                        if (Stats.ENABLED) {
                            Assert.assertTrue((Stats.getInstance().FileTotalRead.get() - lastRead) < mainWriteLength / 10);
                            lastRead = Stats.getInstance().FileTotalRead.get();
                        }
                    }
                }
            });

            Log.write("Read back objects from map.");

            PersistenceClass key = map.get("object" + COUNT);
            Assert.assertTrue(map.get(key) != null);

            if (Stats.ENABLED) {
                Assert.assertEquals(0, Stats.getInstance().FileTotalWritten.get());
                Stats.getInstance().writeAndReset();
            }

            PersistenceClass object = new PersistenceClass();
            object.setInt(COUNT + 1);
            map.put("object" + (COUNT + 1), object);

            store.flush();
            Log.write("Inserted 1 more object.");

            if (Stats.ENABLED) {
                Assert.assertTrue(Stats.getInstance().FileTotalRead.get() < mainWriteLength / 10);
                Assert.assertTrue(Stats.getInstance().FileTotalWritten.get() < mainWriteLength / 10);
                Stats.getInstance().writeAndReset();
            }

            store.close();
            PlatformAdapter.shutdown();
        }
    }

    public static final class TestRead {

        public static void main(String[] args) throws IOException {
            PersistenceObjectModel.register();

            FileStore store = new FileStore(JdbmTest.FILE);

            @SuppressWarnings("unchecked")
            final LazyMap<Object, PersistenceClass> map = (LazyMap) store.getRoot();
            long mainWriteLength = map.get("object0").getLong();

            if (Stats.ENABLED)
                Assert.assertTrue(Stats.getInstance().FileTotalRead.get() < mainWriteLength / 10);

            for (int i = 0; i < COUNT + 2; i++) {
                final PersistenceClass object = map.get("object" + i);
                Assert.assertTrue(object.getInt() == i);

                if (Stats.ENABLED) {
                    Assert.assertTrue(Stats.getInstance().FileTotalRead.get() < mainWriteLength / 10);
                    Stats.getInstance().FileTotalRead.set(0);
                }
            }

            if (Stats.ENABLED) {
                // 2 for log file initialization
                Assert.assertEquals(2, Stats.getInstance().FileTotalWritten.get());
            }

            store.close();
            PlatformAdapter.shutdown();
        }
    }
}
