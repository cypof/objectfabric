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

package com.objectfabric.tools;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.lang.reflect.UndeclaredThrowableException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.ListIterator;
import java.util.Set;

import org.junit.Assert;

import com.objectfabric.Transaction;
import com.objectfabric.Transaction.CommitStatus;

public class TransactionalProxy {

    /**
     * Starts and commits an arbitrary number of transactions around every method call
     * made to the tested object.
     */
    public static Object wrap(final Object target, Class c, final int transactionCount, final boolean assertCommitSuccess) {
        ClassLoader cl = Thread.currentThread().getContextClassLoader();

        return Proxy.newProxyInstance(cl, new Class[] { c }, new InvocationHandler() {

            public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
                // This also asserts transactions are not messed up by call
                ArrayList<Transaction> transactions = new ArrayList<Transaction>();

                for (int i = 0; i < transactionCount; i++)
                    transactions.add(Transaction.start());

                Object result;

                try {
                    result = method.invoke(target, args);
                } finally {
                    for (int i = transactions.size() - 1; i >= 0; i--) {
                        CommitStatus status = transactions.get(i).commit();

                        if (assertCommitSuccess)
                            Assert.assertEquals(CommitStatus.SUCCESS, status);
                    }
                }

                if (method.getName().equals("keySet") || method.getName().equals("entrySet"))
                    result = wrap(result, Set.class, transactionCount, assertCommitSuccess);

                if (method.getName().equals("values"))
                    result = wrap(result, Collection.class, transactionCount, assertCommitSuccess);

                if (method.getName().equals("iterator"))
                    result = wrap(result, Iterator.class, transactionCount, assertCommitSuccess);

                if (method.getName().equals("listIterator"))
                    result = wrap(result, ListIterator.class, transactionCount, assertCommitSuccess);

                return result;
            }
        });
    }

    public static void checkWrappedException(UndeclaredThrowableException ex, Class c) {
        Assert.assertTrue(c.isInstance(((InvocationTargetException) ex.getUndeclaredThrowable()).getTargetException()));
    }
}
