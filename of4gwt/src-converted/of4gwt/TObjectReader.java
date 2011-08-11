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

import java.util.Arrays;

import of4gwt.TObject.Record;
import of4gwt.TObject.UserTObject;
import of4gwt.TObject.Version;
import of4gwt.misc.Debug;
import of4gwt.misc.List;
import of4gwt.misc.PlatformAdapter;
import of4gwt.misc.PlatformClass;
import of4gwt.misc.ThreadAssert;
import of4gwt.misc.ThreadAssert.SingleThreaded;
import of4gwt.misc.UID;

@SingleThreaded
abstract class TObjectReader extends ImmutableReader {

    private final Version[] _unknown = new Version[0xff + 1];

    private final Version[] _sessions = new Version[0xff + 1];

    private final Version[] _models = new Version[0xff + 1];

    /*
     * Prevents garbage collection of new objects until transactions are fully built and
     * reference their TObjects. Also used to keep track of new objects to register
     * branches to extensions.
     */
    private final List<UserTObject> _newTObjects;

    private Session _currentSession;

    private ObjectModel _currentModel;

    // Debug

    private int _debugCommandCounter;

    protected TObjectReader(List<UserTObject> newTObjects) {
        _newTObjects = newTObjects;
    }

    @Override
    final void reset() {
        super.reset();

        if (Debug.COMMUNICATIONS)
            _debugCommandCounter = 0;
    }

    //

    final List<UserTObject> getNewTObjects() {
        return _newTObjects;
    }

    private final void pushNewTObject(UserTObject object) {
        if (Debug.COMMUNICATIONS) {
            Debug.assertion(object != null);

            if (!Helper.getInstance().getAllowMultipleCreations().containsKey(this))
                for (int i = _newTObjects.size() - 1; i >= 0; i--)
                    Debug.assertion(object != _newTObjects.get(i));
        }

        _newTObjects.add(object);
    }

    //

    public final UserTObject readTObject() {
        Version shared = readTObjectShared(null, _unknown);
        return shared != null ? shared.getOrRecreateTObject() : null;
    }

    final void readTObject(UserTObject bind) {
        readTObjectShared(bind, _unknown);
    }

    final Version readTObjectShared() {
        return readTObjectShared(null, _unknown);
    }

    private static final int FLAGS = 0;

    private static final int DEBUG_COUNTER = 1;

    private static final int VALUE = 2;

    @SuppressWarnings("fallthrough")
    private final Version readTObjectShared(UserTObject bind, Version[] cache) {
        int step = FLAGS;
        byte flags = 0;

        if (interrupted()) {
            step = resumeInt();
            flags = resumeByte();
        }

        Version shared = null;

        switch (step) {
            case FLAGS: {
                if (!canReadByte()) {
                    interruptByte(flags);
                    interruptInt(FLAGS);
                    return null;
                }

                flags = readByte(TObjectWriter.DEBUG_TAG_CODE);
            }
            case DEBUG_COUNTER: {
                if (Debug.COMMUNICATIONS) {
                    if (!canReadInteger()) {
                        interruptByte(flags);
                        interruptInt(DEBUG_COUNTER);
                        return null;
                    }

                    int number = readInteger();
                    Debug.assertion(_debugCommandCounter == number);
                    _debugCommandCounter++;
                }
            }
            case VALUE: {
                shared = readTObjectShared(flags, bind, cache);

                if (interrupted()) {
                    if (Debug.ENABLED)
                        Debug.assertion(shared == null);

                    interruptByte(flags);
                    interruptInt(VALUE);
                    return null;
                }

                break;
            }
            default:
                throw new IllegalStateException();
        }

        return shared;
    }

    final Version readTObjectShared(byte flags) {
        return readTObjectShared(flags, null, _unknown);
    }

