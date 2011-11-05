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

import com.objectfabric.misc.PlatformAdapter;
import com.objectfabric.misc.TransparentExecutor;

public class TListListenerTest extends TestsHelper {

    private final ArrayList<Integer> _added = new ArrayList<Integer>();

    private final ArrayList<Integer> _removed = new ArrayList<Integer>();

    private boolean _cleared;

    @Test
    public void test1() {
        TList<String> list = new TList<String>();

        list.addListener(new ListListener() {

            public void onAdded(int index) {
                _added.add(index);
            }

            public void onRemoved(int index) {
                _removed.add(index);
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

        list.add("Blah");

        Assert.assertEquals(1, _added.size());
        Assert.assertEquals(0, _removed.size());
        Assert.assertFalse(_cleared);
        Assert.assertTrue(_added.get(0) == 0);

        list.remove(0);

        Assert.assertEquals(1, _added.size());
        Assert.assertTrue(_added.get(0) == 0);
        Assert.assertEquals(1, _removed.size());
        Assert.assertTrue(_removed.get(0) == 0);
        Assert.assertFalse(_cleared);

        list.clear();

        Assert.assertEquals(1, _added.size());
        Assert.assertTrue(_added.get(0) == 0);
        Assert.assertEquals(1, _removed.size());
        Assert.assertTrue(_removed.get(0) == 0);
        Assert.assertTrue(_cleared);

        PlatformAdapter.reset();
    }

    public static void main(String[] args) {
        TListListenerTest test = new TListListenerTest();
        test.test1();
    }
}
