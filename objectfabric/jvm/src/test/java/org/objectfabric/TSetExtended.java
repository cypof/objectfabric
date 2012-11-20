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

import java.util.Set;

import org.junit.After;
import org.junit.Before;
import org.junit.Ignore;

@Ignore
public class TSetExtended extends ExtendedTester {

    TSetExtended(Workspace workspace) {
        super(workspace);
    }

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

    private class Tests extends TSetTests implements ExtendedTest {

        private final int _threads;

        private final int _flags;

        private final TSet _set = new TSet(Workspace.resolve(""));

        private final TSet _set2 = new TSet(Workspace.resolve(""));

        private final Set _wrapper;

        private final Set _wrapper2;

        public Tests(int threads, int flags) {
            _threads = threads;
            _flags = flags;

            if ((flags & PROXY_3) != 0)
                _wrapper = (Set) TransactionalProxy.wrap(Workspace, _set, Set.class, 3, true);
            else if ((flags & PROXY_2) != 0)
                _wrapper = (Set) TransactionalProxy.wrap(Workspace, _set, Set.class, 2, true);
            else if ((flags & PROXY_1) != 0)
                _wrapper = (Set) TransactionalProxy.wrap(Workspace, _set, Set.class, 1, true);
            else
                _wrapper = null;

            if ((flags & PROXY_3) != 0)
                _wrapper2 = (Set) TransactionalProxy.wrap(Workspace, _set2, Set.class, 3, true);
            else if ((flags & PROXY_2) != 0)
                _wrapper2 = (Set) TransactionalProxy.wrap(Workspace, _set2, Set.class, 2, true);
            else if ((flags & PROXY_1) != 0)
                _wrapper2 = (Set) TransactionalProxy.wrap(Workspace, _set2, Set.class, 1, true);
            else
                _wrapper2 = null;
        }

        public int flags() {
            return _flags;
        }

        public int threadCount() {
            return _threads;
        }

        @Override
        public void run(Runnable runnable) {
            // TODO Auto-generated method stub

        }

        public void threadBefore() {
            // ExtendedTester.threadBefore(this);
        }

        public void threadAfter() {
            ExtendedTester.threadAfter(this);
        }

        @Override
        protected boolean transactionIsPrivate() {
            return ExtendedTester.transactionIsPrivate(this);
        }

        @Override
        protected Set createSet() {
            Set set = (Set) getCachedOrProxy(_flags, _set, _wrapper);

            if (set != null)
                return set;

            return super.createSet();
        }

        @Override
        protected Set createSet2() {
            Set set = (Set) getCachedOrProxy(_flags, _set2, _wrapper2);

            if (set != null)
                return set;

            return super.createSet2();
        }
    }
}
