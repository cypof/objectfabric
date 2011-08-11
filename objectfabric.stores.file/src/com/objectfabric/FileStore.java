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
import java.util.concurrent.atomic.AtomicReference;

import com.objectfabric.Index.Insert;
import com.objectfabric.TObject.Descriptor;
import com.objectfabric.TObject.Record;
import com.objectfabric.TObject.UserTObject;
import com.objectfabric.TObject.Version;
import com.objectfabric.misc.AsyncCallback;
import com.objectfabric.misc.Debug;
import com.objectfabric.misc.Log;
import com.objectfabric.misc.PlatformAdapter;
import com.objectfabric.misc.PlatformFile;
import com.objectfabric.misc.PlatformThreadPool;
import com.objectfabric.misc.RuntimeIOException;
import com.objectfabric.misc.RuntimeIOException.StoreCloseException;
import com.objectfabric.misc.SparseArrayHelper;
import com.objectfabric.misc.ThreadAssert;
import com.objectfabric.misc.Utils;

/**
 * High performance file-based store (Above 50K writes/s and 10K reads/s on a good SSD).
 * This store is based on an a modified version of the JDBM open source persistence engine
 * (http://jdbm.sourceforge.net). It stores objects in a scalable BTree, and uses a
 * write-ahead log mechanism and java.io.FileDescriptor.sync() for transactional
 * consistency. Objects are serialized using the same format as network operations.
 */
public class FileStore extends Store {

    public static final String LOG_EXTENSION = ".log";

    private static final int SESSIONS = 0;

    private static final int ROOT = 1;

    private final RecordManager _jdbm;

    private final BTree _sessions;

    private final StoreReader _reader;

    private final StoreWriter _writer;

    private final Executor _executor;

    private final boolean _terminateProcessOnException;

    private final UserTObjectSetAndList<Transaction> _processed = new UserTObjectSetAndList<Transaction>();

    private byte[] _processedInterceptions = new byte[10];

    private final VersionSetAndList _updatedVersions = new VersionSetAndList();

    private long[] _updatedVersionsRecords = new long[10];

    private final UserTObjectSetAndList<Session> _updatedSessions = new UserTObjectSetAndList<Session>();

    private long[] _updatedSessionsRecords = new long[10];

    public FileStore(String file) throws RuntimeIOException {
        this(file, false);
    }

    /**
     * disableLogAndSync disables the write ahead log mechanism and skip
     * FileDescriptor.sync(). It should only be used when durability and consistency do
     * not matter, e.g. for off-line batch insertions.
     */
    public FileStore(String file, boolean disableLogAndSync) throws RuntimeIOException {
        this(file, disableLogAndSync, PlatformThreadPool.getInstance());
    }

    /**
     * By default read and write operations are performed on ObjectFabric's thread pool.
     * You can specify another executor.
     */
    public FileStore(String file, boolean disableLogAndSync, Executor executor) throws RuntimeIOException {
        this(file, disableLogAndSync, executor, false);
    }

    /**
     * terminateProcessOnException kills the process if an exception occurs. On a local
     * disk, the only error that usually occur is running out of disk space. In some cases
     * the only useful reaction is to log the exception and terminate the process.
     */
    public FileStore(String file, boolean disableLogAndSync, Executor executor, boolean terminateProcessOnException) throws RuntimeIOException {
        PlatformFile data = new PlatformFile(file);
        PlatformFile log = new PlatformFile(file + LOG_EXTENSION);

        _jdbm = new RecordManager(data, log);

        if (disableLogAndSync)
            _jdbm.disableTransactions();

        _executor = executor;
        _terminateProcessOnException = terminateProcessOnException;

        long sessionsId = _jdbm.getRoot(SESSIONS);

        if (sessionsId != 0) {
            _sessions = BTree.load(_jdbm, sessionsId, true);

            if (_sessions == null)
                throw new RuntimeIOException(Strings.CORRUPTED_STORE);
        } else {
            _sessions = new BTree(_jdbm, true);
            _jdbm.setRoot(SESSIONS, _sessions.getId());
        }

        _reader = new StoreReader(this);
        _writer = new StoreWriter(this);

        setRun(new Run());

        if (!end())
            requestRunOnce();

        if (Debug.THREADS) {
            ThreadAssert.exchangeGive(this, _sessions);
            ThreadAssert.exchangeGiveList(this, _reader.getThreadContextObjects());
            ThreadAssert.exchangeGiveList(this, _writer.getThreadContextObjects());
            ThreadAssert.exchangeGive(this, this);
        }
    }

    public Object getRoot() throws RuntimeIOException {
        FutureWithCallback<Object> result = getRootAsync();

        try {
            return result.get();
        } catch (Throwable t) {
            throw new RuntimeIOException(t);
        }
    }

    @SuppressWarnings("unchecked")
    public FutureWithCallback<Object> getRootAsync() {
        return getRootAsync(FutureWithCallback.NOP_CALLBACK);
    }

