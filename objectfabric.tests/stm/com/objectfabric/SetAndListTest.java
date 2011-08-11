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

import java.util.HashSet;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import com.objectfabric.TObject.Version;
import com.objectfabric.generated.SimpleClass;
import com.objectfabric.misc.Debug;
import com.objectfabric.misc.PlatformAdapter;

public class SetAndListTest {

    @Before
    public void disableCheck() {
        if (Debug.ENABLED)
            Helper.getInstance().disableEqualsOrHashCheck();
    }

    @After
    public void enableCheck() {
        if (Debug.ENABLED)
            Helper.getInstance().enableEqualsOrHashCheck();
    }

    @Test
    public void versions() {
        VersionSetAndList object = new VersionSetAndList();
        object.add(new SimpleClass().getSharedVersion_objectfabric());
        object.add(new SimpleClass().getSharedVersion_objectfabric());
        Version a = new SimpleClass().getSharedVersion_objectfabric();
        Version b = new SimpleClass().getSharedVersion_objectfabric();
        object.add(a);
        object.add(b);
        Assert.assertEquals(4, object.size());
        object.add(a);
        Assert.assertEquals(4, object.size());
        object.poll();
        Assert.assertEquals(3, object.size());
        object.add(a);
        Assert.assertEquals(3, object.size());
        object.add(b);
        Assert.assertEquals(4, object.size());
        object.poll();
        Assert.assertEquals(3, object.size());
        object.poll();
        object.poll();
        Assert.assertEquals(1, object.size());
        object.poll();
        Assert.assertEquals(0, object.size());

        HashSet<SimpleClass> ref = new HashSet<SimpleClass>();

        for (int cycle = 0; cycle < 1000; cycle++) {
            if (PlatformAdapter.getRandomDouble() < 0.6 || object.size() == 0) {
                SimpleClass o = new SimpleClass();
                Version v = o.getSharedVersion_objectfabric();
                ref.add(o);
                object.add(v);
            } else {
                SimpleClass o = (SimpleClass) object.poll().getReference().get();
                ref.remove(o);
            }

            Assert.assertEquals(ref.size(), object.size());

            for (SimpleClass o : ref)
                Assert.assertTrue(object.contains(o.getSharedVersion_objectfabric()));

            for (int i = 0; i < object.size(); i++) {
                Version v = object.get(i);
                Assert.assertTrue(ref.contains(v.getReference().get()));
            }
        }
    }

    @Test
    public void objects() {
        UserTObjectSetAndList<SimpleClass> object = new UserTObjectSetAndList<SimpleClass>();
        object.add(new SimpleClass());
        object.add(new SimpleClass());
        SimpleClass a = new SimpleClass();
        SimpleClass b = new SimpleClass();
        object.add(a);
        object.add(b);
        Assert.assertEquals(4, object.size());
        object.add(a);
        Assert.assertEquals(4, object.size());
        object.poll();
        Assert.assertEquals(3, object.size());
        object.add(a);
        Assert.assertEquals(3, object.size());
        object.add(b);
        Assert.assertEquals(4, object.size());
        object.poll();
        Assert.assertEquals(3, object.size());
        object.poll();
        object.poll();
        Assert.assertEquals(1, object.size());
        object.poll();
        Assert.assertEquals(0, object.size());

        HashSet<SimpleClass> ref = new HashSet<SimpleClass>();

        for (int cycle = 0; cycle < 1000; cycle++) {
            if (PlatformAdapter.getRandomDouble() < 0.6 || object.size() == 0) {
                SimpleClass o = new SimpleClass();
                ref.add(o);
                object.add(o);
            } else {
                SimpleClass o = object.poll();
                ref.remove(o);
            }

            Assert.assertEquals(ref.size(), object.size());

            for (SimpleClass o : ref)
                Assert.assertTrue(object.contains(o));

            for (int i = 0; i < object.size(); i++) {
                SimpleClass o = object.get(i);
                Assert.assertTrue(ref.contains(o));
            }
        }
    }

    public static void main(String[] args) {
        SetAndListTest test = new SetAndListTest();
        test.versions();
    }
}
