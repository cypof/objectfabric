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

import org.objectfabric.TObject.Transaction;
import org.objectfabric.TObject.Version;
import org.objectfabric.ThreadAssert.AllowSharedRead;
import org.objectfabric.ThreadAssert.SingleThreadedThenShared;

@SingleThreadedThenShared
abstract class TransactionBase {

    static final int FLAG_IGNORE_READS = 1 << 0;

    static final int FLAG_NO_WRITES = 1 << 1;

    // TODO remove? (OK to start read-only nested)
    static final int FLAG_COMMITTED = 1 << 2;

    private final Workspace _workspace;

    private final Transaction _parent;

    private Snapshot _snapshot;

    private Version[] _reads;

    private Version[] _writes;

    /*
     * Copied from snapshot to save one indirection level.
     */
    private Version[][] _publicSnapshotVersions;

    /*
     * Allows private snapshots, e.g. to start a child transaction, to iterate over a
     * collection or to run a method remotely.
     */
    private Version[][] _privateSnapshotVersions;

    @AllowSharedRead
    private int _flags;

    // TODO needed in transaction? merge?
    private VersionMap _map;

    // !! Add new fields to reset()

    TransactionBase(Workspace workspace, Transaction parent) {
        if (workspace == null)
            throw new IllegalArgumentException();

        _workspace = workspace;
        _parent = parent;
    }

    final Workspace workspace() {
        return _workspace;
    }

    /**
     * Parent if transaction is nested, null otherwise.
     */
    final Transaction parent() {
        return _parent;
    }

    /**
     * Transaction must be cleaned because it is about to be reused, e.g. when committing
     * after a method call on same machine. later.
     */
    final void reset() {
        if (Debug.ENABLED) {
            Helper.instance().disableEqualsOrHashCheck();
            Helper.instance().getAllowExistingReadsOrWrites().remove(this);
            Helper.instance().enableEqualsOrHashCheck();
        }

        boolean failReset = false;

        if (Debug.ENABLED)
            if (Helper.instance().FailReset)
                failReset = true;

        _reads = null;
        _writes = null;

        if (!failReset)
            _publicSnapshotVersions = null;

        _privateSnapshotVersions = null;
        _flags = 0;

        _snapshot = null;
        _map = null;
    }

    final int flags() {
        return _flags;
    }

    final void flags(int value) {
        if (Debug.ENABLED)
            Debug.assertion(_flags == 0);

        _flags = value;
    }

    final void addFlags(int value) {
        _flags |= value;
    }

    final boolean ignoreReads() {
        return (_flags & FLAG_IGNORE_READS) != 0;
    }

    final boolean noWrites() {
        return (_flags & FLAG_NO_WRITES) != 0;
    }

    // Reads

    final Version[] getReads() {
        return _reads;
    }

    final void setReads(Version[] value) {
        if (Debug.ENABLED) {
            Debug.assertion(value == null || !ignoreReads());
            Debug.assertion(_reads == null);
        }

        _reads = value;
    }

    final Version getRead(TObject object) {
        if (_reads != null)
            return getVersion(_reads, object);

        return null;
    }

    final void putRead(Version read) {
        if (Debug.ENABLED)
            Debug.assertion(!ignoreReads());

        if (_reads == null)
            _reads = new Version[OpenMap.CAPACITY];

        while (!tryToPut(_reads, read)) {
            Version[] previous = _reads;

            for (;;) {
                _reads = new Version[_reads.length << OpenMap.TIMES_TWO_SHIFT];

                if (rehash(previous, _reads))
                    break;
            }
        }
    }

    // Writes

    final Version[] getWrites() {
        return _writes;
    }

    final void setWrites(Version[] value) {
        if (Debug.ENABLED)
            Debug.assertion(!noWrites() || value == null);

        _writes = value;
    }

    final Version getVersion(TObject object) {
        if (_writes != null)
            return getVersion(_writes, object);

        return null;
    }

    static Version getVersion(Version[] versions, TObject object) {
        int index = object.hash() & (versions.length - 1);

        for (int i = OpenMap.attemptsStart(versions.length); i >= 0; i--) {
            Version version = versions[index];

            if (version == null)
                return null;

            if (version.object() == object)
                return version;

            index = (index + 1) & (versions.length - 1);
        }

        return null;
    }

