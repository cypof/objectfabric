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

import org.junit.Assert;
import org.junit.Test;

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
        Workspace workspace = Platform.newTestWorkspace();
        TMap<Integer, String> map = new TMap<Integer, String>(workspace.resolve(""));

        map.addListener(new KeyListener<Integer>() {

            public void onPut(Integer key) {
                _added.add(key);
            }

            public void onRemove(Integer key) {
                _removed.add(key);
            }

            public void onClear() {
                _cleared = true;
            }
        });

        map.put(45, "Blah");
        workspace.flushNotifications();

        Assert.assertEquals(1, _added.size());
        Assert.assertEquals(0, _removed.size());
        Assert.assertFalse(_cleared);
        Assert.assertTrue(_added.get(0) == 45);

        map.remove(45);
        workspace.flushNotifications();

        Assert.assertEquals(1, _added.size());
        Assert.assertEquals(1, _removed.size());
        Assert.assertTrue(_added.get(0) == 45);
        Assert.assertTrue(_removed.get(0) == 45);
        Assert.assertFalse(_cleared);

        map.clear();
        workspace.flushNotifications();

        Assert.assertEquals(1, _added.size());
        Assert.assertEquals(1, _removed.size());
        Assert.assertTrue(_added.get(0) == 45);
        Assert.assertTrue(_removed.get(0) == 45);
        Assert.assertTrue(_cleared);

        workspace.close();
    }
}
