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

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import org.junit.Assert;
import org.junit.Test;

import com.objectfabric.Site;
import com.objectfabric.TMap;
import com.objectfabric.Transaction;
import com.objectfabric.Transaction.CommitStatus;
import com.objectfabric.extensions.Logger;
import com.objectfabric.misc.PlatformAdapter;

public class TMapSimple extends TestsHelper {

    private static final boolean LOG = true;

    private static final int CYCLES = 100;

    @Test
    public void run() {
        Logger logger = null;

        if (LOG) {
            logger = new Logger();
            logger.log(Site.getLocal().getTrunk());
        }

        Transaction barrier1 = Transaction.start();
        Transaction.setCurrent(null);

        Transaction a = Transaction.start();

        TMap<Integer, String> map = new TMap<Integer, String>();

        map.put(0, "A");
        map.put(1, "B");

        Assert.assertEquals("A", map.get(0));
        Assert.assertEquals("B", map.get(1));
        Assert.assertEquals(2, map.size());

        Transaction.setCurrent(null);
        Transaction b = Transaction.start();

        Assert.assertEquals(null, map.get(0));
        Assert.assertEquals(null, map.get(1));
        Assert.assertEquals(0, map.size());

        map.put(0, "X");
        map.put(1, "Y");

        Assert.assertEquals("X", map.get(0));
        Assert.assertEquals("Y", map.get(1));
        Assert.assertEquals(2, map.size());

        Transaction.setCurrent(a);

        Assert.assertEquals("A", map.get(0));
        Assert.assertEquals("B", map.get(1));
        Assert.assertEquals(2, map.size());

        map.put(10, "test");

        Assert.assertEquals(3, map.size());

        Transaction p = Transaction.start();
        map.remove(0);
        map.remove(1);
        Assert.assertEquals(1, map.size());
        map.remove(10);
        Assert.assertEquals(0, map.size());
        p.abort();

        //

        Assert.assertEquals(CommitStatus.SUCCESS, a.commit());

        Assert.assertEquals(3, map.size());

        map.remove(0);
        map.remove(1);

        Assert.assertEquals("test", map.get(10));
        Assert.assertEquals(1, map.size());

        Transaction.setCurrent(barrier1);
        barrier1.abort();

        Assert.assertEquals(1, map.size());

        Transaction.setCurrent(b);

        Assert.assertEquals("X", map.get(0));
        Assert.assertEquals("Y", map.get(1));
        Assert.assertEquals(2, map.size());
        Assert.assertEquals(null, map.get(10));

        Assert.assertEquals(CommitStatus.CONFLICT, b.commit());

        Assert.assertEquals("test", map.get(10));
        Assert.assertEquals(1, map.size());

        Transaction c = Transaction.start();
        Assert.assertEquals(1, map.size());
        map.remove(10);
        Assert.assertEquals(0, map.size());
        c.abort();
        Assert.assertEquals(1, map.size());

        if (LOG)
            logger.stop();
    }

    @Test
    public void conflict1() {
        TMap<Integer, String> map = new TMap<Integer, String>();
        map.put(0, "A");

        Transaction a = Transaction.start();
        map.get(0);
        map.put(0, "B");

        Transaction.setCurrent(null);
        Transaction b = Transaction.start();
        map.put(0, "C");
        b.commit();

        Transaction.setCurrent(a);
        Assert.assertEquals(CommitStatus.CONFLICT, a.commit());
    }

    @Test
    public void conflict2() {
        TMap<Integer, String> map = new TMap<Integer, String>();
        map.put(0, "A");

        Transaction a = Transaction.start();
        map.remove(0);

        Transaction.setCurrent(null);
        Transaction b = Transaction.start();
        map.remove(0);
        b.commit();

        Transaction.setCurrent(a);
        Assert.assertEquals(CommitStatus.CONFLICT, a.commit());
    }

    @Test
    public void sizePrivatePublic() {
        TMap<String, Integer> map = new TMap<String, Integer>();

        map.put("A", 0);
        map.put("B", 1);
        map.put("C", 2);

        Transaction w1 = Transaction.start();

        Transaction a = Transaction.start();
        map.remove("A");
        map.remove("B");
        map.remove("C");
        a.commit();

        Assert.assertEquals(0, map.size());

        Transaction b = Transaction.start();
        map.put("A", 0);
        map.remove("A");

        Assert.assertEquals(0, map.size());
        b.commit();
        Assert.assertEquals(0, map.size());
        w1.commit();
        Assert.assertEquals(0, map.size());
    }

    @Test
    public void loop1() {
        TMap<Integer, Integer> map = new TMap<Integer, Integer>();

        for (int i = 0; i < CYCLES; i++) {
            Transaction.start();
            addRemove1(map);
            Transaction.getCurrent().commit();
        }
    }

    @Test
    public void loop2() {
        TMap<Integer, Integer> map = new TMap<Integer, Integer>();

        for (int i = 0; i < CYCLES; i++) {
            Transaction.start();
            addRemove2(map);
            Transaction.getCurrent().commit();
        }
    }

