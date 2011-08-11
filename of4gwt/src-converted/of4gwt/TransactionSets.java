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


import of4gwt.TransactionBase;
import of4gwt.misc.Debug;
import of4gwt.misc.SparseArrayHelper;
import of4gwt.misc.ThreadAssert.AllowSharedRead;
import of4gwt.misc.ThreadAssert.SingleThreadedOrShared;

@SingleThreadedOrShared
abstract class TransactionSets extends TransactionBase {

    private TObject.Version[] _reads;

    private TObject.Version[] _writes;

    /*
     * Copied from snapshot to save one indirection level.
     */
    private TObject.Version[][] _publicSnapshotVersions;

    static final int IMPORTS_INDEX = 0;

    /*
     * Allows private snapshots, e.g. to start a child transaction, to iterate over a
     * collection or to run a method remotely.
     */
    private TObject.Version[][] _privateSnapshotVersions;

    @AllowSharedRead
    private int _flags;

    // TODO put all booleans from TransactionSets and Transaction in bitset

    protected TransactionSets(TObject.Version shared, Transaction trunk) {
        super(shared, trunk, null, 0, null, null, null);
    }

    final void resetSets() {
        boolean failReset = false;

        if (Debug.ENABLED)
            if (Helper.getInstance().FailReset)
                failReset = true;

        _reads = null;
        _writes = null;

        if (!failReset)
            _publicSnapshotVersions = null;

        _privateSnapshotVersions = null;
        _flags = 0;
    }

    final int getFlags() {
        return _flags;
    }

    final void setFlags(int value) {
        if (Debug.ENABLED)
            Debug.assertion(_flags == 0);

        _flags = value;
    }

    final void addFlags(int value) {
        _flags |= value;
    }

    final boolean noReads() {
        return (_flags & Transaction.FLAG_NO_READS) != 0;
    }

    final boolean noWrites() {
        return (_flags & Transaction.FLAG_NO_WRITES) != 0;
    }

    // Reads

    final TObject.Version[] getReads() {
        if (Debug.ENABLED)
            Debug.assertion(!isPublic());

        return _reads;
    }

    final void setReads(TObject.Version[] value) {
        if (Debug.ENABLED) {
            Debug.assertion(!isPublic() && (value == null || !noReads()));
            Debug.assertion(_reads == null);
        }

        _reads = value;
    }

    final TObject.Version getRead(TObject object) {
        if (Debug.ENABLED)
            Debug.assertion(!isPublic());

        return getVersion(_reads, object.getSharedVersion_objectfabric(), object.getSharedHashCode_objectfabric());
    }

    final void putRead(UserTObject object, TObject.Version read) {
        putRead(object.getSharedVersion_objectfabric(), object.getSharedHashCode_objectfabric(), read, false);
    }

    final void putRead(Object shared, TObject.Version read) {
        putRead(shared, System.identityHashCode(shared), read, false);
    }

    private final void putRead(Object shared, int hash, TObject.Version read, boolean allowMerge) {
        if (Debug.ENABLED) {
            Debug.assertion(!isPublic() && !noReads());
            Debug.assertion(!(((TObject.Version) shared).getReference().get() instanceof Method));
        }

        if (_reads == null)
            _reads = new TObject.Version[SparseArrayHelper.DEFAULT_CAPACITY];

        while (!tryToPut(_reads, read, shared, hash, allowMerge)) {
            TObject.Version[] previous = _reads;

            for (;;) {
                _reads = new TObject.Version[_reads.length << SparseArrayHelper.TIMES_TWO_SHIFT];

                if (rehash(previous, _reads))
                    break;
            }
        }
    }

    // Writes

    final TObject.Version[] getWrites() {
        if (Debug.ENABLED)
            Debug.assertion(!isPublic());

        return _writes;
    }

    final void setWrites(TObject.Version[] value) {
        if (Debug.ENABLED)
            Debug.assertion(!isPublic() && (!noWrites() || value == null));

        _writes = value;
    }

    final TObject.Version getVersionFromTObject(UserTObject object) {
        if (Debug.ENABLED)
            Debug.assertion(!isPublic());

        return getVersionFromTObject(_writes, object);
    }

    static TObject.Version getVersionFromTObject(TObject.Version[] versions, UserTObject object) {
        return getVersion(versions, object.getSharedVersion_objectfabric(), object.getSharedHashCode_objectfabric());
    }

    final TObject.Version getVersionFromSharedVersion(Object shared) {
        if (Debug.ENABLED)
            Debug.assertion(!isPublic());

        return getVersionFromSharedVersion(_writes, shared);
    }

