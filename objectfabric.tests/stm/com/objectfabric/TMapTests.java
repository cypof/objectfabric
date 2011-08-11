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

import java.lang.reflect.UndeclaredThrowableException;
import java.util.BitSet;
import java.util.Collection;
import java.util.ConcurrentModificationException;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.Vector;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import com.objectfabric.ExpectedExceptionThrower;
import com.objectfabric.TMap;
import com.objectfabric.Transaction;
import com.objectfabric.misc.Debug;
import com.objectfabric.tools.TransactionalProxy;

@SuppressWarnings("unchecked")
public class TMapTests extends TestsHelper {

    // TODO, tester TMap avec random exception dans hash() et equals().

    private void check(boolean condition) {
        check(condition, "");
    }

    private void check(boolean condition, String message) {
        // Assert.assertTrue(message, condition);
        Debug.assertAlways(condition);
    }

    @Override
    protected boolean skipMemory() {
        return true;
    }

    protected Map createMap() {
        return new TMap();
    }

    protected Map createMap2() {
        return new TMap();
    }

    protected boolean transactionIsPrivate() {
        return false;
    }

    private Map fillMap() {
        Map map = createMap();
        buildMap(map);
        return map;
    }

    private Map fillMap2() {
        Map map = createMap2();
        buildMap(map);
        return map;
    }

    private void buildMap(Map map) {
        for (int i = 0; i < 15; i++) {
            String s = "a" + i;
            map.put(s, s + " value");
        }

        map.put(this, null);
    }

    @Before
    public void disableExceptionCheck() {
        ExpectedExceptionThrower.disableCounter();
    }

    @After
    public void enableExceptionCheck() {
        ExpectedExceptionThrower.enableCounter();
    }

    @Test
    public void test_get() {
        Map map = fillMap();
        check(map.get(this) == null, "checking get -- 1");
        check(map.get(this) == null, "checking get -- 2");
        map.put("a", this);
        check("a1 value".equals(map.get("a1")), "checking get -- 3");
        check("a11 value".equals(map.get("a11")), "checking get -- 4");
        check(map.get(new Integer(97)) == null, "checking get -- 5");
    }

    @Test
    public void test_containsKey() {
        Map map = createMap();
        map.clear();
        check(!map.containsKey(this), "Map is empty");
        map.put("a", this);
        check(!map.containsKey(this), "Map does not containsthe key -- 1");
        check(map.containsKey("a"), "Map does contain the key -- 2");
        map = fillMap();
        check(map.containsKey(this), "Map does contain the key -- 3");
        check(!map.containsKey("a"), "Map does not contain the key -- 4");
    }

    @Test
    public void test_containsValue() {
        Map map = createMap();
        map.clear();
        check(!map.containsValue(null), "Map is empty");
        map.put("a", this);
        check(!map.containsValue(null), "Map does not containsthe value -- 1");
        check(!map.containsValue("a"), "Map does not contain the value -- 2");
        check(map.containsValue(this), "Map does contain the value -- 3");
        map = fillMap();
        check(map.containsValue(null), "Map does contain the value -- 4");
        check(!map.containsValue(this), "Map does not contain the value -- 5");
        check(!map.containsValue("a1value"), "Map does not contain the value -- 6");
        check(map.containsValue("a1 value"), "Map does contain the value -- 7");
    }

    @Test
    public void test_isEmpty() {
        Map map = createMap();
        check(map.isEmpty(), "Map is empty");
        map.put("a", this);
        check(!map.isEmpty(), "Map is not empty");
    }

    @Test
    public void test_size() {
        Map map = createMap();
        check(map.size() == 0, "Map is empty");
        map.put("a", this);
        check(map.size() == 1, "Map has 1 element");
        map = fillMap();
        check(map.size() == 16, "Map has 16 elements");
    }

    @Test
    public void test_clear() {
        Map map = fillMap();
        map.clear();
        check(map.isEmpty(), "Map is cleared");
    }

    @Test
    public void test_put() {
        Map map = createMap();
        check(map.put(this, this) == null, "check on return value -- 1");
        check(map.get(this) == this, "check on value -- 1");

        if (transactionIsPrivate()) {
            check(map.put(this, "a") == this, "check on return value -- 2");
            check("a".equals(map.get(this)), "check on value -- 2");
            check("a".equals(map.put(this, "a")), "check on return value -- 3");
            check("a".equals(map.get(this)), "check on value -- 3");
        }

        check(map.size() == 1, "only one key added");
        check(map.put("a", null) == null, "check on return value -- 4");
        check(map.get("a") == null, "check on value -- 4");
        check(map.put("a", this) == null, "check on return value -- 5");
        check(map.get("a") == this, "check on value -- 5");
        check(map.size() == 2, "two keys added");
    }

