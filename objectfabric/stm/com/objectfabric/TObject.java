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

import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.Future;

import com.objectfabric.Snapshot.SlowChanging;
import com.objectfabric.TObject.Reference.UserReferenceWithCount;
import com.objectfabric.misc.AsyncCallback;
import com.objectfabric.misc.CompletedFuture;
import com.objectfabric.misc.Debug;
import com.objectfabric.misc.List;
import com.objectfabric.misc.Log;
import com.objectfabric.misc.OverrideAssert;
import com.objectfabric.misc.PlatformAdapter;
import com.objectfabric.misc.PlatformClass;
import com.objectfabric.misc.PlatformConcurrentMap;
import com.objectfabric.misc.PlatformThread;
import com.objectfabric.misc.PlatformWeakReference;
import com.objectfabric.misc.SparseArrayHelper;
import com.objectfabric.misc.ThreadAssert;
import com.objectfabric.misc.UID;
import com.objectfabric.misc.Utils;

/**
 * Root of all transactional classes. TObjects can only reference other transactional
 * classes, or immutable classes enumerated in
 * <code>com.objectfabric.ImmutableClass</code>.
 */
public abstract class TObject {

    public static final TType TYPE = new TType(DefaultObjectModel.COM_OBJECTFABRIC_TOBJECT_CLASS_ID);

    /**
     * Objects with UID like sessions and object models must be registered here.
     */
    // TODO use weak values & use GCQueue to atomically remove UID.
    // TODO partition in security domains
    private static final PlatformConcurrentMap<UID, TObject.Version> _objectsWithUID = new PlatformConcurrentMap<UID, TObject.Version>();

    static {
        PlatformAdapter.init();
    }

    static final TObject.Version getObjectWithUID(byte[] uid) {
        return _objectsWithUID.get(new UID(uid));
    }

    static final TObject.Version putObjectWithUIDIfAbsent(byte[] uid, TObject.Version shared) {
        return _objectsWithUID.putIfAbsent(new UID(uid), shared);
    }

    /**
     * Site this object has been created at. Returns null for universal objects like
     * object models.
     */
    public abstract Site getOrigin();

    /**
     * Transactional objects belong to a trunk, by default the one started by the local
     * Site.
     */
    public abstract Transaction getTrunk();

    public TType getTType() {
        return new TType(DefaultObjectModel.getInstance(), DefaultObjectModel.COM_OBJECTFABRIC_TOBJECT_CLASS_ID);
    }

    /*
     * _objectfabric is added to reduce probability of name conflicts with user methods.
     */
    protected abstract UserTObject getUserTObject_objectfabric();

    protected abstract Version getSharedVersion_objectfabric();

    protected abstract int getSharedHashCode_objectfabric();

    protected static Object getUserTObject_objectfabric(Object object) {
        return object instanceof TObject ? ((TObject) object).getUserTObject_objectfabric() : object;
    }

    protected final <V> Future<V> getCompletedFuture_objectfabric(V result, Exception exception, AsyncCallback<V> callback, AsyncOptions asyncOptions) {
        if (callback != null || exception != null) {
            FutureWithCallback<V> async = new FutureWithCallback<V>(callback, asyncOptions);

            if (exception != null)
                async.setException(exception);
            else
                async.set(result);

            return async;
        }

        return new CompletedFuture<V>(result);
    }

    /**
     * TObjects are split between a user part, and an internal one. The internal part is
     * also a version (the shared version), and contains all state for the TObject. For
     * GC, user parts are referenced only by user code and other user parts, and by future
     * TObject versions (versions contained in transactions). They are not directly
     * referenced by internal parts, otherwise they would never be GCed as internal parts
     * can be referenced by extensions. Versions contain fields of type TObject, which can
     * either contain a user part or an internal one. In transactions' versions, fields
     * are of type UserTObject, in shared versions, they are of type Version.
     */
    protected static class UserTObject extends TObject {

        /**
         * Shared version internals must always be visible by all threads. Merge functions
         * must be carefully designed to only modify shadowed memory. Shared version is
         * unique and stable for a TObject.
         */
        private Version _shared;

        /**
         * System.identityHashCode seems a little costly so cache since heavily used. TODO
         * move to version to remove remaining identityHashCode calls during merges etc.
         * and bench.
         */
        private int _sharedHash;

        private Transaction _trunk;

        /**
         * References are just an optimization to prevent GC of referenced UserTObjects.
         * It would work without, but objects might have to be recreated when reading a
         * field. This could be OK locally but forces connections to send snapshots every
         * time they write an TObject.
         * <nl>
         * TODO use fields instead of array in generated TObjects.
         * <nl>
         * TODO To help GC, use an array of RefWithCount, individually referenced by
         * another array in shared version so that it is not needed to re-reference the
         * full array for each update.
         */
        private Object[] _userReferences;

        public UserTObject() {
            this(Transaction.getDefaultTrunk());
        }

        public UserTObject(Transaction trunk) {
            this(new Version(null), trunk);
        }

