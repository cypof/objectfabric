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

import junit.framework.Assert;

import org.junit.Test;

import com.objectfabric.OF.AutoCommitPolicy;
import com.objectfabric.OF.Config;
import com.objectfabric.generated.PersistenceClass;
import com.objectfabric.generated.PersistenceObjectModel;
import com.objectfabric.misc.PlatformAdapter;
import com.objectfabric.misc.PlatformFile;
import com.objectfabric.misc.SeparateClassLoader;

public class KeyedPersistenceTest extends TestsHelper {

    public static void main(String[] args) throws Exception {
        KeyedPersistenceTest test = new KeyedPersistenceTest();

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
            TSet<PersistenceClass> set = new TSet<PersistenceClass>();
            map.put("set1", set);
            map.put("set2", null);
            PersistenceClass object = new PersistenceClass();
            object.setInt(55);
            PersistenceClass object2 = new PersistenceClass();
            object2.setText("456");
            object.setObject(object2);
            set.add(object);
            set.add(object2);

            Assert.assertNull(store.getRoot());
            store.setRoot(map);

            OF.update();

            store.close();

            // Re open

            FileStore store2 = new FileStore(JdbmTest.FILE);

            @SuppressWarnings("unchecked")
            TMap<String, TSet<PersistenceClass>> map2 = (TMap) store2.getRoot();
            Assert.assertTrue(map2 == map);
            TSet<PersistenceClass> set2 = map.get("set1");
            Assert.assertTrue(set2 == set);

            for (PersistenceClass current : set2)
                Assert.assertTrue(current == object || current == object2);

            store2.close();
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
            Assert.assertTrue(map.size() == 2);
            final TSet<PersistenceClass> set = map.get("set1");
            Assert.assertTrue(map.containsKey("set2"));
            Assert.assertTrue(map.get("set2") == null);

            Transaction.run(new Runnable() {

                public void run() {
                    TSet<PersistenceClass> set3 = new TSet<PersistenceClass>();
                    set3.add(new PersistenceClass());
                    map.put("set3", set3);
                }
            });

            Transaction.run(new Runnable() {

                public void run() {
                    int count = 0;

                    for (PersistenceClass object : set) {
                        Assert.assertTrue(object.getInt() == 55 || "456".equals(object.getText()));
                        count++;
                    }

                    Assert.assertEquals(2, count);
                    Assert.assertEquals(count, set.size());
                }
            });

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
            Assert.assertTrue(map.size() == 3);
            final TSet<PersistenceClass> set = map.get("set1");
            Assert.assertTrue(map.containsKey("set2"));
            Assert.assertTrue(map.get("set2") == null);
            // Assert.assertTrue(map.containsKey("set3"));
            // TSet<PersistenceClass> set3 = map.get("set3");
            // Assert.assertTrue(set3.size() == 1);

            Transaction.run(new Runnable() {

                public void run() {
                    int count = 0;

                    for (PersistenceClass object : set) {
                        Assert.assertTrue(object.getInt() == 55 || "456".equals(object.getText()));
                        count++;
                    }

                    Assert.assertEquals(2, count);
                    Assert.assertEquals(count, set.size());
                }
            }, set.getTrunk());

            store.close();
            PlatformAdapter.shutdown();
        }
    }
}
