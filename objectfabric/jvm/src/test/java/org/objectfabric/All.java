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
import java.util.concurrent.atomic.AtomicInteger;

import org.objectfabric.TObject.Transaction;
import org.objectfabric.TObject.Version;
import org.objectfabric.Workspace.Granularity;
import org.objectfabric.generated.Limit32;
import org.objectfabric.generated.LimitN;
import org.objectfabric.generated.LimitsObjectModel;

@SuppressWarnings({ "unchecked", "rawtypes" })
class All {

    public static final int INCREMENTS = 10;

    final TMap<String, TObject> Root;

    final AtomicInteger ListenerCount = new AtomicInteger();

    All(final Resource resource, final int writers, final int flags) {
        LimitsObjectModel.register();

        LimitN limitN = new LimitN(resource);
        Root = new TMap<String, TObject>(resource);
        int n = 0;
        Root.put("" + n++, limitN);
        Root.put("" + n++, new Limit32(resource));
        Root.put("" + n++, new TMap<Integer, Integer>(resource));

        // Create arrays using object model to also test .NET
        TType[] type = new TType[] { Immutable.BYTE.type() };
        Root.put("" + n++, Platform.get().defaultObjectModel().createInstance(resource, -LimitN.FIELD_COUNT - 1, type));
        // Object
        Root.put("" + n++, Platform.get().defaultObjectModel().createInstance(resource, -LimitN.FIELD_COUNT - 1, null));
        type = new TType[] { TObject.TYPE };
        Root.put("" + n++, Platform.get().defaultObjectModel().createInstance(resource, -LimitN.FIELD_COUNT - 1, type));

        final int[] last = new int[LimitN.FIELD_COUNT];

        limitN.addListener(new IndexListener() {

            @SuppressWarnings("null")
            @Override
            public void onSet(final int field) {
                resource.atomicRead(new Runnable() {

                    @Override
                    public void run() {
                        check(Root, flags, field, writers == 1 ? last : null);
                        ListenerCount.incrementAndGet();
                    }
                });
            }
        });
    }

    public static int update(TMap<String, TObject> root, int flags) {
        int n = 0;
        LimitN limitN = (LimitN) root.get("" + n++);
        Limit32 limit32 = (Limit32) root.get("" + n++);
        TMap<Integer, Integer> map = (TMap) root.get("" + n++);
        TIndexed byteArray = (TIndexed) root.get("" + n++);
        TIndexed objectArray = (TIndexed) root.get("" + n++);
        TIndexed tObjectArray = (TIndexed) root.get("" + n++);

        Workspace workspace = limit32.resource().workspaceImpl();
        ArrayList<Integer> previous = new ArrayList<Integer>();

        for (int i = 0; i < INCREMENTS; i++) {
            int index, value;

            for (;;) {
                index = Platform.get().randomInt(LimitN.FIELD_COUNT);

                // To assert increments of 1 when granularity is ALL
                if (!previous.contains(index)) {
                    previous.add(index);
                    break;
                }
            }

            //

            int ref = (Integer) limitN.getField(index);
            limitN.setField(index, ref + 1);

            //

            if (index < Limit32.FIELD_COUNT) {
                value = (Integer) limit32.getField(index);
                Debug.assertAlways(value == ref);
                limit32.setField(index, value + 1);
            }

            //

            value = map.get(index) != null ? map.get(index) : 0;
            Debug.assertAlways(value == ref);
            map.put(index, value + 1);

            //

            value = (Byte) byteArray.getAsObject(index);
            Debug.assertAlways(value == ref);
            byteArray.setAsObject(index, (byte) (value + 1));

            //

            Object boxed = objectArray.getAsObject(index);

            if (boxed instanceof Byte) {
                value = (Byte) boxed;
                Debug.assertAlways(value == ref);
                objectArray.setAsObject(index, value + 1);
            } else
                objectArray.setAsObject(index, 1);

            //

            Limit32 object = (Limit32) tObjectArray.getAsObject(index);

            if (object != null) {
                value = object.int20();
                Debug.assertAlways(value == ref);
                object.int20(value + 1);
                tObjectArray.setAsObject(index, object);
            } else {
                object = new Limit32(tObjectArray.resource());
                object.int20(1);
                tObjectArray.setAsObject(index, object);
            }

            //

            Version[] writes = workspace.transaction().getWrites();
            Bits.Entry[] a = getBits(writes, limitN);
            Bits.Entry[] b = getBits(writes, byteArray);

            if (a != null && b != null)
                Debug.assertAlways(Bits.get(a, index) == Bits.get(b, index));
        }

        int delta = INCREMENTS;

        if ((flags & Multi.FLAG_RESETS) != 0) {
            for (n = 0; n < 3; n++) {
                int index = Platform.get().randomInt(LimitN.FIELD_COUNT);
                int before = (Integer) limitN.getField(index);
                delta -= before;
                limitN.setField(index, 0);

                if (index < Limit32.FIELD_COUNT)
                    limit32.setField(index, 0);

                map.remove(index);
                byteArray.setAsObject(index, (byte) 0);
                objectArray.setAsObject(index, null);
                tObjectArray.setAsObject(index, null);
            }
        }

        return delta;
    }

