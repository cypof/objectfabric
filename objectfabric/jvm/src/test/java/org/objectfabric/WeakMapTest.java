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

public class WeakMapTest extends TestsHelper {

    private static final class Value {

        final String Key;

        Value(String key) {
            Key = key;
        }
    }

    @Test
    public void run1() throws Exception {
        final List<Value> refs = new List<Value>();

        WeakCache<String, Value> map = new WeakCache<String, Value>() {

            @Override
            Value create(String key) {
                Value value = new Value(key);
                refs.add(value);
                return value;
            }

            @Override
            void onAdded(PlatformRef<Value> ref) {
            }
        };

        for (int i = 0; i < 1000; i++)
            map.getOrCreate("" + i);

        Assert.assertEquals(1000, count(map));
        System.gc();
        Thread.sleep(50);
        Assert.assertEquals(1000, count(map));

        for (int i = 0; i < 1000; i++)
            Assert.assertEquals("" + i, map.get("" + i).Key);

        Assert.assertEquals(1000, map.getInternalMapSize());
        refs.clear();
        System.gc();
        Assert.assertEquals(0, count(map));

        while (map.getInternalMapSize() > 0)
            Thread.sleep(1);
    }

    @SuppressWarnings("unused")
    private static int count(WeakCache<String, Value> map) {
        int count = 0;

        for (Value value : map.values())
            count++;

        return count;
    }
}