        protected UserTObject(Version shared, Transaction trunk) {
            if (trunk == null) {
                if (this instanceof Transaction)
                    trunk = (Transaction) this;
                else
                    throw new IllegalArgumentException(Strings.ARGUMENT_NULL);
            }

            setSharedVersion(shared);
            shared.setUnion(new Reference(this, false), true);
            _trunk = trunk;

            if (Debug.ENABLED) {
                PlatformAdapter.assertEqualsAndHashCodeAreDefault(this);

                if (Transaction.getLocalTrunk() != null && !(this instanceof DefaultObjectModel))
                    TType.checkTType(this);
            }
        }

        /*
         * TODO: constructor with descriptor to deserialize objects without creating a
         * Reference, and for better descriptor locality with this.
         */

        /**
         * Also called to rollback a GC.
         */
        final void setSharedVersion(Version shared) {
            _shared = shared;

            // TODO: bench with random or other hash
            _sharedHash = System.identityHashCode(shared);
        }

        @Override
        protected final UserTObject getUserTObject_objectfabric() {
            return this;
        }

        @Override
        protected final Version getSharedVersion_objectfabric() {
            return _shared;
        }

        @Override
        protected final int getSharedHashCode_objectfabric() {
            return _sharedHash;
        }

        @Override
        public final Transaction getTrunk() {
            return _trunk;
        }

        final void setTrunk(Transaction value) {
            if (Debug.ENABLED)
                Debug.assertion(value != null);

            _trunk = value;
        }

        @Override
        public final Site getOrigin() {
            return getSharedVersion_objectfabric().getOrigin();
        }

        @Override
        public boolean equals(Object obj) {
            /*
             * Assert equals & hash code is not used as user can override behavior.
             */
            if (Debug.ENABLED)
                if (!Helper.getInstance().allowEqualsOrHash())
                    Debug.fail();

            if (obj == getSharedVersion_objectfabric())
                return true;

            return super.equals(obj);
        }

        @Override
        public int hashCode() {
            if (Debug.ENABLED)
                if (!Helper.getInstance().allowEqualsOrHash())
                    Debug.fail();

            // Constant hash if object recreated after GC.
            return getSharedHashCode_objectfabric();
        }

        //

        @Override
        public String toString() {
            if (Debug.ENABLED)
                return getSharedVersion_objectfabric().toString();

            return super.toString();
        }

        // Helpers

        protected final Transaction startRead_objectfabric(Transaction outer) {
            return Transaction.startRead(outer, this);
        }

        protected final void endRead_objectfabric(Transaction outer, Transaction inner) {
            Transaction.endRead(outer, inner);
        }

        protected final Transaction startWrite_objectfabric(Transaction outer) {
            return Transaction.startWrite(outer, this);
        }

        protected final void endWrite_objectfabric(Transaction outer, Transaction inner) {
            Transaction.endWrite(outer, inner);
        }

        protected final Version getOrCreateVersion_objectfabric(Transaction transaction) {
            TObject.Version version = transaction.getVersionFromTObject(this);

            if (version == null) {
                version = getSharedVersion_objectfabric().createVersion();
                transaction.putVersion(this, version);
            }

            return version;
        }

        protected static final Version createVersion_objectfabric(UserTObject object) {
            return object.getSharedVersion_objectfabric().createVersion();
        }

        protected final AsyncOptions getDefaultAsyncOptions_objectfabric() {
            return OF.getDefaultAsyncOptions();
        }

        // User references

        final Object[] getUserReferences() {
            return _userReferences;
        }

        final void setUserReferences(Object[] value) {
            _userReferences = value;
        }

        //

        /**
         * System classes are the one used by ObjectFabric for its inner workings. They
         * are also the minimum set of classes that need to be sent over a network for two
         * sites to connect.
         */
        interface SystemClass {
        }

        /**
         * Methods are TObjects, to reuse serialization code, and declare this interface.
         */
        protected interface Method extends SystemClass {

            String getName();
        }

        //

        protected Executor getDefaultMethodExecutor_objectfabric() {
            Site origin = getOrigin();

            if (origin != null)
                return origin.getMethodExecutor();

            return Site.getLocal().getMethodExecutor();
        }

        /**
         * @param call
         */
        protected void invoke_objectfabric(MethodCall call) {
            throw new IllegalStateException();
        }

        /**
         * @param methodVersion
         * @param index
         * @param result
         */
        protected void setResult_objectfabric(TObject.Version methodVersion, int index, Object result) {
            throw new IllegalStateException();
        }

        /**
         * @param methodVersion
         * @param index
         * @param error
         */
        protected void setError_objectfabric(TObject.Version methodVersion, int index, String error) {
            throw new IllegalStateException();
        }

        /**
         * @param call
         */
        protected void getResultOrError_objectfabric(MethodCall call) {
            throw new IllegalStateException();
        }

        //

        protected static final TObject.Version getMethodVersion_objectfabric(MethodCall call) {
            return call.getMethodVersion();
        }

        protected static final int getMethodCallIndex_objectfabric(MethodCall call) {
            return call.getIndex();
        }

        protected static final void setDirect(MethodCall call, Object value) {
            call.set(value, true);
        }

        protected static final void setExceptionDirect(MethodCall call, Exception e) {
            call.setException(e, true);
        }

        //

        protected static final AsyncCallback getNopCallback_objectfabric() {
            return FutureWithCallback.NOP_CALLBACK;
        }

        /**
         * Local method call, can be sent to another machine, where it will be partially
         * deserialized to a RemoteMethodCall.
         */
        protected static class LocalMethodCall extends MethodCall {

