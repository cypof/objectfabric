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

import com.objectfabric.misc.PlatformConcurrentMap;

/**
 * Allows a store to maintain unique object identities by reusing instances between
 * queries.
 */
final class UniqueIdentityMap extends TMap<Object, TObject> {

    public UniqueIdentityMap() {
        super(new UniqueIdentityMapSharedVersion(), Transaction.getDefaultTrunk());
    }

    @Override
    public TObject get(Object key) {
        return super.get(key);
    }

    private static class UniqueIdentityMapSharedVersion extends TMap.SharedVersion {

        /*
         * Objects just loaded by a transaction might not yet be visible to other threads.
         * Use a concurrent map in this case to make sure we do not create two instances.
         */
        private final PlatformConcurrentMap<Object, TObject> _initializingObjects = new PlatformConcurrentMap<Object, TObject>();

        @Override
        public Version merge(Version target, Version next, int flags) {
            Version merged = super.merge(target, next, flags);
            TKeyedVersion source = (TKeyedVersion) next;

            /*
             * When a key is written to shared memory, it will be visible to any
             * transaction so no need to keep it in concurrent map.
             */
            for (int i = 0; i < source.getEntries().length; i++) {
                Object key = source.getEntries()[i].getKey();
                _initializingObjects.remove(key);
            }

            return merged;
        }
    }
}