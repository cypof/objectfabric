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

import java.util.concurrent.Executor;

/**
 * This counter can be incremented by multiple threads and processes without loosing
 * increments. Can be used in a transaction. A transaction will never be in conflict if it
 * only adds to a counter without reading its value.
 */
public class Counter extends TObject {

    public static final TType TYPE;

    static {
        TYPE = Platform.newTType(Platform.get().defaultObjectModel(), BuiltInClass.COUNTER_CLASS_ID);
    }

    public Counter(Resource resource) {
        super(resource, new CounterSharedVersion());
    }

    public long get() {
        Transaction outer = current_();
        Transaction inner = startRead_(outer);
        long value = getForRead(inner);
        endRead_(outer, inner);
        return value;
    }

    public void add(long delta) {
        Transaction outer = current_();
        Transaction inner = startWrite_(outer);
        CounterVersion version = (CounterVersion) inner.getVersion(this);

        if (version == null) {
            version = createVersion_();
            inner.putVersion(version);

            long previous = getForWrite(inner);
            version._delta = delta;
            version._value = previous + delta;
        } else {
            version._delta += delta;
            version._value += delta;
        }

        endWrite_(outer, inner);
    }

    public void reset() {
        Transaction outer = current_();
        Transaction inner = startWrite_(outer);
        CounterVersion version = (CounterVersion) getOrCreateVersion_(inner);

        version._delta = 0;
        version._value = 0;
        version._reset = true;

        endWrite_(outer, inner);
    }

    private final long getForRead(Transaction transaction) {
        long value = 0;
        boolean set = false;

        // Current version
        {
            CounterVersion version = (CounterVersion) transaction.getVersion(this);

            if (version != null) {
                value = version._value;
                set = true;

                if (version._reset)
                    return value;
            }
        }

        // Private versions
        {
            Version[][] versions = transaction.getPrivateSnapshotVersions();

            if (versions != null) {
                for (int i = versions.length - 1; i >= 0; i--) {
                    CounterVersion version = (CounterVersion) TransactionBase.getVersion(versions[i], this);

                    if (version != null) {
                        if (!set) {
                            value = version._value;
                            set = true;
                        }

                        if (version._reset)
                            return value;
                    }
                }
            }
        }

        if (!transaction.ignoreReads()) {
            Version read = transaction.getRead(this);

            if (read == null) {
                read = createRead();
                transaction.putRead(read);
            }

            if (Debug.ENABLED)
                Debug.assertion(read instanceof CounterRead);
        }

        if (set)
            return value;

        return getPublic(transaction);
    }

    private final long getForWrite(Transaction transaction) {
        Version[][] versions = transaction.getPrivateSnapshotVersions();

        if (versions != null) {
            for (int i = versions.length - 1; i >= 0; i--) {
                CounterVersion version = (CounterVersion) TransactionBase.getVersion(versions[i], this);

                if (version != null)
                    return version._value;
            }
        }

        return getPublic(transaction);
    }

    private final long getPublic(Transaction transaction) {
        Version[][] versions = transaction.getPublicSnapshotVersions();

        for (int i = versions.length - 1; i > TransactionManager.OBJECTS_VERSIONS_INDEX; i--) {
            CounterVersion version = (CounterVersion) TransactionBase.getVersion(versions[i], this);

            if (version != null)
                return version._value;
        }

        CounterSharedVersion shared = (CounterSharedVersion) shared_();
        return shared._value;
    }

    /**
     * Registers a listener to be called when the object changes.
     */
    public final void addListener(CounterListener listener) {
        addListener(listener, workspace().callbackExecutor());
    }

    /**
     * Also specifies on which executor the listener should be invoked.
     */
    public final void addListener(CounterListener listener, Executor executor) {
        workspace().addListener(this, listener, executor);
    }

    public final void removeListener(CounterListener listener) {
        removeListener(listener, workspace().callbackExecutor());
    }

    public final void removeListener(CounterListener listener, Executor executor) {
        workspace().removeListener(this, listener, executor);
    }

    //

    @Override
    final CounterRead createRead() {
        CounterRead version = new CounterRead();
        version.setObject(this);
        return version;
    }

