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


import com.objectfabric.TArrayInteger;
import com.objectfabric.TArrayTObject;
import com.objectfabric.TIndexedNRead;
import com.objectfabric.TList;
import com.objectfabric.TMap;
import com.objectfabric.TObject;
import com.objectfabric.Transaction;
import com.objectfabric.TransactionSets;
import com.objectfabric.TObject.UserTObject;
import com.objectfabric.TObject.Version;
import com.objectfabric.Transaction.Granularity;
import com.objectfabric.generated.Limit32;
import com.objectfabric.generated.LimitN;
import com.objectfabric.misc.Bits;
import com.objectfabric.misc.Debug;
import com.objectfabric.misc.PlatformAdapter;

public class CrossHelper {

    public static final int INCREMENTS = 10;

    public static int update(Limit32 c32, LimitN cN, TMap<Integer, Integer> map, TList<Integer> listIndexes, TList<Integer> listCounters, TArrayTObject<Limit32> arrayTObjects, TArrayInteger ref, int flags) {
        ArrayList<Integer> previous = new ArrayList<Integer>();

        for (int i = 0; i < INCREMENTS; i++) {
            int index, value;

            for (;;) {
                index = PlatformAdapter.getRandomInt(LimitN.FIELD_COUNT);

                // To assert increments of 1 when granularity is ALL
                if (!previous.contains(index)) {
                    previous.add(index);
                    break;
                }
            }

            //

            if (index < Limit32.FIELD_COUNT) {
                value = (Integer) c32.getField(index);
                Debug.assertAlways(value == ref.get(index));
                c32.setField(index, value + 1);
            }

            //

            value = (Integer) cN.getField(index);
            Debug.assertAlways(value == ref.get(index));
            cN.setField(index, value + 1);

            //

            value = map.get(index) != null ? map.get(index) : 0;
            Debug.assertAlways(value == ref.get(index));
            map.put(index, value + 1);

            //

            int listIndex = listIndexes.indexOf(index);

            if (listIndex == -1) {
                Debug.assertAlways(listIndexes.size() == listCounters.size());

                listIndex = listIndexes.size();
                listIndexes.add(index);
                listCounters.add(0);

                Debug.assertAlways(listIndexes.size() == listCounters.size());
            }

            value = listCounters.get(listIndex);
            Debug.assertAlways(value == ref.get(index));
            listCounters.set(listIndex, value + 1);

            Debug.assertAlways(map.size() == listIndexes.size());

            //

            Limit32 object = arrayTObjects.get(index);

            if (object != null) {
                value = object.getInt0();
                Debug.assertAlways(value == ref.get(index));
                arrayTObjects.set(index, object = new Limit32());
                object.setInt0(value + 1);
            } else {
                arrayTObjects.set(index, object = new Limit32());
                object.setInt0(1);
            }

            //

            ref.set(index, ref.get(index) + 1);

            Bits.Entry[] a = getBits(Transaction.getCurrent().getWrites(), cN);
            Bits.Entry[] b = getBits(Transaction.getCurrent().getWrites(), ref);

            if (a != null && b != null)
                Debug.assertAlways(Bits.get(a, index) == Bits.get(b, index));
        }

        int delta = INCREMENTS;

        if ((flags & Cross.FLAG_RESETS) != 0) {
            for (int i = 0; i < 3; i++) {
                int index = PlatformAdapter.getRandomInt(LimitN.FIELD_COUNT);

                if (index < Limit32.FIELD_COUNT)
                    c32.setField(index, 0);

                int before = (Integer) cN.getField(index);
                delta -= before;
                cN.setField(index, 0);

                map.remove(index);

                int listIndex = listIndexes.indexOf(index);

                if (listIndex != -1) {
                    listIndexes.remove(listIndex);
                    listCounters.remove(listIndex);
                }

                arrayTObjects.set(index, null);
                ref.set(index, 0);
            }
        }

        return delta;
    }

    public static void check(Limit32 c32, LimitN cN, TMap<Integer, Integer> map, TList<Integer> listIndexes, TList<Integer> listCounters, TArrayTObject<Limit32> arrayTObjects, TArrayInteger ref, int flags) {
        for (int i = 0; i < LimitN.FIELD_COUNT; i++)
            check(c32, cN, map, listIndexes, listCounters, arrayTObjects, ref, null, i, flags);
    }

    public static void check(Limit32 c32, LimitN cN, TMap<Integer, Integer> map, TList<Integer> listIndexes, TList<Integer> listCounters, TArrayTObject<Limit32> arrayTObjects, TArrayInteger ref, int[] last, int i, int flags) {
        if (i < Limit32.FIELD_COUNT)
            Debug.assertAlways(((Integer) c32.getField(i)) == ref.get(i));

        Transaction initial = Transaction.getCurrent();
        Transaction transaction = initial;

        if (initial == null)
            transaction = Transaction.start();

        Bits.Entry[] a = getSnapshotBits(transaction, cN);
        Bits.Entry[] b = getSnapshotBits(transaction, ref);

        if (a != null && b != null) {
            Debug.assertAlways(a.length == b.length);
            Debug.assertAlways(Bits.get(a, i) == Bits.get(b, i));
        }

        if (initial == null)
            transaction.abort();

        Debug.assertAlways((Integer) cN.getField(i) == ref.get(i));
        Debug.assertAlways((map.get(i) != null ? map.get(i) : 0) == ref.get(i));

        int value = 0;
        int index = listIndexes.indexOf(i);

        if (index >= 0)
            value = listCounters.get(index) != null ? listCounters.get(index) : 0;

        Debug.assertAlways(value == ref.get(i));
        int size = 0;

        for (int j = 0; j < LimitN.FIELD_COUNT; j++)
            if (ref.get(j) != 0)
                size++;

        Debug.assertAlways(size == map.size());
        Debug.assertAlways(listIndexes.size() == map.size());
        Debug.assertAlways(listCounters.size() == map.size());

        Limit32 object = arrayTObjects.get(i);

        if (object != null)
            Debug.assertAlways(object.getInt0() == ref.get(i));
        else
            Debug.assertAlways(0 == ref.get(i));

        if (last != null && ref.getTrunk().getGranularity() == Granularity.ALL) {
            if (ref.get(i) == 0) {
                if (Debug.ENABLED)
                    Debug.assertion((flags & Cross.FLAG_RESETS) != 0);

                last[i] = 0;
            } else
                Debug.assertAlways(ref.get(i) == ++last[i]);
        }
    }

    private static Bits.Entry[] getSnapshotBits(Transaction transaction, TObject object) {
        if (transaction.getSnapshot().getWrites().length == 2)
            return getBits(transaction.getSnapshot().getWrites()[1], object);

        return null;
    }

    private static Bits.Entry[] getBits(Version[] versions, TObject object) {
        TIndexedNRead read = null;

        if (versions != null)
            read = (TIndexedNRead) TransactionSets.getVersionFromTObject(versions, (UserTObject) object);

        return read != null ? read.getBits() : null;
    }
}
