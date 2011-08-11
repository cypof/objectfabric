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
import java.util.Collections;

import org.junit.Assert;
import org.junit.Test;

import com.objectfabric.Snapshot;
import com.objectfabric.TList;
import com.objectfabric.TListVersion;
import com.objectfabric.TObject;
import com.objectfabric.Transaction;
import com.objectfabric.misc.PlatformAdapter;

public class TListVersionTest extends TestsHelper {

    @Test
    public void insert1() {
        TList<String> list = new TList<String>();
        TListVersion a = new TListVersion(list.getSharedVersion_objectfabric());
        TListVersion b = new TListVersion(list.getSharedVersion_objectfabric());

        a.insert(4);
        b.setBit(3);
        Assert.assertTrue(!a.offsets(b.getBits()));

        a.insert(3);
        Assert.assertTrue(a.offsets(b.getBits()));
    }

    @Test
    public void insert2() {
        TList<String> list = new TList<String>();
        TListVersion version = new TListVersion(list.getSharedVersion_objectfabric());
        version.insert(2);
        version.insert(4);
        version.insert(4);
        version.insert(5);
        Assert.assertEquals(-2, TListVersion.binarySearch(version.getInserts(), version.getInsertsCount(), 4));
    }

    @Test
    public void remove1() {
        TList<String> list = new TList<String>();
        TListVersion a = new TListVersion(list.getSharedVersion_objectfabric());
        TListVersion b = new TListVersion(list.getSharedVersion_objectfabric());

        a.remove(4);
        b.setBit(3);
        Assert.assertTrue(!a.offsets(b.getBits()));

        a.remove(3);
        Assert.assertTrue(a.offsets(b.getBits()));
    }

    @Test
    public void remove2() {
        TList<String> list = new TList<String>();
        TListVersion version = new TListVersion(list.getSharedVersion_objectfabric());
        version.remove(2);
        version.remove(4);
        version.remove(4);
        version.remove(5);
        Assert.assertEquals(-3, TListVersion.binarySearch(version.getRemovals(), version.getRemovalsCount(), 4));
    }

    @Test
    public void offset1() {
        TList<String> list = new TList<String>();
        TListVersion a = new TListVersion(list.getSharedVersion_objectfabric());
        TListVersion b = new TListVersion(list.getSharedVersion_objectfabric());

        Transaction current = Transaction.start();
        Snapshot previous = current.getSnapshot();
        Snapshot snapshot = createSnapshot(a, b, null);
        current.forceSnapshot(snapshot);
        current.setPublicSnapshotVersions(snapshot.getWrites());

        b.setSize(6);
        a.setBit(3);
        a.set(3, "a");

        Assert.assertEquals(null, list.get(2));
        Assert.assertEquals("a", list.get(3));
        Assert.assertEquals(null, list.get(4));

        b.insert(2);

        Assert.assertEquals(null, list.get(3));
        Assert.assertEquals("a", list.get(4));
        Assert.assertEquals(null, list.get(5));

        current.forceSnapshot(previous);
        current.abort();
    }

    @Test
    public void offset2() {
        TList<String> list = new TList<String>();
        TListVersion a = new TListVersion(list.getSharedVersion_objectfabric());
        TListVersion b = new TListVersion(list.getSharedVersion_objectfabric());

        Transaction current = Transaction.start();
        Snapshot previous = current.getSnapshot();
        Snapshot snapshot = createSnapshot(a, b, null);
        current.forceSnapshot(snapshot);
        current.setPublicSnapshotVersions(snapshot.getWrites());

        b.setSize(6);
        a.setBit(3);
        a.set(3, "a");

        Assert.assertEquals(null, list.get(2));
        Assert.assertEquals("a", list.get(3));
        Assert.assertEquals(null, list.get(4));

        b.remove(2);

        Assert.assertEquals(null, list.get(1));
        Assert.assertEquals("a", list.get(2));
        Assert.assertEquals(null, list.get(3));

        current.forceSnapshot(previous);
        current.abort();
    }

    @Test
    public void offset3() {
        TList<String> list = new TList<String>();
        TListVersion a = new TListVersion(list.getSharedVersion_objectfabric());
        TListVersion b = new TListVersion(list.getSharedVersion_objectfabric());
        TListVersion c = new TListVersion(list.getSharedVersion_objectfabric());

        Transaction current = Transaction.start();
        Snapshot previous = current.getSnapshot();
        Snapshot snapshot = createSnapshot(a, b, c);
        current.forceSnapshot(snapshot);
        current.setPublicSnapshotVersions(snapshot.getWrites());

        c.setSize(6);
        a.setBit(3);
        a.set(3, "a");

        Assert.assertEquals(null, list.get(2));
        Assert.assertEquals("a", list.get(3));
        Assert.assertEquals(null, list.get(4));

        b.insert(2);
        c.remove(2);

        Assert.assertEquals(null, list.get(2));
        Assert.assertEquals("a", list.get(3));
        Assert.assertEquals(null, list.get(4));

        current.forceSnapshot(previous);
        current.abort();
    }