    @Test
    public void test_putAll() {
        Map map = createMap();
        HashMap hm = new HashMap();
        map.putAll(hm);
        check(map.isEmpty(), "nothing addad");
        buildMap(hm);
        map.putAll(hm);
        check(map.size() == 16, "checking if all enough elements are added -- 1");
        HashMap ref = new HashMap();
        buildMap(ref);
        check(ref.equals(map), "check on all elements -- 1");
        map.put(this, this);
        check(map.size() == 16, "putAll 3");
        check(!ref.equals(map), "putAll 4");
        map.clear();
        map.putAll(fillMap2());
        check(ref.equals(map), "putAll 5");
    }

    @Test
    public void test_remove() {
        Map map = fillMap();
        check(map.remove(this) == null, "checking return value -- 1");
        check(map.remove(this) == null, "checking return value -- 2");
        check(!map.containsKey(this), "checking removed key -- 1");
        check(!map.containsValue(null), "checking removed value -- 1");
        for (int i = 0; i < 15; i++) {
            if (transactionIsPrivate())
                check(("a" + i + " value").equals(map.remove("a" + i)), " removing a" + i);
            else
                check(map.remove("a" + i) == null, " removing a" + i);
        }
        check(map.isEmpty(), "checking if al is gone");
    }

    @Test
    public void test_entrySet() {
        Map map = fillMap();
        Set s = map.entrySet();

        if (!transactionIsPrivate())
            Transaction.start();

        Iterator it = s.iterator();
        Map.Entry me = null;
        it.next();
        try {
            s.add("ADDING");
            check(false, "add should throw an UnsupportedOperationException");
        } catch (UnsupportedOperationException uoe) {
        } catch (UndeclaredThrowableException ex) {
            TransactionalProxy.checkWrappedException(ex, UnsupportedOperationException.class);
        }
        check(s.size() == 16, "size 16");
        map.remove("a12");
        check(s.size() == 15, "size 15");
        try {
            check(it.hasNext(), "hasNext 1");
        } catch (ConcurrentModificationException cme) {
            check(false, "it.hasNext should not throw ConcurrentModificationException");
        }
        it.remove();
        check(s.size() == 14, "size 14");

        if (!transactionIsPrivate())
            Transaction.getCurrent().commit();

        if (!transactionIsPrivate())
            Transaction.start();

        it = s.iterator();

        me = (Map.Entry) it.next();
        if (me.getKey() == this)
            me = (Map.Entry) it.next();
        check(me.hashCode() == (me.getValue().hashCode() ^ me.getKey().hashCode()), "verifying hashCode");
        check(!me.equals(it.next()), "equals");
        it = s.iterator();
        Vector v = new Vector();
        Object ob;
        v.addAll(s);
        while (it.hasNext()) {
            ob = it.next();
            it.remove();
            if (!v.remove(ob))
                check(false, "Object " + ob + " not in the Vector");
        }
        check(v.isEmpty(), "all elements gone from the vector");
        check(map.isEmpty(), "all elements removed from the Map");
        it = s.iterator();
        map.put(12, "sdf");
        check(!it.hasNext(), "iterator should reflect state before put");
        it = s.iterator();
        map.clear();
        check(it.hasNext(), "iterator should reflect state before clear");

        if (!transactionIsPrivate())
            Transaction.getCurrent().commit();
    }

    @Test
    public void test_keySet() {
        Map map = fillMap();
        check(map.size() == 16, "checking map size(), got " + map.size());
        Set s = null;
        Object[] o;
        s = map.keySet();
        check(s.size() == 16, "checking size keyset, got " + s.size());
        o = s.toArray();
        check(o.length == 16, "checking length, got " + o.length);

        if (!transactionIsPrivate())
            Transaction.start();

        Iterator it = s.iterator();
        Vector v = new Vector();
        Object ob;
        v.addAll(s);
        while (it.hasNext()) {
            ob = it.next();
            it.remove();
            if (!v.remove(ob))
                check(false, "Object " + ob + " not in the Vector");
        }

        if (!transactionIsPrivate())
            Transaction.getCurrent().commit();

        check(v.isEmpty(), "all elements gone from the vector");
        check(map.isEmpty(), "all elements removed from the Map");
        try {
            s.add("ADDING");
            check(false, "add should throw an UnsupportedOperationException");
        } catch (UnsupportedOperationException uoe) {
        } catch (UndeclaredThrowableException ex) {
            TransactionalProxy.checkWrappedException(ex, UnsupportedOperationException.class);
        }
    }

