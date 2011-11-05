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

import com.objectfabric.BTree.Walker;
import com.objectfabric.TObject.Record;
import com.objectfabric.TObject.UserTObject;
import com.objectfabric.TObject.UserTObject.Method;
import com.objectfabric.TObject.Version;
import com.objectfabric.misc.Debug;
import com.objectfabric.misc.List;
import com.objectfabric.misc.Queue;
import com.objectfabric.misc.SparseArrayHelper;

final class StoreReader extends Reader {

    private final BinaryStore _store;

    private final List<UserTObject> _newTObjects = new List<UserTObject>();

    private final UserTObjectSetAndList _newBranches = new UserTObjectSetAndList();

    private final Queue<UserTObject> _tempQueue = new Queue<UserTObject>();

    private Version[] _versions;

    public StoreReader(BinaryStore store) {
        super(new List<UserTObject>());

        if (store == null)
            throw new IllegalArgumentException();

        _store = store;
    }

    public final Object read(byte[] data) {
        setBuffer(data);
        setOffset(1);
        setLimit(data.length);
        reset();
        setFlags(data[0]);

        Object object = readObject();

        if (Debug.ENABLED) {
            Debug.assertion(!interrupted());
            Debug.assertion(getOffset() == data.length);
        }

        return object;
    }

    public final void readVersions() {
        for (;;) {
            for (int i = 0; i < getNewTObjects().size(); i++) {
                UserTObject object = getNewTObjects().get(i);
                long record = ((Record) object.getSharedVersion_objectfabric().getUnion()).getRecord();

                if (record == Record.UNKNOWN) {
                    object.getSharedVersion_objectfabric().setRecord(_store.fetchRecord(object.getSharedVersion_objectfabric()));
                    _newTObjects.add(object);
                    _tempQueue.add(getNewTObjects().get(i));
                }
            }

            getNewTObjects().clear();

            if (_tempQueue.size() == 0)
                break;

            Version shared = _tempQueue.poll().getSharedVersion_objectfabric();
            long record = ((Record) shared.getUnion()).getRecord();

            if (Debug.ENABLED) {
                Debug.assertion(record != Record.UNKNOWN);
                Debug.assertion(!(shared.getReference().get() instanceof Method));
            }

            if (Record.isStored(record) && !shared.isLazy()) {
                byte[] data = _store.getRecordManager().fetch(record);

                reset();

                setBuffer(data);
                setOffset(1);
                setLimit(data.length);
                setFlags(data[0]);

                TObject.Version version = shared.createVersion();
                version.visit(this);

                if (Debug.ENABLED) {
                    Debug.assertion(!interrupted());
                    Debug.assertion(getOffset() == getLimit());
                }

                if (!shared.isImmutable()) {
                    if (_versions == null)
                        _versions = new Version[SparseArrayHelper.DEFAULT_CAPACITY];

                    _versions = TransactionSets.putForShared(_versions, version, version.getUnion());
                } else if (Debug.ENABLED)
                    Debug.assertion(!version.hasWritesForDebug());
            }
        }
    }

    public final Version getOrCreateVersion(UserTObject object) {
        if (_versions == null)
            _versions = new Version[SparseArrayHelper.DEFAULT_CAPACITY];

        Version version = TransactionSets.getVersionFromTObject(_versions, object);

        if (version == null) {
            version = object.getSharedVersion_objectfabric().createVersion();
            _versions = TransactionSets.putForTObject(_versions, version, object);
        }

        return version;
    }

    @SuppressWarnings("null")
    public final void importVersions() {
        /*
         * Trunks are assigned during read, so sort by trunk only after all reads are
         * done.
         */
        for (int i = 0; i < _newTObjects.size(); i++) {
            UserTObject object = _newTObjects.get(i);

            if (!object.getSharedVersion_objectfabric().isImmutable()) {
                Transaction trunk = object.getTrunk();
                _newBranches.add(trunk);
            }
        }

        // Intercept future changes so they get stored

        for (int i = 0; i < _newBranches.size(); i++) {
            Transaction branch = (Transaction) _newBranches.get(i);

            if (!_store.registered(branch)) {
                _store.register(branch);
                Interceptor.intercept(branch);

                if (Debug.ENABLED)
                    Debug.assertion(branch.getStore() == _store);
            }
        }

        // Commit versions

        if (_versions != null) {
            Transaction current = Transaction.getCurrent();
            Version[] transactionImports = null;

            if (_newBranches.size() == 1) {
                if (current == null)
                    TransactionManager.propagate((Transaction) _newBranches.get(0), _versions, VersionMap.IMPORTS_SOURCE);
                else {
                    if (Debug.ENABLED)
                        for (int i = 0; i < _versions.length; i++)
                            if (_versions[i] != null)
                                Debug.assertion(_versions[i].getShared().getTrunk() == current.getTrunk());

                    transactionImports = _versions;
                }
            } else {
                for (int t = 0; t < _newBranches.size(); t++) {
                    Transaction trunk = (Transaction) _newBranches.get(t);
                    Version[] versions = new Version[SparseArrayHelper.DEFAULT_CAPACITY];

                    for (int i = 0; i < _newTObjects.size(); i++) {
                        UserTObject object = _newTObjects.get(i);

                        if (object.getTrunk() == trunk) {
                            Version shared = object.getSharedVersion_objectfabric();

                            if (!shared.isImmutable() && Record.isStored(shared.getRecord())) {
                                Version version = TransactionSets.getVersionFromTObject(_versions, object);
                                versions = TransactionSets.putForTObject(versions, version, object);
                            } else if (Debug.ENABLED)
                                Debug.assertion(TransactionSets.getVersionFromTObject(_versions, object) == null);
                        }
                    }

                    if (current == null || current.getTrunk() != trunk)
                        TransactionManager.propagate(trunk, versions, VersionMap.IMPORTS_SOURCE);
                    else
                        transactionImports = versions;
                }
            }

            if (transactionImports != null)
                current.addImports(transactionImports);

            _versions = null;
        }

        _newTObjects.clear();
        _newBranches.clear();
    }

    //

    @Override
    protected UserTObject createInstance(ObjectModel model, Transaction trunk, int classId, TType[] genericParameters) {
        if (model == DefaultObjectModel.getInstance() && classId == DefaultObjectModelBase.COM_OBJECTFABRIC_TRANSACTION_CLASS_ID)
            return new Transaction(trunk, _store);

        return super.createInstance(model, trunk, classId, genericParameters);
    }

    //

    @Override
    protected void visit(TKeyedVersion version) {
        BTree tree = visitTKeyed(version);
        version.setCleared(tree.getCleared());
    }

    @Override
    protected void visit(LazyMapVersion version) {
        throw new AssertionError();
    }

    private final BTree visitTKeyed(TKeyedBase2 version) {
        BTree tree = BTree.loadReadOnly(_store.getRecordManager(), getBuffer(), false);
        TreeWalker walker = new TreeWalker(version);
        tree.walk(walker);
        return tree;
    }

    private final class TreeWalker implements Walker {

        private final TKeyedBase2 _version;

        public TreeWalker(TKeyedBase2 version) {
            _version = version;
        }

        public void onEntry(byte[] keyBytes, long valueRecord) {
            byte[] valueBytes = _store.getRecordManager().fetch(valueRecord);
            Object key = read(keyBytes);
            readVersions();
            Object value = read(valueBytes);
            readVersions();
            @SuppressWarnings("unchecked")
            TKeyedEntry entry = new TKeyedEntry(key, TKeyed.hash(key), value, false);
            _version.putEntry(key, entry, true, true);
        }
    }
}
