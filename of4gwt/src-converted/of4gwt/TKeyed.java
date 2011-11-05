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

import java.util.HashSet;

import of4gwt.TObject.UserTObject;
import of4gwt.Visitor.ClassVisitor;
import of4gwt.misc.Debug;
import of4gwt.misc.List;
import of4gwt.misc.PlatformClass;

public abstract class TKeyed<K> extends UserTObject {

    protected TKeyed(TObject.Version shared, Transaction trunk) {
        super(shared, trunk);
    }

    @SuppressWarnings("unchecked")
    final TKeyedEntry getEntry(Transaction current, K key, int hash) {
        if (Debug.ENABLED)
            Debug.assertion(!current.isPublic());

        /*
         * If we have already written to this key, use this.
         */
        {
            TKeyedBase2 version = (TKeyedBase2) current.getVersionFromTObject(this);

            if (version != null) {
                TKeyedEntry entry = version.getWrite(key, hash);

                if (entry != null)
                    return entry;

                if (version.getCleared())
                    return null;
            }
        }

        /*
         * Same if it was in a private snapshot.
         */
        Version[][] versions = current.getPrivateSnapshotVersions();

        if (versions != null) {
            for (int i = versions.length - 1; i >= 0; i--) {
                TKeyedBase2 version = (TKeyedBase2) TransactionSets.getVersionFromTObject(versions[i], this);

                if (version != null) {
                    TKeyedEntry entry = version.getWrite(key, hash);

                    if (entry != null)
                        return entry;

                    if (version.getCleared())
                        return null;
                }
            }
        }

        /*
         * Otherwise keep track of read and find previous value.
         */
        if (!current.noReads()) {
            TKeyedRead read = getOrCreateRead(current);
            TKeyedEntry entry = new TKeyedEntry(key, hash, TKeyedEntry.READ, false);
            read.putEntry(key, entry, true, true);
        }

        return getPublicEntry(current, key, hash);
    }

    final TKeyedEntry getPublicEntry(Transaction transaction, K key, int hash) {
        for (int i = transaction.getPublicSnapshotVersions().length - 1; i > TransactionManager.OBJECTS_VERSIONS_INDEX; i--) {
            TKeyedBase2 version = (TKeyedBase2) TransactionSets.getVersionFromTObject(transaction.getPublicSnapshotVersions()[i], this);

            if (version != null) {
                TKeyedEntry entry = version.getWrite(key, hash);

                if (entry != null)
                    return entry;

                if (version.getCleared())
                    return null;
            }
        }

        TKeyedSharedVersion shared = (TKeyedSharedVersion) getSharedVersion_objectfabric();
        TKeyedEntry entry = TKeyedBase1.getEntry(shared.getWrites(), key, hash);

        if (Debug.ENABLED)
            if (!PlatformClass.getClassName(this).contains("Lazy") && entry != null)
                Debug.assertion(!entry.isSoft() && entry.getKeyDirect() != null);

        return entry;
    }

    static final TKeyedEntry getEntry(Version[][] snapshot, int mapIndex, Object key, int hash, Object shared) {
        for (int i = mapIndex - 1; i > TransactionManager.OBJECTS_VERSIONS_INDEX; i--) {
            TKeyedBase2 version = (TKeyedBase2) TransactionSets.getVersionFromSharedVersion(snapshot[i], shared);

            if (version != null) {
                TKeyedEntry entry = version.getWrite(key, hash);

                if (entry != null)
                    return entry;

                if (version.getCleared())
                    return null;
            }
        }

        return TKeyedBase1.getEntry(((TKeyedSharedVersion) shared).getWrites(), key, hash);
    }

    //

    final TKeyedEntry putEntry(Object key, TKeyedEntry entry, boolean dependency) {
        Transaction outer = Transaction.getCurrent();
        return putEntry(key, entry, dependency, outer);
    }

    final TKeyedEntry putEntry(Object key, TKeyedEntry entry, boolean dependency, Transaction outer) {
        Transaction inner = Transaction.startWrite(outer, this);

        if (inner != outer)
            dependency = false;

        TKeyedEntry previous;
        boolean ok = false;

        try {
            previous = putEntry(this, inner, key, entry, dependency);
            ok = true;
        } finally {
            Transaction.endWrite(outer, inner, ok);
        }

        return previous;
    }

