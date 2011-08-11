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

import com.objectfabric.TIndexedNRead;
import com.objectfabric.TIndexedNVersion;
import com.objectfabric.misc.Bits;
import com.objectfabric.misc.PlatformAdapter;

public class BitsTest {

    @Test
    public void sparseReads() {
        final int SIZE = 1000;

        TIndexedNVersion shared = new TIndexedNVersion(null, 10) {
        };
        TIndexedNRead tester = new TIndexedNRead(shared);

        for (int set = 0; set < SIZE; set++) {
            for (int i = 0; i < SIZE; i++)
                Assert.assertTrue(tester.getBit(i) == (i < set));

            tester.setBit(set);
        }
    }

    @Test
    public void sparseWrites() {
        final int SIZE = 1000;

        TIndexedNVersion shared = new TIndexedNVersion(null, 10) {
        };
        TIndexedNVersion tester = new TIndexedNVersion(shared, SIZE) {
        };

        for (int set = 0; set < SIZE; set++) {
            for (int i = 0; i < SIZE; i++)
                Assert.assertTrue(tester.getBit(i) == (i < set));

            tester.setBit(set);
        }
    }

    @Test
    public void randomReads() {
        final int SIZE = 10000;
        boolean[] array = new boolean[SIZE];

        TIndexedNVersion shared = new TIndexedNVersion(null, 10) {
        };
        TIndexedNRead tester = new TIndexedNRead(shared);

        for (int i = 0; i < SIZE / 10; i++) {
            int index = PlatformAdapter.getRandomInt(SIZE);
            array[index] = true;
            tester.setBit(index);
        }

        for (int i = 0; i < SIZE; i++)
            Assert.assertTrue(array[i] == tester.getBit(i));
    }

    @Test
    public void randomWrites() {
        final int SIZE = 10000;
        boolean[] array = new boolean[SIZE];

        TIndexedNVersion shared = new TIndexedNVersion(null, 10) {
        };
        TIndexedNVersion tester = new TIndexedNVersion(shared, SIZE) {
        };

        for (int i = 0; i < SIZE / 10; i++) {
            int index = PlatformAdapter.getRandomInt(SIZE);
            array[index] = true;
            tester.setBit(index);
        }

        for (int i = 0; i < SIZE; i++)
            Assert.assertTrue(array[i] == tester.getBit(i));
    }

    @Test
    public void remove32() {
        for (int i = 0; i < 32; i++) {
            int set = Bits.set(0, i);

            for (int r = 0; r < 32; r++) {
                int test = Bits.remove(set, r);

                if (r == i)
                    Assert.assertEquals(0, test);
                else if (r > i)
                    Assert.assertEquals(Bits.set(0, i), test);
                else
                    Assert.assertEquals(Bits.set(0, i - 1), test);
            }
        }
    }

    public static void main(String[] args) throws Exception {
        BitsTest test = new BitsTest();
        test.remove32();
    }
}