    @Test
    public void test_values() {
        Map map = fillMap();
        check(map.size() == 16, "checking map size(), got " + map.size());
        Collection s = null;
        Object[] o;
        s = map.values();
        check(s.size() == 16, "checking size keyset, got " + s.size());

        if (!transactionIsPrivate())
            Transaction.start();

        o = s.toArray();
        check(o.length == 16, "checking length, got " + o.length);
        Iterator it = s.iterator();
        Vector v = new Vector();
        Object ob;
        v.addAll(s);
        while (it.hasNext()) {
            ob = it.next();
            it.remove();
            if (!v.remove(ob))
                check(false, "Object " + ob + " not in the Vector");
        }

        if (!transactionIsPrivate())
            Transaction.getCurrent().commit();

        check(v.isEmpty(), "all elements gone from the vector");
        check(map.isEmpty(), "all elements removed from the Map");
        try {
            s.add("ADDING");
            check(false, "add should throw an UnsupportedOperationException");
        } catch (UnsupportedOperationException uoe) {
        } catch (UndeclaredThrowableException ex) {
            TransactionalProxy.checkWrappedException(ex, UnsupportedOperationException.class);
        }
    }

    /**
     * The goal of this test is to see how the map behaves if we do a lot put's and
     * removes.
     */
    private final String _st = "a";

    private final Byte _b = new Byte((byte) 97);

    private final Short _sh = new Short((short) 97);

    private final Integer _i = new Integer(97);

    private final Long _l = new Long(97L);

    private int _sqnce = 1;

    protected void check_presence(Map h) {
        check(h.get(_st) != null, "checking presence st -- sequence " + _sqnce);
        check(h.get(_sh) != null, "checking presence sh -- sequence " + _sqnce);
        check(h.get(_i) != null, "checking presence i -- sequence " + _sqnce);
        check(h.get(_b) != null, "checking presence b -- sequence " + _sqnce);
        check(h.get(_l) != null, "checking presence l -- sequence " + _sqnce);
        _sqnce++;
    }

    @Test
    public void test_behaviour() {
        Map h = createMap();
        int j = 0;
        Float f;
        h.put(_st, "a");
        h.put(_b, "byte");
        h.put(_sh, "short");
        h.put(_i, "int");
        h.put(_l, "long");
        check_presence(h);
        _sqnce = 1;

        for (; j < 100; j++) {
            f = new Float(j);
            h.put(f, f);
        }

        check(h.size() == 105, "size checking -- 1 got: " + h.size());
        check_presence(h);

        for (; j < 200; j++) {
            f = new Float(j);
            h.put(f, f);
        }

        check(h.size() == 205, "size checking -- 2 got: " + h.size());
        check_presence(h);

        for (; j < 300; j++) {
            f = new Float(j);
            h.put(f, f);
        }

        check(h.size() == 305, "size checking -- 3 got: " + h.size());
        check_presence(h);

        // replacing values -- checking if we get a non-zero value
        check("a".equals(h.get(_st)), "get values -- 1 - st");
        check("byte".equals(h.get(_b)), "get values -- 2 - b");
        check("short".equals(h.get(_sh)), "get values -- 3 -sh");
        check("int".equals(h.get(_i)), "get values -- 4 -i");
        check("long".equals(h.get(_l)), "get values -- 5 -l");

        h.put(_st, "na");
        h.put(_b, "nbyte");
        h.put(_sh, "nshort");
        h.put(_i, "nint");
        h.put(_l, "nlong");

        for (; j > 199; j--) {
            f = new Float(j);
            h.remove(f);
        }

        check(h.size() == 205, "size checking -- 4 got: " + h.size());
        check_presence(h);

        for (; j > 99; j--) {
            f = new Float(j);
            h.remove(f);
        }

        check(h.size() == 105, "size checking -- 5 got: " + h.size());
        check_presence(h);

        for (; j > -1; j--) {
            f = new Float(j);
            h.remove(f);
        }

        check(h.size() == 5, "size checking -- 6 got: " + h.size());
        check_presence(h);
    }

    //

    private final Object[] array = { "key1", "val1", "key2", "val2", new Integer(1), new Integer(1), new Float(3), new Float(3), new Double(5), new Double(6), new Boolean(true), new Boolean(true), new BitSet(), new BitSet(), "key3",
            "val3", "key4", "val4", "key5", "val5", "key6", "val6", this, this };

    @Test
    public void HashMap0004() {
        Map map = createMap();

        for (int i = 0; i < array.length; i++)
            check(!map.containsKey(array[i]), "");
    }

    @Test
    public void HashMap0005() {
        Map map = createMap();

        for (int i = 0; i < array.length - 2; i += 2) {
            map.put(array[i], array[i + 1]);

            for (int j = i + 2; j < array.length; j += 2)
                check(!map.containsKey(array[j]), "");
        }
    }