            public LocalMethodCall(UserTObject target, UserTObject method, TObject.Version methodVersion, int index, AsyncCallback callback, AsyncOptions asyncOptions) {
                super(target, method, index, callback, asyncOptions);

                if (getTransaction() != null) {
                    if (Debug.THREADS)
                        ThreadAssert.exchangeGive(this, getTransaction());

                    Transaction.setCurrentUnsafe(null);
                }

                setMethodVersion(methodVersion);
            }

            @Override
            public final Object get() throws java.lang.InterruptedException, ExecutionException {
                return ExpectedExceptionThrower.getCallResult(this);
            }

            final Object superDotGet() throws java.lang.InterruptedException, ExecutionException {
                return super.get();
            }

            @Override
            public final void set(Object value, boolean direct) {
                if (direct)
                    super.set(value, direct);
                else {
                    Exception e = afterRun(this, null);

                    if (e == null) {
                        beforePut();

                        super.set(value, direct);
                    } else
                        setException(e, direct);
                }
            }

            @Override
            public final void setException(Exception e, boolean direct) {
                if (!direct) {
                    e = afterRun(this, e);
                    beforePut();
                }

                super.setException(e, direct);
            }

            private final void beforePut() {
                if (Debug.ENABLED)
                    Debug.assertion(Transaction.getCurrent() == getTransaction());

                if (getTransaction() != null) {
                    if (Debug.THREADS)
                        ThreadAssert.exchangeGive(this, getTransaction());

                    Transaction.setCurrentUnsafe(null);
                } else
                    OF.updateAsync();
            }

            static final Exception afterRun(MethodCall call, Exception ex) {
                Transaction current = Transaction.getCurrent();

                if (call.getTransaction() == null) {
                    if (current != null) {
                        if (ex != null) {
                            /*
                             * Method probably failed before it was done with transaction,
                             * ignore.
                             */
                        } else {
                            String message = ((Method) call.getMethod()).getName() + ": " + Strings.USER_CODE_CHANGED_CURRENT_TRANSACTION;
                            ex = new RuntimeException(message);
                        }

                        Transaction.setCurrent(null);
                    }
                } else {
                    if (current != call.getTransaction()) {
                        if (ex != null) {
                            /*
                             * Method probably failed before it could switch back to
                             * transaction, ignore.
                             */
                        } else {
                            String message = ((Method) call.getMethod()).getName() + ": " + Strings.USER_CODE_CHANGED_CURRENT_TRANSACTION;
                            ex = new RuntimeException(message);
                        }

                        Transaction.setCurrent(call.getTransaction());
                    }
                }

                return ex;
            }

            @Override
            public final void run() {
                /*
                 * Only used when method is executed locally.
                 */
                if (!isDone()) {
                    if (Debug.THREADS)
                        ThreadAssert.exchangeTake(this);

                    if (getTransaction() != null)
                        Transaction.setCurrentUnsafe(getTransaction());

                    getTarget().invoke_objectfabric(this);
                } else
                    super.run();
            }
        }

        // Misc

        static final UserTObject[] extendArray(UserTObject[] array) {
            UserTObject[] temp = new UserTObject[array.length << SparseArrayHelper.TIMES_TWO_SHIFT];
            PlatformAdapter.arraycopy(array, 0, temp, 0, array.length);
            return temp;
        }

        // Debug

        final List<UserTObject> getUserReferencesAsList() {
            if (!Debug.TESTING)
                throw new RuntimeException();

            List<UserTObject> list = new List<UserTObject>();

            if (_userReferences != null) {
                Object[] references = _userReferences;

                if (references != null)
                    for (int i = references.length - 1; i >= 0; i--)
                        if (references[i] != null && references[i] != Reference.NULL_USER_REFERENCE)
                            list.add(references[i] instanceof UserReferenceWithCount ? ((UserReferenceWithCount) references[i]).getObject() : (UserTObject) references[i]);
            }

            return list;
        }
    }

    // Versions

    /**
     * ! Shared version can be used as a lock to update descriptor.
     */
    protected static class Version extends TObject {

        public static final int MERGE_FLAG_NONE = 0;

        public static final int MERGE_FLAG_READS = 1 << 0;

        public static final int MERGE_FLAG_PRIVATE = 1 << 1;

        public static final int MERGE_FLAG_CLONE = 1 << 2;

        public static final int MERGE_FLAG_COPY_ARRAYS = 1 << 3;

        public static final int MERGE_FLAG_COPY_ARRAY_ELEMENTS = 1 << 4;

        /**
         * Can be either the shared version or a Reference to the object.
         */
        private Object _union;

        public Version(Version shared) {
            if (Debug.ENABLED)
                Debug.assertion(shared == null || shared.isShared());

            _union = shared;
        }

        /**
         * TObjectRef implementation.
         */
        @Override
        protected final UserTObject getUserTObject_objectfabric() {
            UserTObject object = getReference().get();

            if (Debug.ENABLED)
                Debug.assertion(object != null);

            return object;
        }

        @Override
        protected final Version getSharedVersion_objectfabric() {
            if (Debug.ENABLED)
                Debug.assertion(isShared());

            return this;
        }

        @Override
        protected final int getSharedHashCode_objectfabric() {
            if (Debug.ENABLED)
                Debug.assertion(isShared());

            return System.identityHashCode(this);
        }