    static int getIndex(Version[] versions, TObject object) {
        int index = object.hash() & (versions.length - 1);

        for (int i = OpenMap.attemptsStart(versions.length); i >= 0; i--) {
            Version version = versions[index];

            if (version == null)
                return -1;

            if (version.object() == object)
                return index;

            index = (index + 1) & (versions.length - 1);
        }

        return -1;
    }

    final void putVersion(Version version) {
        if (Debug.ENABLED) {
            Debug.assertion(getWrites() == null || getVersion(version.object()) == null);
            Debug.assertion(!noWrites());
        }

        if (_writes == null) {
            if (noWrites())
                throw new RuntimeException(Strings.READ_ONLY);

            _writes = new Version[OpenMap.CAPACITY];
        }

        _writes = putVersion(_writes, version);
    }

    static Version[] putVersion(Version[] versions, Version version) {
        while (!tryToPut(versions, version)) {
            Version[] previous = versions;

            for (;;) {
                versions = new Version[versions.length << OpenMap.TIMES_TWO_SHIFT];

                if (rehash(previous, versions))
                    break;
            }
        }

        return versions;
    }

    //

    private static boolean tryToPut(Version[] versions, Version version) {
        int index = version.object().hash() & (versions.length - 1);

        if (Stats.ENABLED)
            Stats.Instance.Put.incrementAndGet();

        for (int i = OpenMap.attemptsStart(versions.length); i >= 0; i--) {
            Version current = versions[index];

            if (current == null) {
                versions[index] = version;
                return true;
            }

            if (Debug.ENABLED) {
                Debug.assertion(current != version);
                Debug.assertion(current.object() != version.object());
            }

            if (Stats.ENABLED)
                Stats.Instance.PutRetry.incrementAndGet();

            index = (index + 1) & (versions.length - 1);
        }

        return false;
    }

    private static boolean rehash(Version[] source, Version[] target) {
        for (int i = source.length - 1; i >= 0; i--) {
            Version current = source[i];

            if (current != null && !tryToPut(target, current))
                return false;
        }

        return true;
    }

    //

    final void mergeReads(TransactionBase child) {
        Version[] sources = child.getReads();

        if (sources != null) {
            if (_reads == null)
                _reads = sources;
            else
                _reads = merge(_reads, sources);
        }
    }

    final void mergeWrites(TransactionBase child) {
        Version[] sources = child.getWrites();

        if (sources != null) {
            if (isCommitted())
                throw new RuntimeException(Strings.COMMITTED);

            if (noWrites())
                throw new RuntimeException(Strings.READ_ONLY);

            if (_writes == null)
                _writes = sources;
            else
                _writes = merge(_writes, sources);
        }
    }

    private final Version[] merge(Version[] targets, Version[] sources) {
        for (int i = sources.length - 1; i >= 0; i--) {
            if (sources[i] != null) {
                int index = getIndex(targets, sources[i].object());

                if (index < 0)
                    targets = putVersion(targets, sources[i]);
                else {
                    targets[index] = targets[index].merge(targets[index], sources[i], true);

                    if (Debug.ENABLED)
                        targets[index].checkInvariants();
                }
            }
        }

        return targets;
    }

    // Snapshot

    final Version[][] getPublicSnapshotVersions() {
        return _publicSnapshotVersions;
    }

    final void setPublicSnapshotVersions(Version[][] value) {
        _publicSnapshotVersions = value;
    }

    //

    static Transaction startAccess(Workspace workspace, boolean read) {
        int flags = TransactionBase.FLAG_IGNORE_READS;

        if (read)
            flags |= TransactionBase.FLAG_NO_WRITES;

        return workspace.startImpl(flags);
    }

    static void endAccess(Transaction inner, boolean commit) {
        if (commit) {
            boolean result = TransactionManager.commit(inner);

            if (Debug.ENABLED)
                Debug.assertion(result);
        } else
            TransactionManager.abort(inner);
    }

    static void checkWorkspace(Transaction outer, TObject object) {
        if (outer.workspace() != object.workspace())
            ExpectedExceptionThrower.throwRuntimeException(Strings.WRONG_WORKSPACE);
    }

    //

    /*
     * Private snapshot.
     */

    final Version[][] getPrivateSnapshotVersions() {
        return _privateSnapshotVersions;
    }