    @Test
    public void HashMap2042() {
        Map map = createMap();

        for (int i = 0; i < array.length - 2; i += 2) {
            map.put(array[i], array[i + 1]);

            for (int j = 0; j <= i; j += 2)
                check(map.containsKey(array[j]));
        }
    }

    @Test
    public void HashMap0007() {
        Map map = createMap();

        for (int i = 0; i < array.length - 2; i += 2) {
            map.put(array[i], array[i + 1]);

            for (int j = i + 2; j < array.length; j += 2)
                check(!map.containsValue(array[j + 1]));
        }
    }

    @Test
    public void HashMap2043() {
        Map map = createMap();

        for (int i = 0; i < array.length - 2; i += 2) {
            map.put(array[i], array[i + 1]);

            for (int j = 0; j <= i; j += 2)
                check(map.containsValue(array[j + 1]));
        }
    }

    @Test
    public void HashMapEquals() {
        Map map = createMap();
        HashMap ref = new HashMap();

        for (int i = 0; i < array.length - 2; i += 2)
            map.put(array[i], array[i + 1]);

        for (int i = 0; i < array.length - 2; i += 2)
            ref.put(array[i], array[i + 1]);

        check(map.equals(ref));
        check(ref.equals(map));

        map.put("blah", null);

        check(!map.equals(ref));
        check(!ref.equals(map));
    }

    @Test
    public void HashMapHashCode() {
        Map map = createMap();
        HashMap ref = new HashMap();

        for (int i = 0; i < array.length - 2; i += 2)
            map.put(array[i], array[i + 1]);

        for (int i = 0; i < array.length - 2; i += 2)
            ref.put(array[i], array[i + 1]);

        check(map.hashCode() == ref.hashCode());

        map.put("blah", null);

        check(map.hashCode() != ref.hashCode());
    }

    @Test
    public void HashMap2004() {
        Set set = null;
        Map.Entry entry = null;
        Object cur = null;

        for (int i = 0; i <= array.length; i += 2) {
            Map map = createMap();

            for (int j = 0; j < i; j += 2)
                map.put(array[j], array[j + 1]);

            set = map.entrySet();
            check(set.size() == map.size());

            if (!transactionIsPrivate())
                Transaction.start();

            Iterator iter = set.iterator();

            while (iter.hasNext()) {
                cur = iter.next();
                check(cur instanceof Map.Entry);
                entry = (Map.Entry) cur;
                check(map.get(entry.getKey()) == entry.getValue());
            }

            if (!transactionIsPrivate())
                Transaction.getCurrent().commit();
        }
    }

    @Test
    public void HashMap2005() {
        for (int i = 0; i <= array.length; i += 2) {
            Map map = createMap();

            for (int j = 0; j < i; j += 2)
                map.put(array[j], array[j + 1]);

            int size = map.size();

            if (!transactionIsPrivate())
                Transaction.start();

            Iterator iter = map.entrySet().iterator();

            while (iter.hasNext()) {
                Map.Entry entry = (Map.Entry) (iter.next());
                iter.remove();
                check(!map.containsKey(entry.getKey()));
                check(map.size() == (size - 1));
                size--;
            }

            if (!transactionIsPrivate())
                Transaction.getCurrent().commit();
        }
    }

    @Test
    public void HashMap2006() {
        if (!transactionIsPrivate())
            return;

        int size = 0;
        Object elArray[] = null;
        Set set = null;
        Map.Entry entry = null;

        for (int i = 0; i <= array.length; i += 2) {
            Map map = createMap();

            for (int j = 0; j < i; j += 2)
                map.put(array[j], array[j + 1]);

            set = map.entrySet();
            elArray = set.toArray();
            size = map.size();

            for (int j = 0; j < elArray.length; j++) {
                entry = (Map.Entry) elArray[j];
                set.remove(elArray[j]);
                check(!map.containsKey(entry.getKey()));
                check(map.size() == (size - 1));
                size--;
            }
        }
    }

    @Test
    public void HashMap2007() {
        if (!transactionIsPrivate())
            return;

        Vector vect = null;
        int size = 0;
        Set set = null;
        Object[] elArray = null;

        for (int i = 0; i <= array.length; i += 2) {
            Map map = createMap();

            for (int j = 0; j < i; j += 2)
                map.put(array[j], array[j + 1]);

            set = map.entrySet();
            elArray = set.toArray();
            size = set.size();

            for (int j = 0; j < elArray.length; j++) {
                vect = new Vector();
                vect.addElement(elArray[j]);
                set.removeAll(vect);
                check(!map.containsKey(((Map.Entry) elArray[j]).getKey()));
                check(map.size() == (size - 1));
                size--;
            }

            map = createMap();

            for (int j = 0; j < i; j += 2)
                map.put(array[j], array[j + 1]);

            set = map.entrySet();
            elArray = set.toArray();
            size = set.size();
            vect = new Vector();

            for (int j = 0; j < elArray.length; j++)
                vect.addElement(elArray[j]);

            set.removeAll(vect);

            check(map.size() == 0);
        }
    }