        public final boolean isShared() {
            return !(_union instanceof Version);
        }

        public final Version getShared() {
            return isShared() ? this : getUnionAsVersion();
        }

        public final Object getUnion() {
            return _union;
        }

        final void setUnion(Object value, boolean privateToThread) {
            if (Debug.ENABLED)
                if (!privateToThread)
                    PlatformThread.assertHoldsLock(this);

            _union = value;
        }

        final Version getUnionAsVersion() {
            return (Version) _union;
        }

        final Reference getReference() {
            return (Reference) _union;
        }

        final Descriptor getOrCreateDescriptor() {
            if (Debug.ENABLED) {
                Debug.assertion(isShared());
                Debug.assertion(getUID() == null);
            }

            if (_union instanceof Descriptor)
                return (Descriptor) _union;

            UserTObject object = getReference().get();
            Transaction trunk = object != null ? object.getTrunk() : Transaction.getDefaultTrunk();
            return trunk.assignId(this);
        }

        // Reads

        /**
         * @param map
         * @param snapshot
         * @param start
         * @param stop
         */
        public boolean validAgainst(VersionMap map, Snapshot snapshot, int start, int stop) {
            return true;
        }

        // Writes

        /**
         * At this point, we know the snapshot in which the version will be published.
         * Returns true if subsequent versions need to be fixed.
         * 
         * @param newSnapshot
         * @param mapIndex
         */
        public boolean onPublishing(Snapshot newSnapshot, int mapIndex) {
            return false;
        }

        /**
         * @param newSnapshot
         * @param mapIndex
         * @return
         */
        public Version onPastChanged(Snapshot newSnapshot, int mapIndex) {
            throw new IllegalStateException();
        }

        /**
         * @param transactionSnapshot
         */
        public void onDeserialized(Snapshot transactionSnapshot) {
        }

        /**
         * ! Source version must not be modified. It always follows this in the snapshot
         * and its values must override ones from this. Doing it the other way would allow
         * threads to see incomplete versions (e.g. while copying separately writes and
         * values or non-atomically a long from a version to the other).
         * 
         * @param target
         * @param source
         * @param flags
         * @return
         */
        public Version merge(Version target, Version source, int flags) {
            return this;
        }

        public final TObject mergeTObject(TObject target, TObject source) {
            if (isShared()) {
                UserTObject current = (UserTObject) source;
                updateUserReference((Version) target, current);
                return current != null ? current.getSharedVersion_objectfabric() : null;
            }

            return source;
        }

        public final Object mergeObject(Object target, Object source) {
            if (isShared()) {
                Version previous = target instanceof Version ? (Version) target : null;
                UserTObject current = source instanceof UserTObject ? (UserTObject) source : null;
                updateUserReference(previous, current);
                return current != null ? current.getSharedVersion_objectfabric() : source;
            }

            return source;
        }

        /**
         * @param visitor
         */
        public void visit(Visitor visitor) {
        }

        //

        public Version createRead() {
            if (Debug.ENABLED)
                Debug.assertion(isShared());

            return new Version(this);
        }

        public Version createVersion() {
            if (Debug.ENABLED)
                Debug.assertion(isShared());

            return new Version(this);
        }

        /**
         * No clone in GWT & removed in .NET -> copy fields.
         */
        public final Version cloneThis(boolean reads, boolean copyArrays) {
            Version version;

            if (reads)
                version = getUnionAsVersion().createRead();
            else
                version = getUnionAsVersion().createVersion();

            int flags = MERGE_FLAG_CLONE | (copyArrays ? MERGE_FLAG_COPY_ARRAYS : 0);
            Version test = version.merge(version, this, flags);

            if (Debug.ENABLED) {
                Debug.assertion(test == version);

                if (!copyArrays) {
                    Class c = getClass();

                    while (c != Object.class) {
                        if (!PlatformAdapter.shallowEquals(this, version, c, "_versionIdOnCopy", "_genericParameters")) {
                            Debug.fail();
                            PlatformAdapter.shallowEquals(this, version, c, "_versionIdOnCopy", "_genericParameters");
                        }

                        c = c.getSuperclass();
                    }
                }
            }

            return version;
        }

        //

        /**
         * Only some objects types have UID, like Session or ObjectModel.
         */
        public byte[] getUID() {
            if (Debug.ENABLED)
                Debug.assertion(isShared());

            return null;
        }

        public ObjectModel getObjectModel() {
            if (Debug.ENABLED)
                Debug.assertion(isShared());

            return DefaultObjectModelBase.getInstance();
        }

        public int getClassId() {
            if (Debug.ENABLED)
                Debug.assertion(isShared());

            return DefaultObjectModel.COM_OBJECTFABRIC_TOBJECT_CLASS_ID;
        }

        public TType[] getGenericParameters() {
            if (Debug.ENABLED)
                Debug.assertion(isShared());

            return null;
        }

        /**
         * Object has no mutable field. When a TObject is sent through a connection, it
         * will remain synchronized. For mutable objects, it means its branch must be
         * watched by the connection. Branch does not need to be watched if only immutable
         * objects are sent, as it is often the case for client connections sending only
         * the local site.
         */
        public boolean isImmutable() {
            if (Debug.ENABLED)
                Debug.assertion(isShared());

            return false;
        }

