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

import java.util.List;

import org.junit.After;
import org.junit.Before;
import org.junit.Ignore;

import com.objectfabric.tools.ExtendedTester;
import com.objectfabric.tools.TransactionalProxy;

public class TListExtended extends ExtendedTester {

    @Override
    protected ExtendedTest getTest(int threads, int flags) {
        return new Tests(threads, flags, new TList(), new TList());
    }

    @Before
    public void disableExceptionCheck() {
        ExpectedExceptionThrower.disableCounter();
    }

    @After
    public void enableExceptionCheck() {
        ExpectedExceptionThrower.enableCounter();
    }

    @Ignore
    public static final class Tests extends TListTests implements ExtendedTest {

        private final int _threads;

        private final int _flags;

        private final TList _list;

        private final TList _list2;

        private final List _wrapper;

        private final List _wrapper2;

        public Tests(int threads, int flags, TList list, TList list2) {
            _threads = threads;
            _flags = flags;
            _list = list;
            _list2 = list2;

            if ((flags & PROXY_3) != 0)
                _wrapper = (List) TransactionalProxy.wrap(_list, List.class, 3, true);
            else if ((flags & PROXY_2) != 0)
                _wrapper = (List) TransactionalProxy.wrap(_list, List.class, 2, true);
            else if ((flags & PROXY_1) != 0)
                _wrapper = (List) TransactionalProxy.wrap(_list, List.class, 1, true);
            else
                _wrapper = null;

            if ((flags & PROXY_3) != 0)
                _wrapper2 = (List) TransactionalProxy.wrap(_list2, List.class, 3, true);
            else if ((flags & PROXY_2) != 0)
                _wrapper2 = (List) TransactionalProxy.wrap(_list2, List.class, 2, true);
            else if ((flags & PROXY_1) != 0)
                _wrapper2 = (List) TransactionalProxy.wrap(_list2, List.class, 1, true);
            else
                _wrapper2 = null;
        }

        public int flags() {
            return _flags;
        }

        public int threadCount() {
            return _threads;
        }

        public void threadBefore() {
            ExtendedTester.threadBefore(this);
        }

        public void threadAfter() {
            ExtendedTester.threadAfter(this);
        }

        @Override
        protected boolean transactionIsPrivate() {
            return ExtendedTester.transactionIsPrivate(this);
        }

        @Override
        protected List createList() {
            List list = (List) getCachedOrProxy(_flags, _list, _wrapper);

            if (list != null)
                return list;

            return super.createList();
        }

        @Override
        protected List createList2() {
            List list = (List) getCachedOrProxy(_flags, _list2, _wrapper2);

            if (list != null)
                return list;

            return super.createList2();
        }
    }

    public static void main(String[] args) throws Exception {
        TListExtended test = new TListExtended();
        test.test(new Tests(1, PROXY_1, new TList(), new TList()), 100);
    }
}