    private final Version readTObjectShared(byte flags, UserTObject bind, Version[] cache) {
        Version shared;

        if ((flags & TObjectWriter.FLAG_UID) == 0) {
            if (Debug.ENABLED)
                Debug.assertion(cache == _unknown);

            shared = readTObjectWithDescriptor(flags, bind);
        } else
            shared = readTObjectWithUID(flags, cache);

        if (Debug.ENABLED)
            if (shared != null)
                if (PlatformClass.getClassName(this).equals("org.objectfabric.StoreReader"))
                    Debug.assertion(((Record) shared.getUnion()).getRecord() != Record.NOT_STORED);

        return shared;
    }

    private static final int TOBJECT_SESSION = 0;

    private static final int TOBJECT_ID = 1;

    private static final int TOBJECT_MODEL = 2;

    private static final int TOBJECT_CLASS_ID = 3;

    private static final int TOBJECT_GENERIC_ARGUMENTS = 4;

    @SuppressWarnings("fallthrough")
    private final Version readTObjectWithDescriptor(byte flags, UserTObject bind) {
        if (flags == UnknownObjectSerializer.FLAGS_NULL) {
            if (Debug.COMMUNICATIONS_LOG)
                log("null flags " + TObjectWriter.writeFlags(flags));

            return null;
        }

        int step = TOBJECT_SESSION;
        byte id = 0;
        int classId = 0;
        TType[] genericParameters = null;

        if (interrupted()) {
            step = resumeInt();
            id = resumeByte();
            classId = resumeInt();
        } else {
            if (Debug.COMMUNICATIONS_LOG) {
                Helper.getInstance().getSB().setLength(0);
                Helper.getInstance().getSB().append(" flags " + TObjectWriter.writeFlags(flags) + ", ");
            }
        }

        switch (step) {
            case TOBJECT_SESSION: {
                if ((flags & TObjectWriter.FLAG_SESSION_CHANGE) != 0) {
                    if ((flags & TObjectWriter.FLAG_SESSION_CACHED) != 0) {
                        if (!canReadByte()) {
                            interruptInt(classId);
                            interruptByte(id);
                            interruptInt(TOBJECT_SESSION);
                            return null;
                        }

                        int index = readByte(TObjectWriter.DEBUG_TAG_CACHED_INDEX) & 0xff;
                        _currentSession = (Session) _sessions[index].getOrRecreateTObject();
                    } else {
                        Version shared = readTObjectShared(null, _sessions);

                        if (interrupted()) {
                            interruptInt(classId);
                            interruptByte(id);
                            interruptInt(TOBJECT_SESSION);
                            return null;
                        }

                        _currentSession = (Session) shared.getOrRecreateTObject();
                    }
                }
            }
            case TOBJECT_ID: {
                if (!canReadByte()) {
                    interruptInt(classId);
                    interruptByte(id);
                    interruptInt(TOBJECT_ID);
                    return null;
                }

                id = readByte();

                if (Debug.COMMUNICATIONS_LOG)
                    Helper.getInstance().getSB().append("id: " + id + ", ");

                if ((flags & TObjectWriter.FLAG_NEW) == 0) {
                    if (Debug.ENABLED)
                        Debug.assertion(bind == null);

                    Version shared = _currentSession.getSharedVersion(id);

                    if (Debug.COMMUNICATIONS_LOG)
                        log(TObjectWriter.getClassName(shared.getOrRecreateTObject()) + Helper.getInstance().getSB().toString());

                    return shared;
                }
            }
            case TOBJECT_MODEL: {
                if ((flags & TObjectWriter.FLAG_MODEL_CHANGE) != 0) {
                    if ((flags & TObjectWriter.FLAG_MODEL_CACHED) != 0) {
                        if (!canReadByte()) {
                            interruptInt(classId);
                            interruptByte(id);
                            interruptInt(TOBJECT_MODEL);
                            return null;
                        }

                        int index = readByte(TObjectWriter.DEBUG_TAG_CACHED_INDEX) & 0xff;
                        _currentModel = (ObjectModel) _models[index].getOrRecreateTObject();
                    } else {
                        Version shared = readTObjectShared(null, _models);

                        if (interrupted()) {
                            interruptInt(classId);
                            interruptByte(id);
                            interruptInt(TOBJECT_MODEL);
                            return null;
                        }

                        _currentModel = (ObjectModel) shared.getOrRecreateTObject();
                    }
                }
            }
            case TOBJECT_CLASS_ID: {
                if (!canReadInteger()) {
                    interruptInt(classId);
                    interruptByte(id);
                    interruptInt(TOBJECT_CLASS_ID);
                    return null;
                }

                classId = readInteger();

                if (Debug.COMMUNICATIONS_LOG)
                    Helper.getInstance().getSB().append("class: " + classId);
            }
            case TOBJECT_GENERIC_ARGUMENTS: {
                if ((flags & TObjectWriter.FLAG_GENERIC_ARGUMENTS) != 0) {
                    genericParameters = readTypes();

                    if (interrupted()) {
                        interruptInt(classId);
                        interruptByte(id);
                        interruptInt(TOBJECT_GENERIC_ARGUMENTS);
                        return null;
                    }
                }

                break;
            }
            default:
                if (Debug.ENABLED)
                    Debug.fail();
        }

        UserTObject object = _currentSession.getOrCreateInstance(id, _currentModel, classId, genericParameters, bind, this);

        if (Debug.COMMUNICATIONS_LOG)
            log(TObjectWriter.getClassName(object) + Helper.getInstance().getSB().toString());

        pushNewTObject(object);
        return object.getSharedVersion_objectfabric();
    }