        public boolean isLazy() {
            if (Debug.ENABLED)
                Debug.assertion(isShared());

            return false;
        }

        public boolean allModifiedFieldsAreReadOnly() {
            return false;
        }

        public void mergeReadOnlyFields() {
        }

        @Override
        public final Site getOrigin() {
            if (Debug.ENABLED)
                Debug.assertion(isShared());

            Object union = getUnion();

            if (union instanceof Descriptor)
                return ((Descriptor) union).getSession().getOriginImpl();

            if (union instanceof DescriptorForUID)
                return null;

            if (Debug.ENABLED)
                Debug.assertion(getSharedVersion_objectfabric().getUID() == null);

            return Site.getLocal();
        }

        /**
         * !! Might return null when called on a UID object version.
         */
        @Override
        public final Transaction getTrunk() {
            if (Debug.ENABLED)
                Debug.assertion(isShared());

            if (getUnion() instanceof Descriptor)
                return ((Descriptor) getUnion()).getSession().getTrunk();

            // This method should be never called on a Reference
            return (Transaction) ((DescriptorForUID) getUnion()).getTrunk().getReference().get();
        }

        //

        public final UserTObject getOrRecreateTObject() {
            if (Debug.ENABLED)
                Debug.assertion(isShared());

            Reference reference = (Reference) getUnion();
            UserTObject current = reference.get();

            if (current != null)
                return current;

            if (Debug.ENABLED)
                Log.write("Recreating object " + this);

            // Recreating object

            UserTObject object = getObjectModel().createInstance(getTrunk(), getClassId(), getGenericParameters());
            object.setSharedVersion(this);

            // Descriptor

            Reference newReference;

            if (reference instanceof Descriptor) {
                Descriptor descriptor = (Descriptor) reference;

                newReference = new Descriptor(object, descriptor.getRecord(), descriptor.getSession(), descriptor.getId());
            } else
                newReference = new DescriptorForUID(object, ((Record) reference).getRecord());

            // User references

            PlatformWeakReference<Object[]> weakUserReferences = reference.getUserReferences();

            if (weakUserReferences != null) {
                Object[] userReferences = weakUserReferences.get();

                if (userReferences != null)
                    newReference.setUserReferences(weakUserReferences);
                else
                    recreateUserReferences(newReference);
            }

            synchronized (this) {
                reference = (Reference) getUnion();
                current = reference.get();

                if (current != null) {
                    object = current;

                    if (Debug.ENABLED)
                        if (weakUserReferences != null)
                            Debug.assertion(object.getSharedVersion_objectfabric().getReference().getUserReferences() != null);
                } else {
                    if (reference instanceof Descriptor) {
                        Descriptor descriptor = (Descriptor) reference;
                        long record = descriptor.getRecord();
                        Session session = descriptor.getSession();
                        byte id = descriptor.getId();
                        setUnion(new Descriptor(object, record, session, id), false);
                    } else
                        setUnion(new DescriptorForUID(object, ((Record) reference).getRecord()), false);
                }

                // Set the trunk in the lock, it can be changing in the session

                if (reference instanceof Descriptor)
                    object.setTrunk(((Descriptor) reference).getSession().getTrunk());
            }

            if (reference instanceof DescriptorForUID) {
                Transaction trunk = (Transaction) ((DescriptorForUID) reference).getTrunk().getReference().get();

                if (trunk == null) {
                    if (Debug.ENABLED)
                        Debug.assertion(getClassId() == DefaultObjectModelBase.COM_OBJECTFABRIC_SESSION_CLASS_ID);

                    /*
                     * We are recreating a session, use current trunk for now, like when
                     * creating a session for the first time. The actual trunk will be set
                     * by the snapshot that must be following the session creation.
                     */
                    trunk = Site.getLocal().getTrunk();
                }

                object.setTrunk(trunk);
            }

            if (Debug.ENABLED)
                checkInvariants();

            return object;
        }

        public final long getRecord() {
            return ((Record) getUnion()).getRecord();
        }

        public final void setRecord(long record) {
            if (Debug.ENABLED)
                Debug.assertion(isShared() && record != Record.UNKNOWN);

            long previous = getRecord();

            if (previous != Record.UNKNOWN && Record.isStored(previous)) {
                if (record != Record.EMPTY) {
                    if (Debug.ENABLED)
                        Debug.assertion(record == previous);

                    return;
                }
            }

            if (Debug.ENABLED)
                if (record == Record.NOT_STORED)
                    Debug.assertion(previous == Record.UNKNOWN);

            synchronized (this) {
                /*
                 * No need to re-check previous value inside synchronized block as only
                 * one thread should be writing records.
                 */

                if (getUnion() instanceof Descriptor) {
                    Descriptor descriptor = (Descriptor) getUnion();
                    setUnion(new Descriptor(descriptor.get(), record, descriptor.getSession(), descriptor.getId()), false);
                } else {
                    DescriptorForUID descriptor = (DescriptorForUID) getUnion();
                    setUnion(new DescriptorForUID(descriptor.get(), record), false);
                }
            }
        }

        // User references to TObjects, to prevent GC. Allows duplicates.

        final void addUserReference(UserTObject object) {
            Reference reference = getReference();
            addUserReference(reference, object);
        }