    @SuppressWarnings("unchecked")
    final TKeyedEntry putEntry(TKeyed object, Transaction transaction, Object key, TKeyedEntry entry, boolean record) {
        TKeyedEntry previous = null;
        boolean cleared = false;
        TKeyedBase2 version = (TKeyedBase2) transaction.getVersionFromTObject(this);

        if (version != null) {
            previous = TKeyedBase1.getEntry(version.getEntries(), key, entry.getHash());
            cleared = version.getCleared();
        }

        /*
         * Private versions.
         */
        if (previous == null && !cleared) {
            Version[][] versions = transaction.getPrivateSnapshotVersions();

            if (versions != null) {
                for (int i = versions.length - 1; i >= 0; i--) {
                    TKeyedBase2 current = (TKeyedBase2) TransactionSets.getVersionFromTObject(versions[i], object);

                    if (current != null) {
                        TKeyedEntry privateEntry = current.getWrite(key, entry.getHash());

                        if (privateEntry != null) {
                            previous = privateEntry;
                            break;
                        }

                        if (current.getCleared()) {
                            cleared = true;
                            break;
                        }
                    }
                }
            }
        }

        TKeyedEntry result = previous;
        boolean verifySizeDeltaOnCommit = false;

        /*
         * If not overwritten or cleared, get past element. If no dependency has been
         * recorded, size delta will have to be verified during commit.
         */
        if (previous == null && !cleared) {
            previous = object.getPublicEntry(transaction, key, entry.getHash());

            if (record) {
                if (!transaction.noReads()) {
                    TKeyedRead read = object.getOrCreateRead(transaction);
                    read.putEntry(key, new TKeyedEntry(key, entry.getHash(), TKeyedEntry.READ, false), true, true);
                }

                result = previous;
            } else
                verifySizeDeltaOnCommit = true;
        }

        boolean existed = previous != null && !previous.isRemoval();

        if (Debug.ENABLED)
            Debug.assertion(!entry.isUpdate());

        if (!entry.isRemovalNoCheck() || existed) {
            entry.setIsUpdate(existed);

            if (version == null) {
                version = (TKeyedBase2) getSharedVersion_objectfabric().createVersion();
                transaction.putVersion(this, version);
            }

            version.putEntry(key, entry, false, true);

            if (version instanceof TKeyedVersion) {
                if (verifySizeDeltaOnCommit)
                    ((TKeyedVersion) version).setVerifySizeDeltaOnCommit();

                ((TKeyedVersion) version).onPutEntry(entry, existed);
            }
        }

        return result;
    }

    //

    final int size(Transaction transaction, boolean record) {
        int delta = 0;
        boolean committed = transaction.isCommitted();
        Version[] writes = transaction.getWrites();
        TKeyedVersion version = (TKeyedVersion) TransactionSets.getVersionFromTObject(writes, this);

        if (version != null) {
            if (Debug.ENABLED)
                Debug.assertion(!version.sizeValid() || transaction.isCommitted());

            if (committed)
                return version.size();

            delta = version.sizeDelta();

            if (version.getCleared())
                return delta;
        }

        Version[][] privateVersions = transaction.getPrivateSnapshotVersions();

        if (privateVersions != null) {
            for (int i = privateVersions.length - 1; i >= 0; i--) {
                version = (TKeyedVersion) TransactionSets.getVersionFromTObject(privateVersions[i], this);

                if (version != null) {
                    if (Debug.ENABLED)
                        Debug.assertion(!version.sizeValid() || transaction.isCommitted());

                    if (committed)
                        return version.size();

                    delta += version.sizeDelta();

                    if (version.getCleared())
                        return delta;
                }
            }
        }

        /*
         * Otherwise mark read and use public versions.
         */
        if (record) {
            if (!transaction.noReads()) {
                TKeyedRead read = getOrCreateRead(transaction);
                read.setFullyRead(true);
            }
        }

        Version[][] publicVersions = transaction.getPublicSnapshotVersions();
        int size = size(publicVersions);
        return size + delta;
    }

    @SuppressWarnings("unchecked")
    final int size(Version[][] publicVersions) {
        int size = sizePublic(publicVersions);

        if (Debug.ENABLED) {
            KeyedIterator iterator = new KeyedIterator(null, null, publicVersions, publicVersions.length - 1, null, false);
            HashSet test = new HashSet();

            while (iterator.hasNext()) {
                Helper.getInstance().disableEqualsOrHashCheck();
                boolean added = test.add(iterator.nextEntry().getKey());
                Helper.getInstance().enableEqualsOrHashCheck();
                Debug.assertion(added);
            }

            Debug.assertion(size == test.size());
        }

        return size;
    }

    private final int sizePublic(Version[][] publicVersions) {
        for (int i = publicVersions.length - 1; i > TransactionManager.OBJECTS_VERSIONS_INDEX; i--) {
            TKeyedVersion version = (TKeyedVersion) TransactionSets.getVersionFromTObject(publicVersions[i], this);

            if (version != null)
                return version.size();
        }

        TKeyedSharedVersion version = (TKeyedSharedVersion) getSharedVersion_objectfabric();
        return version.size();
    }

    //