    static TObject.Version getVersionFromSharedVersion(TObject.Version[] versions, Object shared) {
        int hash = System.identityHashCode(shared); // TODO cache in versions?
        return getVersion(versions, shared, hash);
    }

    static TObject.Version getVersion(TObject.Version[] versions, Object shared, int hash) {
        if (Debug.ENABLED)
            Debug.assertion(((TObject.Version) shared).isShared());

        if (versions != null) {
            int index = hash & (versions.length - 1);

            for (int i = SparseArrayHelper.attemptsStart(versions.length); i >= 0; i--) {
                TObject.Version version = versions[index];

                if (version == null)
                    return null;

                if (version.getShared() == shared)
                    return version;

                index = (index + 1) & (versions.length - 1);
            }
        }

        return null;
    }

    static int getIndex(TObject.Version[] versions, Object shared, int hash) {
        if (Debug.ENABLED)
            Debug.assertion(((TObject.Version) shared).isShared());

        int index = hash & (versions.length - 1);

        for (int i = SparseArrayHelper.attemptsStart(versions.length); i >= 0; i--) {
            TObject.Version version = versions[index];

            if (version == null)
                return -1;

            if (version.getShared() == shared)
                return index;

            index = (index + 1) & (versions.length - 1);
        }

        return -1;
    }

    final void putVersion(UserTObject object, TObject.Version version) {
        if (Debug.ENABLED) {
            Debug.assertion(getWrites() == null || getVersionFromTObject(object) == null);
            Debug.assertion(!isPublic());
            Debug.assertion(!(object instanceof Method));
        }

        if (_writes == null) {
            if (noWrites())
                throw new RuntimeException(Strings.READ_ONLY_OR_COMMITTED);

            _writes = new TObject.Version[SparseArrayHelper.DEFAULT_CAPACITY];
        }

        _writes = putForTObject(_writes, version, object);
    }

    final void mergeVersion(Object shared, TObject.Version version) {
        putVersion(shared, version, true);
    }

    private final void putVersion(Object shared, TObject.Version version, boolean allowMerge) {
        if (Debug.ENABLED) {
            Debug.assertion(!isPublic() && !noWrites());
            Debug.assertion(!(((TObject.Version) shared).getReference().get() instanceof Method));
        }

        if (_writes == null) {
            if (noWrites())
                throw new RuntimeException(Strings.READ_ONLY_OR_COMMITTED);

            _writes = new TObject.Version[SparseArrayHelper.DEFAULT_CAPACITY];
        }

        _writes = putForShared(_writes, version, shared, allowMerge);
    }

    static TObject.Version[] putForTObject(TObject.Version[] versions, TObject.Version version, UserTObject object) {
        return put(versions, version, object.getSharedVersion_objectfabric(), object.getSharedHashCode_objectfabric(), false);
    }

    static TObject.Version[] putForShared(TObject.Version[] versions, TObject.Version version, Object shared) {
        return putForShared(versions, version, shared, false);
    }

    static TObject.Version[] putForShared(TObject.Version[] versions, TObject.Version version, Object shared, boolean allowMerge) {
        return put(versions, version, shared, System.identityHashCode(shared), allowMerge);
    }

    //

    static TObject.Version[] put(TObject.Version[] versions, TObject.Version version, Object shared, int hash, boolean allowMerge) {
        while (!tryToPut(versions, version, shared, hash, allowMerge)) {
            TObject.Version[] previous = versions;

            for (;;) {
                versions = new TObject.Version[versions.length << SparseArrayHelper.TIMES_TWO_SHIFT];

                if (rehash(previous, versions))
                    break;
            }
        }

        return versions;
    }

    private static final boolean tryToPut(TObject.Version[] versions, TObject.Version version, Object shared, int hash, boolean allowMerge) {
        if (Debug.ENABLED) {
            Debug.assertion(((TObject.Version) shared).isShared());
            Debug.assertion(version.getUnion() == shared);
        }

        int index = hash & (versions.length - 1);

        if (Stats.ENABLED)
            Stats.getInstance().Put.incrementAndGet();

        for (int i = SparseArrayHelper.attemptsStart(versions.length); i >= 0; i--) {
            TObject.Version current = versions[index];

            if (current == null) {
                versions[index] = version;
                return true;
            }

            if (Debug.ENABLED)
                Debug.assertion(current != version);

            if (current.getShared() == shared) {
                if (Debug.ENABLED)
                    Debug.assertion(allowMerge);

                TObject.Version merged = versions[index];
                merged = merged.merge(merged, version, TObject.Version.MERGE_FLAG_PRIVATE);

                if (Debug.ENABLED) {
                    merged.checkInvariants();
                    Debug.assertion(merged == versions[index]);
                }

                return true;
            }

            if (Stats.ENABLED)
                Stats.getInstance().PutRetry.incrementAndGet();

            index = (index + 1) & (versions.length - 1);
        }

        return false;
    }