    @Test
    public void HashMap2008() {
        Vector vect = null;
        Set set = null;
        Object[] elArray = null;

        for (int i = 0; i <= array.length; i += 2) {
            Map map = createMap();

            for (int j = 0; j < i; j += 2)
                map.put(array[j], array[j + 1]);

            set = map.entrySet();
            elArray = set.toArray();

            for (int j = 0; j < elArray.length; j++) {
                vect = new Vector();

                for (int k = 0; k < elArray.length - j; k++)
                    vect.addElement(elArray[k]);

                if (!transactionIsPrivate())
                    Transaction.start();

                set.retainAll(vect);

                if (!transactionIsPrivate())
                    Transaction.getCurrent().commit();

                for (int k = 0; k < elArray.length - j; k++)
                    check(map.containsKey(((Map.Entry) elArray[k]).getKey()));

                check(map.size() == (elArray.length - j));
            }
        }
    }

    @Test
    public void HashMap2009() {
        Set set = null;

        for (int i = 0; i <= array.length; i += 2) {
            Map map = createMap();

            for (int j = 0; j < i; j += 2)
                map.put(array[j], array[j + 1]);

            set = map.entrySet();
            set.clear();

            check(map.size() == 0);

            for (int j = 0; j < array.length; j += 2)
                check(map.get(array[j]) == null);
        }
    }

    @Test
    public void HashMap2010() {
        Map map = createMap();
        Map map1 = null;
        Set set = map.entrySet();
        Map.Entry entry = null;

        for (int i = 0; i < array.length; i += 2) {
            map1 = createMap();
            map1.put(array[i], array[i + 1]);

            if (!transactionIsPrivate())
                Transaction.start();

            entry = (Map.Entry) map1.entrySet().iterator().next();

            if (!transactionIsPrivate())
                Transaction.getCurrent().commit();

            try {
                set.add(entry);
                check(false);
            } catch (UnsupportedOperationException e) {
            } catch (UndeclaredThrowableException ex) {
                TransactionalProxy.checkWrappedException(ex, UnsupportedOperationException.class);
            }

            try {
                set.add(array[i]);
                check(false);
            } catch (UnsupportedOperationException e) {
            } catch (UndeclaredThrowableException ex) {
                TransactionalProxy.checkWrappedException(ex, UnsupportedOperationException.class);
            }
        }
    }

    @Test
    public void HashMap0008() {
        Map map = createMap();

        for (int i = 0; i < array.length; i++) {
            check(map.get(array[i]) == null);
        }
    }

    @Test
    public void HashMap2044() {
        Map map = createMap();

        for (int i = 0; i < array.length - 2; i += 2) {
            map.put(array[i], array[i + 1]);

            for (int j = i + 2; j < array.length; j += 2) {
                check(map.get(array[j]) == null);
            }
        }
    }

    @Test
    public void HashMap2045() {
        Map map = createMap();

        for (int i = 0; i < array.length - 2; i += 2) {
            map.put(array[i], array[i + 1]);

            for (int j = 0; j <= i; j += 2) {
                check(map.get(array[j]) == array[j + 1]);
            }
        }
    }

    @Test
    public void HashMap0009() {
        for (int i = 0; i < array.length; i += 2) {
            Map map = createMap();
            check(map.put(array[i], array[i + 1]) == null);
            check(map.get(array[i]) == array[i + 1]);
        }
    }

    @Test
    public void HashMap2046() {
        Object expected = null;

        for (int i = 0; i < array.length; i += 2) {
            Map map = createMap();

            for (int j = 0; j <= i; j += 2)
                map.put(array[j], array[j + 1]);

            for (int j = 0; j < array.length; j += 2) {
                if (j > i) {
                    expected = null;
                } else {
                    expected = array[j + 1];
                }

                if (!transactionIsPrivate())
                    expected = null;

                check(map.put(array[j], array[j + 1]) == expected);
                check(map.get(array[j]) == array[j + 1]);
            }
        }
    }