    @Test
    public void offset4() {
        TList<String> list = new TList<String>();
        TListVersion a = new TListVersion(list.getSharedVersion_objectfabric());
        TListVersion b = new TListVersion(list.getSharedVersion_objectfabric());
        TListVersion c = new TListVersion(list.getSharedVersion_objectfabric());

        Transaction current = Transaction.start();
        Snapshot previous = current.getSnapshot();
        Snapshot snapshot = createSnapshot(a, b, c);
        current.forceSnapshot(snapshot);
        current.setPublicSnapshotVersions(snapshot.getWrites());

        c.setSize(7);
        a.setBit(3);
        a.set(3, "a");

        Assert.assertEquals(null, list.get(2));
        Assert.assertEquals("a", list.get(3));
        Assert.assertEquals(null, list.get(4));

        b.insert(2);
        b.insert(2);
        b.insert(2);
        c.remove(2);

        Assert.assertEquals(null, list.get(4));
        Assert.assertEquals("a", list.get(5));
        Assert.assertEquals(null, list.get(6));

        current.forceSnapshot(previous);
        current.abort();
    }

    @Test
    public void merge1() {
        TList<String> list = new TList<String>();
        TListVersion a = new TListVersion(list.getSharedVersion_objectfabric());
        TListVersion b = new TListVersion(list.getSharedVersion_objectfabric());
        TListVersion c = new TListVersion(list.getSharedVersion_objectfabric());

        Transaction current = Transaction.start();
        Snapshot previous = current.getSnapshot();
        Snapshot snapshot = createSnapshot(a, b, null);
        current.forceSnapshot(snapshot);
        current.setPublicSnapshotVersions(snapshot.getWrites());

        b.setSize(5);
        a.setBit(3);
        a.set(3, "a");

        Assert.assertEquals(null, list.get(2));
        Assert.assertEquals("a", list.get(3));
        Assert.assertEquals(null, list.get(4));

        b.insert(2);
        c.setSize(5);
        c.remove(2);

        snapshot.getWrites()[2][0] = b.merge(b, c, 0);

        Assert.assertEquals(null, list.get(2));
        Assert.assertEquals("a", list.get(3));
        Assert.assertEquals(null, list.get(4));

        current.forceSnapshot(previous);
        current.abort();
    }

    @Test
    public void merge2() {
        TList<String> list = new TList<String>();
        TListVersion a = new TListVersion(list.getSharedVersion_objectfabric());
        TListVersion b = new TListVersion(list.getSharedVersion_objectfabric());
        TListVersion c = new TListVersion(list.getSharedVersion_objectfabric());

        Transaction current = Transaction.start();
        Snapshot previous = current.getSnapshot();
        Snapshot snapshot = createSnapshot(a, b, null);
        current.forceSnapshot(snapshot);
        current.setPublicSnapshotVersions(snapshot.getWrites());

        b.setSize(5);
        a.setBit(3);
        a.set(3, "a");

        Assert.assertEquals(null, list.get(2));
        Assert.assertEquals("a", list.get(3));
        Assert.assertEquals(null, list.get(4));

        b.remove(2);
        c.setSize(5);
        c.insert(2);

        snapshot.getWrites()[2][0] = b.merge(b, c, 0);

        Assert.assertEquals(null, list.get(2));
        Assert.assertEquals("a", list.get(3));
        Assert.assertEquals(null, list.get(4));

        current.forceSnapshot(previous);
        current.abort();
    }

    @Test
    public void cancel1() {
        TList<String> list = new TList<String>();
        TListVersion version = new TListVersion(list.getSharedVersion_objectfabric());
        version.insert(2);
        Assert.assertEquals(1, version.getInsertsCount());
        Assert.assertEquals(0, version.getRemovalsCount());
        version.remove(2);
        Assert.assertEquals(0, version.getInsertsCount());
        Assert.assertEquals(0, version.getRemovalsCount());
        version.remove(2);
        Assert.assertEquals(0, version.getInsertsCount());
        Assert.assertEquals(1, version.getRemovalsCount());
    }

