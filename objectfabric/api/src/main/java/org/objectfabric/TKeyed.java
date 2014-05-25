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

import java.util.HashSet;
import java.util.concurrent.Executor;

@SuppressWarnings("rawtypes")
abstract class TKeyed<K> extends TObject {

    protected TKeyed(Resource resource, TObject.Version shared) {
        super(resource, shared);
    }

    @SuppressWarnings("unchecked")
    final TKeyedEntry getEntry(Transaction current, K key, int hash) {
        /*
         * If we have already written to this key, use this.
         */
        {
            TKeyedBase2 version = (TKeyedBase2) current.getVersion(this);

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
                TKeyedBase2 version = (TKeyedBase2) TransactionBase.getVersion(versions[i], this);

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
        if (!current.ignoreReads()) {
            TKeyedRead read = getOrCreateRead(current);
            TKeyedEntry entry = new TKeyedEntry(key, hash, TKeyedEntry.READ);
            read.putEntry(entry, true, true, false);
        }

        return getPublicEntry(current, key, hash);
    }

    final TKeyedEntry getPublicEntry(Transaction transaction, K key, int hash) {
        for (int i = transaction.getPublicSnapshotVersions().length - 1; i > TransactionManager.OBJECTS_VERSIONS_INDEX; i--) {
            TKeyedBase2 version = (TKeyedBase2) TransactionBase.getVersion(transaction.getPublicSnapshotVersions()[i], this);

            if (version != null) {
                TKeyedEntry entry = version.getWrite(key, hash);

                if (entry != null)
                    return entry;

                if (version.getCleared())
                    return null;
            }
        }

        TKeyedSharedVersion shared = (TKeyedSharedVersion) shared_();
        return TKeyedBase1.getEntry(shared.getWrites(), key, hash);
    }

    static TKeyedEntry getEntry(Version[][] snapshot, TObject object, int mapIndex, Object key, int hash) {
        for (int i = mapIndex - 1; i > TransactionManager.OBJECTS_VERSIONS_INDEX; i--) {
            TKeyedBase2 version = (TKeyedBase2) TransactionBase.getVersion(snapshot[i], object);

            if (version != null) {
                TKeyedEntry entry = version.getWrite(key, hash);

                if (entry != null)
                    return entry;

                if (version.getCleared())
                    return null;
            }
        }

        return TKeyedBase1.getEntry(((TKeyedSharedVersion) object.shared_()).getWrites(), key, hash);
    }

    //

    final TKeyedEntry putEntry(TKeyedEntry<K, Object> entry, boolean addRead) {
        Transaction outer = current_();
        Transaction inner = startWrite_(outer);

        TKeyedEntry previous;
        boolean ok = false;

        try {
            previous = putEntry(inner, entry, addRead);
            ok = true;
        } finally {
            endWrite_(outer, inner, ok);
        }

        return previous;
    }

    /*
     * Pass key to avoid GC if entry is lazy.
     */
    @SuppressWarnings("unchecked")
    final TKeyedEntry putEntry(Transaction transaction, TKeyedEntry<K, Object> entry, boolean addRead) {
        TKeyedEntry previous = null;
        boolean cleared = false;
        TKeyedBase2 version = (TKeyedBase2) transaction.getVersion(this);

        if (version != null) {
            previous = TKeyedBase1.getEntry(version.getEntries(), entry.getKey(), entry.getHash());
            cleared = version.getCleared();
        }

        /*
         * Private versions.
         */
        if (previous == null && !cleared) {
            Version[][] versions = transaction.getPrivateSnapshotVersions();

            if (versions != null) {
                for (int i = versions.length - 1; i >= 0; i--) {
                    TKeyedBase2 current = (TKeyedBase2) TransactionBase.getVersion(versions[i], this);

                    if (current != null) {
                        TKeyedEntry privateEntry = current.getWrite(entry.getKey(), entry.getHash());

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

        boolean verifySizeDeltaOnCommit = false;

        /*
         * If not overwritten or cleared, get past element. If no dependency has been
         * recorded, size delta will have to be verified during commit.
         */
        if (previous == null && !cleared) {
            previous = getPublicEntry(transaction, entry.getKey(), entry.getHash());

            if (addRead && !transaction.ignoreReads()) {
                TKeyedRead read = getOrCreateRead(transaction);
                read.putEntry(new TKeyedEntry(entry.getKey(), entry.getHash(), TKeyedEntry.READ), true, true, false);
            } else
                verifySizeDeltaOnCommit = true;
        }

        boolean existed = previous != null && !previous.isRemoval();

        if (!entry.isRemovalNoCheck() || existed) {
            if (version == null) {
                version = (TKeyedBase2) createVersion_();
                transaction.putVersion(version);
            }

            version.putEntry(entry, true, true, false);

            if (version instanceof TKeyedVersion) {
                if (verifySizeDeltaOnCommit)
                    ((TKeyedVersion) version).setVerifySizeDeltaOnCommit();

                ((TKeyedVersion) version).onPutEntry(entry, existed);
            }
        }

        return previous;
    }

    //

    final int size(Transaction transaction, boolean record) {
        int delta = 0;
        boolean committed = transaction.isCommitted();
        TKeyedVersion version = null;

        if (transaction.getWrites() != null)
            version = (TKeyedVersion) TransactionBase.getVersion(transaction.getWrites(), this);

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
                version = (TKeyedVersion) TransactionBase.getVersion(privateVersions[i], this);

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
            if (!transaction.ignoreReads()) {
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
            KeyedIterator iterator = new KeyedIterator(null, null, publicVersions, publicVersions.length - 1, null);
            HashSet test = new HashSet();

            while (iterator.hasNext()) {
                Helper.instance().disableEqualsOrHashCheck();
                boolean added = test.add(iterator.nextEntry().getKey());
                Helper.instance().enableEqualsOrHashCheck();
                Debug.assertion(added);
            }

            Debug.assertion(size == test.size());
        }

        return size;
    }

    private final int sizePublic(Version[][] publicVersions) {
        for (int i = publicVersions.length - 1; i > TransactionManager.OBJECTS_VERSIONS_INDEX; i--) {
            TKeyedVersion version = (TKeyedVersion) TransactionBase.getVersion(publicVersions[i], this);

            if (version != null)
                return version.size();
        }

        TKeyedSharedVersion version = (TKeyedSharedVersion) shared_();
        return version.size();
    }

    //

    static int hash(Object key) {
        if (Debug.ENABLED) {
            Helper.instance().disableEqualsOrHashCheck();
            Debug.assertion(!(key instanceof Version));
        }

        int h = key.hashCode();

        if (Debug.ENABLED)
            Helper.instance().enableEqualsOrHashCheck();

        return rehash(h);
    }

    /**
     * Variant of single-word Wang/Jenkins hash. C.f. ConcurrentHashMap.
     */
    static int rehash(int h) {
        h += (h << 15) ^ 0xffffcd7d;
        h ^= (h >>> 10);
        h += (h << 3);
        h ^= (h >>> 6);
        h += (h << 2) + (h << 14);
        return h ^ (h >>> 16);
    }

    static boolean equals(Object a, TKeyedEntry entry) {
        Object b = entry.getKey();

        if (a == b)
            return true;

        if (Debug.ENABLED)
            Helper.instance().disableEqualsOrHashCheck();

        boolean value = a.equals(b);

        if (Debug.ENABLED)
            Helper.instance().enableEqualsOrHashCheck();

        return value;
    }

    //

    final void clearTKeyed() {
        Transaction outer = current_();
        Transaction inner = startWrite_(outer);
        TKeyedVersion version = (TKeyedVersion) inner.getVersion(this);

        if (version == null) {
            version = (TKeyedVersion) createVersion_();
            inner.putVersion(version);
        }

        version.clearCollection();
        endWrite_(outer, inner);
    }

    final int sizeTKeyed() {
        Transaction outer = current_();
        Transaction inner = startRead_(outer);
        int size;

        try {
            size = size(inner, true);
        } finally {
            endRead_(outer, inner);
        }

        return size;
    }

    //

    public final void addListener(KeyListener<K> listener) {
        addListener(listener, workspace().callbackExecutor());
    }

    public final void addListener(KeyListener<K> listener, Executor executor) {
        workspace().addListener(this, listener, executor);
    }

    public final void removeListener(KeyListener<K> listener) {
        removeListener(listener, workspace().callbackExecutor());
    }

    public final void removeListener(KeyListener<K> listener, Executor executor) {
        workspace().removeListener(this, listener, executor);
    }

    //

    final KeyedIterator createIterator(Version[][] publicVersions, int mapIndex) {
        return new KeyedIterator(null, null, publicVersions, mapIndex, null);
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
            if (transaction != null) {
                TransactionBase.checkWorkspace(transaction, TKeyed.this);
                init(transaction.getWrites(), transaction.getPrivateSnapshotVersions(), transaction.getPublicSnapshotVersions(), transaction.getPublicSnapshotVersions().length - 1, transaction);
            } else {
                Snapshot snapshot = TKeyed.this.workspace().snapshot();
                init(null, null, snapshot.writes(), snapshot.writes().length - 1, transaction);
            }
        }

        protected KeyedIterator(Version[] writes, Version[][] privateVersions, Version[][] publicVersions, int mapIndex, Transaction transaction) {
            if (!Debug.ENABLED)
                throw new IllegalStateException();

            init(writes, privateVersions, publicVersions, mapIndex, transaction);
        }

        // TODO remove mapIndex?
        private final void init(Version[] writes, Version[][] privateVersions, Version[][] publicVersions, int mapIndex, Transaction transaction) {
            _publicVersions = publicVersions;
            _publicVersionIndex = mapIndex;

            _privateVersions = privateVersions;
            _privateVersionIndex = privateVersions != null ? privateVersions.length - 1 : -1;

            TKeyedBase2 version = null;

            if (writes != null)
                version = (TKeyedBase2) TransactionBase.getVersion(writes, TKeyed.this);

            if (version != null) {
                _cleared = version.getCleared();
                _writes = version.getEntries();
            }

            if (transaction != null && !transaction.ignoreReads()) {
                TKeyedRead read = TKeyed.this.getOrCreateRead(transaction);
                read.setFullyRead(true);
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
                    TKeyedBase2 version = (TKeyedBase2) TransactionBase.getVersion(versions, TKeyed.this);

                    if (version != null) {
                        writes = version.getEntries();
                        _cleared |= version.getCleared();
                    }
                } else if (_publicVersionIndex >= 0) {
                    if (_publicVersionIndex == TransactionManager.OBJECTS_VERSIONS_INDEX) {
                        TKeyedSharedVersion version = (TKeyedSharedVersion) TKeyed.this.shared_();
                        writes = version.getWrites();
                    } else {
                        TObject.Version[] versions = _publicVersions[_publicVersionIndex];
                        TKeyedBase2 version = (TKeyedBase2) TransactionBase.getVersion(versions, TKeyed.this);

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
            read = createRead();
            transaction.putRead(read);
        }

        return read;
    }

    @Override
    final TKeyedRead createRead() {
        TKeyedRead version = new TKeyedRead();
        version.setObject(this);
        return version;
    }

    @Override
    protected final TObject.Version createVersion_() {
        TKeyedVersion version = new TKeyedVersion();
        version.setObject(this);
        return version;
    }
}