    @Test
    public void HashMap2016() {
        Set set = null;
        Iterator iter = null;
        Object cur = null;

        for (int i = 0; i <= array.length; i += 2) {
            Map map = createMap();
            for (int j = 0; j < i; j += 2) {
                map.put(array[j], array[j + 1]);
            }

            set = map.keySet();

            check(set.size() == map.size());

            if (!transactionIsPrivate())
                Transaction.start();

            iter = set.iterator();

            while (iter.hasNext()) {
                cur = iter.next();
                check(map.containsKey(cur));
            }

            if (!transactionIsPrivate())
                Transaction.getCurrent().commit();
        }
    }

    @Test
    public void HashMap2017() {
        Iterator iter = null;
        int size = 0;
        Object key = null;

        for (int i = 0; i <= array.length; i += 2) {
            Map map = createMap();
            for (int j = 0; j < i; j += 2) {
                map.put(array[j], array[j + 1]);
            }

            size = map.size();

            if (!transactionIsPrivate())
                Transaction.start();

            iter = map.keySet().iterator();

            while (iter.hasNext()) {
                key = iter.next();
                iter.remove();
                check(!map.containsKey(key));
                check(map.size() == (size - 1));
                size--;
            }

            if (!transactionIsPrivate())
                Transaction.getCurrent().commit();
        }
    }

    @Test
    public void HashMap2018() {
        int size = 0;
        Object elArray[] = null;
        Set set = null;

        for (int i = 0; i <= array.length; i += 2) {
            Map map = createMap();

            for (int j = 0; j < i; j += 2)
                map.put(array[j], array[j + 1]);

            set = map.keySet();
            elArray = set.toArray();
            size = map.size();

            for (int j = 0; j < elArray.length; j++) {
                set.remove(elArray[j]);
                check(!map.containsKey(elArray[j]));
                check(map.size() == (size - 1));
                size--;
            }
        }
    }

    @Test
    public void HashMap2019() {
        Vector vect = null;
        int size = 0;
        Set set = null;
        Object[] elArray = null;

        for (int i = 0; i <= array.length; i += 2) {
            Map map = createMap();

            for (int j = 0; j < i; j += 2)
                map.put(array[j], array[j + 1]);

            set = map.keySet();
            elArray = set.toArray();
            size = set.size();

            for (int j = 0; j < elArray.length; j++) {
                vect = new Vector();
                vect.addElement(elArray[j]);
                set.removeAll(vect);
                check(!map.containsKey(elArray[j]));
                check(map.size() == (size - 1));
                size--;
            }

            map = createMap();
            for (int j = 0; j < i; j += 2) {
                map.put(array[j], array[j + 1]);
            }

            set = map.keySet();
            elArray = set.toArray();
            size = set.size();
            vect = new Vector();

            for (int j = 0; j < elArray.length; j++)
                vect.addElement(elArray[j]);

            set.removeAll(vect);
            check(map.size() == 0);
        }
    }

    @Test
    public void HashMap2020() {
        Vector vect = null;
        Set set = null;
        Object[] elArray = null;

        for (int i = 0; i <= array.length; i += 2) {
            Map map = createMap();

            for (int j = 0; j < i; j += 2)
                map.put(array[j], array[j + 1]);

            set = map.keySet();
            elArray = set.toArray();

            for (int j = 0; j < elArray.length; j++) {
                vect = new Vector();

                for (int k = 0; k < elArray.length - j; k++)
                    vect.addElement(elArray[k]);

                set.retainAll(vect);

                for (int k = 0; k < elArray.length - j; k++)
                    check(map.containsKey(elArray[k]));

                check(map.size() == (elArray.length - j));
            }
        }
    }

    @Test
    public void HashMap2021() {
        Set set = null;

        for (int i = 0; i <= array.length; i += 2) {
            Map map = createMap();

            for (int j = 0; j < i; j += 2) {
                map.put(array[j], array[j + 1]);
            }

            set = map.keySet();
            set.clear();
            check(map.size() == 0);

            for (int j = 0; j < array.length; j += 2)
                check(map.get(array[j]) == null);
        }
    }

    @Test
    public void HashMap2022() {
        Map map = createMap();
        Set set = map.keySet();

        try {
            set.add(new Integer(2));
            check(false);
        } catch (UnsupportedOperationException e) {
        } catch (UndeclaredThrowableException ex) {
            TransactionalProxy.checkWrappedException(ex, UnsupportedOperationException.class);
        }
    }

    @Test
    public void HashMap2023() {
        Set set = null;
        Vector vect = null;
        Object[] elArray = null;

        for (int i = 0; i <= array.length; i += 2) {
            Map map = createMap();

            for (int j = 0; j < i; j += 2)
                map.put(array[j], array[j + 1]);

            set = map.keySet();
            elArray = set.toArray();
            vect = new Vector();

            for (int j = 0; j < elArray.length; j++)
                vect.addElement(elArray[j]);

            try {
                set.addAll(vect);
                check(false);
            } catch (UnsupportedOperationException e) {
            } catch (UndeclaredThrowableException ex) {
                TransactionalProxy.checkWrappedException(ex, UnsupportedOperationException.class);
            }
        }
    }

