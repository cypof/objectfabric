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

import java.lang.ref.WeakReference;
import java.util.Iterator;

@SuppressWarnings({ "rawtypes", "unchecked" })
final class ThreadContext extends PlatformThreadContext {

    private static final PlatformConcurrentMap _instances = new PlatformConcurrentMap();

    final byte[] Sha1 = new byte[SHA1Digest.LENGTH];

    final char[] PathCache = new char[Utils.TIME_HEX + Utils.PEER_HEX];

    List<Buff> Buffs;

    private final Reader _reader = new Reader();

    byte[] Buffer;

    static ThreadContext get() {
        ThreadContext value = (ThreadContext) getInstance();

        if (value == null) {
            Object key;

            if (Debug.THREADS)
                ThreadAssert.suspend(key = new Object());

            setInstance(value = new ThreadContext());
            WeakReference<ThreadContext> ref = new WeakReference<ThreadContext>(value);
            _instances.put(ref, ref);

            if (Debug.THREADS) {
                ThreadAssert.suspend(value);
                ThreadAssert.resume(key);
            }
        }

        return value;
    }

    private ThreadContext() {
    }

    final Reader getReader() {
        return _reader;
    }

    static final Iterable<ThreadContext> getInstances() {
        return new Iterable<ThreadContext>() {

            @Override
            public Iterator<ThreadContext> iterator() {
                return new It(_instances.keySet().iterator());
            }
        };
    }

    static void disposeAll() {
        if (Debug.THREADS)
            for (ThreadContext context : getInstances())
                context.dispose();
    }

    final List<Object> getThreadContextObjects() {
        return _reader.getThreadContextObjects();
    }

    private void dispose() {
        ThreadAssert.resume(this);
        ThreadAssert.removePrivateList(getThreadContextObjects());
    }

    @SuppressWarnings({ "unchecked", "rawtypes" })
    private static class It implements Iterator<ThreadContext> {

        Iterator _iterator;

        ThreadContext _next;

        It(Iterator iterator) {
            _iterator = iterator;
        }

        @Override
        public boolean hasNext() {
            while (_iterator.hasNext()) {
                WeakReference<ThreadContext> ref = (WeakReference) _iterator.next();
                _next = ref.get();

                if (_next != null)
                    return true;

                _instances.remove(ref);
            }

            return false;
        }

        @Override
        public ThreadContext next() {
            return _next;
        }

        @Override
        public void remove() {
            throw new UnsupportedOperationException();
        }
    }
}