    protected UserTObject createInstance(ObjectModel model, Transaction trunk, int classId, TType[] genericParameters) {
        return model.createInstance(trunk, classId, genericParameters);
    }

    private static final int UID_CACHE = 0;

    private static final int UID_UID = 1;

    private static final int UID_CLASS_ID = 2;

    @SuppressWarnings({ "fallthrough", "null" })
    private final Version readTObjectWithUID(byte flags, Version[] cache) {
        if (Debug.ENABLED)
            Debug.assertion(flags != UnknownObjectSerializer.FLAGS_NULL);

        int step = TOBJECT_SESSION;
        byte[] uid = null;
        Version shared = null;

        if (interrupted()) {
            step = resumeInt();
            uid = (byte[]) resume();
            shared = (Version) resume();
        } else {
            if (Debug.COMMUNICATIONS_LOG) {
                Helper.getInstance().getSB2().setLength(0);
                Helper.getInstance().getSB2().append(" flags " + TObjectWriter.writeFlags(flags) + ", ");
            }
        }

        switch (step) {
            case UID_CACHE: {
                if ((flags & TObjectWriter.FLAG_CACHED) != 0) {
                    if (!canReadByte()) {
                        interrupt(shared);
                        interrupt(uid);
                        interruptInt(UID_CACHE);
                        return null;
                    }

                    int index = readByte(TObjectWriter.DEBUG_TAG_CACHED_INDEX) & 0xff;
                    return cache[index];
                }
            }
            case UID_UID: {
                uid = readBinary();

                if (interrupted()) {
                    interrupt(shared);
                    interrupt(uid);
                    interruptInt(UID_UID);
                    return null;
                }

                shared = TObject.getObjectWithUID(uid);

                if (Debug.COMMUNICATIONS_LOG)
                    Helper.getInstance().getSB2().append("uid: " + new UID(uid).toShortString() + ", ");
            }
            case UID_CLASS_ID: {
                if ((flags & TObjectWriter.FLAG_NEW) != 0) {
                    if (!canReadInteger()) {
                        interrupt(shared);
                        interrupt(uid);
                        interruptInt(UID_CLASS_ID);
                        return null;
                    }

                    int classId = readInteger();
                    Version previous = shared;
                    UserTObject object;

                    if (previous == null) {
                        if (classId == DefaultObjectModel.COM_OBJECTFABRIC_OBJECT_MODEL_CLASS_ID)
                            throw new RuntimeException(Strings.UNKNOWN_OBJECT_MODEL + " (UID: " + Arrays.toString(uid) + ")");

                        if (Debug.ENABLED)
                            Debug.assertion(classId == DefaultObjectModelBase.COM_OBJECTFABRIC_SESSION_CLASS_ID);

                        object = new Session(Site.getLocal().getTrunk(), null, null, uid, Record.UNKNOWN);
                        shared = object.getSharedVersion_objectfabric();
                        previous = TObject.putObjectWithUIDIfAbsent(uid, shared);

                        if (previous != null) {
                            shared.getReference().clear();
                            shared = previous;
                            object = shared.getOrRecreateTObject();
                        }
                    } else
                        object = shared.getOrRecreateTObject();

                    pushNewTObject(object);

                    if (Debug.COMMUNICATIONS_LOG)
                        Helper.getInstance().getSB2().append("class: " + shared.getClassId());
                }

                cache[uid[0] & 0xff] = shared;
                break;
            }
            default:
                if (Debug.ENABLED)
                    Debug.fail();
        }

        if (Debug.COMMUNICATIONS_LOG)
            log(TObjectWriter.getClassName(shared.getOrRecreateTObject()) + Helper.getInstance().getSB2().toString());

        return shared;
    }