    public FutureWithCallback<Object> getRootAsync(AsyncCallback<Object> callback) {
        return getRootAsync(callback, null);
    }

    public FutureWithCallback<Object> getRootAsync(AsyncCallback<Object> callback, AsyncOptions options) {
        final FutureWithCallback<Object> future = new FutureWithCallback<Object>(callback, options);

        getRun().execute(new Runnable() {

            public void run() {
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
        });

        return future;
    }

    public void setRoot(Object value) throws RuntimeIOException {
        FutureWithCallback<Void> result = setRootAsync(value);

        try {
            result.get();
        } catch (Throwable t) {
            ExpectedExceptionThrower.throwRuntimeException(t);
        }
    }

    @SuppressWarnings("unchecked")
    public FutureWithCallback<Void> setRootAsync(Object value) {
        return setRootAsync(value, FutureWithCallback.NOP_CALLBACK);
    }

    public FutureWithCallback<Void> setRootAsync(Object value, AsyncCallback<Void> callback) {
        return setRootAsync(value, callback, null);
    }

    public FutureWithCallback<Void> setRootAsync(final Object value, AsyncCallback<Void> callback, AsyncOptions options) {
        final FutureWithCallback<Void> future = new FutureWithCallback<Void>(callback, options);

        getRun().execute(new Runnable() {

            public void run() {
                if (value instanceof UserTObject && ((UserTObject) value).getTrunk().getStore() != FileStore.this)
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
        });

        return future;
    }

    //

    @Override
    protected void getAsync(byte[] ref, FutureWithCallback<Object> future) {
        Object value = _reader.read(ref);
        _reader.readVersions();
        _reader.importVersions();
        future.set(value);
    }

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
                    @SuppressWarnings("unchecked")
                    TKeyedEntry entry = new TKeyedEntry(key, TKeyed.hash(key), value, false);
                    ((LazyMapVersion) version).putEntry(key, entry, true, true);

                    _reader.importVersions();
                    future.set(value);
                    return;
                }
            }
        }

        future.set(null);
    }

    @Override
    protected void insert(Insert insert) {
        AtomicReference<Insert> inserts = ((Run) getRun())._inserts;

        for (;;) {
            Insert current = inserts.get();
            insert.setNext(current);

            if (inserts.compareAndSet(current, insert)) {
                requestRun();
                return;
            }
        }
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
    protected void requestRunOnce() {
        _executor.execute((Run) getRun());
    }

    //

    private final class Run extends DefaultRunnable implements Runnable {

        private final AtomicReference<Insert> _inserts = new AtomicReference<Insert>();

        public Run() {
            super(FileStore.this);
        }

        @Override
        public void onException(Throwable throwable) {
            unregisterFromAllBranches(throwable);

            try {
                _jdbm.close(false);
            } catch (Throwable t) {
                // Ignore
            }

            dispose();

            super.onException(throwable);

            if (Debug.THREADS) {
                ThreadAssert.resume(this);
                ThreadAssert.removePrivate(_sessions);
                ThreadAssert.removePrivateList(_reader.getThreadContextObjects());
                ThreadAssert.removePrivateList(_writer.getThreadContextObjects());
                ThreadAssert.removePrivate(this);
            }
        }

        public void run() {
            if (Debug.THREADS) {
                ThreadAssert.resume(this, false);
                ThreadAssert.exchangeTake(this);
            }

            Throwable throwable;

            try {
                for (;;) {
                    before();

                    //

                    Insert inserts = null;

                    for (;;) {
                        inserts = _inserts.get();

                        if (inserts == null)
                            break;

                        if (_inserts.compareAndSet(inserts, null))
                            break;
                    }

                    Insert insert = inserts;

                    while (insert != null) {
                        insert.setData(writeObject(insert.Object));
                        insert = insert.getNext();
                    }

                    //

                    FileStore.this.run(_writer);

                    if (Debug.ENABLED)
                        Debug.assertion(!_writer.interrupted());

                    _writer.writeSnapshots();

                    //

                    Throwable wrongStore = null;

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

                    insert = inserts;

                    if (inserts != null)
                        inserts.begin();

                    while (insert != null) {
                        if (wrongStore == null)
                            insert.onSuccess();
                        else
                            insert.onFailure(wrongStore);

                        insert = insert.getNext();
                    }

                    if (inserts != null)
                        inserts.commit();

                    //

                    _writer.resetWrongStore();

                    if (Debug.THREADS)
                        ThreadAssert.suspend(this);

                    if (after())
                        return;

                    if (Debug.THREADS)
                        ThreadAssert.resume(this);
                }
            } catch (Throwable ex) {
                if (!(ex instanceof StoreCloseException)) {
                    Log.write(ex);

                    if (_terminateProcessOnException)
                        System.exit(1);
                }

                throwable = ex;
            }

            // Only in case of exception (can be closing)
            onException(throwable);
        }
    }
}