        protected static final void addUserReference(Reference reference, UserTObject object) {
            PlatformWeakReference<Object[]> weak = reference.getUserReferences();
            Object[] references = null;

            if (weak != null)
                references = weak.get();
            else {
                references = new Object[Reference.USER_REFERENCES_DEFAULT_LENGTH];
                reference.setUserReferences(new PlatformWeakReference<Object[]>(references, null));
                UserTObject userTObject = reference.get();

                if (userTObject != null)
                    userTObject.setUserReferences(references);
            }

            if (references != null) {
                while (!reference.tryToPutUserReference(references, object, false) && !reference.tryToPutUserReference(references, object, true)) {
                    Object[] previous = references;

                    for (;;) {
                        references = new Object[references.length << SparseArrayHelper.TIMES_TWO_SHIFT];

                        if (Reference.rehash(previous, references))
                            break;
                    }

                    reference.setUserReferences(new PlatformWeakReference<Object[]>(references, null));
                    UserTObject userTObject = reference.get();

                    if (userTObject != null)
                        userTObject.setUserReferences(references);
                }

                if (Debug.SLOW_CHECKS) {
                    for (int i = 0; i < references.length; i++) {
                        if (references[i] != null && references[i] != Reference.NULL_USER_REFERENCE) {
                            TObject current = references[i] instanceof TObject ? (TObject) references[i] : ((UserReferenceWithCount) references[i]).getObject();

                            for (int t = 0; t < references.length; t++) {
                                if (t != i && references[t] != null && references[t] != Reference.NULL_USER_REFERENCE) {
                                    if (references[t] instanceof TObject)
                                        Debug.assertion(references[t] != current);
                                    else
                                        Debug.assertion(((UserReferenceWithCount) references[t]).getObject() != current);
                                }
                            }
                        }
                    }
                }
            }
        }

        protected final void updateUserReference(Version previous, UserTObject current) {
            if (Debug.ENABLED)
                Debug.assertion(isShared());

            Version shared = current != null ? current.getSharedVersion_objectfabric() : null;

            if (previous != shared) {
                if (previous != null)
                    getReference().removeUserReference(previous.getUserTObject_objectfabric());

                if (current != null)
                    addUserReference(current);
            }
        }

        /**
         * @param reference
         */
        protected void recreateUserReferences(Reference reference) {
        }

        //

        @Override
        public final boolean equals(Object obj) {
            if (obj instanceof UserTObject)
                return this == ((UserTObject) obj).getSharedVersion_objectfabric();

            return super.equals(obj);
        }

        @Override
        public final int hashCode() {
            return super.hashCode();
        }

        @Override
        public String toString() {
            if (Debug.ENABLED) {
                if (isShared()) {
                    Reference reference = getReference();
                    UserTObject object = reference.get();

                    if (object == null) {
                        byte[] uid = getUID();

                        if (uid != null) {
                            Version newShared = getObjectWithUID(uid);
                            object = newShared != null ? newShared.getReference().get() : null;
                        }

                        if (object == null)
                            object = getObjectModel().createInstance(getTrunk(), getClassId(), getGenericParameters());
                    }

                    String id = null;

                    if (reference instanceof Descriptor) {
                        Descriptor descriptor = (Descriptor) reference;
                        id = new UID(descriptor.getSession().getSharedVersion_objectfabric().getUID()).toShortString();
                        id += "-" + Utils.padLeft(Integer.toHexString(descriptor.getId() & 0xff), 2, '0');
                    } else {
                        byte[] uid = getUID();

                        if (uid != null)
                            id = new UID(uid).toShortString();
                    }

                    Helper.getInstance().disableEqualsOrHashCheck();
                    String value = (PlatformClass.getClassName(object) + "@" + Integer.toHexString(hashCode())) + (id != null ? " (" + id + ")" : "");
                    Helper.getInstance().enableEqualsOrHashCheck();
                    return value;
                }
            }

            return super.toString();
        }

        // Accessors

        protected static Version getSharedVersion_objectfabric(UserTObject object) {
            return object != null ? object.getSharedVersion_objectfabric() : null;
        }

        // Misc

        static final Version[] extendArray(Version[] array) {
            Version[] temp = new Version[array.length << SparseArrayHelper.TIMES_TWO_SHIFT];
            PlatformAdapter.arraycopy(array, 0, temp, 0, array.length);
            return temp;
        }

        // Debug

        /**
         * @param list
         */
        public void getContentForDebug(List<Object> list) {
            if (!Debug.ENABLED)
                throw new IllegalStateException();
        }

        public boolean hasWritesForDebug() {
            if (!Debug.ENABLED)
                throw new IllegalStateException();

            return false;
        }

        public final void checkInvariants() {
            OverrideAssert.add(this);
            checkInvariants_();
            OverrideAssert.end(this);
        }