    @Test
    public void HashMap0010() {
        Map map1 = createMap();
        Map map2 = createMap2();
        map1.putAll(map2);
        check(map1.size() == 0);
    }

    @Test
    public void HashMap0011() {
        Map map1 = createMap();
        Map map2 = createMap2();

        for (int i = 0; i < array.length; i += 2)
            map1.put(array[i], array[i + 1]);

        map1.putAll(map2);
        check(map1.size() == (array.length >> 1));

        for (int i = 0; i < array.length; i += 2)
            check(map1.get(array[i]) == array[i + 1]);
    }

    @Test
    public void HashMap0012() {
        Map map1 = createMap();
        Map map2 = createMap2();

        for (int i = 0; i < array.length; i += 2)
            map2.put(array[i], array[i + 1]);

        map1.putAll(map2);
        check(map1.size() == (array.length >> 1));

        for (int i = 0; i < array.length; i += 2)
            check(map1.get(array[i]) == array[i + 1]);
    }

    @Test
    public void HashMap2047() {
        Object[] keys = { "key1", "key2", new Integer(1), new Float(3), new Double(5), new Boolean(true), new BitSet(), "key3", "key4", "key5", "key6", this };
        Object[] values1 = { "val1", "val2", new Integer(2), new Float(4), new Double(6), new Boolean(true), new BitSet(), "val3", "val4", "val5", "val6", this };
        Object[] values2 = { "val21", "val22", new Integer(3), new Float(5), new Double(7), new Boolean(false), new BitSet(), "val23", "val24", "val25", "val26", this };

        for (int i = 0; i < keys.length; i++) {
            Map map1 = createMap();
            Map map2 = createMap2();

            for (int j = 0; j <= i; j++) {
                // Creating two maps with keys from same array and values from
                // different arrays.

                map1.put(keys[j], values1[j]);
                map2.put(keys[keys.length - j - 1], values2[keys.length - j - 1]);
            }

            map1.putAll(map2);

            if (i < (keys.length - i - 1)) {
                /*
                 * This branch checks the case when set of keys from map1 does not
                 * intersects with set of keys from map2.
                 */
                check(map1.size() == (2 * (i + 1)));

                for (int j = 0; j <= i; j++)
                    check(map1.get(keys[j]) == values1[j] && map1.get(keys[keys.length - j - 1]) == values2[keys.length - j - 1]);
            } else {
                /*
                 * This branch checks the case when set of keys from map1 intersects with
                 * set of keys from map2.
                 */
                check(map1.size() == keys.length);

                for (int j = 0; j < (keys.length - i - 1); j++)
                    check(map1.get(keys[j]) == values1[j]);

                for (int j = (keys.length - i - 1); j < keys.length; j++)
                    check(map1.get(keys[j]) == values2[j]);
            }
        }
    }

    @Test
    public void HashMap0013() {
        Map map = createMap();

        for (int i = 0; i < array.length; i += 2)
            check(map.remove(array[i]) == null);

        check(map.size() == 0);
    }

    @Test
    public void HashMap2048() {
        Object result = null;

        for (int i = 0; i < array.length; i += 2) {
            for (int j = 0; j < array.length; j += 2) {
                Map map = createMap();

                for (int k = 0; k <= i; k += 2)
                    map.put(array[k], array[k + 1]);

                result = map.remove(array[j]);

                for (int k = 0; k <= i; k += 2) {
                    if (k != j) {
                        check(map.get(array[k]) == array[k + 1]);
                    } else {
                        if (transactionIsPrivate())
                            check(map.get(array[k]) == null && result == array[k + 1]);
                        else
                            check(map.get(array[k]) == null);
                    }
                }
            }
        }
    }

    @Test
    public void HashMap2029() {
        Collection col = null;
        Iterator iter = null;
        Object cur = null;

        for (int i = 0; i <= array.length; i += 2) {
            Map map = createMap();

            for (int j = 0; j < i; j += 2)
                map.put(array[j], array[j + 1]);

            col = map.values();
            check(col.size() == map.size());

            if (!transactionIsPrivate())
                Transaction.start();

            iter = col.iterator();

            while (iter.hasNext()) {
                cur = iter.next();
                check(map.containsValue(cur));
            }

            if (!transactionIsPrivate())
                Transaction.getCurrent().commit();
        }
    }

