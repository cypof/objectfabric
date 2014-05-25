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
import org.objectfabric.TObject.Transaction;

public class TransactionalProxy {

    /**
     * Starts and commits an arbitrary number of transactions around every method call
     * made to the tested object.
     */
    public static Object wrap(final Workspace workspace, final Object target, Class c, final int transactionCount, final boolean assertCommitSuccess) {
        ClassLoader cl = Thread.currentThread().getContextClassLoader();

        return Proxy.newProxyInstance(cl, new Class[] { c }, new InvocationHandler() {

            public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
                // This also asserts transactions are not messed up by call
                ArrayList<Transaction> transactions = new ArrayList<Transaction>();

                for (int i = 0; i < transactionCount; i++)
                    transactions.add(workspace.startImpl(0));

                Object result;

                try {
                    result = method.invoke(target, args);
                } finally {
                    for (int i = transactions.size() - 1; i >= 0; i--) {
                        boolean success = TransactionManager.commit(transactions.get(i));

                        if (assertCommitSuccess)
                            Assert.assertTrue(success);
                    }
                }

                if (method.getName().equals("keySet") || method.getName().equals("entrySet"))
                    result = wrap(workspace, result, Set.class, transactionCount, assertCommitSuccess);

                if (method.getName().equals("values"))
                    result = wrap(workspace, result, Collection.class, transactionCount, assertCommitSuccess);

                if (method.getName().equals("iterator"))
                    result = wrap(workspace, result, Iterator.class, transactionCount, assertCommitSuccess);

                if (method.getName().equals("listIterator"))
                    result = wrap(workspace, result, ListIterator.class, transactionCount, assertCommitSuccess);

                return result;
            }
        });
    }

    public static void checkWrappedException(UndeclaredThrowableException ex, Class c) {
        Assert.assertTrue(c.isInstance(((InvocationTargetException) ex.getUndeclaredThrowable()).getTargetException()));
    }
}