    public static void check(TMap<String, TObject> root, int flags) {
        for (int index = 0; index < LimitN.FIELD_COUNT; index++)
            check(root, flags, index, null);
    }

    public static void check(TMap<String, TObject> root, int flags, final int index, int[] last) {
        int n = 0;
        final LimitN limitN = (LimitN) root.get("" + n++);
        final Limit32 limit32 = (Limit32) root.get("" + n++);
        final TMap<Integer, Integer> map = (TMap) root.get("" + n++);
        final TIndexed byteArray = (TIndexed) root.get("" + n++);
        final TIndexed objectArray = (TIndexed) root.get("" + n++);
        final TIndexed tObjectArray = (TIndexed) root.get("" + n++);

        int ref = (Integer) limitN.getField(index);
        final Workspace workspace = limitN.resource().workspaceImpl();

        workspace.atomic(new Runnable() {

            @Override
            public void run() {
                Bits.Entry[] a = getSnapshotBits(workspace.transaction(), limitN);
                Bits.Entry[] b = getSnapshotBits(workspace.transaction(), byteArray);

                if (a != null && b != null) {
                    Debug.assertAlways(a.length == b.length);
                    Debug.assertAlways(Bits.get(a, index) == Bits.get(b, index));
                }
            }
        });

        Debug.assertAlways((Integer) limitN.getField(index) == ref);

        if (index < Limit32.FIELD_COUNT)
            Debug.assertAlways(((Integer) limit32.getField(index)) == ref);

        Debug.assertAlways((map.get(index) != null ? map.get(index) : 0) == ref);
        Debug.assertAlways((Byte) byteArray.getAsObject(index) == ref);

        Object boxed = objectArray.getAsObject(index);

        if (boxed instanceof Byte)
            Debug.assertAlways((Byte) boxed == ref);
        else
            Debug.assertAlways(ref == 0);

        //

        Limit32 object = (Limit32) tObjectArray.getAsObject(index);

        if (object != null)
            Debug.assertAlways(object.int20() == ref);
        else
            Debug.assertAlways(ref == 0);

        //

        if (last != null) {
            if (ref == 0) {
                if (Debug.ENABLED)
                    Debug.assertion((flags & Multi.FLAG_RESETS) != 0);

                last[index] = 0;
            } else
                last[index]++;

            if (workspace.granularity() == Granularity.COALESCE)
                Debug.assertAlways(ref >= last[index]);
            else if (workspace.granularity() == Granularity.ALL)
                Debug.assertAlways(ref == last[index]);
        }
    }

    private static Bits.Entry[] getSnapshotBits(Transaction transaction, TObject object) {
        if (transaction.getSnapshot().writes().length == 2)
            return getBits(transaction.getSnapshot().writes()[1], object);

        return null;
    }

    private static Bits.Entry[] getBits(Version[] versions, TObject object) {
        TIndexedNRead read = null;

        if (versions != null)
            read = (TIndexedNRead) TransactionBase.getVersion(versions, object);

        return read != null ? read.getBits() : null;
    }
}
