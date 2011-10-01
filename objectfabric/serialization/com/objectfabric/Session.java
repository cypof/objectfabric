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

import java.util.Arrays;
import java.util.concurrent.atomic.AtomicIntegerFieldUpdater;
import java.util.concurrent.atomic.AtomicReferenceArray;

import com.objectfabric.TObject.UserTObject.SystemClass;
import com.objectfabric.misc.Debug;
import com.objectfabric.misc.PlatformAdapter;
import com.objectfabric.misc.PlatformClass;
import com.objectfabric.misc.PlatformThread;
import com.objectfabric.misc.PlatformWeakReference;

/**
 * Distributes ids to transactional objects and keep a weak reference to their shared
 * version so they can be retrieved by id. Sessions are GCed after their objects'
 * descriptors, when they all have been GCed or disconnected.
 */
final class Session extends SessionBase implements SystemClass {

    static final int TOTAL_LENGTH = 0xff + 1;

    // Index 0xff represents the session itself
    static final int OBJECTS_LENGTH = 0xff;

    static final byte UID_OBJECT_ID = (byte) 0xff;

    @SuppressWarnings("unused")
    private volatile int _currentId;

    private static final AtomicIntegerFieldUpdater<Session> _currentIdUpdater;

    private final AtomicReferenceArray<SharedRef> _refs = new AtomicReferenceArray<SharedRef>(OBJECTS_LENGTH);

    private long _records;

    /*
     * TODO Compact/Reuse ids.
     */

    static {
        _currentIdUpdater = AtomicIntegerFieldUpdater.newUpdater(Session.class, "_currentId");
    }

    // Constructor for object model
    protected Session(Transaction trunk, Site originImpl, Transaction trunkImpl) {
        this(trunk, originImpl, trunkImpl, null, 0);

        // Sessions are deserialized separately
        throw new RuntimeException();
    }

    public Session(Transaction trunk, Site originImpl, Transaction trunkImpl, byte[] uid, long record) {
        super(new Version(null, uid), trunk, originImpl, trunkImpl);

        Version shared = (Version) getSharedVersion_objectfabric();
        shared.setUnion(new DescriptorForUID(this, record), true);
    }

    public long getRecords() {
        return _records;
    }

    public void setRecords(long value) {
        int i; // TODO
//        if (Debug.ENABLED)
//            Debug.assertion(_records == Record.NOT_STORED);

        _records = value;
    }

    public TObject.Version getSharedVersion(byte id) {
        SharedRef ref = _refs.get(id & 0xff);

        if (ref != null)
            return ref.get();

        return null;
    }

    @SuppressWarnings("static-access")
    public Descriptor assignId(TObject.Version shared) {
        if (Debug.ENABLED) {
            if (PlatformAdapter.PLATFORM != CompileTimeSettings.PLATFORM_GWT)
                Debug.assertion(PlatformThread.holdsLock(shared));

            Debug.assertion(shared.getReference().getClass() == Reference.class);
        }

        int id;

        for (;;) {
            id = _currentId;

            if (Debug.ENABLED)
                Debug.assertion(id <= OBJECTS_LENGTH);

            if (id == OBJECTS_LENGTH)
                return null;

            if (_currentIdUpdater.compareAndSet(this, id, id + 1))
                break;
        }

        Descriptor descriptor = new Descriptor(((Reference) shared.getUnion()).get(), Record.NOT_STORED, this, (byte) id);
        shared.setUnion(descriptor, false);

        if (Debug.ENABLED)
            Debug.assertion(_refs.get(id) == null);

        _refs.set(id, new SharedRef(shared));
        return descriptor;
    }

