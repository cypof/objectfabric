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

final class InstanceCache {

    private static final int MAX_SIZE = 10; // TODO tune

    private InstanceCache() {
    }

    static <T> List<T> getOrCreateList(List<T> threadLocal, PlatformConcurrentQueue<List<T>> shared) {
        if (threadLocal == null) {
            threadLocal = shared.poll();

            if (threadLocal == null)
                threadLocal = new List<T>();
        } else if (threadLocal.size() == 0) {
            List<T> list = shared.poll();

            if (list != null) {
                if (Debug.ENABLED)
                    Debug.assertion(list.size() > 0);

                threadLocal = list;
            }
        }

        return threadLocal;
    }

    static <T> List<T> recycle(List<T> threadLocal, PlatformConcurrentQueue<List<T>> shared, T instance) {
        if (Debug.ENABLED)
            checkNotCached(threadLocal, shared, instance);

        if (threadLocal == null)
            threadLocal = new List<T>();

        threadLocal.add(instance);

        if (threadLocal.size() >= MAX_SIZE) {
            shared.add(threadLocal);
            threadLocal = null;
        }

        return threadLocal;
    }

    static <T> void checkNotCached(List<T> threadLocal, PlatformConcurrentQueue<List<T>> shared, T instance) {
        if (!Debug.ENABLED)
            throw new IllegalStateException();

        List<List<T>> temp = new List<List<T>>();

        for (;;) {
            List<T> list = shared.poll();

            if (list == null)
                break;

            temp.add(list);
        }

        for (int i = 0; i < temp.size(); i++) {
            for (int t = 0; t < temp.get(i).size(); t++)
                Debug.assertion(instance != temp.get(i).get(t));

            shared.add(temp.get(i));
        }

        if (threadLocal != null)
            for (int i = 0; i < threadLocal.size(); i++)
                Debug.assertion(instance != threadLocal.get(i));
    }
}
