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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Vector;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import com.objectfabric.ExpectedExceptionThrower;
import com.objectfabric.TList;
import com.objectfabric.Transaction;
import com.objectfabric.tools.TransactionalProxy;

@SuppressWarnings("unchecked")
public class TListTests extends TestsHelper {

    private void assertion(boolean value, String message) {
        if (!value)
            throw new RuntimeException("Assertion failed " + message);
    }

    private void fail(String message) {
        assertion(false, message);
    }

    @Override
    protected boolean skipMemory() {
        return true;
    }

    protected List createList() {
        return new TList();
    }

    protected List createList2() {
        return new TList();
    }

    protected boolean transactionIsPrivate() {
        return false;
    }

    private List fillList() {
        List list = createList();
        fillList(list);
        return list;
    }

    private void fillList(List list) {
        list.add("a");
        list.add("c");
        list.add("u");
        list.add("n");
        list.add("i");
        list.add("a");
        list.add(null);
        list.add("a");
        list.add("c");
        list.add("u");
        list.add("n");
        list.add("i");
        list.add("a");
        list.add(null);
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
    public void test_TransactedList() {
        Vector v = new Vector();
        List al = createList();
        al.addAll(v);
        assertion(al.isEmpty(), "no elements added");

        v.add("a");
        v.add("c");
        v.add("u");
        v.add("n");
        v.add("i");
        v.add("a");
        v.add(null);
        al.addAll(v);

        if (!transactionIsPrivate())
            Transaction.start();

        assertion(v.equals(al), "check if everything is OK");

        if (!transactionIsPrivate())
            Transaction.getCurrent().commit();
    }

    @Test
    public void test_get() {
        List al = createList();

        try {
            al.get(0);
            fail("should throw an IndexOutOfBoundsException -- 1");
        } catch (IndexOutOfBoundsException ioobe) {
        } catch (UndeclaredThrowableException ex) {
            TransactionalProxy.checkWrappedException(ex, IndexOutOfBoundsException.class);
        }

        try {
            al.get(-1);
            fail("should throw an IndexOutOfBoundsException -- 2");
        } catch (IndexOutOfBoundsException ioobe) {
        } catch (UndeclaredThrowableException ex) {
            TransactionalProxy.checkWrappedException(ex, IndexOutOfBoundsException.class);
        }

        al = fillList();

        try {
            al.get(14);
            fail("should throw an IndexOutOfBoundsException -- 3");
        } catch (IndexOutOfBoundsException ioobe) {
        } catch (UndeclaredThrowableException ex) {
            TransactionalProxy.checkWrappedException(ex, IndexOutOfBoundsException.class);
        }

        try {
            al.get(-1);
            fail("should throw an IndexOutOfBoundsException -- 4");
        } catch (IndexOutOfBoundsException ioobe) {
        } catch (UndeclaredThrowableException ex) {
            TransactionalProxy.checkWrappedException(ex, IndexOutOfBoundsException.class);
        }

        assertion("a".equals(al.get(0)), "checking returnvalue -- 1");
        assertion("c".equals(al.get(1)), "checking returnvalue -- 2");
        assertion("u".equals(al.get(2)), "checking returnvalue -- 3");
        assertion("a".equals(al.get(5)), "checking returnvalue -- 4");
        assertion("a".equals(al.get(7)), "checking returnvalue -- 5");
        assertion("c".equals(al.get(8)), "checking returnvalue -- 6");
        assertion("u".equals(al.get(9)), "checking returnvalue -- 7");
        assertion("a".equals(al.get(12)), "checking returnvalue -- 8");
        assertion(null == al.get(6), "checking returnvalue -- 9");
        assertion(null == al.get(13), "checking returnvalue -- 10");
    }

    @Test
    public void test_add() {
        List al = createList();

        try {
            al.add(-1, "a");
            fail("should throw an IndexOutOfBoundsException -- 1");
        } catch (IndexOutOfBoundsException ioobe) {
        } catch (UndeclaredThrowableException ex) {
            TransactionalProxy.checkWrappedException(ex, IndexOutOfBoundsException.class);
        }

        try {
            al.add(1, "a");
            fail("should throw an IndexOutOfBoundsException -- 2");
        } catch (IndexOutOfBoundsException ioobe) {
        } catch (UndeclaredThrowableException ex) {
            TransactionalProxy.checkWrappedException(ex, IndexOutOfBoundsException.class);
        }

        al.add(0, "a");
        al.add(1, "c");
        al.add(2, "u");
        al.add(1, null);
        assertion("a".equals(al.get(0)) && null == al.get(1) && "c".equals(al.get(2)) && "u".equals(al.get(3)), "checking add ...");
        al = createList();
        assertion(al.add("a"), "checking return value -- 1");
        assertion(al.add("c"), "checking return value -- 2");
        assertion(al.add("u"), "checking return value -- 3");
        assertion(al.add("n"), "checking return value -- 4");
        assertion(al.add("i"), "checking return value -- 5");
        assertion(al.add("a"), "checking return value -- 6");
        assertion(al.add(null), "checking return value -- 7");
        assertion(al.add("end"), "checking return value -- 8");
        assertion("a".equals(al.get(0)) && null == al.get(6) && "c".equals(al.get(1)) && "u".equals(al.get(2)), "checking add ... -- 1");
        assertion("a".equals(al.get(5)) && "end".equals(al.get(7)) && "n".equals(al.get(3)) && "i".equals(al.get(4)), "checking add ... -- 2");
    }

    @Test
    public void test_addAll() {
        List al = createList();

        try {
            al.addAll(null);
            fail("should throw NullPointerException");
        } catch (NullPointerException ne) {
        } catch (UndeclaredThrowableException ex) {
            TransactionalProxy.checkWrappedException(ex, NullPointerException.class);
        }

        Collection c = Arrays.asList(al.toArray());
        assertion(!al.addAll(c), "checking returnvalue -- 1");
        al.add("a");
        al.add("b");
        al.add("c");
        c = Arrays.asList(al.toArray());
        al = fillList();

        assertion(al.addAll(c), "checking returnvalue -- 2");
        assertion(al.containsAll(c), "extra on containsAll -- 1");
        assertion(al.get(14) == "a" && al.get(15) == "b" && al.get(16) == "c", "checking added on right positions");

        al = createList();
        c = Arrays.asList(al.toArray());
        assertion(!al.addAll(0, c), "checking returnvalue -- 1");
        al.add("a");
        al.add("b");
        al.add("c");
        c = Arrays.asList(al.toArray());
        al = fillList();

        try {
            al.addAll(-1, c);
            fail("should throw exception -- 1");
        } catch (IndexOutOfBoundsException ae) {
        } catch (UndeclaredThrowableException ex) {
            TransactionalProxy.checkWrappedException(ex, IndexOutOfBoundsException.class);
        }
        try {
            al.addAll(15, c);
            fail("should throw exception -- 2");
        } catch (IndexOutOfBoundsException ae) {
        } catch (UndeclaredThrowableException ex) {
            TransactionalProxy.checkWrappedException(ex, IndexOutOfBoundsException.class);
        }

        try {
            assertion(al.addAll(11, c), "checking returnvalue -- 2");
        } catch (ArrayIndexOutOfBoundsException ae) {
            fail("shouldn't throw exception -- 1");
        }

        assertion(al.containsAll(c), "extra on containsAll -- 1");
        assertion(al.get(11) == "a" && al.get(12) == "b" && al.get(13) == "c", "checking added on right positions -- 1");
        assertion(al.addAll(1, c), "checking returnvalue -- 3");
        assertion(al.get(1) == "a" && al.get(2) == "b" && al.get(3) == "c", "checking added on right positions -- 2");
    }

    @Test
    public void test_clear() {
        List al = createList();
        al.clear();
        al = fillList();
        al.clear();
        assertion(al.size() == 0 && al.isEmpty(), "list is empty ...");
    }

    @Test
    public void test_equals() {
        List list = createList();
        list.add("a");
        list.add(10);
        list.add(null);

        ArrayList ref = new ArrayList();
        ref.add("a");
        ref.add(10);
        ref.add(null);

        assertion(list.equals(ref), "equals 1");

        if (!transactionIsPrivate())
            Transaction.start();

        assertion(ref.equals(list), "equals 2");

        if (!transactionIsPrivate())
            Transaction.getCurrent().commit();

        list.add("blah");

        assertion(!list.equals(ref), "equals 3");

        if (!transactionIsPrivate())
            Transaction.start();

        assertion(!ref.equals(list), "equals 4");

        if (!transactionIsPrivate())
            Transaction.getCurrent().commit();
    }

    @Test
    public void test_hashCode() {
        List list = createList();
        list.add("a");
        list.add(10);
        list.add(null);

        ArrayList ref = new ArrayList();
        ref.add("a");
        ref.add(10);
        ref.add(null);

        assertion(list.hashCode() == ref.hashCode(), "hashCode 1");

        list.add("blah");

        assertion(list.hashCode() != ref.hashCode(), "hashCode 2");
    }

    @Test
    public void test_remove() {
        List al = fillList();

        try {
            al.remove(-1);
            fail("should throw an IndexOutOfBoundsException -- 1");
        } catch (IndexOutOfBoundsException e) {
        } catch (UndeclaredThrowableException ex) {
            TransactionalProxy.checkWrappedException(ex, IndexOutOfBoundsException.class);
        }

        try {
            al.remove(14);
            fail("should throw an IndexOutOfBoundsException -- 2");
        } catch (IndexOutOfBoundsException e) {
        } catch (UndeclaredThrowableException ex) {
            TransactionalProxy.checkWrappedException(ex, IndexOutOfBoundsException.class);
        }

        assertion("a".equals(al.remove(5)), "checking returnvalue remove -- 1");
        assertion("a".equals(al.get(0)) && null == al.get(5) && "c".equals(al.get(1)) && "u".equals(al.get(2)), "checking remove ... -- 1");
        assertion("a".equals(al.get(6)) && "c".equals(al.get(7)) && "n".equals(al.get(3)) && "i".equals(al.get(4)), "checking remove ... -- 2");
        assertion(al.size() == 13, "checking new size -- 1");
        assertion(al.remove(5) == null, "checking returnvalue remove -- 2");
        assertion(al.size() == 12, "checking new size -- 2");
        assertion(al.remove(11) == null, "checking returnvalue remove -- 3");
        assertion("a".equals(al.remove(0)), "checking returnvalue remove -- 4");
        assertion("u".equals(al.remove(1)), "checking returnvalue remove -- 5");
        assertion("i".equals(al.remove(2)), "checking returnvalue remove -- 6");
        assertion("a".equals(al.remove(2)), "checking returnvalue remove -- 7");
        assertion("u".equals(al.remove(3)), "checking returnvalue remove -- 8");
        assertion("a".equals(al.remove(5)), "checking returnvalue remove -- 9");
        assertion("i".equals(al.remove(4)), "checking returnvalue remove -- 10");
        assertion("c".equals(al.get(0)) && "c".equals(al.get(2)) && "n".equals(al.get(3)) && "n".equals(al.get(1)), "checking remove ... -- 3");
        assertion(al.size() == 4, "checking new size -- 3");
        al.remove(0);
        al.remove(0);
        al.remove(0);
        al.remove(0);
        assertion(al.size() == 0, "checking new size -- 4");
        al = createList();

        try {
            al.remove(0);
            fail("should throw an IndexOutOfBoundsException -- 3");
        } catch (IndexOutOfBoundsException e) {
        } catch (UndeclaredThrowableException ex) {
            TransactionalProxy.checkWrappedException(ex, IndexOutOfBoundsException.class);
        }
    }

    @Test
    public void test_set() {
        List al = createList();

        try {
            al.set(-1, "a");
            fail("should throw an IndexOutOfBoundsException -- 1");
        } catch (IndexOutOfBoundsException e) {
        } catch (UndeclaredThrowableException ex) {
            TransactionalProxy.checkWrappedException(ex, IndexOutOfBoundsException.class);
        }

        try {
            al.set(0, "a");
            fail("should throw an IndexOutOfBoundsException -- 2");
        } catch (IndexOutOfBoundsException e) {
        } catch (UndeclaredThrowableException ex) {
            TransactionalProxy.checkWrappedException(ex, IndexOutOfBoundsException.class);
        }

        al = fillList();

        try {
            al.set(-1, "a");
            fail("should throw an IndexOutOfBoundsException -- 3");
        } catch (IndexOutOfBoundsException e) {
        } catch (UndeclaredThrowableException ex) {
            TransactionalProxy.checkWrappedException(ex, IndexOutOfBoundsException.class);
        }

        try {
            al.set(14, "a");
            fail("should throw an IndexOutOfBoundsException -- 4");
        } catch (IndexOutOfBoundsException e) {
        } catch (UndeclaredThrowableException ex) {
            TransactionalProxy.checkWrappedException(ex, IndexOutOfBoundsException.class);
        }

        assertion("a".equals(al.set(5, "b")), "checking returnvalue of set -- 1");
        assertion("a".equals(al.set(0, null)), "checking returnvalue of set -- 2");
        assertion("b".equals(al.get(5)), "checking effect of set -- 1");
        assertion(al.get(0) == null, "checking effect of set -- 2");
        assertion("b".equals(al.set(5, "a")), "checking returnvalue of set -- 3");
        assertion(al.set(0, null) == null, "checking returnvalue of set -- 4");
        assertion("a".equals(al.get(5)), "checking effect of set -- 3");
        assertion(al.get(0) == null, "checking effect of set -- 4");
    }

    @Test
    public void test_contains() {
        List al = createList();
        assertion(!al.contains(null), "checking empty List -- 1");
        assertion(!al.contains(al), "checking empty List -- 2");
        al = fillList();
        assertion(al.contains(null), "check contains ... -- 1");
        assertion(al.contains("a"), "check contains ... -- 2");
        assertion(al.contains("c"), "check contains ... -- 3");
        assertion(!al.contains(this), "check contains ... -- 4");
        al.remove(6);
        assertion(al.contains(null), "check contains ... -- 5");
        al.remove(12);
        assertion(!al.contains(null), "check contains ... -- 6");
        assertion(!al.contains("b"), "check contains ... -- 7");
        assertion(!al.contains(al), "check contains ... -- 8");
    }

    @Test
    public void test_isEmpty() {
        List al = createList();
        assertion(al.isEmpty(), "checking returnvalue -- 1");
        al.add("A");
        assertion(!al.isEmpty(), "checking returnvalue -- 2");
        al.remove(0);
        assertion(al.isEmpty(), "checking returnvalue -- 3");
    }

    @Test
    public void test_indexOf() {
        List al = createList();
        assertion(al.indexOf(null) == -1, "checks on empty list -- 1");
        assertion(al.indexOf(al) == -1, "checks on empty list -- 2");
        String o = new String();
        al = fillList();
        assertion(al.indexOf(o) == -1, " doesn't contain -- 1");
        assertion(al.indexOf("a") == 0, "contains -- 2");
        assertion(al.indexOf(o) == -1, "contains -- 3");
        al.add(9, o);
        assertion(al.indexOf(o) == 9, "contains -- 4");
        assertion(al.indexOf(new Object()) == -1, "doesn't contain -- 5");
        assertion(al.indexOf(null) == 6, "null was added to the Vector");
        al.remove(6);
        assertion(al.indexOf(null) == 13, "null was added twice to the Vector");
        al.remove(13);
        assertion(al.indexOf(null) == -1, "null was removed to the Vector");
        assertion(al.indexOf("c") == 1, "contains -- 6");
        assertion(al.indexOf("u") == 2, "contains -- 7");
        assertion(al.indexOf("n") == 3, "contains -- 8");
    }

    @Test
    public void test_size() {
        List al = createList();
        assertion(al.size() == 0, "check on size -- 1");
        List a2 = createList2();
        fillList(a2);
        Collection c = Arrays.asList(a2.toArray());
        al.addAll(c);
        assertion(al.size() == 14, "check on size -- 1");
        al.remove(5);
        assertion(al.size() == 13, "check on size -- 1");
        al.add(4, "G");
        assertion(al.size() == 14, "check on size -- 1");
    }

    @Test
    public void test_lastIndexOf() {
        List al = createList();
        assertion(al.lastIndexOf(null) == -1, "checks on empty list -- 1");
        assertion(al.lastIndexOf(al) == -1, "checks on empty list -- 2");
        String o = new String();
        al = fillList();
        assertion(al.lastIndexOf(o) == -1, " doesn't contain -- 1");
        assertion(al.lastIndexOf("a") == 12, "contains -- 2");
        assertion(al.lastIndexOf(o) == -1, "contains -- 3");
        al.add(9, o);
        assertion(al.lastIndexOf(o) == 9, "contains -- 4");
        assertion(al.lastIndexOf(new Object()) == -1, "doesn't contain -- 5");
        assertion(al.lastIndexOf(null) == 14, "null was added to the Vector");
        al.remove(14);
        assertion(al.lastIndexOf(null) == 6, "null was added twice to the Vector");
        al.remove(6);
        assertion(al.lastIndexOf(null) == -1, "null was removed to the Vector");
        assertion(al.lastIndexOf("c") == 7, "contains -- 6, got " + al.lastIndexOf("c"));
        assertion(al.lastIndexOf("u") == 9, "contains -- 7, got " + al.lastIndexOf("u"));
        assertion(al.lastIndexOf("n") == 10, "contains -- 8, got " + al.lastIndexOf("n"));
    }

    @Test
    public void test_toArray() {
        List v = createList();
        Object o[] = v.toArray();
        assertion(o.length == 0, "checking size Object array");
        v.add("a");
        v.add(null);
        v.add("b");
        o = v.toArray();
        assertion(o[0] == "a" && o[1] == null && o[2] == "b", "checking elements -- 1");
        assertion(o.length == 3, "checking size Object array");

        v = createList();

        try {
            v.toArray(null);
            fail("should throw NullPointerException -- 1");
        } catch (NullPointerException ne) {
        } catch (UndeclaredThrowableException ex) {
            TransactionalProxy.checkWrappedException(ex, NullPointerException.class);
        }

        v.add("a");
        v.add(null);
        v.add("b");
        String sa[] = new String[5];
        sa[3] = "deleteme";
        sa[4] = "leavemealone";
        assertion(v.toArray(sa) == sa, "sa is large enough, no new array created");
        assertion(sa[0] == "a" && sa[1] == null && sa[2] == "b", "checking elements -- 1" + sa[0] + ", " + sa[1] + ", " + sa[2]);
        assertion(sa.length == 5, "checking size Object array");
        assertion(sa[3] == null && sa[4] == "leavemealone", "check other elements -- 1" + sa[3] + ", " + sa[4]);
        v = fillList();

        try {
            v.toArray(null);
            fail("should throw NullPointerException -- 2");
        } catch (NullPointerException ne) {
        } catch (UndeclaredThrowableException ex) {
            TransactionalProxy.checkWrappedException(ex, NullPointerException.class);
        }

        try {
            v.toArray(new Class[5]);
            fail("should throw an ArrayStoreException");
        } catch (ArrayStoreException ae) {
        } catch (UndeclaredThrowableException ex) {
            TransactionalProxy.checkWrappedException(ex, ArrayStoreException.class);
        }

        v.add(null);
        String sar[];
        sa = new String[15];
        sar = (String[]) v.toArray(sa);
        assertion(sar == sa, "returned array is the same");
    }

    public static void main(String[] args) {
        TListTests test = new TListTests();
        test.test_lastIndexOf();
    }
}