    static int hash(Object key) {
        if (Debug.ENABLED)
            Helper.getInstance().disableEqualsOrHashCheck();

        int h = key.hashCode();

        if (Debug.ENABLED)
            Helper.getInstance().enableEqualsOrHashCheck();

        /*
         * C.f. HashMap.
         */

        // This function ensures that hashCodes that differ only by
        // constant multiples at each bit position have a bounded
        // number of collisions (approximately 8 at default load factor).
        h ^= (h >>> 20) ^ (h >>> 12);
        return h ^ (h >>> 7) ^ (h >>> 4);
    }

    static boolean equals(Object a, TKeyedEntry entry) {
        Object b = entry.getKey();

        if (b == null) {
            if (Debug.ENABLED)
                Debug.assertion(entry.isSoft());

            return false;
        }

        if (a == b)
            return true;

        if (Debug.ENABLED)
            Helper.getInstance().disableEqualsOrHashCheck();

        boolean value = a.equals(b);

        if (Debug.ENABLED)
            Helper.getInstance().enableEqualsOrHashCheck();

        return value;
    }

    //

    final void clearTKeyed() {
        Transaction outer = Transaction.getCurrent();
        Transaction inner = Transaction.startWrite(outer, this);
        TKeyedVersion version = (TKeyedVersion) inner.getVersionFromTObject(this);

        if (version == null) {
            version = (TKeyedVersion) getSharedVersion_objectfabric().createVersion();
            inner.putVersion(this, version);
        }

        version.clearCollection();
        Transaction.endWrite(outer, inner);
    }

    final int sizeTKeyed() {
        Transaction outer = Transaction.getCurrent();
        Transaction inner = Transaction.startRead(outer, this);
        int size;

        try {
            size = size(inner, true);
        } finally {
            Transaction.endRead(outer, inner);
        }

        return size;
    }

    //

    public final void addListener(KeyListener<K> listener) {
        addListener(listener, OF.getDefaultAsyncOptions());
    }

    public final void addListener(KeyListener<K> listener, AsyncOptions options) {
        OF.addListener(this, listener, options);
    }

    public final void removeListener(KeyListener<K> listener) {
        removeListener(listener, OF.getDefaultAsyncOptions());
    }

    public final void removeListener(KeyListener<K> listener, AsyncOptions options) {
        OF.removeListener(this, listener, options);
    }

    //

    final KeyedIterator createIterator(Version[][] publicVersions, int mapIndex) {
        return new KeyedIterator(null, null, publicVersions, mapIndex, null, false);
    }

    protected class KeyedIterator {

        private List<TKeyedEntry[]> _previousWrites;

        private Version[][] _publicVersions, _privateVersions;

        private int _publicVersionIndex, _privateVersionIndex, _entryIndex;

        /*
         * The the writes themselves and not the version as the array can be changed
         * during iteration.
         */
        private TKeyedEntry[] _writes;

        private boolean _cleared;

        private TKeyedEntry _next;

        private TKeyedEntry _current;

        protected KeyedIterator(Transaction transaction) {
            transaction = Transaction.startIteration(transaction, TKeyed.this);

            init(transaction.getWrites(), transaction.getPrivateSnapshotVersions(), transaction.getPublicSnapshotVersions(), transaction.getPublicSnapshotVersions().length - 1, transaction, true);
        }

        protected KeyedIterator(Version[] writes, Version[][] privateVersions, Version[][] publicVersions, int mapIndex, Transaction transaction, boolean record) {
            init(writes, privateVersions, publicVersions, mapIndex, transaction, record);
        }

        private final void init(Version[] writes, Version[][] privateVersions, Version[][] publicVersions, int mapIndex, Transaction transaction, boolean record) {
            if (Debug.ENABLED && transaction != null) {
                Debug.assertion(!transaction.isPublic());
                Debug.assertion(transaction.getTrunk() == getTrunk());
            }

            _publicVersions = publicVersions;
            _publicVersionIndex = mapIndex;

            _privateVersions = privateVersions;
            _privateVersionIndex = privateVersions != null ? privateVersions.length - 1 : -1;

            TKeyedBase2 version = (TKeyedBase2) TransactionSets.getVersionFromTObject(writes, TKeyed.this);

            if (version != null) {
                _cleared = version.getCleared();
                _writes = version.getEntries();
            }

            if (record) {
                if (transaction == null)
                    throw new AssertionError();

                if (!transaction.noReads()) {
                    TKeyedRead read = TKeyed.this.getOrCreateRead(transaction);
                    read.setFullyRead(true);
                }
            }

            if (_writes == null)
                _writes = findNextWrites();

            if (_writes != null) {
                _entryIndex = _writes.length - 1;
                findNext();
            }
        }

        public final boolean hasNext() {
            return _next != null;
        }

        public final TKeyedEntry nextEntry() {
            if (_next == null)
                ExpectedExceptionThrower.throwNoSuchElementException();

            _current = _next;
            findNext();

            if (Debug.ENABLED)
                Debug.assertion(!_current.isRemoval());

            return _current;
        }

