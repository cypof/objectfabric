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

import java.util.concurrent.Future;

import org.objectfabric.ThreadAssert.SingleThreadedThenShared;

/**
 * Root of all transactional classes.
 */
@SuppressWarnings("rawtypes")
public class TObject {

    public static final TType TYPE;

    static {
        TYPE = Platform.newTType(Platform.get().defaultObjectModel(), BuiltInClass.TOBJECT_CLASS_ID);
    }

    static final String OBJECT_FABRIC_VERSION = "0.9";

    static final byte SERIALIZATION_VERSION = 1;

    static final int FLAG_REFERENCED_BY_URI = 1 << 8;

    private final Resource _resource;

    /**
     * Shared version internals must always be visible by all threads. Merge functions
     * must be carefully designed to only modify shadowed memory.
     */
    private final Version _shared;

    /**
     * System.identityHashCode seems a little costly, STM is faster when caching.
     */
    private final int _hash;

    private Range _range;

    // TODO merge with _hash
    private int _info;

    private PlatformSet<Object> _listeners;

    TObject(Resource resource) {
        this(resource, new Version());
    }

    TObject(Resource resource, Version shared) {
        if (shared == null)
            throw new IllegalArgumentException(Strings.ARGUMENT_NULL);

        if (resource == null)
            _resource = (Resource) this;
        else
            _resource = resource;

        _shared = shared;

        // TODO bench with random, TKeyed.rehash or other hash
        _hash = System.identityHashCode(this);

        shared.setObject(this);

        if (Debug.ENABLED)
            TType.checkTType(this);
    }

    /**
     * Resource this object belongs to.
     */
    public final Resource resource() {
        return _resource;
    }

    /**
     * Workspace this object belongs to.
     */
    public final Workspace workspace() {
        return _resource.workspaceImpl();
    }

    public final TType getTType() {
        return Platform.newTType(objectModel_(), classId_());
    }

    /**
     * Shortcut to {@link Workspace#atomic(Runnable)}.
     */
    public final void atomic(Runnable runnable) {
        resource().workspace().atomic(runnable);
    }

    /**
     * Shortcut to {@link Workspace#atomicRead(Runnable)}.
     */
    public void atomicRead(Runnable runnable) {
        resource().workspace().atomicRead(runnable);
    }

    /**
     * Shortcut to {@link Workspace#atomicWrite(Runnable)}.
     */
    public void atomicWrite(Runnable runnable) {
        resource().workspace().atomicWrite(runnable);
    }

    /*
     * '_' is added to names to reduce probability of name conflicts with user methods.
     */

    protected final <V> Future<V> getCompletedFuture_(V result, Exception exception, AsyncCallback<V> callback) {
        if (callback != null || exception != null) {
            FutureWithCallback<V> async = new FutureWithCallback<V>(callback, workspace().callbackExecutor());

            if (exception != null)
                async.setException(exception);
            else
                async.set(result);

            return async;
        }

        return new CompletedFuture<V>(result);
    }

    protected final Version shared_() {
        return _shared;
    }

    final int hash() {
        return _hash;
    }

    final Range range() {
        return _range;
    }

    final void range(Range value) {
        _range = value;
    }

    final int id() {
        return _info & 0xff;
    }

    final void id(int value) {
        if (Debug.ENABLED) {
            Debug.assertion(id() == 0);
            Debug.assertion((value & ~0xff) == 0);
        }

        _info |= value;
    }

    final boolean isReferencedByURI() {
        return (_info & FLAG_REFERENCED_BY_URI) != 0;
    }

    final void setReferencedByURI() {
        _info |= FLAG_REFERENCED_BY_URI;
    }

    final PlatformSet<Object> listeners() {
        return _listeners;
    }

    final void listeners(PlatformSet<Object> value) {
        _listeners = value;
    }

    //

    Version createRead() {
        throw new IllegalStateException();
    }

    protected Version createVersion_() {
        Version version = new Version();
        version.setObject(this);
        return version;
    }

    protected ObjectModel objectModel_() {
        return Platform.get().defaultObjectModel();
    }

    protected int classId_() {
        return BuiltInClass.TOBJECT_CLASS_ID;
    }

    TType[] genericParameters() {
        return null;
    }

    //

    protected final Transaction current_() {
        return _resource.transaction();
    }

    protected final Transaction startRead_(Transaction outer) {
        if (outer != null) {
            TransactionBase.checkWorkspace(outer, this);
            return outer;
        }

        return TransactionBase.startAccess(workspace(), true);
    }

    protected final void endRead_(Transaction outer, Transaction inner) {
        if (outer == null)
            TransactionBase.endAccess(inner, false);
    }

    protected final Transaction startWrite_(Transaction outer) {
        if (outer != null) {
            if (outer.noWrites())
                throw new RuntimeException(Strings.READ_ONLY);

            TransactionBase.checkWorkspace(outer, this);
            return outer;
        }

        return TransactionBase.startAccess(workspace(), false);
    }