    final void setPrivateSnapshotVersions(Version[][] value) {
        if (Debug.ENABLED)
            Debug.assertion(_privateSnapshotVersions == null);

        _privateSnapshotVersions = value;
    }

    final void addPrivateSnapshotVersions(Version[] value) {
        if (_privateSnapshotVersions != null)
            _privateSnapshotVersions = Helper.addVersions(_privateSnapshotVersions, value);
        else
            setPrivateSnapshotVersions(value);
    }

    final void setPrivateSnapshotVersions(Version[] versions) {
        if (Debug.ENABLED) {
            Debug.assertion(versions.length > 0);
            Debug.assertion(_privateSnapshotVersions == null);
        }

        _privateSnapshotVersions = new Version[1][];
        _privateSnapshotVersions[0] = versions;
    }

    final boolean isCommitted() {
        boolean value = (flags() & FLAG_COMMITTED) != 0;

        if (Debug.ENABLED)
            if (value)
                Debug.assertion(getWrites() == null);

        return value;
    }

    final Snapshot getSnapshot() {
        return _snapshot;
    }

    final void setSnapshot(Snapshot value) {
        if (Debug.ENABLED)
            Debug.assertion(value != null);

        _snapshot = value;
    }

    final VersionMap getVersionMap() {
        return _map;
    }

    final void setVersionMap(VersionMap value) {
        _map = value;
    }

    final VersionMap getOrCreateVersionMap() {
        if (_map == null) {
            _map = new VersionMap();

            if (Debug.ENABLED)
                Helper.instance().addWatcher(_map, workspace(), _snapshot, "New commit");
        }

        return _map;
    }

    //

    final Transaction startChild(int flags) {
        Transaction transaction = new Transaction(workspace(), (Transaction) this);
        transaction.setSnapshot(getSnapshot());
        transaction.setPublicSnapshotVersions(getPublicSnapshotVersions());
        transaction.setPrivateSnapshotVersions(getPrivateSnapshotVersions());

        if (getWrites() != null)
            transaction.addPrivateSnapshotVersions(getWrites());

        transaction.onStart(flags | flags());
        return transaction;
    }

    final void onStart(int flags) {
        flags(flags);

        if (Debug.ENABLED)
            checkInvariants();
    }

    final void mergePrivate(Transaction child) {
        if (Debug.ENABLED) {
            Debug.assertion(child.parent() == this);

            if (child.getVersionMap() != null)
                Helper.instance().removeWatcher(child.getVersionMap(), this, _snapshot, "merge(Transaction child)");
        }

        mergeReads(child);
        mergeWrites(child);

        if (Debug.ENABLED)
            assertUpdates();
    }

    // Debug

    final void forceSnapshot(Snapshot value) {
        _snapshot = value;
    }

    final void assertUpdates() {
        if (!Debug.ENABLED)
            throw new IllegalStateException();

        Version[][] versions = getPublicSnapshotVersions();

        if (getPrivateSnapshotVersions() != null || getWrites() != null) {
            if (getPrivateSnapshotVersions() != null)
                for (Version[] map : getPrivateSnapshotVersions())
                    if (map != null)
                        versions = Helper.addVersions(versions, map);

            if (getWrites() != null)
                versions = Helper.addVersions(versions, getWrites());
        }
    }

    final void checkInvariants() {
        if (!Debug.ENABLED)
            throw new IllegalStateException();

        if (parent() == null)
            workspace().checkNotCached((Transaction) this);

        if (Debug.THREADS) {
            /*
             * If committed, must be shared, otherwise private.
             */
            Snapshot snapshot = null;

            if (parent() == null)
                snapshot = workspace().snapshot();

            if (snapshot != null && getVersionMap() != null)
                ThreadAssert.assertShared(getVersionMap());
            else
                ThreadAssert.assertPrivate(this);
        }

        Helper.instance().disableEqualsOrHashCheck();

        if (ignoreReads() && !Helper.instance().getAllowExistingReadsOrWrites().containsKey(this))
            Debug.assertion(_reads == null);

        if (noWrites() && !Helper.instance().getAllowExistingReadsOrWrites().containsKey(this))
            Debug.assertion(_writes == null);

        Helper.instance().enableEqualsOrHashCheck();
    }
}