    @Test
    public void loop3() {
        TMap<Integer, Integer> map = new TMap<Integer, Integer>();

        for (int i = 0; i < CYCLES; i++) {
            map.clear();

            Transaction.start();
            addRemove3(map);
            Transaction.getCurrent().commit();
        }
    }

    @Test
    public void loop3_bis() {
        TMap<Integer, Integer> map = new TMap<Integer, Integer>();

        for (int i = 0; i < CYCLES; i++) {
            Transaction.start();

            for (Iterator it = map.entrySet().iterator(); it.hasNext();) {
                it.next();
                it.remove();
            }

            Transaction.getCurrent().commit();

            Transaction.start();
            addRemove3(map);
            Transaction.getCurrent().commit();
        }
    }

    @Test
    public void threads1() {
        final TMap<Integer, Integer> map = new TMap<Integer, Integer>();
        int threads = Runtime.getRuntime().availableProcessors();

        new TMapExtended().run(threads, new Runnable() {

            public void run() {
                TMapSimple.addRemove1(map);
            }
        }, CYCLES / threads);
    }

    @Test
    public void threads2() {
        final TMap<Integer, Integer> map = new TMap<Integer, Integer>();
        int threads = Runtime.getRuntime().availableProcessors();

        new TMapExtended().run(threads, new Runnable() {

            public void run() {
                TMapSimple.addRemove2(map);
            }
        }, CYCLES / threads);
    }

    @Test
    public void threads3() {
        final TMap<Integer, Integer> map = new TMap<Integer, Integer>();
        int threads = Runtime.getRuntime().availableProcessors();

        new TMapExtended().run(threads, new Runnable() {

            public void run() {
                map.clear();
                TMapSimple.addRemove3(map);
            }
        }, CYCLES / threads);
    }

    @Test
    public void threads3_bis() {
        final TMap<Integer, Integer> map = new TMap<Integer, Integer>();
        int threads = Runtime.getRuntime().availableProcessors();

        new TMapExtended().run(threads, new Runnable() {

            public void run() {
                for (Iterator it = map.entrySet().iterator(); it.hasNext();) {
                    it.next();
                    it.remove();
                }

                TMapSimple.addRemove3(map);
            }
        }, CYCLES / threads);
    }

    private static void addRemove1(TMap<Integer, Integer> map) {
        final int MAP_SIZE = 80;

        for (int i = 0; i < MAP_SIZE; i++)
            map.put(i, i);

        Assert.assertEquals(MAP_SIZE, map.size());

        for (int i = 0; i < MAP_SIZE; i++)
            Assert.assertTrue(i == map.get(i));

        for (int i = MAP_SIZE - 1; i >= 0; i--) {
            map.remove(i);
            Assert.assertEquals(i, map.size());
        }
    }

    private static void addRemove2(TMap<Integer, Integer> map) {
        final int MAP_SIZE = 40;

        for (int i = 0; i < MAP_SIZE; i++) {
            Transaction.start();
            Assert.assertEquals(i, map.size());
            map.put(i, i);
            Assert.assertEquals(i + 1, map.size());
            Transaction.getCurrent().commit();
            Assert.assertEquals(i + 1, map.size());
        }

        for (int i = MAP_SIZE - 1; i >= 0; i--) {
            Transaction.start();
            map.remove(i);
            Assert.assertEquals(i, map.size());
            Transaction.getCurrent().commit();
            Assert.assertEquals(i, map.size());
        }
    }

    private static void addRemove3(TMap<Integer, Integer> map) {
        HashMap<Integer, Integer> ref = new HashMap<Integer, Integer>();

        check(map, ref);

        int id = 0;

        for (int i = 0; i < 10; i++) {
            record("Transaction.start();");
            Transaction.start();

            for (int t = 0; t < 100; t++) {
                int size = map.size();
                boolean insert = size == 0 || PlatformAdapter.getRandomInt(10) < 8;
                int index;

                if (insert) {
                    index = PlatformAdapter.getRandomInt(size + 1);
                    record("map.add(" + index + ", " + id + ");");
                    record("ref.add(" + index + ", " + id + ");");
                    map.put(index, id);
                    ref.put(index, id++);
                } else {
                    index = PlatformAdapter.getRandomInt(size);
                    record("map.remove(" + index + ");");
                    record("ref.remove(" + index + ");");
                    map.remove(index);
                    ref.remove(index);
                }

                check(map, ref);
            }

            record("Transaction.getCurrent().commit();");
            Transaction.getCurrent().commit();

            check(map, ref);
        }
    }

    private static void record(String message) {
        // System.out.println(message);
    }

    private static <K, V> void check(TMap<K, V> map, HashMap<K, V> ref) {
        Assert.assertEquals(ref.size(), map.size());

        for (Map.Entry<K, V> entry : map.entrySet())
            Assert.assertTrue(ref.get(entry.getKey()).equals(entry.getValue()));

        for (Map.Entry<K, V> entry : ref.entrySet())
            Assert.assertTrue(map.get(entry.getKey()).equals(entry.getValue()));
    }

    public static void main(String[] args) throws Exception {
        TMapSimple test = new TMapSimple();
        test.before();
        test.conflict1();
        test.after();
    }
}