    @Test
    public void cancel2() {
        TList<String> list = new TList<String>();
        TListVersion version = new TListVersion(list.getSharedVersion_objectfabric());
        ArrayList<Object> ref = new ArrayList<Object>();
        final int INITIAL = 1000;
        final int ADDED = 100;

        for (int i = 0; i < INITIAL; i++) {
            version.setBit(i);
            version.set(i, i);
            ref.add(i);
        }

        ArrayList<Object> inserts = new ArrayList<Object>();

        for (int i = 0; i < ADDED; i++) {
            int index = PlatformAdapter.getRandomInt(INITIAL);
            Object object = new Object();

            inserts.add(object);
            version.insert(index);
            version.set(index, object);
            ref.add(index, object);

            for (int t = 0; t < INITIAL + i + 1; t++)
                Assert.assertEquals(ref.get(t), version.get(t));

            Assert.assertEquals(inserts.size(), version.getInsertsCount());

            for (int t = 0; t < inserts.size(); t++) {
                int u = ref.indexOf(inserts.get(t));
                Assert.assertTrue(Arrays.binarySearch(version.getInserts(), 0, version.getInsertsCount(), u) >= 0);
            }
        }

        Collections.shuffle(inserts);

        for (int i = 0; i < inserts.size(); i++) {
            int index = ref.indexOf(inserts.get(i));
            version.remove(index);
            ref.remove(index);

            for (int t = 0; t < INITIAL + (ADDED - 1 - i); t++)
                Assert.assertEquals(ref.get(t), version.get(t));

            for (int t = i + 1; t < inserts.size(); t++) {
                int u = ref.indexOf(inserts.get(t));
                Assert.assertTrue(Arrays.binarySearch(version.getInserts(), 0, version.getInsertsCount(), u) >= 0);
            }
        }

        Assert.assertEquals(0, version.getInsertsCount());
        Assert.assertEquals(0, version.getRemovalsCount());
    }

    @Test
    public void cancel3() {
        TList<Integer> list = new TList<Integer>();
        ArrayList<Integer> ref = new ArrayList<Integer>();

        for (int i = 0; i < 10; i++) {
            list.add(i);
            ref.add(i);
        }

        Transaction current = Transaction.start();
        Snapshot previous = current.getSnapshot();
        TListVersion version = new TListVersion(list.getSharedVersion_objectfabric());
        Snapshot snapshot = createSnapshot(version, null, null);
        current.forceSnapshot(snapshot);
        current.setPublicSnapshotVersions(snapshot.getWrites());

        version.setSize(12);

        version.insert(2);
        version.setBit(2);
        version.set(2, 42);
        ref.add(2, 42);

        version.insert(2);
        version.setBit(2);
        version.set(2, 42);
        ref.add(2, 42);

        for (int i = 0; i < ref.size(); i++)
            Assert.assertEquals(ref.get(i), list.get(i));

        Assert.assertEquals(2, version.getInsertsCount());
        Assert.assertEquals(0, version.getRemovalsCount());
        version.remove(2);
        Assert.assertEquals(1, version.getInsertsCount());
        Assert.assertEquals(0, version.getRemovalsCount());
        version.remove(2);
        Assert.assertEquals(0, version.getInsertsCount());
        Assert.assertEquals(0, version.getRemovalsCount());

        ref.remove(2);
        ref.remove(2);

        for (int i = 0; i < ref.size(); i++)
            Assert.assertEquals(ref.get(i), list.get(i));

        current.forceSnapshot(previous);
        current.abort();
    }

    private Snapshot createSnapshot(TListVersion a, TListVersion b, TListVersion c) {
        Snapshot snapshot = new Snapshot();
        snapshot.setWrites(new TObject.Version[2 + (b != null ? 1 : 0) + (c != null ? 1 : 0)][]);
        snapshot.getWrites()[1] = new TObject.Version[1];
        snapshot.getWrites()[1][0] = a;
        a.onPublishing(snapshot, 0);

        if (b != null) {
            snapshot.getWrites()[2] = new TObject.Version[1];
            snapshot.getWrites()[2][0] = b;
            b.onPublishing(snapshot, 0);
        }

        if (c != null) {
            snapshot.getWrites()[3] = new TObject.Version[1];
            snapshot.getWrites()[3][0] = c;
            c.onPublishing(snapshot, 0);
        }

        return snapshot;
    }

    public static void main(String[] args) {
        TListVersionTest test = new TListVersionTest();
        test.cancel2();

        // long start = System.nanoTime();
        //
        // for (int i = 0; i < 10; i++)
        // test.run3();
        //
        // System.out.println("Perf test : " + (System.nanoTime() - start) / 1e6
        // + " ms");
    }
}