    private static final boolean rehash(TObject.Version[] source, TObject.Version[] target) {
        for (int i = source.length - 1; i >= 0; i--) {
            TObject.Version current = source[i];

            if (current != null) {
                Object shared = current.getShared();

                if (!tryToPut(target, current, shared, System.identityHashCode(shared), false))
                    return false;
            }
        }

        return true;
    }

    //

    final void mergeReads(TransactionSets child) {
        TObject.Version[] sources = child.getReads();

        if (sources != null) {
            if (_reads == null)
                _reads = sources;
            else
                _reads = merge(_reads, sources);
        }
    }

    final void mergeWrites(TransactionSets child) {
        if (Debug.ENABLED)
            Debug.assertion(!isPublic());

        TObject.Version[] sources = child.getWrites();

        if (sources != null) {
            if (noWrites())
                throw new RuntimeException(Strings.READ_ONLY_OR_COMMITTED);

            if (_writes == null)
                _writes = sources;
            else
                _writes = merge(_writes, sources);
        }
    }

    private final TObject.Version[] merge(TObject.Version[] targets, TObject.Version[] sources) {
        for (int i = sources.length - 1; i >= 0; i--) {
            TObject.Version source = sources[i];

            if (source != null) {
                Object shared = source.getShared();
                TObject.Version target = getVersionFromSharedVersion(targets, shared);
                TObject.Version merged = null;

                if (target != null)
                    merged = VersionMap.merge(target, source, TObject.Version.MERGE_FLAG_PRIVATE);
                else
                    merged = source;

                if (merged != target)
                    targets = putForShared(targets, merged, shared);
            }
        }

        return targets;
    }

    // Snapshot

    final TObject.Version[][] getPublicSnapshotVersions() {
        if (Debug.ENABLED)
            Debug.assertion(!isPublic());

        return _publicSnapshotVersions;
    }

    final void setPublicSnapshotVersions(TObject.Version[][] value) {
        if (Debug.ENABLED)
            Debug.assertion(!isPublic());

        _publicSnapshotVersions = value;
    }

    /*
     * Private snapshot.
     */

    final TObject.Version[][] getPrivateSnapshotVersions() {
        if (Debug.ENABLED)
            Debug.assertion(!isPublic());

        return _privateSnapshotVersions;
    }

    final void setPrivateSnapshotVersions(TObject.Version[][] value) {
        if (Debug.ENABLED) {
            Debug.assertion(!isPublic());
            Debug.assertion(_privateSnapshotVersions == null);
        }

        _privateSnapshotVersions = value;
    }

    final void addPrivateSnapshotVersions(TObject.Version[] value) {
        if (Debug.ENABLED)
            Debug.assertion(!isPublic());

        if (_privateSnapshotVersions != null)
            _privateSnapshotVersions = Helper.addReads(_privateSnapshotVersions, value);
        else
            setPrivateSnapshotVersions(value);
    }

    final void setPrivateSnapshotVersions(TObject.Version[] versions) {
        if (Debug.ENABLED) {
            Debug.assertion(!isPublic());
            Debug.assertion(versions.length > 0);
            Debug.assertion(_privateSnapshotVersions == null);
        }

        _privateSnapshotVersions = new TObject.Version[2][];
        _privateSnapshotVersions[IMPORTS_INDEX] = null;
        _privateSnapshotVersions[1] = versions;
    }

    // Debug

    private final boolean isPublic() {
        if (!Debug.ENABLED)
            throw new IllegalStateException();

        return ((Transaction) this).isPublic();
    }

    final void checkInvariantsSets() {
        if (!Debug.ENABLED)
            throw new AssertionError();

        Helper.getInstance().disableEqualsOrHashCheck();

        if (noReads() && !Helper.getInstance().getAllowExistingReadsOrWrites().containsKey(this))
            Debug.assertion(_reads == null);

        if (noWrites() && !Helper.getInstance().getAllowExistingReadsOrWrites().containsKey(this))
            Debug.assertion(_writes == null);

        Helper.getInstance().enableEqualsOrHashCheck();
    }
}
