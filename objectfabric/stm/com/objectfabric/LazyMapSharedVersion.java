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

import com.objectfabric.misc.Debug;

final class LazyMapSharedVersion extends TKeyedSharedVersion {

    private final boolean _inMemory;

    private boolean _cache = true;

    public LazyMapSharedVersion(boolean inMemory) {
        _inMemory = inMemory;
    }

    // TODO: Remote caching not ready
    public void disableCache() {
        _cache = false;
    }

    @Override
    public int getClassId() {
        return DefaultObjectModelBase.COM_OBJECTFABRIC_LAZYMAP_CLASS_ID;
    }

    @SuppressWarnings("unchecked")
    @Override
    public TObject.Version merge(TObject.Version target, TObject.Version next, int flags) {
        if (Debug.ENABLED) {
            Debug.assertion(flags == 0);
            Debug.assertion(size() == 0);
            Debug.assertion(this == target);
        }

        if (_cache) {
            if (_inMemory) {
                /*
                 * Without a store, keep in memory -> usual map.
                 */
                super.merge(target, next, flags);
            } else {
                if (Debug.ENABLED) {
                    Debug.assertion(getReference().getUserReferences() == null);
                    UserTObject object = getReference().get();

                    if (object != null)
                        Debug.assertion(object.getUserReferences() == null);
                }

                /*
                 * Else keep objects through soft references. Content can be GCed and
                 * retrieved from the store as needed later.
                 */
                TKeyedBase2 source = (TKeyedBase2) next;
                TKeyedEntry[] initialWrites = getWrites();
                TKeyedEntry[] writes = initialWrites;

                if (source.getCleared()) {
                    if (writes != null)
                        for (int i = writes.length - 1; i >= 0; i--)
                            writes[i] = null;

                    _entryCount = 0;
                }

                if (source.getEntries() != null) {
                    for (int i = source.getEntries().length - 1; i >= 0; i--) {
                        TKeyedEntry entry = source.getEntries()[i];

                        if (entry != null && entry != TKeyedEntry.REMOVED) {
                            Object key = entry.getKeyDirect();
                            Object value = entry.getValueDirect();
                            entry = new TKeyedEntry(key, entry.getHash(), value, true);

                            if (writes == null)
                                writes = new TKeyedEntry[source.getEntries().length];

                            putEntryAndSkipOnException(writes, key, entry, true, true);

                            if (_entryCount > writes.length >> LOAD_BIT_SHIFT)
                                writes = rehash(writes);
                        }
                    }
                }

                if (writes != initialWrites)
                    setWrites(writes);

                if (Debug.ENABLED)
                    checkInvariants();
            }
        } else if (Debug.ENABLED)
            Debug.assertion(getWrites() == null);

        return this;
    }

    @Override
    public TObject.Version createVersion() {
        return new LazyMapVersion(this);
    }

    @Override
    public boolean isLazy() {
        return true;
    }

    // Debug

    @Override
    public void checkInvariants_() {
        super.checkInvariants_();

        Debug.assertion(size() == 0);
    }
}