    @Override
    protected final CounterVersion createVersion_() {
        CounterVersion version = new CounterVersion();
        version.setObject(this);
        return version;
    }

    @Override
    protected final int classId_() {
        return BuiltInClass.COUNTER_CLASS_ID;
    }

    //

    static final class CounterRead extends TObject.Version {

        @Override
        public boolean validAgainst(VersionMap map, Snapshot snapshot, int start, int stop) {
            for (int i = start; i < stop; i++) {
                TObject.Version write = TransactionBase.getVersion(snapshot.writes()[i], object());

                if (write != null)
                    return false;
            }

            return true;
        }

        @Override
        public void visit(org.objectfabric.Visitor visitor) {
            visitor.visit(this);
        }
    }

    static final class CounterVersion extends TObject.Version {

        private long _delta;

        private boolean _reset;

        // Stored for concurrent reads
        private long _value;

        final long getDelta() {
            return _delta;
        }

        final boolean getReset() {
            return _reset;
        }

        final void init(long delta, boolean reset) {
            _delta = delta;
            _reset = reset;
        }

        @Override
        void onPublishing(Snapshot newSnapshot, int mapIndex) {
            CounterVersion version = null;

            for (int i = mapIndex - 1; i > TransactionManager.OBJECTS_VERSIONS_INDEX; i--) {
                version = (CounterVersion) TransactionBase.getVersion(newSnapshot.writes()[i], object());

                if (version != null)
                    break;
            }

            if (!_reset) {
                long previous = 0;

                if (version != null)
                    previous = version._value;
                else
                    previous = ((CounterSharedVersion) object().shared_())._value;

                _value = previous + _delta;
            }
        }

        @Override
        TObject.Version merge(TObject.Version target, TObject.Version next, boolean threadPrivate) {
            CounterVersion source = (CounterVersion) next;
            CounterVersion merged = this;

            if (this == target && !threadPrivate) {
                // For long assignment atomicity on 32 bits, and extensions merges
                merged = (CounterVersion) clone(false);
            }

            merged.merge(source);
            return merged;
        }

        @Override
        void deepCopy(TObject.Version source) {
            if (source instanceof CounterSharedVersion) {
                if (Debug.ENABLED)
                    Debug.assertion(_value == 0);

                _value = ((CounterSharedVersion) source)._value;
            } else
                merge((CounterVersion) source);
        }

        private final void merge(CounterVersion source) {
            if (source._reset) {
                _delta = source._delta;
                _reset = true;
            } else
                _delta += source._delta;

            _value = source._value;
        }

        @Override
        void visit(org.objectfabric.Visitor visitor) {
            visitor.visit(this);
        }

        @Override
        boolean mask(Version version) {
            boolean empty = false;

            if (_reset) {
                CounterVersion c = (CounterVersion) version;
                c._delta = 0;
                c._value = 0;
                empty = true;
            }

            return empty;
        }

        // Debug

        @Override
        void getContentForDebug(List<Object> list) {
            if (!Debug.ENABLED)
                throw new IllegalStateException();

            list.add(_delta);
            list.add(_reset);
            list.add(_value);
        }

        @Override
        boolean hasWritesForDebug() {
            if (!Debug.ENABLED)
                throw new IllegalStateException();

            return _delta != 0 || _reset;
        }
    }

    static final class CounterSharedVersion extends TObject.Version {

        /*
         * Volatile for long assignment atomicity on 32 bits. TODO x64 code path, or check
         * if Atomic* weak set can keep atomicity without the memory barrier.
         */
        private volatile long _value;

        @Override
        TObject.Version merge(TObject.Version target, TObject.Version next, boolean threadPrivate) {
            CounterVersion source = (CounterVersion) next;

            if (threadPrivate)
                _value += source.getDelta();
            else
                _value = source._value;

            return this;
        }

        @Override
        void visit(org.objectfabric.Visitor visitor) {
            visitor.visit(this);
        }

        // Debug

        @Override
        void getContentForDebug(List<Object> list) {
            if (!Debug.ENABLED)
                throw new IllegalStateException();

            list.add(_value);
        }
    }
}