    protected final void endWrite_(Transaction outer, Transaction inner) {
        endWrite_(outer, inner, true);
    }

    static void endWrite_(Transaction outer, Transaction inner, boolean ok) {
        if (outer == null)
            TransactionBase.endAccess(inner, ok);
    }

    protected final Version getOrCreateVersion_(Transaction transaction) {
        TObject.Version version = transaction.getVersion(this);

        if (version == null) {
            version = createVersion_();
            transaction.putVersion(version);
        }

        return version;
    }

    protected static void wrongResource_() {
        throw new RuntimeException(Strings.WRONG_RESOURCE);
    }

    //

    @Override
    public boolean equals(Object obj) {
        /*
         * Assert equals & hash code is not used as user can override behavior.
         */
        if (Debug.ENABLED) {
            if (!Helper.instance().allowEqualsOrHash())
                Debug.fail();

            Debug.assertion(!(obj instanceof Version));
        }

        return super.equals(obj);
    }

    @Override
    public int hashCode() {
        if (Debug.ENABLED)
            if (!Helper.instance().allowEqualsOrHash())
                Debug.fail();

        return _hash;
    }

    @Override
    public String toString() {
        if (Debug.ENABLED) {
            if (!(this instanceof Resource)) {
                Helper.instance().disableEqualsOrHashCheck();
                String default_ = Platform.get().defaultToString(this);
                String id = null;

                if (_range != null)
                    id += _range.id() + "-" + Utils.padLeft(Integer.toHexString(id()), 2, '0');

                String value = default_ + (id != null ? " (" + id + ")" : "");
                Helper.instance().enableEqualsOrHashCheck();
                return value;
            }
        }

        return super.toString();
    }

    protected static class Version {

        private TObject _object;

        final TObject object() {
            if (Debug.ENABLED)
                Debug.assertion(_object != null);

            return _object;
        }

        public final void setObject(TObject value) {
            if (Debug.ENABLED)
                Debug.assertion(_object == null && value != null);

            _object = value;
        }

        boolean validAgainst(VersionMap map, Snapshot snapshot, int start, int stop) {
            return true;
        }

        /**
         * At this point, we know the snapshot in which the version will be published.
         * Returns true if subsequent versions need to be fixed.
         */
        void onPublishing(Snapshot newSnapshot, int mapIndex) {
        }

        void onDeserialized(Snapshot transactionSnapshot) {
        }

        /**
         * ! Source version must not be modified. It always follows this in the snapshot
         * and its values must override ones from this. Doing it the other way would allow
         * threads to see incomplete versions (e.g. while copying separately writes and
         * values or non-atomically a long from a version to the other).
         */
        Version merge(Version target, Version source, boolean threadPrivate) {
            return this;
        }

        /**
         * Must copy all mutable state.
         */
        void deepCopy(Version source) {
        }

        /**
         * Shallow copy of all fields. (No clone in GWT & removed in .NET)
         */
        void clone(Version source) {
            // For simple types, same as deepCopy
            deepCopy(source);
        }

        final Version clone(boolean reads) {
            Version version;

            if (reads)
                version = _object.createRead();
            else
                version = _object.createVersion_();

            if (Debug.ENABLED)
                Debug.assertion(version._object == _object);

            version.clone(this);

            if (Debug.ENABLED) {
                java.lang.Class c = Platform.get().getClass(this);

                while (c != Platform.get().objectClass()) {
                    String[] exceptions = new String[0];

                    if (c == Platform.get().tKeyedVersionClass())
                        exceptions = new String[] { "_sizeDelta", "_verifySizeDeltaOnCommit" };

                    if (!Platform.get().shallowEquals(this, version, c, exceptions)) {
                        Debug.fail();
                        Platform.get().shallowEquals(this, version, c, exceptions);
                    }

                    c = Platform.get().superclass(c);
                }
            }

            return version;
        }

        void mergeReadOnlyFields() {
        }

        void visit(Visitor visitor) {
        }

        boolean mask(Version version) {
            return true;
        }

        // Debug

        void getContentForDebug(List<Object> list) {
            if (!Debug.ENABLED)
                throw new IllegalStateException();
        }

        boolean hasWritesForDebug() {
            if (!Debug.ENABLED)
                throw new IllegalStateException();

            return false;
        }

        final void checkInvariants() {
            OverrideAssert.add(this);
            checkInvariants_();
            OverrideAssert.end(this);
        }

        void checkInvariants_() {
            if (!Debug.ENABLED)
                throw new IllegalStateException();

            OverrideAssert.set(this);
        }

        @Override
        public final boolean equals(Object obj) {
            // Final as used as key (Notifier)
            return this == obj;
        }

        @Override
        public final int hashCode() {
            return _object.hash();
        }
    }

    //

    @SingleThreadedThenShared
    protected static final class Transaction extends TransactionBase {

        protected Transaction(Workspace workspace, Transaction parent) {
            super(workspace, parent);
        }
    }
}