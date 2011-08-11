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
import java.util.concurrent.Executor;

import org.junit.Assert;
import org.junit.Test;

import com.objectfabric.AsyncOptions;
import com.objectfabric.KeyListener;
import com.objectfabric.TMap;
import com.objectfabric.misc.PlatformAdapter;
import com.objectfabric.misc.TransparentExecutor;

public class TMapListenerTest extends TestsHelper {

    private final ArrayList<Integer> _added = new ArrayList<Integer>();

    private final ArrayList<Integer> _removed = new ArrayList<Integer>();

    private boolean _cleared;

    @Override
    public void before() {
        super.before();

        _added.clear();
        _removed.clear();
        _cleared = false;
    }

    @Test
    public void test1() {
        TMap<Integer, String> map = new TMap<Integer, String>();

        map.addListener(new KeyListener<Integer>() {

            public void onPut(Integer key) {
                _added.add(key);
            }

            public void onRemoved(Integer key) {
                _removed.add(key);
            }

            public void onCleared() {
                _cleared = true;
            }
        }, new AsyncOptions() {

            @Override
            public Executor getExecutor() {
                return TransparentExecutor.getInstance();
            }
        });

        map.put(45, "Blah");

        Assert.assertEquals(1, _added.size());
        Assert.assertEquals(0, _removed.size());
        Assert.assertFalse(_cleared);
        Assert.assertTrue(_added.get(0) == 45);

        map.remove(45);

        Assert.assertEquals(1, _added.size());
        Assert.assertEquals(1, _removed.size());
        Assert.assertTrue(_added.get(0) == 45);
        Assert.assertTrue(_removed.get(0) == 45);
        Assert.assertFalse(_cleared);

        map.clear();

        Assert.assertEquals(1, _added.size());
        Assert.assertEquals(1, _removed.size());
        Assert.assertTrue(_added.get(0) == 45);
        Assert.assertTrue(_removed.get(0) == 45);
        Assert.assertTrue(_cleared);

        PlatformAdapter.reset();
    }

    public static void main(String[] args) {
        TMapListenerTest test = new TMapListenerTest();

        for (int i = 0; i < 100; i++) {
            test.test1();
            test.before();
        }
    }
}