    @Test
    public void HashMap2030() {
        Iterator iter = null;
        int size = 0;
        Object key = null;

        for (int i = 0; i <= array.length; i += 2) {
            Map map = createMap();

            for (int j = 0; j < i; j += 2)
                map.put(array[j], array[j + 1]);

            size = map.size();

            if (!transactionIsPrivate())
                Transaction.start();

            iter = map.values().iterator();

            while (iter.hasNext()) {
                key = iter.next();
                iter.remove();
                check(!map.containsValue(key));
                check(map.size() == (size - 1));
                size--;
            }

            if (!transactionIsPrivate())
                Transaction.getCurrent().commit();
        }
    }

    @Test
    public void HashMap2031() {
        int size = 0;
        Object elArray[] = null;
        Collection col = null;

        for (int i = 0; i <= array.length; i += 2) {
            Map map = createMap();

            for (int j = 0; j < i; j += 2)
                map.put(array[j], array[j + 1]);

            col = map.values();
            elArray = col.toArray();
            size = map.size();

            for (int j = 0; j < elArray.length; j++) {
                col.remove(elArray[j]);
                check(!map.containsValue(elArray[j]));
                check(map.size() == (size - 1));
                size--;
            }
        }
    }

    @Test
    public void HashMap2032() {
        Vector vect = null;
        int size = 0;
        Collection col = null;
        Object[] elArray = null;

        for (int i = 0; i <= array.length; i += 2) {
            Map map = createMap();

            for (int j = 0; j < i; j += 2)
                map.put(array[j], array[j + 1]);

            col = map.values();
            elArray = col.toArray();
            size = col.size();

            for (int j = 0; j < elArray.length; j++) {
                vect = new Vector();
                vect.addElement(elArray[j]);
                col.removeAll(vect);
                check(!map.containsValue(elArray[j]));
                check(map.size() == (size - 1));
                size--;
            }

            map = createMap();

            for (int j = 0; j < i; j += 2)
                map.put(array[j], array[j + 1]);

            col = map.values();
            elArray = col.toArray();
            size = col.size();
            vect = new Vector();

            for (int j = 0; j < elArray.length; j++)
                vect.addElement(elArray[j]);

            col.removeAll(vect);
            check(map.size() == 0);
        }
    }

    @Test
    public void HashMap2033() {
        Vector vect = null;
        Collection col = null;
        Object[] elArray = null;

        for (int i = 0; i <= array.length; i += 2) {
            Map map = createMap();

            for (int j = 0; j < i; j += 2)
                map.put(array[j], array[j + 1]);

            col = map.values();
            elArray = col.toArray();

            for (int j = 0; j < elArray.length; j++) {
                vect = new Vector();

                for (int k = 0; k < elArray.length - j; k++)
                    vect.addElement(elArray[k]);

                col.retainAll(vect);

                for (int k = 0; k < elArray.length - j; k++)
                    check(map.containsValue(elArray[k]));

                check(map.size() == (elArray.length - j));
            }
        }
    }

    @Test
    public void HashMap2034() {
        Collection col = null;

        for (int i = 0; i <= array.length; i += 2) {
            Map map = createMap();

            for (int j = 0; j < i; j += 2)
                map.put(array[j], array[j + 1]);

            col = map.values();
            col.clear();
            check(map.size() == 0);

            for (int j = 0; j < array.length; j += 2)
                check(map.get(array[j]) == null);
        }
    }

    @Test
    public void HashMap2035() {
        Map map = createMap();
        Collection col = map.values();

        try {
            col.add(new Integer(2));
            check(false);
        } catch (UnsupportedOperationException e) {
        } catch (UndeclaredThrowableException ex) {
            TransactionalProxy.checkWrappedException(ex, UnsupportedOperationException.class);
        }
    }

    @Test
    public void HashMap2036() {
        Collection col = null;
        Vector vect = null;
        Object[] elArray = null;

        for (int i = 0; i <= array.length; i += 2) {
            Map map = createMap();

            for (int j = 0; j < i; j += 2)
                map.put(array[j], array[j + 1]);

            col = map.values();
            elArray = col.toArray();
            vect = new Vector();

            for (int j = 0; j < elArray.length; j++)
                vect.addElement(elArray[j]);

            try {
                col.addAll(vect);
                check(false);
            } catch (UnsupportedOperationException e) {
            } catch (UndeclaredThrowableException ex) {
                TransactionalProxy.checkWrappedException(ex, UnsupportedOperationException.class);
            }
        }
    }

    public static void main(String[] args) throws Exception {
        TMapTests test = new TMapTests();
        // TMapPrivateTransactions test = new TMapPrivateTransactions();

        for (int i = 0; i < 1000; i++) {
            test.before();
            test.test_behaviour();
            test.after();
        }
    }
}