    public UserTObject getOrCreateInstance(byte id, ObjectModel model, int classId, TType[] genericParameters, UserTObject bind, TObjectReader reader) {
        TObject.Version shared;
        UserTObject newObject = null;

        // Get or create the shared version
        for (;;) {
            SharedRef ref = _refs.get(id & 0xff);
            shared = ref != null ? ref.get() : null;

            if (shared != null) {
                if (bind != null)
                    throw new RuntimeException(Strings.CANNOT_CONNECT_TO_SELF);

                if (newObject != null) {
                    newObject.getSharedVersion_objectfabric().getReference().clear();
                    newObject = null;
                }

                break;
            }

            if (newObject == null) {
                if (bind == null)
                    newObject = reader.createInstance(model, getTrunk(), classId, genericParameters);
                else {
                    if (Debug.ENABLED) {
                        Debug.assertion(bind.getSharedVersion_objectfabric().getUnion().getClass() == Reference.class);
                        Debug.assertion(bind.getSharedVersion_objectfabric().getObjectModel() == model);
                        Debug.assertion(bind.getSharedVersion_objectfabric().getClassId() == classId);
                        Debug.assertion(Arrays.equals(bind.getSharedVersion_objectfabric().getGenericParameters(), genericParameters));
                        Debug.assertion(bind.getTrunk() == Site.getLocal().getTrunk());
                    }

                    newObject = bind;
                }

                newObject.getSharedVersion_objectfabric().setUnion(new Descriptor(newObject, Record.UNKNOWN, this, id), true);
                newObject.setTrunk(getTrunk());
            }

            shared = newObject.getSharedVersion_objectfabric();

            if (_refs.compareAndSet(id & 0xff, ref, new SharedRef(shared)))
                break;
        }

        UserTObject object = shared.getReference().get();

        if (object == null) {
            if (newObject != null)
                object = newObject; // Keep reference until here to avoid GC
            else
                object = shared.getOrRecreateTObject();
        }

        if (Debug.ENABLED) {
            Debug.assertion(object.getTrunk() == getTrunk());
            Debug.assertion(shared.getReference().get() == object);
            Debug.assertion(shared.getClassId() == classId);
            Debug.assertion(shared.getUID() == null);
            Debug.assertion(((Descriptor) shared.getReference()).getSession() == this);
            Debug.assertion(((Descriptor) shared.getReference()).getId() == id);

            if (classId >= 0) {
                java.lang.Class c = model.getClass(classId, genericParameters);

                if (PlatformAdapter.PLATFORM != CompileTimeSettings.PLATFORM_GWT)
                    Debug.assertion(PlatformClass.isInstance(c, object));
            } else {
                Debug.assertion(model == DefaultObjectModelBase.getInstance());
                Debug.assertion(PlatformClass.getClassName(object).contains("TArray"));
            }
        }

        return object;
    }

    private final void onTrunkChanged() {
        for (int i = 0; i < _refs.length(); i++) {
            SharedRef ref = _refs.get(i);

            if (ref != null) {
                TObject.Version shared = ref.get();

                if (shared != null) {
                    /*
                     * Sync in case object is being recreated (C.f.
                     * Version.getOrRecreateTObject()).
                     */
                    synchronized (shared) {
                        UserTObject object = shared.getReference().get();

                        if (object != null)
                            object.setTrunk(getTrunk());
                    }
                }
            }
        }

        DescriptorForUID descriptor = (DescriptorForUID) getSharedVersion_objectfabric().getUnion();

        if (descriptor.getTrunk() != getTrunk().getSharedVersion_objectfabric()) {
            synchronized (getSharedVersion_objectfabric()) {
                descriptor = (DescriptorForUID) getSharedVersion_objectfabric().getUnion();

                if (descriptor.getTrunk() != getTrunk().getSharedVersion_objectfabric())
                    getSharedVersion_objectfabric().setUnion(new DescriptorForUID(this, descriptor.getRecord()), false);
            }
        }
    }

    //

    /*
     * TODO: have different types: strong ref to user object + 2 counter, strong ref to
     * shared + 1 counter, or weak ref to shared.
     */
    private static final class SharedRef extends PlatformWeakReference<TObject.Version> {

        public SharedRef(TObject.Version shared) {
            super(shared, null);

            if (Debug.ENABLED)
                Debug.assertion(shared.isShared());
        }
    }

    //

    private static final class Version extends SessionBase.Version {

        private final byte[] _uid;

        public Version(SessionBase.Version shared, byte[] uid) {
            super(shared, FIELD_COUNT);

            _uid = uid;
        }

        @Override
        public TObject.Version createVersion() {
            return new Session.Version(this, null);
        }

        @Override
        public boolean isImmutable() {
            return true;
        }

        @Override
        public byte[] getUID() {
            return _uid;
        }

        @Override
        public void readWrite(com.objectfabric.Reader reader, int index) {
            super.readWrite(reader, index);

            if (!reader.interrupted()) {
                /*
                 * Update trunk on all objects that might already have been deserialized
                 * for this session.
                 */
                if (index == TRUNK_IMPL_INDEX) {
                    Session.Version shared = (Session.Version) getUnion();
                    Session session = (Session) shared.getReference().get();
                    session.setTrunk((Transaction) shared._trunkImpl.getUserTObject_objectfabric());
                    session.onTrunkChanged();
                }
            }
        }
    }
}
