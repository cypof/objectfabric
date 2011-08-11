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
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.Vector;

import junit.framework.Assert;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import com.objectfabric.ExpectedExceptionThrower;
import com.objectfabric.TSet;
import com.objectfabric.Transaction;
import com.objectfabric.tools.TransactionalProxy;

@SuppressWarnings("unchecked")
public class TSetTests extends TestsHelper {

    // TODO, tester avec random exception dans hash() et equals().

    private void check(boolean condition) {
        check(condition, "");
    }

    private void check(boolean condition, String message) {
        Assert.assertTrue(message, condition);
        // Debug.assertion(condition);
    }

    @Override
    protected boolean skipMemory() {
        return true;
    }

    protected Set createSet() {
        return new TSet();
    }

    protected Set createSet2() {
        return new TSet();
    }

    protected boolean transactionIsPrivate() {
        return false;
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
    public void HashSet0003() {
        int size = 0;
        Object[] o = { this, new String("Hello World"), new Double(50.0), new Integer("100") };
        Set hs = createSet();
        for (int i = 0; i < o.length; i++) {
            boolean modified = hs.add(o[i]);
            check(modified ^ !transactionIsPrivate());
            check(hs.contains(o[i]));
        }
        size = hs.size();
        check(size == o.length);
        Object[] duplicates = { this, "Hello World", new Double(50.0), new Integer("100") };

        for (int j = 0; j < duplicates.length; j++) {
            check(!hs.add(duplicates[j]));
            check(hs.contains(duplicates[j]));
        }
        check(hs.size() == size);
    }

    @Test
    public void HashSet2002() {
        Set hs = createSet();
        hs.clear();
        check(hs.isEmpty());
    }

    @Test
    public void HashSet2003() {
        Object[] o = { this, new String("Hello World"), new Double(50.0), new Integer("100") };
        Set hs = createSet();
        for (int i = 0; i < o.length; i++) {
            hs.add(o[i]);
        }
        hs.clear();
        check(hs.isEmpty());
    }

    @Test
    public void HashSet0006() {
        Object[] o = { this, new String("Hello World") };
        Set hs = createSet();
        for (int i = 0; i < o.length; i++) {
            check(!hs.contains(o[i]));
        }
    }

    @Test
    public void HashSet0007() {
        Object[] o = { this, new String("Hello World"), new Double(50.0), new Integer("100") };
        Set hs = createSet();
        for (int i = 0; i < o.length; i++) {
            hs.add(o[i]);
            check(hs.contains(o[i]));
        }
    }

    @Test
    public void HashSet0001() {
        Object o[] = { this, new Boolean("true"), new String("Hello World"), new Integer("100") };
        Collection c = new Vector();
        for (int i = 0; i < o.length; i++) {
            c.add(o[i]);
        }
        Set hs = createSet();
        hs.addAll(c);
        check(hs.size() == o.length);
        for (int j = 0; j < o.length; j++) {
            check(hs.contains(o[j]));
        }
    }

    @Test
    public void HashSet1007() {
        Set hs = createSet();
        hs.add(new String("Hello World"));
        check(!hs.isEmpty());
    }

    @Test
    public void HashSet0012() {
        Set hs = createSet();

        if (!transactionIsPrivate())
            Transaction.start();

        Iterator it = hs.iterator();
        try {
            it.next();
            check(false);
        } catch (NoSuchElementException e) {
        } catch (UndeclaredThrowableException ex) {
            TransactionalProxy.checkWrappedException(ex, NoSuchElementException.class);
        }

        if (!transactionIsPrivate())
            Transaction.getCurrent().commit();
    }

    @Test
    public void HashSet0013() {
        Object nextElement = null;
        Object[] o = { this, new String("Hello World"), new Double(50.0), new Integer("100") };
        Set hs = createSet();
        for (int i = 0; i < o.length; i++) {
            hs.add(o[i]);
        }

        if (!transactionIsPrivate())
            Transaction.start();

        Iterator it = hs.iterator();
        HashSet hs_copy = new HashSet();
        while (it.hasNext()) {
            nextElement = it.next();
            hs_copy.add(nextElement);
        }

        check(hs_copy.equals(hs));

        if (!transactionIsPrivate())
            Transaction.getCurrent().commit();
    }

    @Test
    public void HashSet0004() {
        Object[] o = { this, new String("Hello World") };
        Set hs = createSet();
        for (int i = 0; i < o.length; i++) {
            check(!hs.remove(o[i]));
        }
    }

    @Test
    public void HashSet0005() {
        Object[] o = { this, new String("Hello World"), new Double(50.0), new Integer("100") };
        Set hs = createSet();
        for (int i = 0; i < o.length; i++) {
            hs.add(o[i]);
        }
        Object[] remove_list = { this, new String("Hello World") };
        for (int j = 0; j < remove_list.length; j++) {
            boolean expected = transactionIsPrivate();
            check(hs.remove(remove_list[j]) == expected);
        }
    }

    @Test
    public void HashSet0009() {
        Object[] o = { this, new String("Hello World"), new Double(50.0), new Integer("100") };
        Set hs = createSet();
        check(hs.size() == 0);
        for (int i = 0; i < o.length; i++) {
            hs.add(o[i]);
        }
        check(hs.size() == o.length);
    }

    private Set getSet(String content) {
        Set t = createSet();

        for (int i = 0; i < content.length(); i++)
            t.add("" + content.charAt(i));

        return t;
    }

    private Set getSet2(String content) {
        Set t = createSet2();

        for (int i = 0; i < content.length(); i++)
            t.add("" + content.charAt(i));

        return t;
    }

    private void checkContent(Set set, String content, String note) {
        if (!transactionIsPrivate())
            Transaction.start();

        Iterator iter = set.iterator();

        while (iter.hasNext()) {
            String c = (String) iter.next();
            check(content.indexOf(c) != -1);
        }

        if (!transactionIsPrivate())
            Transaction.getCurrent().commit();
    }

    @Test
    public void test_add() {
        Set set = getSet("bcdabcddabbccaabbccadbcdababbcdabcxabcxccda");
        checkContent(set, "abcdx", "add");
        check(set.size() == 5, "size");
    }

    @Test
    public void test_addAll2() {
        Set set = getSet("dac");
        Set t = getSet2("xay");

        set.addAll(t);

        checkContent(set, "acdxy", "addAll");
    }

    @Test
    public void test_contains2() {
        String t = "abcdefghij";
        Set set = getSet(t);

        for (int i = 0; i < t.length(); i++) {
            String s = t.substring(i, i + 1);
            check(set.contains(s), "must contain '" + s + "'");
        }

        check(!set.contains("aa"), "must not contain 'aa'");
    }

    @Test
    public void test_remove2() {
        String t = "abcdefghij";
        Set set = getSet(t);

        for (int i = 0; i < t.length(); i++) {
            String s = t.substring(i, i + 1);
            set.remove(s);

            check(!set.contains(s));
        }

        check(set.size() == 0, "non zero size after removing all elements");
        check(set.isEmpty(), "non empty when it should be");
    }

    @Test
    public void test_clear() {
        Set set = getSet("a");
        set.clear();
        check(set.size() == 0, "clear");
    }

    @Test
    public void test_remove() {
        Set set = createSet();
        set.add("a");
        set.add(this);
        set.add("c");
        set.add("a");
        check(set.remove("a") ^ !transactionIsPrivate(), "returns true if removed -- 1");
        check(set.size() == 2, "one element was removed -- 1");
        check(!set.remove("a"), "returns true if removed -- 2");
        check(set.size() == 2, "one element was removed -- 2");
    }

    @Test
    public void test_removeAll() {
        Set set = createSet();
        set.add("a");
        set.add(this);
        set.add("c");
        set.add("a");
        Vector v = new Vector();
        v.add("a");
        v.add(this);
        v.add("de");
        v.add("fdf");
        check(set.removeAll(v) ^ !transactionIsPrivate(), "should return true");
        check(set.size() == 1, "duplicate elements are removed");
        check(!set.removeAll(v), "should return false");
        check(set.size() == 1, "no elements were removed");
        check(set.remove("c") ^ !transactionIsPrivate());
        check(set.isEmpty());
    }

    @Test
    public void test_retainAll() {
        Set set = createSet();
        set.add("a");
        set.add(this);
        set.add("c");
        set.add("a");
        Vector v = new Vector();
        v.add("a");
        v.add(this);
        v.add("de");
        v.add("fdf");

        if (transactionIsPrivate()) {
            check(set.retainAll(v), "should return true");
            check(set.size() == 2, "duplicate elements are retained");
            check(!set.retainAll(v), "should return false");
            check(set.size() == 2, "all elements were retained");
            check(set.contains(this) && set.contains("a"));
        }
    }

    @Test
    public void test_contains() {
        Set set = createSet();
        set.add("a");
        set.add(this);
        set.add("c");
        set.add("a");
        check(set.contains("a"), "true -- 1");
        check(set.contains(this), "true -- 2");
        check(set.contains("c"), "true -- 3");
        check(!set.contains("ab"), "false -- 4");
        check(!set.contains("b"), "false -- 5");
        set.remove(this);
        check(!set.contains(this), "false -- 4");
    }

    @Test
    public void test_containsAll() {
        Set set = createSet();
        set.add("a");
        set.add(this);
        set.add("c");
        set.add("a");
        Vector v = new Vector();
        check(set.containsAll(v), "should return true -- 1");
        v.add("a");
        v.add(this);
        v.add("a");
        v.add(this);
        v.add("a");
        check(set.containsAll(v), "should return true -- 2");
        v.add("c");
        check(set.containsAll(v), "should return true -- 3");
        v.add("c+");
        check(!set.containsAll(v), "should return false -- 4");
        v.clear();
        set.clear();
        check(set.containsAll(v), "should return true -- 5");
    }

    @Test
    public void test_isEmpty() {
        Set set = createSet();
        check(set.isEmpty(), "should return true -- 1");
        check(set.isEmpty(), "should return true -- 2");
        set.add(this);
        check(!set.isEmpty(), "should return false -- 3");
        set.clear();
        check(set.isEmpty(), "should return true -- 4");
    }

    @Test
    public void test_toArray() {
        Set set = createSet();
        Object[] oa = set.toArray();
        check(oa != null, "returning null is not allowed");

        if (oa != null)
            check(oa.length == 0, "empty array");

        set.add("a");
        set.add("b");
        set.add("c");
        set.add("a");
        oa = set.toArray();
        check(oa.length == 3);
        check(Arrays.asList(oa).contains("a") && Arrays.asList(oa).contains("b") && Arrays.asList(oa).contains("c"));
        String[] sa = new String[2];

        for (int i = 0; i < sa.length; i++)
            sa[i] = "ok";

        oa = set.toArray(sa);
        check(oa.length == 3);
        check(Arrays.asList(oa).contains("a") && Arrays.asList(oa).contains("b") && Arrays.asList(oa).contains("c"));
        sa = new String[3];

        for (int i = 0; i < sa.length; i++)
            sa[i] = "ok";

        oa = set.toArray(sa);
        check(oa.length == 3);
        check(Arrays.asList(oa).contains("a") && Arrays.asList(oa).contains("b") && Arrays.asList(oa).contains("c"));
        check(oa instanceof String[], "checking  class type of returnvalue");
        check(oa == sa, "array large enough --> fill + return it");
        sa = new String[4];

        for (int i = 0; i < sa.length; i++)
            sa[i] = "ok";

        oa = set.toArray(sa);
        check(Arrays.asList(oa).contains("a") && Arrays.asList(oa).contains("b") && Arrays.asList(oa).contains("c"));
        check(oa instanceof String[], "checking  class type of returnvalue");
        check(oa == sa, "array large enough --> fill + return it");
        check(oa[3] == null);
    }

    @Test
    public void test_Equals() {
        Set set = createSet();
        set.add("a");
        set.add("b");
        set.add("c");
        set.add("a");
        HashSet ref = new HashSet();
        ref.add("a");
        ref.add("b");
        ref.add("c");
        ref.add("a");

        check(set.equals(ref));

        if (!transactionIsPrivate())
            Transaction.start();

        check(ref.equals(set));

        if (!transactionIsPrivate())
            Transaction.getCurrent().commit();

        set.add("blah");

        check(!set.equals(ref));
        check(!ref.equals(set));
    }

    @Test
    public void test_HashCode() {
        Set set = createSet();
        set.add("a");
        set.add("b");
        set.add("c");
        set.add("a");
        HashSet ref = new HashSet();
        ref.add("a");
        ref.add("b");
        ref.add("c");
        ref.add("a");

        check(set.hashCode() == ref.hashCode());

        set.add("blah");

        check(set.hashCode() != ref.hashCode());
    }

    @Test
    public void TestCase0001_() {
        final Integer[] data = new Integer[] { new Integer(0), new Integer(1), new Integer(2) };
        Set set = createSet();
        check(set.add(data[2]) ^ !transactionIsPrivate());
        check(set.add(data[0]) ^ !transactionIsPrivate());
        check(set.add(data[1]) ^ !transactionIsPrivate());
        check(set.size() == 3);
        Object[] setAsArray = set.toArray();
        check(setAsArray.length == data.length);
    }

    @Test
    public void TestCase0001__() {
        Set set = createSet();
        set.clear();
        check(set.isEmpty());
        set.add(new Integer(1));
        set.add(new Integer(2));
        check(set.size() == 2);
        set.clear();
        check(set.isEmpty());
    }

    @Test
    public void TestCase0001___() {
        Set set = createSet();
        check(set.isEmpty());
        int s = 5;// no of elements added to the TreeSet
        set.add(new Integer(3));
        set.add(new Integer(0));
        set.add(new Integer(1));
        set.add(new Integer(4));
        set.add(new Integer(2));
        check(set.size() == s);
        Object obj[] = set.toArray();

        for (int i = 0; i < obj.length; i++)
            check(((Integer) obj[i]) < s);
    }

    @Test
    public void TestCase0001____() {
        Collection cln1 = new Vector();
        Collection cln2 = new Vector();
        cln2.add(new Integer(3));
        cln2.add(new Integer(0));
        cln2.add(new Integer(1));
        cln2.add(new Integer(4));
        cln2.add(new Integer(2));
        Collection cln3 = new Vector();
        cln3.add(new Integer(10));
        cln3.add(new SimpleDateFormat());

        Collection[] cln = { cln1, cln2 };

        for (int i = 0; i < cln.length; i++) {
            Set set = createSet();
            set.addAll(cln[i]);

            if (i == 0)
                check(set.isEmpty());

            if (i == 1) {
                check(cln2.size() == set.size());
                Object[] obj = set.toArray();

                for (int j = 0; j < set.size(); j++)
                    check(cln2.contains(obj[j]));
            }
        }
    }

    @Test
    public void TestCase0002_() {
        SortedSet ss = new TreeSet();
        Set set = createSet();
        set.addAll(ss);
        check(set.size() == 0);
    }

    @Test
    public void TestCase0001_2() {
        Integer check1 = new Integer(100);
        Set set = createSet();
        check(!set.contains(check1));
    }

    @Test
    public void TestCase0002_2() {
        Integer check = new Integer(2);
        Set set = createSet();
        set.add(new Integer(0));
        set.add(new Integer(1));
        set.add(new Integer(2));
        check(set.contains(check));
    }

    @Test
    public void TestCase0003_2() {
        Integer check = new Integer(3);
        Set set = createSet();
        set.add(new Integer(0));
        set.add(new Integer(1));
        set.add(new Integer(2));
        check(!set.contains(check));
    }

    @Test
    public void TestCase0001_3() {
        Set set = createSet();
        check(set.isEmpty());
        set.add(new Integer("1"));
        set.add(new Integer("2"));
        check(!set.isEmpty());
    }

    @Test
    public void TestCase0001_4() {
        Set set = createSet();

        if (!transactionIsPrivate())
            Transaction.start();

        Iterator it = set.iterator();
        check(!it.hasNext());

        if (!transactionIsPrivate())
            Transaction.getCurrent().commit();

        set.add(new Integer("1"));
        set.add(new Integer("2"));

        int i = 0;

        if (!transactionIsPrivate())
            Transaction.start();

        for (Object o : set) {
            check(o.equals(1) || o.equals(2));
            i++;
        }

        if (!transactionIsPrivate())
            Transaction.getCurrent().commit();

        check(i == 2);
    }

    @Test
    public void TestCase0001_5() {
        Integer integer = new Integer(2);
        Set set = createSet();
        check(!set.remove(integer));
        set.add(integer);
        check(set.remove(integer) ^ !transactionIsPrivate());

    }

    @Test
    public void TestCase0002_3() {
        Integer integer = new Integer(2);
        Set set = createSet();
        set.add(new Integer(1));
        set.add(new Integer(2));
        set.add(new Integer(3));
        check(set.remove(integer) ^ !transactionIsPrivate());
        check(!set.remove(integer));
    }

    @Test
    public void TestCase0003_3() {
        Integer integer = new Integer(5);
        Set set = createSet();
        set.add(new Integer(1));
        set.add(new Integer(2));
        set.add(new Integer(3));
        check(!set.remove(integer));
    }

    @Test
    public void TestCase0001_6() {
        Set set = createSet();
        check(set.size() == 0);
        set.add(new Integer("1"));
        set.add(new Integer("2"));
        check(set.size() == 2);
    }

    public static void main(String[] args) throws Exception {
        TSetTests test = new TSetTests();
        test.before();
        test.HashSet0005();
        test.after();
    }
}
