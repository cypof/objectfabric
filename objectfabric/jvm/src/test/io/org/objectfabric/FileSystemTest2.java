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

import org.junit.Assert;
import org.junit.Test;
import org.objectfabric.generated.Limit32;
import org.objectfabric.generated.LimitsObjectModel;

public class FileSystemTest2 extends TestsHelper {

    private static final int COUNT = 1000;

    @Test
    public void test() {
        PlatformGenerator.mkdir(FileSystemTest.TEMP);

        for (int i = 0; i < 1; i++) {
            PlatformGenerator.clearFolder(FileSystemTest.TEMP);

            SeparateCL test = new SeparateCL(TestWrite.class.getName());
            test.run();

            SeparateCL update = new SeparateCL(TestUpdate.class.getName());
            update.run();

            SeparateCL read = new SeparateCL(TestRead.class.getName());
            read.run();
        }
    }

    public static final class TestWrite {

        public static void main(String[] args) {
            JVMPlatform.loadClass();
            LimitsObjectModel.register();
            FileSystem temp = (FileSystem) Platform.get().newTestStore(FileSystemTest.TEMP);
            Workspace workspace = Platform.newTestWorkspace();
            workspace.addURIHandler(temp);
            Resource resource = workspace.open("file:///test");
            TMap<String, TSet<Limit32>> map = new TMap<String, TSet<Limit32>>(resource);

            for (int i = 0; i < COUNT; i++) {
                TSet<Limit32> set = new TSet<Limit32>(resource);
                map.put("set" + i, set);
                Limit32 object = new Limit32(resource);
                object.int0(i);
                set.add(object);
            }

            Assert.assertNull(resource.get());
            resource.set(map);

            Assert.assertEquals(COUNT, map.size());
            Log.write("Inserted " + COUNT + " sets.");
            workspace.flush();

            if (Stats.ENABLED) {
                Assert.assertEquals(1, Stats.Instance.BlockWriteCount.get());
                Stats.Instance.writeAndReset();
            }

            TSet<Limit32> set = new TSet<Limit32>(resource);
            Limit32 object = new Limit32(resource);
            object.int0(COUNT);
            set.add(object);
            map.put("set" + COUNT, set);
            workspace.flush();
            Log.write("Inserted 1 more set.");
            long addedWriteLength = 0;

            if (Stats.ENABLED)
                Assert.assertEquals(1, Stats.Instance.BlockWriteCount.get());

            workspace.close();

            if (Stats.ENABLED)
                Stats.Instance.reset();

            // Re open

            workspace = Platform.newTestWorkspace();
            workspace.addURIHandler(temp);
            resource = workspace.open("file:///test");

            @SuppressWarnings("unchecked")
            TMap<String, TSet<Limit32>> map2 = (TMap) resource.get();

            for (int i = 0; i < COUNT; i++) {
                TSet<Limit32> set2 = map2.get("set" + i);
                Assert.assertEquals(1, set2.size());

                for (Limit32 current : set2)
                    Assert.assertTrue(current.int0() == i);
            }

            Log.write("Reopened and read all.");
            workspace.close();

            if (Stats.ENABLED) {
                Assert.assertEquals(0, Stats.Instance.BlockWriteCount.get());
                Assert.assertEquals(2, Stats.Instance.BlockReadCount.get());
                Stats.Instance.writeAndReset();
            }

            temp.close();
        }
    }

    public static final class TestUpdate {

        public static void main(String[] args) {
            JVMPlatform.loadClass();
            LimitsObjectModel.register();
            FileSystem temp = (FileSystem) Platform.get().newTestStore(FileSystemTest.TEMP);
            Workspace workspace = Platform.newTestWorkspace();
            workspace.addURIHandler(temp);
            Resource resource = workspace.open("file:///test");

            @SuppressWarnings("unchecked")
            final TMap<String, TSet<Limit32>> map = (TMap) resource.get();
            Assert.assertEquals(COUNT + 1, map.size());

            workspace.atomic(new Runnable() {

                public void run() {
                    for (int i = 0; i < COUNT + 1; i++) {
                        TSet<Limit32> set = map.get("set" + i);
                        Assert.assertEquals(1, set.size());

                        for (Limit32 current : set)
                            Assert.assertTrue(current.int0() == i);
                    }
                }
            });

            if (Stats.ENABLED) {
                Assert.assertEquals(0, Stats.Instance.BlockWriteCount.get());
                Assert.assertEquals(2, Stats.Instance.BlockReadCount.get());
                Stats.Instance.writeAndReset();
            }

            TSet<Limit32> set = new TSet<Limit32>(resource);
            Limit32 object = new Limit32(resource);
            object.int0(COUNT + 1);
            set.add(object);
            map.put("set" + (COUNT + 1), set);
            workspace.flush();
            Log.write("Inserted 1 more set.");

            if (Stats.ENABLED) {
                int todo;
                // Assert.assertEquals(1, Stats.Instance.FileWriteCount.get());
                Assert.assertEquals(0, Stats.Instance.BlockReadCount.get());
                Stats.Instance.writeAndReset();
            }

            workspace.close();
            temp.close();
        }
    }

    public static final class TestRead {

        public static void main(String[] args) {
            JVMPlatform.loadClass();
            LimitsObjectModel.register();
            FileSystem temp = (FileSystem) Platform.get().newTestStore(FileSystemTest.TEMP);
            Workspace workspace = Platform.newTestWorkspace();
            workspace.addURIHandler(temp);
            Resource resource = workspace.open("file:///test");

            @SuppressWarnings("unchecked")
            final TMap<String, TSet<Limit32>> map = (TMap) resource.get();

            Assert.assertEquals(COUNT + 2, map.size());

            for (int i = 0; i < COUNT + 2; i++) {
                final TSet<Limit32> set = map.get("set" + i);
                Assert.assertEquals(1, set.size());
                final int i_ = i;

                workspace.atomic(new Runnable() {

                    public void run() {
                        for (Limit32 current : set)
                            Assert.assertTrue(current.int0() == i_);
                    }
                });
            }

            Log.write("Reopened and read all.");

            if (Stats.ENABLED) {
                Assert.assertEquals(3, Stats.Instance.BlockReadCount.get());
                Stats.Instance.writeAndReset();
            }

            workspace.close();
            temp.close();
        }
    }
}
