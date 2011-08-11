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
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Random;

import org.junit.Assert;
import org.junit.Test;

import com.objectfabric.misc.Debug;
import com.objectfabric.misc.PlatformFile;

public class BTreeTest {

    private static final byte[] KEY1 = { 122, 30, 2, -90, 9, 94, 94, -53, -104, 108, -4, -29, 62, -102, -92, -11 };

    private static final byte[] KEY2 = { 122, 30, 2, -90, 9, 77, 94, -53, -104, 108, -4, -29, 62, -102, -92, -11 };

    private static final byte[] KEY3 = { -22, 30, 2, -90, 9, 94, 94, -53, -104, 108, -4, -29, 62, -102, -92, -11 };

    private static final byte[] KEY4 = { -90 };

    private static final byte[] KEY5 = { 62, -102, -92, -11 };

    @Test
    public void test() {
        write();
        read();
    }

    private void write() {
        PlatformFile.mkdir(JdbmTest.TEMP);
        PlatformFile.deleteFileIfExists(JdbmTest.FILE);
        PlatformFile.deleteFileIfExists(JdbmTest.FILE + ".log");

        PlatformFile db = new PlatformFile(JdbmTest.FILE);
        PlatformFile lg = new PlatformFile(JdbmTest.FILE + ".log");
        RecordManager manager = new RecordManager(db, lg);

        long root = manager.getRoot(0);
        Debug.assertAlways(root == 0);

        BTree tree = new BTree(manager, true);
        tree.put(KEY1, 87);
        tree.put(KEY2, -78);
        manager.setRoot(0, tree.getId());
        manager.commit();
        manager.close();
    }

    private void read() {
        PlatformFile db = new PlatformFile(JdbmTest.FILE);
        PlatformFile lg = new PlatformFile(JdbmTest.FILE + ".log");
        RecordManager manager = new RecordManager(db, lg);

        long root = manager.getRoot(0);
        BTree tree = BTree.load(manager, root, true);
        Debug.assertAlways(tree.fetch(KEY1) == 87);
        Debug.assertAlways(tree.fetch(KEY2) == -78);
        Debug.assertAlways(tree.fetch(KEY3) == 0);
        manager.close();
    }

    @Test
    public void test2() {
        write2();
        read2();
    }

    private void write2() {
        PlatformFile.deleteFileIfExists(JdbmTest.FILE);
        PlatformFile.deleteFileIfExists(JdbmTest.FILE + ".log");

        PlatformFile db = new PlatformFile(JdbmTest.FILE);
        PlatformFile lg = new PlatformFile(JdbmTest.FILE + ".log");
        RecordManager manager = new RecordManager(db, lg);

        long root = manager.getRoot(0);
        Debug.assertAlways(root == 0);

        BTree tree = new BTree(manager, false);
        tree.put(KEY4, 22);
        tree.put(KEY5, -78);
        manager.setRoot(0, tree.getId());
        manager.commit();
        manager.close();
    }

    private void read2() {
        PlatformFile db = new PlatformFile(JdbmTest.FILE);
        PlatformFile lg = new PlatformFile(JdbmTest.FILE + ".log");
        RecordManager manager = new RecordManager(db, lg);

        long root = manager.getRoot(0);
        BTree tree = BTree.load(manager, root, false);
        Debug.assertAlways(tree.fetch(KEY4) == 22);
        Debug.assertAlways(tree.fetch(KEY5) == -78);
        manager.close();
    }

