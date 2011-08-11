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

import java.util.Map;

import org.junit.After;
import org.junit.Before;


import com.objectfabric.ExpectedExceptionThrower;
import com.objectfabric.TMap;
import com.objectfabric.tools.ExtendedTester;
import com.objectfabric.tools.TransactionalProxy;

public class TMapExtended extends ExtendedTester {

    @Override
    protected ExtendedTest getTest(int threads, int flags) {
        return new Tests(threads, flags);
    }

    @Before
    public void disableExceptionCheck() {
        ExpectedExceptionThrower.disableCounter();
    }

    @After
    public void enableExceptionCheck() {
        ExpectedExceptionThrower.enableCounter();
    }

    private static class Tests extends TMapTests implements ExtendedTest {

        private final int _threads;

        private final int _flags;

        private final TMap _map = new TMap();

        private final TMap _map2 = new TMap();

        private final Map _wrapper;

        private final Map _wrapper2;

        public Tests(int threads, int flags) {
            _threads = threads;
            _flags = flags;

            if ((flags & PROXY_3) != 0)
                _wrapper = (Map) TransactionalProxy.wrap(_map, Map.class, 3, true);
            else if ((flags & PROXY_2) != 0)
                _wrapper = (Map) TransactionalProxy.wrap(_map, Map.class, 2, true);
            else if ((flags & PROXY_1) != 0)
                _wrapper = (Map) TransactionalProxy.wrap(_map, Map.class, 1, true);
            else
                _wrapper = null;

            if ((flags & PROXY_3) != 0)
                _wrapper2 = (Map) TransactionalProxy.wrap(_map2, Map.class, 3, true);
            else if ((flags & PROXY_2) != 0)
                _wrapper2 = (Map) TransactionalProxy.wrap(_map2, Map.class, 2, true);
            else if ((flags & PROXY_1) != 0)
                _wrapper2 = (Map) TransactionalProxy.wrap(_map2, Map.class, 1, true);
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
        protected Map createMap() {
            Map map = (Map) getCachedOrProxy(_flags, _map, _wrapper);

            if (map != null)
                return map;

            return super.createMap();
        }

        @Override
        protected Map createMap2() {
            Map map = (Map) getCachedOrProxy(_flags, _map2, _wrapper2);

            if (map != null)
                return map;

            return super.createMap2();
        }
    }
}