        public void checkInvariants_() {
            if (!Debug.ENABLED)
                throw new IllegalStateException();

            OverrideAssert.set(this);

            /*
             * Check shared does not directly references TObjects. Should only be
             * versions, otherwise cycles can prevent TObjects from being GC.
             */
            if (isShared() && PlatformAdapter.PLATFORM == CompileTimeSettings.PLATFORM_JAVA) {
                PlatformAdapter.assertHasNoUserTObjects(this);

                // User references

                Object[] references = getReference().getUserReferencesArray();

                if (references != null) {
                    for (int i = 0; i < references.length; i++) {
                        if (references[i] != null && references[i] != Reference.NULL_USER_REFERENCE) {
                            TObject object = references[i] instanceof TObject ? (TObject) references[i] : ((UserReferenceWithCount) references[i]).getObject();

                            for (int t = 0; t < references.length; t++) {
                                if (t != i && references[t] != null && references[t] != Reference.NULL_USER_REFERENCE) {
                                    if (references[t] instanceof TObject)
                                        Debug.assertion(references[t] != object);
                                    else
                                        Debug.assertion(((UserReferenceWithCount) references[t]).getObject() != object);
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    //

    static class Reference extends WeakReferenceAccessor<UserTObject> {

        static final int USER_REFERENCES_DEFAULT_LENGTH = 4;

        static final Object NULL_USER_REFERENCE = new Object();

        /*
         * References held by a UserTObject to other UserTObjects. References between user
         * level objects are managed when merging a version to the shared version. Also
         * stored here to avoid re-referencing the UserTObject for every update, otherwise
         * continuously updated objects would never be GCed.
         */
        private PlatformWeakReference<Object[]> _userReferences;

        // TODO
        // private final Version[] _branches;

        // TODO remove (Disables GC for now)
        private UserTObject _object;

        @SuppressWarnings("unchecked")
        protected Reference(UserTObject object, boolean enqueue) {
            super(object, enqueue ? GCQueue.getInstance() : null);

            _object = object;
            // _branches = branches;

            if (object != null && object.getUserReferences() != null)
                _userReferences = new PlatformWeakReference<Object[]>(object.getUserReferences(), null);
        }

        // public final Version[] getBranches() {
        // return _branches;
        // }

        protected void collected() {
            throw new IllegalStateException();
        }

        // User references

        final PlatformWeakReference<Object[]> getUserReferences() {
            return _userReferences;
        }

        final void setUserReferences(PlatformWeakReference<Object[]> value) {
            _userReferences = value;
        }

        final Object[] getUserReferencesArray() {
            return _userReferences != null ? _userReferences.get() : null;
        }

        final boolean tryToPutUserReference(Object[] references, UserTObject object, boolean overrideNullMarker) {
            if (Debug.ENABLED)
                Debug.assertion(references == getUserReferencesArray());

            int index = object.getSharedHashCode_objectfabric() & (references.length - 1);

            for (int i = SparseArrayHelper.attemptsStart(references.length); i >= 0; i--) {
                if (references[index] == null || (overrideNullMarker && references[index] == NULL_USER_REFERENCE)) {
                    references[index] = object;
                    return true;
                }

                if (references[index] == object) {
                    references[index] = new UserReferenceWithCount(object, 2);
                    return true;
                }

                if (references[index] instanceof UserReferenceWithCount) {
                    UserReferenceWithCount ref = (UserReferenceWithCount) references[index];

                    if (ref.getObject() == object) {
                        ref.increment();
                        return true;
                    }
                }

                index = (index + 1) & (references.length - 1);
            }

            return false;
        }

        static final boolean rehash(Object[] previous, Object[] current) {
            for (int i = previous.length - 1; i >= 0; i--)
                if (previous[i] != null && previous[i] != NULL_USER_REFERENCE)
                    if (!rehashPut(current, previous[i]))
                        return false;

            return true;
        }

        private static final boolean rehashPut(Object[] array, Object ref) {
            UserTObject object = ref instanceof UserTObject ? (UserTObject) ref : ((UserReferenceWithCount) ref).getObject();
            int index = object.getSharedHashCode_objectfabric() & (array.length - 1);

            for (int i = SparseArrayHelper.attemptsStart(array.length); i >= 0; i--) {
                if (Debug.ENABLED)
                    Debug.assertion(array[index] != NULL_USER_REFERENCE);

                if (array[index] == null) {
                    array[index] = ref;
                    return true;
                }

                index = (index + 1) & (array.length - 1);
            }

            return false;
        }

        final boolean containsUserReference(UserTObject object) {
            Object[] references = getUserReferencesArray();

            if (references != null) {
                int index = object.getSharedHashCode_objectfabric() & (references.length - 1);

                for (int i = SparseArrayHelper.attemptsStart(references.length); i >= 0; i--) {
                    if (references[index] == object)
                        return true;

                    if (references[index] instanceof UserReferenceWithCount) {
                        UserReferenceWithCount ref = (UserReferenceWithCount) references[index];

                        if (ref.getObject() == object)
                            return true;
                    }

                    if (Debug.ENABLED) // Should not be holes
                        Debug.assertion(references[index] != null);

                    index = (index + 1) & (references.length - 1);
                }
            }

            return false;
        }

        final void removeUserReference(UserTObject object) {
            Object[] references = getUserReferencesArray();
            int index = object.getSharedHashCode_objectfabric() & (references.length - 1);

            for (;;) {
                if (references[index] == object) {
                    references[index] = NULL_USER_REFERENCE;
                    return;
                }

                if (references[index] instanceof UserReferenceWithCount) {
                    UserReferenceWithCount ref = (UserReferenceWithCount) references[index];

                    if (ref.getObject() == object) {
                        ref.decrement();

                        if (ref.getCount() == 0)
                            references[index] = NULL_USER_REFERENCE;

                        return;
                    }
                }

                if (Debug.ENABLED) // Should not be holes
                    Debug.assertion(references[index] != null);

                index = (index + 1) & (references.length - 1);
            }
        }

        final int sizeUserReferences() {
            int counter = 0;
            Object[] references = getUserReferencesArray();

            if (references != null) {
                for (int i = 0; i < references.length; i++) {
                    if (references[i] != null && references[i] != NULL_USER_REFERENCE) {
                        if (references[i] instanceof UserReferenceWithCount) {
                            UserReferenceWithCount ref = (UserReferenceWithCount) references[i];
                            counter += ref.getCount();
                        } else
                            counter++;
                    }
                }
            }

            return counter;
        }

        final void clearUserReferences() {
            Object[] references = getUserReferencesArray();

            if (references != null)
                for (int i = references.length - 1; i >= 0; i--)
                    references[i] = null;
        }

        static final class UserReferenceWithCount {

            private final UserTObject _object;

            private int _count;

            public UserReferenceWithCount(UserTObject object, int count) {
                _object = object;
                _count = count;
            }

            public UserTObject getObject() {
                return _object;
            }

            public int getCount() {
                return _count;
            }

            public void increment() {
                _count++;
            }

            public void decrement() {
                _count--;
            }
        }
    }

    /*
     * TODO: try with volatile fields instead of recreating instances for the following
     * classes.
     */

    /**
     * Storage purposes.
     */
    static abstract class Record extends Reference {

        // No version has ever been stored, or not read yet
        public static final long NOT_STORED = 0;

        // Version has been stored and was empty
        public static final long EMPTY = -1;

        public static final long UNKNOWN = -2;

        private final long _record;

        protected Record(UserTObject object, boolean enqueue, long record) {
            super(object, enqueue);

            _record = record;
        }

        public final long getRecord() {
            return _record;
        }

        public static final boolean isStored(long record) {
            if (Debug.ENABLED)
                Debug.assertion(record != UNKNOWN);

            return record > 0;
        }
    }

    /**
     * Final fields as descriptors can be changed without versioning.<br>
     * TODO: For immutable objects, assign only when initialized, and ignore
     * initialization if has a descriptor, to avoid re-read of immutable objects.
     */
    static final class Descriptor extends Record {

        private final Session _session;

        private final byte _id;

        public Descriptor(UserTObject object, long record, Session session, byte id) {
            super(object, true, record);

            if (Debug.ENABLED)
                Debug.assertion(object.getSharedVersion_objectfabric().getUID() == null);

            if (session == null)
                throw new IllegalArgumentException();

            _session = session;
            _id = id;
        }

        public final Session getSession() {
            return _session;
        }

        public final byte getId() {
            return _id;
        }

        @Override
        protected void collected() {
            Version shared = _session.getSharedVersion(_id);

            if (shared != null)
                onGarbageCollected(shared);
        }
    }

    /**
     * Final fields as descriptors can be changed without versioning.
     */
    static final class DescriptorForUID extends Record {

        private final Version _shared;

        // Only a version to allow trunk GC
        private final Version _trunk;

        public DescriptorForUID(UserTObject object, long record) {
            super(object, true, record);

            _shared = object.getSharedVersion_objectfabric();
            _trunk = object.getTrunk().getSharedVersion_objectfabric();

            if (Debug.ENABLED)
                Debug.assertion(_shared.getUID() != null);
        }

        public Version getShared() {
            return _shared;
        }

        public Version getTrunk() {
            return _trunk;
        }

        @Override
        protected void collected() {
            onGarbageCollected(_shared);
        }
    }

    /**
     * Notify extensions. Some of them have processing to do like disconnecting the object
     * from remote sites or might still need the descriptor for a while, e.g. to
     * deserialize incoming versions.
     */
    private static void onGarbageCollected(Version shared) {
        if (Debug.DGC_LOG)
            Log.write("GCed object: " + shared);

        Transaction trunk = shared.getTrunk();

        if (trunk != null) {
            SlowChanging slowChanging = trunk.getSharedSnapshot().getSlowChanging();

            if (slowChanging != null && slowChanging.getExtensions() != null)
                for (int i = 0; i < slowChanging.getExtensions().length; i++)
                    slowChanging.getExtensions()[i].onGarbageCollected(shared);

            // TODO
            // if (getBranches() != null)
            // for (Version branchShared : getBranches())
            // if (branchShared != null)
            // onGarbageCollected(((DescriptorForBranch)
            // branchShared.getUnion()).getBranch(), _shared);
        }
    }

    // /*
    // * Final fields as descriptors can be changed without versioning.
    // */
    // static final class DescriptorForBranch {
    //
    // private final Version _shared;
    //
    // private final Transaction _branch;
    //
    // public DescriptorForBranch(UserTObject object, Transaction branch) {
    // _shared = object.getSharedVersion_objectfabric();
    // _branch = branch;
    //
    // if (Debug.ENABLED)
    // Debug.assertion(branch != branch.getTrunk());
    // }
    //
    // public Version getShared() {
    // return _shared;
    // }
    //
    // public Transaction getBranch() {
    // return _branch;
    // }
    // }
}