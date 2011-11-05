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

import com.objectfabric.Extension.TObjectMapEntry;
import com.objectfabric.TObject.Descriptor;
import com.objectfabric.TObject.Record;
import com.objectfabric.TObject.UserTObject;
import com.objectfabric.TObject.UserTObject.Method;
import com.objectfabric.TObject.Version;
import com.objectfabric.misc.Debug;
import com.objectfabric.misc.Log;
import com.objectfabric.misc.PlatformAdapter;
import com.objectfabric.misc.SparseArrayHelper;
import com.objectfabric.misc.ThreadAssert.SingleThreaded;

@SingleThreaded
final class StoreWriter extends Writer {

    private static final int DEFAULT_BUFFER_LENGTH = 256;

    private static final int SKIP_BUFFER_WRITE_OFFSET = 0;

    private final BinaryStore _store;

    private final VersionSetAndList _created = new VersionSetAndList();

    @SuppressWarnings("unchecked")
    private TObjectMapEntry<VersionSetAndList>[] _pendingSnapshots = new TObjectMapEntry[SparseArrayHelper.DEFAULT_CAPACITY];

    private final UserTObjectSetAndList _pendingSnapshotsBranches = new UserTObjectSetAndList();

    private Version _wrongStore;

    protected StoreWriter(BinaryStore store) {
        super(true);

        if (store == null)
            throw new IllegalArgumentException();

        _store = store;

        if (Debug.COMMUNICATIONS_LOG) {
            Boolean previous = Helper.getInstance().getWriters().put(this, true);
            Debug.assertion(previous == null);
        }

        init(_store, true);

        setBuffer(new byte[DEFAULT_BUFFER_LENGTH]);
        setLimit(getBuffer().length);

        byte flags = (byte) (PlatformAdapter.SUPPORTED_SERIALIZATION_FLAGS | CompileTimeSettings.SERIALIZATION_VERSION);
        setFlags(flags);
        getBuffer()[0] = flags;
    }

    @Override
    void reset() {
        super.reset();

        // 1 byte for flags
        setOffset(1);

        _created.clear();
    }

    public Version getWrongStore() {
        return _wrongStore;
    }

    public void resetWrongStore() {
        _wrongStore = null;
    }

    final void grow() {
        doubleBufferLength();
        setLimit(getBuffer().length);
        getBuffer()[0] = getFlags();
    }

    public final void write(Object object) {
        if (Debug.ENABLED)
            Debug.assertion(getLimit() == getBuffer().length);

        reset();

        for (;;) {
            writeObject(object);

            if (!interrupted())
                break;

            grow();
        }
    }

    public final void writeSnapshots() {
        setVisitingNewObject(true);

        for (;;) {
            UserTObject object = _pendingSnapshotsBranches.poll();

            if (object == null)
                break;

            Transaction branch = (Transaction) object;
            VersionSetAndList pending = TObjectMapEntry.get(_pendingSnapshots, branch);

            if (Debug.ENABLED) {
                for (int t = 0; t < pending.size(); t++) {
                    Version shared = pending.get(t);
                    Debug.assertion(!(shared.getReference().get() instanceof Method));

                    if (!shared.isImmutable())
                        Debug.assertion(_store.registered(branch));
                }
            }

            // Write snapshots

            setBranch(branch);

            Snapshot snapshot = _store.getSnapshot(branch);
            boolean created = false;

            if (snapshot == null) {
                snapshot = branch.takeSnapshot();

                if (Debug.ENABLED)
                    branch.takeSnapshotDebug(snapshot, this, "StoreWriter.writeSnapshots");

                created = true;
            }

            setSnapshot(snapshot);
            setMapIndex1(TransactionManager.OBJECTS_VERSIONS_INDEX);
            setMapIndex2(snapshot.getWrites().length);

            for (;;) {
                Version shared = pending.poll();

                if (shared == null)
                    break;

                if (Debug.ENABLED)
                    if (!shared.isImmutable())
                        Debug.assertion(!created);

                writeSnapshot(shared);
            }

            if (created) {
                if (Debug.ENABLED)
                    branch.releaseSnapshotDebug(snapshot, this, "StoreWriter.writeSnapshots");

                branch.releaseSnapshot(snapshot);
            }
        }

        setVisitingNewObject(false);
    }

    private final void writeSnapshot(Version shared) {
        reset();

        for (;;) {
            shared.visit(this);

            if (!interrupted())
                break;

            grow();
        }

        storeBuffer(shared);
    }

    final void storeBuffer(Version shared) {
        if (getOffset() == SKIP_BUFFER_WRITE_OFFSET) {
            if (Debug.ENABLED) {
                int id = shared.getClassId();
                Debug.assertion(id == DefaultObjectModel.COM_OBJECTFABRIC_TMAP_CLASS_ID || id == DefaultObjectModel.COM_OBJECTFABRIC_TSET_CLASS_ID || id == DefaultObjectModelBase.COM_OBJECTFABRIC_LAZYMAP_CLASS_ID);
            }
        } else {
            byte[] uid = shared.getUID();

            if (uid != null) {
                // UID object has to be a session, object models do not have fields
                Session session = (Session) shared.getReference().get();
                storeBuffer(shared, session, uid, Session.UID_OBJECT_ID & 0xff);
            } else {
                Descriptor descriptor = shared.getOrCreateDescriptor();
                Session session = descriptor.getSession();
                uid = session.getSharedVersion_objectfabric().getUID();
                storeBuffer(shared, session, uid, descriptor.getId() & 0xff);
            }
        }
    }

