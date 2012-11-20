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

@SuppressWarnings("rawtypes")
class TKeyedBase1 extends TObject.Version {

    static int getIndex(TKeyedEntry[] entries, Object key, int hash) {
        if (entries == null)
            return -1;

        int index = hash & (entries.length - 1);

        for (int i = OpenMap.attemptsStart(entries.length); i >= 0; i--) {
            TKeyedEntry entry = entries[index];

            if (entry == null)
                return -1;

            // Might throw, no effect
            if (entry != TKeyedEntry.REMOVED && entry.getHash() == hash && TKeyed.equals(key, entry))
                return index;

            index = (index + 1) & (entries.length - 1);
        }

        return -1;
    }

    static TKeyedEntry getEntry(TKeyedEntry[] entries, Object key, int hash) {
        int index = getIndex(entries, key, hash);

        if (index >= 0)
            return entries[index];

        return null;
    }

    final TKeyedEntry[] putEntry(TKeyedEntry[] entries, TKeyedEntry entry, boolean keepRemovals, boolean checkNoPrevious) {
        if (Debug.ENABLED)
            Debug.assertion(entry != TKeyedEntry.REMOVED);

        int index = getIndex(entries, entry.getKey(), entry.getHash());

        if (index >= 0) {
            TKeyedEntry previous = entries[index];

            if (keepRemovals)
                entries[index] = entry;
            else {
                if (Debug.ENABLED)
                    if (entry.isRemoval())
                        Debug.assertion(!previous.isRemoval());

                if (entry.isRemoval())
                    entries[index] = TKeyedEntry.REMOVED;
                else
                    entries[index] = entry;
            }
        } else if (keepRemovals || !entry.isRemoval()) {
            if (Stats.ENABLED)
                Stats.Instance.Put.incrementAndGet();

            while (!tryToPut(entries, entry)) {
                TKeyedEntry[] previous = entries;

                for (;;) {
                    entries = new TKeyedEntry[entries.length << OpenMap.TIMES_TWO_SHIFT];

                    if (rehash(previous, entries))
                        break;
                }
            }
        }

        return entries;
    }

    private static boolean tryToPut(TKeyedEntry[] entries, TKeyedEntry entry) {
        int index = entry.getHash() & (entries.length - 1);

        for (int i = OpenMap.attemptsStart(entries.length); i >= 0; i--) {
            if (entries[index] == null || entries[index] == TKeyedEntry.REMOVED) {
                entries[index] = entry;
                return true;
            }

            if (Stats.ENABLED)
                Stats.Instance.PutRetry.incrementAndGet();

            index = (index + 1) & (entries.length - 1);
        }

        return false;
    }

    private static boolean rehash(TKeyedEntry[] source, TKeyedEntry[] target) {
        for (int i = source.length - 1; i >= 0; i--)
            if (source[i] != null && !tryToPut(target, source[i]))
                return false;

        return true;
    }

    //

    final TKeyedEntry[] putEntryAndSkipOnException(TKeyedEntry[] entries, TKeyedEntry entry, boolean keepRemovals) {
        try {
            return putEntry(entries, entry, keepRemovals, false);
        } catch (Exception e) {
            Log.write(e);
            return entries;
        }
    }

    // Debug

    final void checkEntries(TKeyedEntry[] entries) {
        if (!Debug.ENABLED)
            throw new AssertionError();

        for (int i = entries.length - 1; i >= 0; i--) {
            TKeyedEntry entry = entries[i];

            if (entry != null && entry != TKeyedEntry.REMOVED) {
                Object key = entry.getKey();
                Debug.assertion(entry.getHash() == TKeyed.hash(key));
                boolean first = this == object().shared_();

                if (this instanceof TKeyedVersion && ((TKeyedVersion) this).getCleared())
                    first = true;

                if (first)
                    Debug.assertion(!entry.isRemoval());
            }
        }

        if (entries.length < 100) {
            for (int j = 0; j < entries.length; j++) {
                for (int i = 0; i < j; i++) {
                    if (entries[i] != null && entries[j] != null && entries[i] != TKeyedEntry.REMOVED && entries[j] != TKeyedEntry.REMOVED) {
                        Helper.instance().disableEqualsOrHashCheck();
                        Debug.assertion(!entries[i].getKey().equals(entries[j].getKey()));
                        Helper.instance().enableEqualsOrHashCheck();
                    }
                }
            }
        }
    }
}