    @Test
    public void test5() {
        PlatformFile.deleteFileIfExists(JdbmTest.FILE);
        PlatformFile.deleteFileIfExists(JdbmTest.FILE + ".log");

        PlatformFile db = new PlatformFile(JdbmTest.FILE);
        PlatformFile lg = new PlatformFile(JdbmTest.FILE + ".log");
        RecordManager manager = new RecordManager(db, lg);

        BTree tree = new BTree(manager, false);
        manager.setRoot(0, tree.getId());
        HashMap<Key, Long> ref = new HashMap<Key, Long>();
        ArrayList<Key> list = new ArrayList<Key>();
        HashSet<Key> removed = new HashSet<Key>();
        Random rand = new Random();
        int inserts = 0, removals = 0, bigs = 0, clears = 0, commits = 0, closings = 0;

        for (int i = 0; i < 1000; i++) {
            if (rand.nextInt(100) != 0) {
                if (list.size() == 0 || rand.nextInt(100) < 66) {
                    for (;;) {
                        int length = rand.nextInt(100);

                        if (length == 22) {
                            length = rand.nextInt((int) 1e5);
                            bigs++;
                        }

                        byte[] key = new byte[length];
                        rand.nextBytes(key);
                        Key wrapper = new Key(key);

                        if (!ref.containsKey(wrapper)) {
                            long value = list.size();
                            tree.put(key, value);
                            ref.put(wrapper, value);
                            list.add(wrapper);
                            removed.remove(wrapper);
                            inserts++;
                            break;
                        }
                    }
                } else {
                    Key wrapper = list.remove(rand.nextInt(list.size()));
                    ref.remove(wrapper);
                    tree.remove(wrapper.getBytes());
                    removed.add(wrapper);
                    removals++;
                }
            } else if (rand.nextInt(100) < 33) {
                tree.clear(false);
                ref.clear();
                list.clear();
                removed.clear();
                clears++;
            } else if (rand.nextInt(100) < 50) {
                manager.commit();
                commits++;
            } else {
                manager.close();
                db = new PlatformFile(JdbmTest.FILE);
                lg = new PlatformFile(JdbmTest.FILE + ".log");
                manager = new RecordManager(db, lg);
                long root = manager.getRoot(0);
                tree = BTree.load(manager, root, false);
                closings++;
            }

            for (int t = 0; t < list.size(); t++) {
                Key wrapper = list.get(t);
                Debug.assertAlways(ref.get(wrapper) == tree.fetch(wrapper.getBytes()));
                Assert.assertTrue(ref.get(wrapper) == tree.fetch(wrapper.getBytes()));
            }

            for (Key wrapper : removed)
                Assert.assertTrue(tree.fetch(wrapper.getBytes()) == 0);

            TestWalker walker = new TestWalker();
            tree.walk(walker);
            Assert.assertEquals(ref.size(), list.size());
            Assert.assertEquals(ref.size(), walker._keys.size());
            Assert.assertEquals(ref.size(), walker._values.size());

            for (int t = 0; t < walker._keys.size(); t++) {
                long value = ref.get(new Key(walker._keys.get(t)));
                Assert.assertTrue(value == walker._values.get(t));
            }
        }

        manager.close();
        System.out.println("BTreeTest, inserts:" + inserts + ", removals:" + removals + ", bigs:" + bigs + ", clears:" + clears + ", commits:" + commits + ", closings:" + closings);
    }

    private static final class TestWalker implements BTree.Walker {

        final ArrayList<byte[]> _keys = new ArrayList<byte[]>();

        final ArrayList<Long> _values = new ArrayList<Long>();

        public void onEntry(byte[] key, long value) {
            _keys.add(key);
            _values.add(value);
        }
    }

    private static final class Key {

        private final byte[] _array;

        public Key(byte[] array) {
            _array = array;
        }

        public byte[] getBytes() {
            return _array;
        }

        @Override
        public boolean equals(Object o) {
            if (o instanceof Key)
                return Arrays.equals(_array, ((Key) o)._array);

            return false;
        }

        @Override
        public int hashCode() {
            int b0 = _array.length > 0 ? (_array[0] & 0xff) : 0;
            int b1 = _array.length > 1 ? (_array[1] & 0xff) << 8 : 0;
            int b2 = _array.length > 2 ? (_array[2] & 0xff) << 16 : 0;
            int b3 = _array.length > 3 ? (_array[3] & 0xff) << 24 : 0;
            return b3 | b2 | b1 | b0;
        }
    }

    public static void main(String[] args) {
        BTreeTest test = new BTreeTest();
        test.test();
    }
}
