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
import of4gwt.misc.List;
import of4gwt.misc.PlatformAdapter;
import of4gwt.misc.PlatformConcurrentQueue;
import of4gwt.misc.PlatformThreadLocal;
import of4gwt.misc.ThreadAssert;
import of4gwt.misc.ThreadAssert.AllowSharedRead;
import of4gwt.misc.ThreadAssert.SingleThreadedOrShared;

/**
 * A transaction is public if it is a trunk, or it was private and has been published by
 * calling method <code>publish</code>.
 */
@SingleThreadedOrShared
abstract class TransactionPublic extends TransactionPrivate {

    private static final int TRANSACTION_CACHE_MAX_SIZE = OverloadHandler.MAP_QUEUE_SIZE_MAXIMUM;

    /*
     * TODO: replace by making the second thread finish the merge that has been delayed.
     */
    @AllowSharedRead
    private final PlatformConcurrentQueue<VersionMap> _toMerge = new PlatformConcurrentQueue<VersionMap>();

    private volatile Snapshot _sharedSnapshot;

    

    // Transaction Cache

    private final PlatformThreadLocal<List<Transaction>> _transactionCache = new PlatformThreadLocal<List<Transaction>>();

    private final PlatformConcurrentQueue<List<Transaction>> _sharedTransactionCache = new PlatformConcurrentQueue<List<Transaction>>();

    protected TransactionPublic(TObject.Version shared, Transaction trunk) {
        super(shared, trunk);
    }

    static {
        
    }

    final void resetPublic() {
        if (Debug.ENABLED) {
            Debug.assertion(_toMerge.size() == 0);

            if (!isPublic())
                Debug.assertion(_sharedSnapshot == null);
        }
    }

    final Snapshot getSharedSnapshot() {
        if (Debug.ENABLED)
            Debug.assertion(isPublic());

        return _sharedSnapshot;
    }

    final void setSharedSnapshot(Snapshot value) {
        _sharedSnapshot = value;
    }

    final boolean casSharedSnapshot(Snapshot expected, Snapshot update) {
        if (Debug.ENABLED) {
            Debug.assertion(isPublic());
            update.checkInvariants((Transaction) this);
        }

        return (_sharedSnapshot == expected ? ((_sharedSnapshot = update) == update) : false);
    }

    //

    final Snapshot takeSnapshot() {
        Snapshot snapshot;

        for (;;) {
            /*
             * Volatile read ensures sync with shared view.
             */
            snapshot = _sharedSnapshot;

            /*
             * Increment watchers count to prevent the map we are using as our snapshot
             * from merging with future commits.
             */
            if (snapshot.getLast().tryToAddWatchers(1)) {
                /*
                 * If no speculative maps, snapshot is valid.
                 */
                if (snapshot.getAcknowledgedIndex() == snapshot.getLastIndex())
                    break;

                /*
                 * Otherwise, also protect the last acknowledged map. This prevents maps
                 * propagated from a server from merging to the shared view. Propagated
                 * maps are not known to the transaction so it would modify its snapshot.
                 */
                if (snapshot.getAcknowledged().tryToAddWatchers(1))
                    break;

                /*
                 * Failed, map had no watcher. It is going to be merged so remove added
                 * watcher and retry.
                 */
                snapshot.getLast().removeWatchers((Transaction) this, 1, false, snapshot);
            }
        }

        return snapshot;
    }

    final void takeSnapshotDebug(Snapshot snapshot, Object watcher, String context) {
        if (!Debug.ENABLED)
            throw new RuntimeException();

        Helper.getInstance().addWatcher(snapshot.getLast(), watcher, context + " (last)");

        if (snapshot.getAcknowledgedIndex() != snapshot.getLastIndex())
            Helper.getInstance().addWatcher(snapshot.getAcknowledged(), watcher, context + " (last ack)");
    }

    final Snapshot takeAcknowledgedSnapshot() {
        Snapshot snapshot;

        for (;;) {
            snapshot = _sharedSnapshot;

            if (snapshot.getAcknowledged().tryToAddWatchers(1))
                break;
        }

        return trim(snapshot, snapshot.getAcknowledgedIndex());
    }

    final void releaseSnapshot(Snapshot snapshot) {
        if (snapshot.getAcknowledgedIndex() != snapshot.getLastIndex())
            snapshot.getAcknowledged().removeWatchers((Transaction) this, 1, false, snapshot);

        snapshot.getLast().removeWatchers((Transaction) this, 1, false, snapshot);
    }

    final void releaseSnapshotDebug(Snapshot snapshot, Object watcher, String context) {
        if (!Debug.ENABLED)
            throw new RuntimeException();

        if (snapshot.getAcknowledgedIndex() != snapshot.getLastIndex())
            Helper.getInstance().removeWatcher(snapshot.getAcknowledged(), watcher, context);

        Helper.getInstance().removeWatcher(snapshot.getLast(), watcher, context);
    }

    final static Snapshot trim(Snapshot snapshot, int limit) {
        int length = limit + 1;

        if (length < snapshot.getWrites().length) {
            Snapshot full = snapshot;
            snapshot = new Snapshot();

            VersionMap[] maps = new VersionMap[length];
            PlatformAdapter.arraycopy(full.getVersionMaps(), 0, maps, 0, maps.length);
            snapshot.setVersionMaps(maps);

            if (full.getReads() != null) {
                boolean empty = true;

                for (int i = length - 1; i >= 0; i--)
                    if (full.getReads()[i] != null)
                        empty = false;

                if (!empty) {
                    TObject.Version[][] reads = new TObject.Version[length][];
                    PlatformAdapter.arraycopy(full.getReads(), 0, reads, 0, reads.length);
                    snapshot.setReads(reads);
                }
            }

            TObject.Version[][] writes = new TObject.Version[length][];
            PlatformAdapter.arraycopy(full.getWrites(), 0, writes, 0, writes.length);
            snapshot.setWrites(writes);

            snapshot.setInterception(full.getInterception());
            snapshot.setSlowChanging(full.getSlowChanging());

            snapshot.setAcknowledgedIndex(Math.min(full.getAcknowledgedIndex(), limit));
        }

        return snapshot;
    }

