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

import java.util.concurrent.Executor;

import com.objectfabric.TObject.Descriptor;
import com.objectfabric.TObject.Record;
import com.objectfabric.TObject.UserTObject;
import com.objectfabric.TObject.Version;
import com.objectfabric.misc.AsyncCallback;
import com.objectfabric.misc.Debug;
import com.objectfabric.misc.List;
import com.objectfabric.misc.PlatformAdapter;
import com.objectfabric.misc.Queue;
import com.objectfabric.misc.RuntimeIOException;
import com.objectfabric.misc.SparseArrayHelper;
import com.objectfabric.misc.ThreadAssert;
import com.objectfabric.misc.Utils;

abstract class BinaryStore extends Store {

    private static final int SESSIONS = 0;

    private static final int ROOT = 1;

    private final Backend _backend;

    private final RecordManager _jdbm;

    private final StoreReader _reader;

    private final StoreWriter _writer;

    private final Executor _executor;

    private final UserTObjectSetAndList<Transaction> _processed = new UserTObjectSetAndList<Transaction>();

    private BTree _sessions;

    private byte[] _processedInterceptions = new byte[10];

    private final VersionSetAndList _updatedVersions = new VersionSetAndList();

    private long[] _updatedVersionsRecords = new long[10];

    private final UserTObjectSetAndList<Session> _updatedSessions = new UserTObjectSetAndList<Session>();

    private long[] _updatedSessionsRecords = new long[10];

    protected BinaryStore(Backend backend, boolean start, Executor executor) throws RuntimeIOException {
        _backend = backend;
        _jdbm = new RecordManager(backend, start);
        _executor = executor;

        _reader = new StoreReader(this);
        _writer = new StoreWriter(this);

        setRun(new Run());

        if (start)
            start();
    }

    public final void start() {
        onStarting();

        long sessionsId = _jdbm.getRoot(SESSIONS);

        if (sessionsId != 0) {
            _sessions = BTree.load(_jdbm, sessionsId, true);

            if (_sessions == null)
                throw new RuntimeIOException(Strings.CORRUPTED_STORE);
        } else {
            _sessions = new BTree(_jdbm, true);
            _jdbm.setRoot(SESSIONS, _sessions.getId());
        }

        if (Debug.THREADS) {
            ThreadAssert.exchangeGive(this, _sessions);
            ThreadAssert.exchangeGiveList(this, _reader.getThreadContextObjects());
            ThreadAssert.exchangeGiveList(this, _writer.getThreadContextObjects());
            ThreadAssert.exchangeGive(this, this);
        }

        onStarted();
    }

    final Backend getBackend() {
        return _backend;
    }

    //

    @Override
    protected void getRootAsync(FutureWithCallback<Object> future) {
        long rootId = _jdbm.getRoot(ROOT);
        byte[] data = null;

        if (rootId == 0)
            future.set(null);
        else {
            data = _jdbm.fetch(rootId);

            if (data == null)
                throw new RuntimeIOException(Strings.CORRUPTED_STORE);

            Object root = _reader.read(data);

            _reader.readVersions();
            _reader.importVersions();
            future.set(root);
        }
    }

    @Override
    protected void setRootAsync(Object value, FutureWithCallback<Void> future) {
        if (value instanceof UserTObject && ((UserTObject) value).getTrunk().getStore() != BinaryStore.this)
            future.setException(new RuntimeException(Strings.WRONG_STORE));
        else {
            byte[] data = writeObject(value);
            long id = _jdbm.getRoot(ROOT);

            if (id != 0)
                _jdbm.update(id, data);
            else {
                id = _jdbm.insert(data);
                _jdbm.setRoot(ROOT, id);
            }

            future.set(null);
        }
    }

    @SuppressWarnings("unchecked")
    @Override
    protected void getAsync(UserTObject object, Object key, FutureWithCallback<Object> future) {
        if (object.getSharedVersion_objectfabric().getUnion() instanceof Record) {
            long record = getOrFetchRecord(object.getSharedVersion_objectfabric());

            if (Record.isStored(record)) {
                BTree tree = BTree.load(getRecordManager(), record, false);
                _writer.write(key);
                byte[] data = new byte[_writer.getOffset()];
                PlatformAdapter.arraycopy(_writer.getBuffer(), 0, data, 0, data.length);
                long id = tree.fetch(data);

                if (id != 0) {
                    data = getRecordManager().fetch(id);
                    Object value = _reader.read(data);

                    _reader.readVersions();

                    Version version = _reader.getOrCreateVersion(object);
                    TKeyedEntry entry = new TKeyedEntry(key, TKeyed.hash(key), value, false);

                    if (Debug.ENABLED)
                        Debug.assertion(value != TKeyedEntry.REMOVAL);

                    ((LazyMapVersion) version).putEntry(key, entry, true, true);
                    _reader.importVersions();
                    future.set(value);
                    return;
                }
            }
        }

        future.set(null);
    }

    //

