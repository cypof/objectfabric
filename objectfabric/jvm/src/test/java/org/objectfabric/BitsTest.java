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

public class BitsTest extends TestsHelper {

    @Test
    public void sparseReads() {
        Workspace workspace = Platform.newTestWorkspace();
        final int SIZE = 100;
        TIndexedNRead tester = new TIndexedNRead();

        tester.setObject(new TObject(workspace.open("")));

        for (int set = 0; set < SIZE; set++) {
            for (int i = 0; i < SIZE; i++)
                Assert.assertTrue(tester.getBit(i) == (i < set));

            tester.setBit(set);
        }

        workspace.close();
    }

    @Test
    public void sparseWrites() {
        Workspace workspace = Platform.newTestWorkspace();
        final int SIZE = 100;
        TIndexedNRead tester = new TIndexedNRead();
        tester.setObject(new TObject(workspace.open("")));

        for (int set = 0; set < SIZE; set++) {
            for (int i = 0; i < SIZE; i++)
                Assert.assertTrue(tester.getBit(i) == (i < set));

            tester.setBit(set);
        }

        workspace.close();
    }

    @Test
    public void randomReads() {
        Workspace workspace = Platform.newTestWorkspace();
        final int SIZE = 1000;
        boolean[] array = new boolean[SIZE];
        TIndexedNRead tester = new TIndexedNRead();
        tester.setObject(new TObject(workspace.open("")));

        for (int i = 0; i < SIZE / 10; i++) {
            int index = Platform.get().randomInt(SIZE);
            array[index] = true;
            tester.setBit(index);
        }

        for (int i = 0; i < SIZE; i++)
            Assert.assertTrue(array[i] == tester.getBit(i));

        workspace.close();
    }

    @Test
    public void randomWrites() {
        Workspace workspace = Platform.newTestWorkspace();
        final int SIZE = 1000;
        boolean[] array = new boolean[SIZE];
        TIndexedNRead tester = new TIndexedNRead();
        tester.setObject(new TObject(workspace.open("")));

        for (int i = 0; i < SIZE / 10; i++) {
            int index = Platform.get().randomInt(SIZE);
            array[index] = true;
            tester.setBit(index);
        }

        for (int i = 0; i < SIZE; i++)
            Assert.assertTrue(array[i] == tester.getBit(i));

        workspace.close();
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
}