    //

    final void keepToMergeLater(VersionMap map) {
        if (Debug.ENABLED) {
            Debug.assertion(isPublic());
            Debug.assertion(!_toMerge.contains(map));
        }

        _toMerge.add(map);
    }

    final VersionMap pollToMergeLater() {
        if (Debug.ENABLED)
            Debug.assertion(isPublic());

        return _toMerge.poll();
    }

    final void checkNothingToMergeLater() {
        if (!Debug.ENABLED)
            throw new IllegalStateException();

        Debug.assertion(_toMerge.isEmpty());
    }

    //

    final Transaction getOrCreateChild() {
        List<Transaction> cache = _transactionCache.get();

        if (cache == null) {
            cache = _sharedTransactionCache.poll();

            if (cache == null)
                cache = new List<Transaction>();

            _transactionCache.set(cache);
        } else if (cache.size() == 0) {
            List<Transaction> shared = _sharedTransactionCache.poll();

            if (shared != null) {
                if (Debug.ENABLED)
                    Debug.assertion(shared.size() > 0);

                cache = shared;

                _transactionCache.set(cache);
            }
        }

        Transaction transaction = null;

        if (cache.size() != 0)
            transaction = cache.remove(cache.size() - 1);

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
        } else if (Debug.THREADS)
            ThreadAssert.addPrivate(transaction);

        if (Debug.ENABLED)
            Debug.assertion(!transaction.isRemote());

        return transaction;
    }

    final void startFromPublic(Transaction transaction, Snapshot snapshot) {
        if (Debug.ENABLED)
            checkGoodToStart(transaction);

        transaction.setSnapshot(snapshot);
        transaction.setPublicSnapshotVersions(snapshot.getWrites());

        if (Debug.ENABLED)
            transaction.checkInvariants();
    }

    //

    final void recycleToPublic(Transaction transaction) {
        if (Debug.ENABLED) {
            Debug.assertion(isPublic());
            Debug.assertion(!transaction.isRemote());

            if (PlatformAdapter.PLATFORM != CompileTimeSettings.PLATFORM_GWT) {
                // To avoid thread context check
                Debug.assertion(PlatformAdapter.getPrivateField(transaction, "_parent", Transaction.class) == this);
            }

            transaction.checkNothingToMergeLater();
            checkNotCached(transaction);
            Helper.getInstance().checkFieldsHaveDefaultValues(transaction);

            if (Debug.THREADS)
                ThreadAssert.assertCleaned(transaction);
        }

        List<Transaction> cache = _transactionCache.get();

        if (cache == null) {
            cache = new List<Transaction>();
            _transactionCache.set(cache);
        }

        cache.add(transaction);

        if (cache.size() >= TRANSACTION_CACHE_MAX_SIZE) {
            _sharedTransactionCache.add(cache);
            _transactionCache.set(null);
        }

        if (Debug.ENABLED)
            Helper.getInstance().removeCreatedNotRecycled(transaction);
    }

    final void checkNotCached(Transaction transaction) {
        if (!Debug.ENABLED)
            throw new IllegalStateException();

        Debug.assertion(isPublic());

        if (PlatformAdapter.PLATFORM != CompileTimeSettings.PLATFORM_GWT)
            Debug.assertion(PlatformAdapter.getPrivateField(transaction, "_parent", Transaction.class) == this);

        List<List<Transaction>> temp = new List<List<Transaction>>();

        for (;;) {
            List<Transaction> list = _sharedTransactionCache.poll();

            if (list == null)
                break;

            temp.add(list);
        }

        for (int i = 0; i < temp.size(); i++) {
            for (int t = 0; t < temp.get(i).size(); t++)
                Debug.assertion(transaction != temp.get(i).get(t));

            _sharedTransactionCache.add(temp.get(i));
        }

        List<Transaction> list = _transactionCache.get();

        if (list != null)
            for (int i = 0; i < list.size(); i++)
                Debug.assertion(transaction != list.get(i));
    }

    // Debug

    final void checkGoodToStart(Transaction transaction) {
        if (!Debug.ENABLED)
            throw new AssertionError();

        Debug.assertion(isPublic());
        Debug.assertion(this == transaction.getParent());
        transaction.checkNothingToMergeLater();
        checkNotCached(transaction);
        Helper.getInstance().checkFieldsHaveDefaultValues(transaction);
        Debug.assertion(transaction != transaction.getTrunk());

        if (!Helper.getInstance().LastResetFailed)
            Debug.assertion(transaction.getPrivateSnapshotVersions() == null);

        Debug.assertion(!transaction.isPublic());
    }

    final void checkInvariantsPublic() {
        if (!Debug.ENABLED)
            throw new AssertionError();

        if (!isPublic()) {
            Debug.assertion(getSnapshot() != null);
            Debug.assertion(_sharedSnapshot == null);
        } else {
            Debug.assertion(getSnapshot() == null);
            Debug.assertion(_sharedSnapshot != null);
        }
    }
}