    @SuppressWarnings("null")
    private final TType[] readTypes() {
        int index = -1;
        TType[] types = null;

        if (interrupted()) {
            index = resumeInt();
            types = (TType[]) resume();
        }

        if (index < 0) {
            if (!canReadByte()) {
                interrupt(types);
                interruptInt(index);
                return null;
            }

            index = readByte();

            if (index == 0)
                return null;

            types = new TType[index--];
        }

        for (; index >= 0; index--) {
            types[index] = readType();

            if (interrupted()) {
                interrupt(types);
                interruptInt(index);
                return null;
            }
        }

        return types;
    }

    private static final int TYPE_MODEL = 0;

    private static final int TYPE_CLASS_ID = 1;

    private static final int TYPE_CHILDREN = 2;

    @SuppressWarnings("fallthrough")
    private final TType readType() {
        int step = TYPE_MODEL;
        ObjectModel model = null;
        int classId = 0;
        TType[] types = null;

        if (interrupted()) {
            step = resumeInt();
            model = (ObjectModel) resume();
            classId = resumeInt();
        }

        switch (step) {
            case TYPE_MODEL: {
                model = (ObjectModel) readTObject();

                if (interrupted()) {
                    interruptInt(classId);
                    interrupt(model);
                    interruptInt(TYPE_MODEL);
                    return null;
                }
            }
            case TYPE_CLASS_ID: {
                if (!canReadInteger()) {
                    interruptInt(classId);
                    interrupt(model);
                    interruptInt(TYPE_CLASS_ID);
                    return null;
                }

                classId = readInteger();
            }
            case TYPE_CHILDREN: {
                types = readTypes();

                if (interrupted()) {
                    interruptInt(classId);
                    interrupt(model);
                    interruptInt(TYPE_CHILDREN);
                    return null;
                }
            }
        }

        return PlatformAdapter.createTType(model, classId, types);
    }

    // Debug

    final int getDebugCommandCounter() {
        if (!Debug.COMMUNICATIONS)
            throw new IllegalStateException();

        return _debugCommandCounter;
    }

    final int getAndIncrementDebugCommandCounter() {
        if (!Debug.COMMUNICATIONS)
            throw new IllegalStateException();

        return _debugCommandCounter++;
    }

    final void log(String event) {
        if (!Debug.ENABLED)
            throw new AssertionError();

        long counter = ThreadAssert.getOrCreateCurrent().getReaderDebugCounter(this);
        TObjectWriter.log(event, PlatformClass.getSimpleName(getClass()), "IN ", counter, _debugCommandCounter, _currentSession);
    }

    void assertIdle() {
        Debug.assertion(_newTObjects.size() == 0);
    }
}
