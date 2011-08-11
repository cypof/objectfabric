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
import java.util.Iterator;

import org.junit.Assert;
import org.junit.Test;

import com.objectfabric.TList;
import com.objectfabric.Transaction;
import com.objectfabric.Transaction.CommitStatus;
import com.objectfabric.extensions.Logger;
import com.objectfabric.misc.PlatformAdapter;

public class TListSimple extends TestsHelper {

    private static final boolean LOG = false;

    private static final int CYCLES = 10;

    @Test
    public void simple() {
        Logger logger = null;

        if (LOG)
            logger = new Logger();

        Transaction a = Transaction.start();

        TList<String> list = new TList<String>();

        list.add("A");
        list.add("B");

        Assert.assertTrue(list.contains("A"));
        Assert.assertTrue(!list.isEmpty());

        Transaction.setCurrent(null);
        Transaction b = Transaction.start();

        Assert.assertTrue(!list.contains("A"));
        Assert.assertTrue(list.isEmpty());

        list.add("X");
        list.add("Y");

        Assert.assertTrue(list.contains("X"));
        Assert.assertTrue(!list.isEmpty());

        Transaction.setCurrent(a);

        Assert.assertTrue(list.contains("A"));
        Assert.assertTrue(!list.isEmpty());

        list.add("test");

        Assert.assertEquals(CommitStatus.SUCCESS, a.commit());

        Assert.assertTrue(list.contains("test"));

        Transaction.setCurrent(b);

        Assert.assertTrue(list.contains("X"));
        Assert.assertTrue(!list.isEmpty());
        Assert.assertFalse(list.contains("test"));

        Assert.assertEquals(CommitStatus.CONFLICT, b.commit());

        Assert.assertTrue(list.contains("test"));

        Transaction c = Transaction.start();

        Assert.assertTrue(list.contains("A"));
        Assert.assertTrue(!list.isEmpty());

        c.abort();

        if (LOG)
            logger.stop();
    }

    @Test
    public void conflict1() {
        TList<String> list = new TList<String>();
        list.add("A");

        Transaction a = Transaction.start();
        list.remove(0);

        Transaction.setCurrent(null);
        Transaction b = Transaction.start();
        list.remove(0);
        b.commit();

        Transaction.setCurrent(a);
        Assert.assertEquals(CommitStatus.CONFLICT, a.commit());
    }

    @Test
    public void conflict2() {
        TList<String> list = new TList<String>();
        list.add("A");

        Transaction a = Transaction.start();
        list.remove(0);

        Transaction.setCurrent(null);
        Transaction b = Transaction.start();
        list.set(0, list.get(0));
        b.commit();

        Transaction.setCurrent(a);
        Assert.assertEquals(CommitStatus.CONFLICT, a.commit());
    }

    @Test
    public void conflict3() {
        TList<String> list = new TList<String>();
        list.add("A");

        Transaction a = Transaction.start();
        list.remove(0);

        Transaction.setCurrent(null);
        Transaction b = Transaction.start();
        list.add("B");
        b.commit();

        Transaction.setCurrent(a);
        Assert.assertEquals(CommitStatus.CONFLICT, a.commit());
    }

    @Test
    public void sizePrivatePublic() {
        TList<String> list = new TList<String>();

        list.add("A");
        list.add("B");
        list.add("C");

        Transaction w1 = Transaction.start();

        Transaction a = Transaction.start();
        list.remove(0);
        list.remove(0);
        list.remove(0);
        a.commit();

        Transaction b = Transaction.start();
        list.add("A");
        list.remove(0);

        Assert.assertEquals(0, list.size());
        b.commit();
        Assert.assertEquals(0, list.size());
        w1.commit();
        Assert.assertEquals(0, list.size());
    }

    @Test
    public void loop1() {
        TList<Integer> list = new TList<Integer>();

        for (int i = 0; i < CYCLES; i++) {
            Transaction.start();
            addRemove1(list);
            Transaction.getCurrent().commit();
        }
    }

    @Test
    public void loop2() {
        TList<Integer> list = new TList<Integer>();

        for (int i = 0; i < CYCLES; i++) {
            Transaction.start();
            addRemove2(list);
            Transaction.getCurrent().commit();
        }
    }