    @Override
    protected void fetchAsync(List<byte[]> refs, AsyncCallback<Object[]> objects) {
        Object[] result = new Object[refs.size()];

        for (int i = 0; i < result.length; i++) {
            result[i] = _reader.read(refs.get(i));
            _reader.readVersions();
            _reader.importVersions();
        }

        objects.onSuccess(result);
    }

    @Override
    protected void insertAsync(Object object, AsyncCallback<byte[]> ref) {
        Run run = (Run) getRun();
        run._inserts.add(object);
        run._callbacks.add(ref);
    }

    //

    final RecordManager getRecordManager() {
        return _jdbm;
    }

    private final byte[] writeObject(Object object) {
        _writer.write(object);
        byte[] data = new byte[_writer.getOffset()];
        PlatformAdapter.arraycopy(_writer.getBuffer(), 0, data, 0, data.length);
        return data;
    }

    final long getRecord(Version shared) {
        int index = _updatedVersions.indexOf(shared);

        if (index >= 0)
            return _updatedVersionsRecords[index];

        Object union = shared.getUnion();

        if (union instanceof Record)
            return ((Record) union).getRecord();

        return Record.NOT_STORED;
    }

    final long getOrFetchRecord(Version shared) {
        long record = getRecord(shared);

        if (record == Record.UNKNOWN)
            record = fetchRecord(shared);

        if (Debug.ENABLED)
            Debug.assertion(record != Record.UNKNOWN);

        return record;
    }

    final long fetchRecord(Version shared) {
        if (Debug.ENABLED)
            Debug.assertion(!_updatedVersions.contains(shared));

        long record = Record.NOT_STORED;
        Object union = shared.getUnion();

        if (union instanceof Descriptor) {
            Descriptor descriptor = (Descriptor) union;
            Session session = descriptor.getSession();
            long records = session.getRecords();

            if (records == Record.NOT_STORED) {
                records = _sessions.fetch(session.getSharedVersion_objectfabric().getUID());
                session.setRecords(records);
            }

            record = fetchRecord(records, descriptor.getId() & 0xff);
        } else {
            byte[] uid = shared.getUID();

            if (uid != null) {
                long records = _sessions.fetch(uid);
                record = fetchRecord(records, Session.UID_OBJECT_ID & 0xff);
            }
        }

        if (Debug.ENABLED)
            Debug.assertion(record != Record.UNKNOWN);

        return record;
    }

    private final long fetchRecord(long records, int id) {
        if (records != Record.NOT_STORED) {
            byte[] session = getRecordManager().fetch(records);

            if (Debug.ENABLED)
                Debug.assertion(session.length == Session.TOTAL_LENGTH * 8);

            return Utils.readLong(session, id * 8);
        }

        return Record.NOT_STORED;
    }

    final void updateRecord(Version shared, Session session, byte[] uid, long currentRecord, long newRecord, int id) {
        if (Debug.ENABLED)
            Debug.assertion(newRecord != currentRecord);

        int index = _updatedVersions.add(shared);

        if (index == _updatedVersionsRecords.length)
            _updatedVersionsRecords = Utils.extend(_updatedVersionsRecords);

        _updatedVersionsRecords[index] = newRecord;

        // Sessions

        int sessionIndex = _updatedSessions.indexOf(session);
        long records = sessionIndex >= 0 ? _updatedSessionsRecords[sessionIndex] : session.getRecords();
        final long initialRecords = records;

        if (records == Record.NOT_STORED)
            records = _sessions.fetch(uid);

        if (records != Record.NOT_STORED) {
            byte[] data = getRecordManager().fetch(records);

            if (Debug.ENABLED) {
                Debug.assertion(data.length == Session.TOTAL_LENGTH * 8);
                Debug.assertion(Utils.readLong(data, id * 8) == currentRecord);
            }

            Utils.writeLong(data, id * 8, newRecord);
            getRecordManager().update(records, data, 0, data.length);
        } else {
            byte[] data = new byte[Session.TOTAL_LENGTH * 8];
            Utils.writeLong(data, id * 8, newRecord);
            records = getRecordManager().insert(data, 0, data.length);
            _sessions.put(uid, records);
        }

        if (records != initialRecords) {
            index = _updatedSessions.add(session);

            if (index == _updatedSessionsRecords.length)
                _updatedSessionsRecords = Utils.extend(_updatedSessionsRecords);

            _updatedSessionsRecords[index] = records;
        }
    }

    //

    @Override
    protected Action onVisitingTObject(Visitor visitor, TObject object) {
        Action action = super.onVisitingTObject(visitor, object);

        if (action == Action.VISIT) {
            /*
             * TODO write version to a cache, for now writer will snapshot entire object
             * for each change.
             */

            // Object union = ((Version) object).getUnion();
            //
            // if (!(union instanceof Record))
            // return Action.SKIP;
            //
            // if (((Record) union).getRecord() == Record.NULL)
            // return Action.SKIP;

            Version shared = (Version) object;
            long record = getOrFetchRecord(shared);
            int id = shared.getClassId();

            if (record != Record.NOT_STORED) {
                // TODO: temporary: other objects should not be snapshotted
                if (id == DefaultObjectModel.COM_OBJECTFABRIC_TMAP_CLASS_ID || id == DefaultObjectModel.COM_OBJECTFABRIC_TSET_CLASS_ID)
                    return Action.VISIT;

                _writer.snapshot(shared);
            } else {
                /*
                 * Lazy objects must always be stored, otherwise during construction their
                 * content can get GCed before the lazy object is added to the store.
                 */
                if (id == DefaultObjectModelBase.COM_OBJECTFABRIC_LAZYMAP_CLASS_ID)
                    return Action.VISIT;
            }
        }

        return Action.SKIP;
    }