    private final void storeBuffer(Version shared, Session session, byte[] uid, int id) {
        long currentRecord = _store.getRecord(shared);

        if (currentRecord == Record.UNKNOWN)
            currentRecord = _store.getOrFetchRecord(shared);

        long record;

        if (getOffset() == 1) {
            if (Record.isStored(currentRecord))
                _store.getRecordManager().delete(currentRecord);

            record = Record.EMPTY;
        } else {
            if (Record.isStored(currentRecord)) {
                record = currentRecord;
                _store.getRecordManager().update(record, getBuffer(), 0, getOffset());
            } else {
                /*
                 * TODO: pre-insert all records for session, and only update object record
                 * on snapshot, might need to modify JDBM id allocator.
                 */
                record = _store.getRecordManager().insert(getBuffer(), 0, getOffset());
            }
        }

        if (record != currentRecord)
            _store.updateRecord(shared, session, uid, currentRecord, record, id);
    }

    //

    @Override
    boolean isKnown(Version shared) {
        /*
         * Mark that object is created for this write. Each write needs to create all
         * objects as we don't know in which order they are going to be read back.
         */
        if (_created.add(shared) == SparseArrayHelper.ALREADY_PRESENT)
            return true;

        /*
         * Read objects do not need to be snapshotted.
         */
        if (!visitingReads()) {
            long record = _store.getOrFetchRecord(shared);

            if (record == Record.NOT_STORED) {
                if (!shared.isImmutable()) {
                    if (shared.getTrunk().getStore() != _store) {
                        _wrongStore = shared;
                        return false;
                    }
                }

                if (Debug.PERSISTENCE_LOG)
                    Log.write("Persisting " + shared);

                snapshot(shared);
            }
        }

        return false;
    }

    public final void snapshot(Version shared) {
        Transaction branch = shared.getTrunk();

        //

        VersionSetAndList pending = TObjectMapEntry.get(_pendingSnapshots, branch);

        if (pending == null) {
            pending = new VersionSetAndList();
            TObjectMapEntry<VersionSetAndList> entry = new TObjectMapEntry<VersionSetAndList>(branch, pending);
            _pendingSnapshots = TObjectMapEntry.put(_pendingSnapshots, entry);
        }

        pending.add(shared);

        //

        _pendingSnapshotsBranches.add(branch);
    }

    @Override
    void setCreated(Version shared) {
    }

    @Override
    public void writeCommand(byte command) {
        throw new AssertionError();
    }

    // TKeyed

    @Override
    protected void visit(TKeyedSharedVersion shared, TKeyedEntry[] entries, boolean cleared, boolean fullyRead) {
        BTree tree = null;
        long record = _store.getRecord(shared);

        if (Record.isStored(record)) {
            tree = BTree.load(_store.getRecordManager(), record, false);

            if (Debug.ENABLED)
                Debug.assertion(tree != null);
        }

        if (tree == null) {
            tree = new BTree(_store.getRecordManager(), false);
            Descriptor descriptor;

            if (shared.getUnion() instanceof Descriptor)
                descriptor = (Descriptor) shared.getUnion();
            else {
                if (Debug.ENABLED) {
                    /*
                     * Lazy classes are only ones that can be visited without being added
                     * by a writer.
                     */
                    Debug.assertion(shared.getClassId() == DefaultObjectModelBase.COM_OBJECTFABRIC_LAZYMAP_CLASS_ID);

                    LazyMap map = (LazyMap) shared.getReference().get();

                    if (map != null) {
                        /*
                         * If no descriptor, no branch referencing the map has been
                         * visited, so map's branch is only one containing its updates.
                         */
                        Debug.assertion(map.getTrunk() == getBranch());
                    }
                }

                descriptor = getBranch().assignId(shared);
            }

            Session session = descriptor.getSession();
            byte[] uid = session.getSharedVersion_objectfabric().getUID();
            _store.updateRecord(shared, session, uid, descriptor.getRecord(), tree.getId(), descriptor.getId() & 0xff);
        }

        if (cleared)
            tree.clear(true);

        for (int i = 0; i < entries.length; i++) {
            if (entries[i] != null) {
                write(entries[i].getKeyDirect());
                byte[] key = new byte[getOffset()];
                PlatformAdapter.arraycopy(getBuffer(), 0, key, 0, key.length);

                if (entries[i].isRemoval()) {
                    // TODO hack! should be called once
                    if (tree.fetch(key) != Record.NOT_STORED) {
                        long id = tree.remove(key);
                        _store.getRecordManager().delete(id);
                    }
                } else {
                    write(entries[i].getValueDirect());
                    long id = _store.getRecordManager().insert(getBuffer(), 0, getOffset());
                    long previous = tree.put(key, id);

                    if (previous != 0)
                        _store.getRecordManager().delete(previous);

                    // TODO bench with in-place updates, or create tree.putOrUpdate
                    // method?
                    // long record = tree.fetch(key);
                    //
                    // if (record == Record.NOT_STORED) {
                    // record = _store.getRecordManager().insert(getBuffer(), 0,
                    // getOffset());
                    // tree.put(key, record);
                    // } else
                    // _store.getRecordManager().update(record, getBuffer(), 0,
                    // getOffset());
                }
            }
        }

        // Will skip writing this buffer
        setOffset(SKIP_BUFFER_WRITE_OFFSET);
    }

    // Debug

    final void assertNoPendingData() {
        if (!Debug.ENABLED)
            throw new IllegalStateException();

        for (int i = _pendingSnapshots.length - 1; i >= 0; i--)
            if (_pendingSnapshots[i] != null)
                Debug.assertion(_pendingSnapshots[i].getValue().size() == 0);

        Debug.assertion(_pendingSnapshotsBranches.size() == 0);
        Debug.assertion(_created.size() == 0);
    }
}
