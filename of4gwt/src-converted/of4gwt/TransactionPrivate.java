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
import of4gwt.misc.PlatformAdapter;
import of4gwt.misc.ThreadAssert;
import of4gwt.misc.ThreadAssert.SingleThreadedOrShared;

/**
 * By default transactions are private to a thread.
 */
@SingleThreadedOrShared
abstract class TransactionPrivate extends TransactionSets {

    private Snapshot _snapshot;

    // TODO needed in transaction?
    private VersionMap _map;

    // !! Add new fields to reset()

    //

    private Transaction _cachedChild;

    protected TransactionPrivate(TObject.Version shared, Transaction trunk) {
        super(shared, trunk);
    }

    final void resetPrivate() {
        _snapshot = null;
        _map = null;
    }

    final boolean isPublic() {
        return (getFlags() & Transaction.FLAG_PUBLIC) != 0;
    }

    final Snapshot getSnapshot() {
        return _snapshot;
    }

    final void setSnapshot(Snapshot value) {
        if (Debug.ENABLED)
            Debug.assertion(value != null);

        _snapshot = value;
    }

    final void forceSnapshot(Snapshot value) {
        if (!Debug.TESTING)
            throw new RuntimeException();

        _snapshot = value;
    }

    final VersionMap getVersionMap() {
        if (Debug.ENABLED)
            Debug.assertion(!isPublic());

        return _map;
    }

    final void setVersionMap(VersionMap value) {
        if (Debug.ENABLED)
            Debug.assertion(!isPublic());

        _map = value;
    }

    final VersionMap getOrCreateVersionMap() {
        if (Debug.ENABLED)
            Debug.assertion(!isPublic());

        if (_map == null) {
            _map = new VersionMap();

            if (Debug.ENABLED)
                Helper.getInstance().addWatcher(_map, ((Transaction) this).getParent(), "New commit");
        }

        return _map;
    }

    final void mergePrivate(Transaction child) {
        if (Debug.ENABLED) {
            Debug.assertion(!isPublic());
            Debug.assertion(!child.isPublic());
            Debug.assertion(child.getParent() == this);

            if (_map != null)
                Debug.assertion(_map.getTransaction() == null);

            if (child.getVersionMap() != null)
                Helper.getInstance().removeWatcher(child.getVersionMap(), this, "merge(Transaction child)");

            /*
             * Needed to be able to retrieve Version (E.g. for TList) during reads merge.
             */
            Debug.assertion(Transaction.getCurrent() == child);
        }

        mergeReads(child);
        mergeWrites(child);

        assertUpdates();
    }

    protected final void assertUpdates() {
        if (Debug.ENABLED) {
            TObject.Version[][] versions = getPublicSnapshotVersions();

            if (getPrivateSnapshotVersions() != null || getWrites() != null) {
                if (getPrivateSnapshotVersions() != null)
                    for (TObject.Version[] map : getPrivateSnapshotVersions())
                        if (map != null)
                            versions = Helper.addVersions(versions, map);

                if (getWrites() != null)
                    versions = Helper.addVersions(versions, getWrites());

                TKeyedVersion.assertUpdates(versions, versions.length - 1);
            }
        }
    }

    //

    final Transaction startFromPrivate(int flags) {
        if (Debug.ENABLED) {
            Debug.assertion(this == Transaction.getCurrent());
            Debug.assertion(!isPublic());
        }

        Transaction transaction = _cachedChild;
        _cachedChild = null;

        if (transaction == null) {
            transaction = (Transaction) DefaultObjectModelBase.getInstance().createInstance(getTrunk(), DefaultObjectModelBase.COM_OBJECTFABRIC_TRANSACTION_CLASS_ID, null);

            if (Debug.ENABLED)
                checkTransactionType(transaction);

            transaction.setParent((Transaction) this);
            ((TransactionBase.Version) transaction.getSharedVersion_objectfabric())._parentImpl = getSharedVersion_objectfabric();
            ((TransactionBase.Version) transaction.getSharedVersion_objectfabric()).setBit(TransactionBase.PARENT_IMPL_INDEX);

            if (Debug.ENABLED) {
                Debug.assertion(transaction.getTrunk() == getTrunk());
                Helper.getInstance().addCreatedNotRecycled(transaction);
            }

            if (Debug.THREADS)
                ThreadAssert.removePrivate(transaction);
        }

        if (Debug.ENABLED) {
            transaction.checkNothingToMergeLater();
            Helper.getInstance().checkFieldsHaveDefaultValues(transaction);

            if (Debug.THREADS) {
                ThreadAssert.assertCleaned(transaction);
                ThreadAssert.addPrivate(transaction);
            }

            if (!Helper.getInstance().LastResetFailed)
                Debug.assertion(transaction.getPrivateSnapshotVersions() == null);

            Debug.assertion(!transaction.isPublic());
        }

        if ((flags & Transaction.FLAG_IGNORE_SPECULATIVE_DATA) == 0) {
            transaction.setSnapshot(getSnapshot());
            transaction.setPublicSnapshotVersions(getPublicSnapshotVersions());
        } else {
            Snapshot snapshot = TransactionPublic.trim(getSnapshot(), getSnapshot().getAcknowledgedIndex());
            transaction.setSnapshot(snapshot);
            transaction.setPublicSnapshotVersions(snapshot.getWrites());
        }

        transaction.setPrivateSnapshotVersions(getPrivateSnapshotVersions());

        if (getWrites() != null)
            transaction.addPrivateSnapshotVersions(getWrites());

        return transaction;
    }

    final void recycleToPrivate(Transaction transaction) {
        if (Debug.ENABLED) {
            Debug.assertion(!isPublic());

            if (PlatformAdapter.PLATFORM != CompileTimeSettings.PLATFORM_GWT) {
                // To avoid thread context check
                Debug.assertion(PlatformAdapter.getPrivateField(transaction, "_parent", Transaction.class) == this);
            }

            transaction.checkNothingToMergeLater();
            Helper.getInstance().checkFieldsHaveDefaultValues(transaction);
            Debug.assertion(!transaction.isRemote());
            Debug.assertion(transaction != transaction.getTrunk());

            if (Debug.THREADS)
                ThreadAssert.assertCleaned(transaction);
        }

        if (Debug.THREADS) {
            // For callbacks with ALL granularity
            if ((getFlags() & Transaction.FLAG_COMMITTED) != 0)
                ThreadAssert.addPrivate(this);
        }

        if (_cachedChild == null)
            _cachedChild = transaction;

        if (Debug.THREADS)
            if ((getFlags() & Transaction.FLAG_COMMITTED) != 0)
                ThreadAssert.removePrivate(this);

        if (Debug.ENABLED)
            Helper.getInstance().removeCreatedNotRecycled(transaction);
    }

    // Debug

    /**
     * Just to make sure overriden DefaultObjectModels and PlatformAdapter.createTrunk()
     * agree on the type of a transaction.
     */
    protected static void checkTransactionType(Transaction transaction) {
        Debug.assertion(transaction.getClass() == Transaction.getLocalTrunk().getClass());
    }
}