    @Override
    protected void onVisitingVersions(Visitor visitor, Version shared) {
        super.onVisitingVersions(visitor, shared);

        if (Debug.ENABLED)
            Debug.assertion(_writer.getLimit() == _writer.getBuffer().length);

        _writer.reset();
    }

    @Override
    protected void onVisitedVersions(Visitor visitor, Version shared) {
        super.onVisitedVersions(visitor, shared);

        _writer.storeBuffer(shared);
    }

    @Override
    protected void onVisitedBranch(Visitor visitor) {
        super.onVisitedBranch(visitor);

        int index = _processed.add(visitor.getBranch());

        if (index != SparseArrayHelper.ALREADY_PRESENT) {
            if (index == _processedInterceptions.length)
                _processedInterceptions = Utils.extend(_processedInterceptions);

            _processedInterceptions[index] = getInterception().getId();
        }
    }

    //

    @Override
    protected void startRun() {
        _executor.execute(getRun());
    }

    //

    private final class Run extends DefaultRunnable {

        private final Queue<Object> _inserts = new Queue<Object>();

        private final Queue<AsyncCallback<byte[]>> _callbacks = new Queue<AsyncCallback<byte[]>>();

        private final Queue<byte[]> _refs = new Queue<byte[]>();

        @Override
        public void onException(Exception e) {
            unregisterFromAllBranches(e);

            try {
                _jdbm.close(false);
            } catch (Exception _) {
                // Ignore
            }

            disposeFromRunThread();

            super.onException(e);

            if (Debug.THREADS) {
                ThreadAssert.resume(this);
                ThreadAssert.removePrivate(_sessions);
                ThreadAssert.removePrivateList(_reader.getThreadContextObjects());
                ThreadAssert.removePrivateList(_writer.getThreadContextObjects());
                ThreadAssert.removePrivate(this);
            }
        }

        @Override
        protected void checkedRun() {
            if (Debug.THREADS) {
                ThreadAssert.resume(this, false);
                ThreadAssert.exchangeTake(this);
            }

            Exception ex;

            try {
                onRunStarting();
                runTasks();

                //

                while (_inserts.size() > 0)
                    _refs.add(writeObject(_inserts.poll()));

                //

                if (Debug.ENABLED)
                    Debug.assertion(!_writer.interrupted());

                for (;;) {
                    BinaryStore.this.run(_writer);

                    if (!_writer.interrupted())
                        break;

                    _writer.grow();
                }

                _writer.writeSnapshots();

                //

                Exception wrongStore = null;
                int toremove;

                if (_writer.getWrongStore() == null)
                    _jdbm.commit();
                else {
                    _jdbm.rollback();
                    wrongStore = new RuntimeException(Strings.WRONG_STORE + _writer.getWrongStore());
                }

                while (_processed.size() > 0) {
                    Transaction branch = _processed.pollPartOfClear();
                    byte interception = _processedInterceptions[_processed.size()];

                    if (wrongStore == null)
                        Interceptor.ack(branch, interception, true);
                    else
                        Interceptor.nack(branch, null, wrongStore);
                }

                if (wrongStore != null) {
                    if (Debug.ENABLED) {
                        // Assert old records have been restored on rollback
                        for (int i = 0; i < _updatedVersions.size(); i++) {
                            Version shared = _updatedVersions.get(i);
                            Debug.assertion(_jdbm.fetch(shared.getRecord()) != null);
                        }
                    }

                    _updatedVersions.clear();
                    _updatedSessions.clear();
                } else {
                    while (_updatedVersions.size() > 0) {
                        Version shared = _updatedVersions.pollPartOfClear();
                        long record = _updatedVersionsRecords[_updatedVersions.size()];
                        shared.setRecord(record);
                    }

                    while (_updatedSessions.size() > 0) {
                        Session session = _updatedSessions.pollPartOfClear();
                        long records = _updatedSessionsRecords[_updatedSessions.size()];
                        session.setRecords(records);
                    }
                }

                //

                while (_callbacks.size() > 0) {
                    AsyncCallback<byte[]> callback = _callbacks.poll();
                    byte[] ref = _refs.poll();

                    if (wrongStore == null)
                        callback.onSuccess(ref);
                    else
                        callback.onFailure(wrongStore);
                }

                //

                _writer.resetWrongStore();

                if (Debug.THREADS)
                    ThreadAssert.suspend(this);

                setFlushes();
                onRunEnded();
                return;
            } catch (Exception e) {
                ex = e;
            }

            while (_callbacks.size() > 0)
                _callbacks.poll().onFailure(ex);

            // Only in case of exception (can be closing)
            onException(ex);
        }
    }
}
