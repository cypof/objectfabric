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

package of4gwt;

import of4gwt.misc.Debug;
import of4gwt.misc.Log;
import of4gwt.misc.SparseArrayHelper;

@SuppressWarnings("unchecked")
class TKeyedBase1 extends TObject.Version {

    protected static final int DEFAULT_INITIAL_CAPACITY = 8;

    protected static final int LOAD_BIT_SHIFT = 2; // 25% load

    protected int _entryCount;

    public TKeyedBase1(TObject.Version shared) {
        super(shared);
    }

    //

    public static final TKeyedEntry getEntry(TKeyedEntry[] entries, Object key, int hash) {
        if (entries == null)
            return null;

        int index = hash & (entries.length - 1);

        for (int fullDetector = entries.length - 1; fullDetector >= 0; fullDetector--) {
            TKeyedEntry entry = entries[index];

            if (entry == null)
                return null;

            // Might throw, no effect
            if (entry.getHash() == hash && entry != TKeyedEntry.REMOVED && TKeyed.equals(key, entry))
                return entry;

            index = (index + 1) & (entries.length - 1);
        }

        return null;
    }

    public final TKeyedEntry putEntry(TKeyedEntry[] entries, Object key, TKeyedEntry entry, boolean keepRemovals, boolean weak) {
        TKeyedEntry previous = putEntry_(entries, key, entry, keepRemovals, weak);

        if (Debug.ENABLED)
            Debug.assertion(previous != TKeyedEntry.REMOVED);

        if (previous == null) {
            _entryCount++;

            if (Debug.ENABLED) {
                boolean cleared = this.isShared();

                if (this instanceof TKeyedRead && ((TKeyedRead) this).getCleared())
                    cleared = true;

                if (cleared) {
                    // Should be a removal only if there is entry to remove
                    Debug.assertion(!entry.isRemoval());
                    Debug.assertion(!entry.isUpdate());
                }
            }
        } else {
            if (!keepRemovals && entry.isRemoval() && !previous.isUpdate())
                _entryCount--;

            if (Debug.ENABLED)
                if (this instanceof TKeyedVersion)
                    Debug.assertion(!(previous.isRemoval() && entry.isRemoval()));
        }

        return previous;
    }

    // Pass key to prevent GC if entry is weak
    private final TKeyedEntry putEntry_(TKeyedEntry[] entries, Object key, TKeyedEntry entry, boolean keepRemovals, boolean weak) {
        if (Debug.ENABLED) {
            Debug.assertion(key != null);
            Debug.assertion(entry != TKeyedEntry.REMOVED);
        }

        int index = entry.getHash() & (entries.length - 1);

        if (Stats.ENABLED)
            Stats.getInstance().Put.incrementAndGet();

        for (;;) {
            if (entries[index] == null) {
                if (Debug.ENABLED)
                    Debug.assertion(getEntry(entries, key, entry.getHash()) == null);

                entries[index] = entry;
                return null;
            }

            if (weak) {
                if (entries[index] != TKeyedEntry.REMOVED && (entries[index].getKey() == null || entries[index].getValue() == null)) {
                    entries[index] = TKeyedEntry.REMOVED;
                    _entryCount--;
                }
            }

            if (entries[index] == TKeyedEntry.REMOVED) {
                int index2 = (index + 1) & (entries.length - 1);

                // Check if present farther
                for (int fullDetector = entries.length - 1; fullDetector >= 0; fullDetector--) {
                    if (entries[index2] == null)
                        break;

                    if (entries[index2] != TKeyedEntry.REMOVED) {
                        // Might throw
                        if (entry.getHash() == entries[index2].getHash() && TKeyed.equals(key, entries[index2])) {
                            TKeyedEntry previous = entries[index2];
                            update(entries, index2, entry, keepRemovals);
                            return previous;
                        }
                    }

                    index2 = (index2 + 1) & (entries.length - 1);
                }

                entries[index] = entry;
                return null;
            }

            // Might throw
            if (entry.getHash() == entries[index].getHash() && TKeyed.equals(key, entries[index])) {
                TKeyedEntry previous = entries[index];
                update(entries, index, entry, keepRemovals);
                return previous;
            }

            if (Stats.ENABLED)
                Stats.getInstance().PutRetry.incrementAndGet();

            index = (index + 1) & (entries.length - 1);
        }
    }

