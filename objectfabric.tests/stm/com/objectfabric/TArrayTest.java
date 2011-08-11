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

import org.junit.Assert;
import org.junit.Test;

import com.objectfabric.TArrayDouble;
import com.objectfabric.TArrayInteger;
import com.objectfabric.TArrayVersionDouble;
import com.objectfabric.TArrayVersionInteger;
import com.objectfabric.misc.Bits;
import com.objectfabric.misc.PlatformAdapter;

public class TArrayTest {

    @Test
    public void run1() {
        TArrayInteger array = new TArrayInteger(100);
        TArrayVersionInteger version = (TArrayVersionInteger) array.getSharedVersion_objectfabric().createVersion();

        Assert.assertTrue(version.getValues() == null);

        for (int i = 0; i < 10; i++) {
            version.setBit(i);
            version.set(i, i);
        }

        Assert.assertTrue(version.getValues().length == Bits.SPARSE_BITSET_DEFAULT_CAPACITY);
        Assert.assertTrue(version.getValues()[0] != null);

        for (int i = 1; i < version.getValues().length; i++)
            Assert.assertTrue(version.getValues()[i] == null);

        for (int i = 0; i < 100; i++)
            Assert.assertTrue(i < 10 ? version.get(i) == i : version.get(i) == 0);

        Assert.assertTrue(version.getValues().length == Bits.SPARSE_BITSET_DEFAULT_CAPACITY);

        for (int i = 0; i < 1000; i++) {
            version.setBit(i);
            version.set(i, i);
        }

        Assert.assertTrue(version.getValues().length == 32);

        for (int i = 0; i < 1000; i++)
            Assert.assertTrue(version.get(i) == i);
    }

    @Test
    public void run2() {
        TArrayInteger array = new TArrayInteger(100);
        TArrayVersionInteger version = (TArrayVersionInteger) array.getSharedVersion_objectfabric().createVersion();
        int[] ref = new int[256];

        for (int i = 0; i < 8; i++) {
            int index = PlatformAdapter.getRandomInt(256);
            ref[index] = i;
            version.setBit(index);
            version.set(index, i);
        }

        Assert.assertTrue(version.getValues().length <= 8); // 256 / 32

        for (int i = 0; i < 256; i++)
            Assert.assertTrue(version.get(i) == ref[i]);

        for (int i = 0; i < 256; i++) {
            version.setBit(i);
            version.set(i, i);
        }

        Assert.assertTrue(version.getValues().length == 8);

        for (int i = 0; i < 256; i++)
            Assert.assertTrue(version.get(i) == i);
    }

    @Test
    public void run3() {
        final int LENGTH = (int) 1e4;
        TArrayDouble array = new TArrayDouble(LENGTH);
        TArrayVersionDouble version = (TArrayVersionDouble) array.getSharedVersion_objectfabric().createVersion();

        double[] test = new double[LENGTH];
        int count = 0;

        for (int i = 0; i < test.length; i++) {
            double value = PlatformAdapter.getRandomDouble();

            if (value < 1.0 / 1000) {
                test[i] = value;
                version.setBit(i);
                version.set(i, value);
                count++;
            }
        }

        Assert.assertTrue(version.getValues() != null);
        System.out.println(count + " -> " + version.getValues().length);

        for (int i = 0; i < test.length; i++) {
            if (test[i] != 0)
                Assert.assertTrue(test[i] == version.get(i));
            else
                Assert.assertTrue(version.get(i) == 0);
        }
    }

    public static void main(String[] args) {
        TArrayTest test = new TArrayTest();
        test.run1();
        test.run2();

        long start = System.nanoTime();

        for (int i = 0; i < 10; i++)
            test.run3();

        System.out.println("Perf test : " + (System.nanoTime() - start) / 1e6 + " ms");
    }
}
