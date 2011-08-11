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

import junit.framework.Assert;

import org.junit.Test;

import com.objectfabric.TList;
import com.objectfabric.TMap;
import com.objectfabric.Transaction;
import com.objectfabric.TObject.UserTObject;
import com.objectfabric.generated.ReferencesClass;
import com.objectfabric.generated.SimpleClass;

public class UserReferencesTest {

    @Test
    public void testTIndexed32() {
        ReferencesClass object = new ReferencesClass();
        Assert.assertEquals(0, ((UserTObject) object).getUserReferencesAsList().size());
        object.setInt(0);
        Assert.assertEquals(0, ((UserTObject) object).getUserReferencesAsList().size());
        object.setRef(new ReferencesClass());
        object.setRef(new ReferencesClass());
        Assert.assertEquals(1, ((UserTObject) object).getUserReferencesAsList().size());
        object.setRef(null);
        Assert.assertEquals(0, ((UserTObject) object).getUserReferencesAsList().size());
        Transaction a = Transaction.start();
        object.setRef(new ReferencesClass());
        Assert.assertEquals(0, ((UserTObject) object).getUserReferencesAsList().size());
        a.commit();
        Assert.assertEquals(1, ((UserTObject) object).getUserReferencesAsList().size());
        Transaction b = Transaction.start();
        object.setRef(null);
        Assert.assertEquals(1, ((UserTObject) object).getUserReferencesAsList().size());
        b.commit();
        Assert.assertEquals(0, ((UserTObject) object).getUserReferencesAsList().size());
        Transaction c = Transaction.start();
        ReferencesClass t = new ReferencesClass();
        object.setRef(t);
        object.setRef2(t);
        Assert.assertEquals(0, ((UserTObject) object).getUserReferencesAsList().size());
        c.commit();
        Assert.assertEquals(1, ((UserTObject) object).getUserReferencesAsList().size());
        object.setRef(null);
        Assert.assertEquals(1, ((UserTObject) object).getUserReferencesAsList().size());
        object.setRef2(null);
        Assert.assertEquals(0, ((UserTObject) object).getUserReferencesAsList().size());
    }

    @Test
    public void testMap1() {
        TMap<SimpleClass, Integer> object = new TMap<SimpleClass, Integer>();
        Assert.assertEquals(0, ((UserTObject) object).getUserReferencesAsList().size());
        SimpleClass key = new SimpleClass();
        object.put(key, 42);
        Assert.assertEquals(1, ((UserTObject) object).getUserReferencesAsList().size());
        object.remove(key);
        Assert.assertEquals(0, ((UserTObject) object).getUserReferencesAsList().size());
        Transaction a = Transaction.start();
        object.put(key, 42);
        Assert.assertEquals(0, ((UserTObject) object).getUserReferencesAsList().size());
        a.commit();
        Assert.assertEquals(1, ((UserTObject) object).getUserReferencesAsList().size());
        Transaction b = Transaction.start();
        object.remove(key);
        Assert.assertEquals(1, ((UserTObject) object).getUserReferencesAsList().size());
        b.commit();
        Assert.assertEquals(0, ((UserTObject) object).getUserReferencesAsList().size());
    }

    @Test
    public void testMap2() {
        TMap<Integer, SimpleClass> object = new TMap<Integer, SimpleClass>();
        Assert.assertEquals(0, ((UserTObject) object).getUserReferencesAsList().size());
        SimpleClass value = new SimpleClass();
        object.put(42, value);
        Assert.assertEquals(1, ((UserTObject) object).getUserReferencesAsList().size());
        object.remove(42);
        Assert.assertEquals(0, ((UserTObject) object).getUserReferencesAsList().size());
        Transaction a = Transaction.start();
        object.put(42, value);
        Assert.assertEquals(0, ((UserTObject) object).getUserReferencesAsList().size());
        a.commit();
        Assert.assertEquals(1, ((UserTObject) object).getUserReferencesAsList().size());
        Transaction b = Transaction.start();
        object.remove(42);
        Assert.assertEquals(1, ((UserTObject) object).getUserReferencesAsList().size());
        b.commit();
        Assert.assertEquals(0, ((UserTObject) object).getUserReferencesAsList().size());
    }

    @Test
    public void testList() {
        TList<SimpleClass> object = new TList<SimpleClass>();
        Assert.assertEquals(0, ((UserTObject) object).getUserReferencesAsList().size());
        SimpleClass simple = new SimpleClass();
        object.add(simple);
        Assert.assertEquals(1, ((UserTObject) object).getUserReferencesAsList().size());
        object.remove(0);
        Assert.assertEquals(0, ((UserTObject) object).getUserReferencesAsList().size());
        Transaction a = Transaction.start();
        object.add(simple);
        Assert.assertEquals(0, ((UserTObject) object).getUserReferencesAsList().size());
        a.commit();
        Assert.assertEquals(1, ((UserTObject) object).getUserReferencesAsList().size());
        Transaction b = Transaction.start();
        object.remove(0);
        Assert.assertEquals(1, ((UserTObject) object).getUserReferencesAsList().size());
        b.commit();
        Assert.assertEquals(0, ((UserTObject) object).getUserReferencesAsList().size());
    }

    public static void main(String[] args) {
        UserReferencesTest test = new UserReferencesTest();
        test.testList();
    }
}