        public final TKeyedEntry getCurrent() {
            return _current;
        }

        private final void findNext() {
            for (;;) {
                if (_entryIndex < 0) {
                    TKeyedEntry[] writes = findNextWrites();

                    if (writes == null) {
                        _next = null;
                        return;
                    }

                    if (_writes != null) {
                        if (_previousWrites == null)
                            _previousWrites = new List<TKeyedEntry[]>();

                        _previousWrites.add(_writes);
                    }

                    _writes = writes;
                    _entryIndex = writes.length - 1;
                }

                TKeyedEntry entry = _writes[_entryIndex--];

                if (entry != null && entry != TKeyedEntry.REMOVED && !entry.isRemoval()) {
                    if (!alreadyIterated(entry)) {
                        _next = entry;
                        return;
                    }
                }
            }
        }

        private final TKeyedEntry[] findNextWrites() {
            for (;;) {
                if (_cleared)
                    return null;

                TKeyedEntry[] writes = null;

                if (_privateVersionIndex >= 0) {
                    TObject.Version[] versions = _privateVersions[_privateVersionIndex--];
                    TKeyedBase2 version = (TKeyedBase2) TransactionSets.getVersionFromTObject(versions, TKeyed.this);

                    if (version != null) {
                        writes = version.getEntries();
                        _cleared |= version.getCleared();
                    }
                } else if (_publicVersionIndex >= 0) {
                    if (_publicVersionIndex == TransactionManager.OBJECTS_VERSIONS_INDEX) {
                        TKeyedSharedVersion version = (TKeyedSharedVersion) TKeyed.this.getSharedVersion_objectfabric();

                        if (!(version instanceof LazyMapSharedVersion))
                            writes = version.getWrites();
                    } else {
                        TObject.Version[] versions = _publicVersions[_publicVersionIndex];
                        TKeyedBase2 version = (TKeyedBase2) TransactionSets.getVersionFromTObject(versions, TKeyed.this);

                        if (version != null) {
                            writes = version.getEntries();
                            _cleared |= version.getCleared();
                        }
                    }

                    _publicVersionIndex--;
                } else
                    return null;

                if (writes != null)
                    return writes;
            }
        }

        private final boolean alreadyIterated(TKeyedEntry entry) {
            if (_previousWrites != null)
                for (int i = 0; i < _previousWrites.size(); i++)
                    if (TKeyedBase1.getEntry(_previousWrites.get(i), entry.getKey(), entry.getHash()) != null)
                        return true;

            return false;
        }
    }

    //

    final TKeyedRead getOrCreateRead(Transaction transaction) {
        TKeyedRead read = (TKeyedRead) transaction.getRead(this);

        if (read == null) {
            read = (TKeyedRead) getSharedVersion_objectfabric().createRead();
            transaction.putRead(this, read);
        }

        return read;
    }

    //

    public static class Visitor<K, V> extends ClassVisitor {

        public Visitor(of4gwt.Visitor visitor) {
            super(visitor);
        }

        @Override
        protected int getId() {
            return of4gwt.Visitor.KEYED_VISITOR_ID;
        }

        /**
         * @param object
         * @param key
         */
        protected void onRead(TObject object, K key) {
        }

        /**
         * @param object
         * @param key
         * @param value
         */
        protected void onPut(TObject object, K key, V value) {
        }

        /**
         * @param object
         * @param key
         */
        protected void onRemoval(TObject object, K key) {
        }

        /**
         * @param object
         */
        protected void onClear(TObject object) {
        }

        @SuppressWarnings("unchecked")
        protected void visitTKeyed(TObject.Version shared, TKeyedEntry[] entries, boolean cleared) {
            if (entries != null || cleared) {
                int index;
                TObject object;

                if (getParent().interrupted()) {
                    index = getParent().resumeInt();
                    object = (TObject) getParent().resume();
                } else {
                    index = -1;
                    object = shared.getReference().get();
                }

                if (object != null) {
                    if (index < 0) {
                        if (cleared) {
                            onClear(object);

                            if (getParent().interrupted()) {
                                getParent().interrupt(object);
                                getParent().interruptInt(index);
                                return;
                            }
                        }

                        index = 0;
                    }

                    if (entries != null) {
                        for (; index < entries.length; index++) {
                            if (entries[index] != null && entries[index] != TKeyedEntry.REMOVED) {
                                if (getParent().visitingReads())
                                    onRead(object, (K) entries[index].getKey());
                                else if (entries[index].isRemoval())
                                    onRemoval(object, (K) entries[index].getKey());
                                else
                                    onPut(object, (K) entries[index].getKey(), (V) entries[index].getValue());

                                if (getParent().interrupted()) {
                                    getParent().interrupt(object);
                                    getParent().interruptInt(index);
                                    return;
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}