    @Test
    public void loop3() {
        TList<Integer> list = new TList<Integer>();

        for (int i = 0; i < CYCLES; i++) {
            list.clear();

            Transaction.start();
            addRemove3(list);
            Transaction.getCurrent().commit();
        }
    }

    @Test
    public void loop3_bis() {
        TList<Integer> list = new TList<Integer>();

        for (int i = 0; i < CYCLES; i++) {
            Transaction.start();

            for (Iterator it = list.iterator(); it.hasNext();) {
                it.next();
                it.remove();
            }

            Transaction.getCurrent().commit();

            Transaction.start();
            addRemove3(list);
            Transaction.getCurrent().commit();
        }
    }

    @Test
    public void threads1() {
        final TList<Integer> list = new TList<Integer>();

        new TListExtended().run(4, new Runnable() {

            public void run() {
                TListSimple.addRemove1(list);
            }
        }, CYCLES);
    }

    @Test
    public void threads2() {
        final TList<Integer> list = new TList<Integer>();

        new TListExtended().run(4, new Runnable() {

            public void run() {
                TListSimple.addRemove2(list);
            }
        }, CYCLES);
    }

    @Test
    public void threads3() {
        final TList<Integer> list = new TList<Integer>();

        new TListExtended().run(4, new Runnable() {

            public void run() {
                list.clear();
                TListSimple.addRemove3(list);
            }
        }, 1);
    }

    @Test
    public void threads3_bis() {
        final TList<Integer> list = new TList<Integer>();

        new TListExtended().run(4, new Runnable() {

            public void run() {
                for (Iterator it = list.iterator(); it.hasNext();) {
                    it.next();
                    it.remove();
                }

                TListSimple.addRemove3(list);
            }
        }, 1);
    }

    private static void addRemove1(TList<Integer> list) {
        final int LIST_SIZE = 80;

        for (int i = 0; i < LIST_SIZE; i++) {
            list.add(i);
            Assert.assertEquals(i + 1, list.size());
        }

        for (int i = 0; i < LIST_SIZE; i++)
            Assert.assertTrue(i == list.get(i));

        for (int i = LIST_SIZE - 1; i >= 0; i--) {
            list.remove(i);
            Assert.assertEquals(i, list.size());
        }
    }

    private static void addRemove2(TList<Integer> list) {
        final int LIST_SIZE = 40;

        for (int i = 0; i < LIST_SIZE; i++) {
            Transaction.start();
            list.add(0);
            Transaction.getCurrent().commit();
        }

        Assert.assertEquals(LIST_SIZE, list.size());

        for (int i = LIST_SIZE - 1; i >= 0; i--) {
            Transaction.start();
            list.remove(i);
            Transaction.getCurrent().commit();
        }

        Assert.assertEquals(0, list.size());
    }

    private static void addRemove3(TList<Integer> list) {
        ArrayList<Integer> ref = new ArrayList<Integer>();

        check(list, ref);

        int id = 0;

        for (int i = 0; i < 4; i++) {
            record("Transaction.start();");
            Transaction.start();

            for (int t = 0; t < 20; t++) {
                int size = list.size();
                boolean insert = size == 0 || PlatformAdapter.getRandomInt(10) < 8;
                int index;

                if (insert) {
                    index = size > 0 ? PlatformAdapter.getRandomInt(list.size() + 1) : 0;
                    record("list.add(" + index + ", " + id + ");");
                    record("ref.add(" + index + ", " + id + ");");
                    list.add(index, id);
                    ref.add(index, id++);
                } else {
                    index = PlatformAdapter.getRandomInt(list.size());
                    record("list.remove(" + index + ");");
                    record("ref.remove(" + index + ");");
                    list.remove(index);
                    ref.remove(index);
                }

                check(list, ref);
            }

            record("Transaction.getCurrent().commit();");
            Transaction.getCurrent().commit();

            check(list, ref);
        }
    }

    private static void record(String message) {
        // System.out.println(message);
    }

    private static void check(TList list, ArrayList ref) {
        int size = list.size();
        Assert.assertEquals(ref.size(), size);

        for (int i = 0; i < size; i++)
            Assert.assertEquals(ref.get(i), list.get(i));
    }

    public static void main(String[] args) throws Exception {
        TListSimple test = new TListSimple();
        test.before();
        test.threads1();
        test.after();
    }
}