    private static final void update(TKeyedEntry[] entries, int index, TKeyedEntry entry, boolean keepRemovals) {
        TKeyedEntry previous = entries[index];

        if (keepRemovals)
            entries[index] = entry;
        else {
            if (Debug.ENABLED) {
                if (!previous.isRemoval())
                    Debug.assertion(entry.isUpdate());

                if (entry.isRemoval())
                    Debug.assertion(!previous.isRemoval());
            }

            if (!previous.isUpdate() && entry.isRemoval())
                entries[index] = TKeyedEntry.REMOVED;
            else {
                entries[index] = entry;
                entry.setIsUpdate(previous.isUpdate());
            }
        }
    }

    //

    protected final TKeyedEntry putEntryAndSkipOnException(TKeyedEntry[] entries, Object key, TKeyedEntry entry, boolean keepRemovals, boolean weak) {
        try {
            return putEntry(entries, key, entry, keepRemovals, weak);
        } catch (Throwable t) {
            Log.write(t);

            /*
             * Don't let a user exception from K.equals() go up into the system as it's
             * not tested for this so skip entry instead.
             */
            return entry;
        }
    }

    protected static final TKeyedEntry[] rehash(TKeyedEntry[] entries) {
        TKeyedEntry[] newEntries = new TKeyedEntry[entries.length << SparseArrayHelper.TIMES_TWO_SHIFT];

        for (int i = entries.length - 1; i >= 0; i--) {
            if (entries[i] != null && entries[i] != TKeyedEntry.REMOVED) {
                int index = entries[i].getHash() & (newEntries.length - 1);

                for (;;) {
                    if (newEntries[index] == null) {
                        newEntries[index] = entries[i];
                        break;
                    }

                    index = (index + 1) & (newEntries.length - 1);
                }
            }
        }

        return newEntries;
    }

    // Debug

    final void checkEntries(TKeyedEntry[] entries) {
        if (!Debug.ENABLED)
            throw new AssertionError();

        int count = 0;

        for (int i = entries.length - 1; i >= 0; i--) {
            TKeyedEntry entry = entries[i];

            if (entry != null && entry != TKeyedEntry.REMOVED) {
                count++;

                Object key = entry.getKey();

                if (key != null)
                    Debug.assertion(entry.getHash() == TKeyed.hash(key));
                else
                    Debug.assertion(entry.isSoft());

                boolean first = false;

                if (isShared()) {
                    if (!entry.isSoft()) {
                        Debug.assertion(!(entry.getKeyDirect() instanceof UserTObject));
                        Debug.assertion(!(entry.getValueDirect() instanceof UserTObject));
                    }

                    first = true;
                } else {
                    Debug.assertion(!(entry.getKeyDirect() instanceof TObject.Version));

                    if (!entry.isRemoval())
                        Debug.assertion(!(entry.getValueDirect() instanceof TObject.Version));
                }

                if (this instanceof TKeyedVersion && ((TKeyedVersion) this).getCleared())
                    first = true;

                if (first)
                    Debug.assertion(!entry.isRemoval() && !entry.isUpdate());
            }
        }

        Debug.assertion(_entryCount <= 1 + (entries.length >> LOAD_BIT_SHIFT));
        Debug.assertion(_entryCount == count);

        if (entries.length < 100) {
            for (int j = 0; j < entries.length; j++) {
                for (int i = 0; i < j; i++) {
                    if (entries[i] != null && entries[j] != null && entries[i] != TKeyedEntry.REMOVED && entries[j] != TKeyedEntry.REMOVED) {
                        Helper.getInstance().disableEqualsOrHashCheck();
                        Debug.assertion(!entries[i].getKey().equals(entries[j].getKey()));
                        Helper.getInstance().enableEqualsOrHashCheck();
                    }
                }
            }
        }
    }
}