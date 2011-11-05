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

import of4gwt.TObject.Descriptor;
import of4gwt.TObject.DescriptorForUID;
import of4gwt.TObject.Version;
import of4gwt.misc.Debug;
import of4gwt.misc.List;
import of4gwt.misc.Log;
import of4gwt.misc.PlatformAdapter;
import of4gwt.misc.PlatformClass;
import of4gwt.misc.ThreadAssert;
import of4gwt.misc.ThreadAssert.SingleThreaded;
import of4gwt.misc.UID;
import of4gwt.misc.Utils;

@SingleThreaded
abstract class TObjectWriter extends ImmutableWriter {

    /*
     * Following data is a transactional object.
     */
    static final byte FLAG_TOBJECT = (byte) (1 << 7);

    /*
     * For transactional object use those flags.
     */

    static final byte FLAG_UID = 1 << 6;

    static final byte FLAG_NEW = 1 << 5;

    // For descriptors

    static final byte FLAG_SESSION_CHANGE = 1 << 4;

    static final byte FLAG_SESSION_CACHED = 1 << 3;

    static final byte FLAG_MODEL_CHANGE = 1 << 2;

    static final byte FLAG_MODEL_CACHED = 1 << 1;

    static final byte FLAG_GENERIC_ARGUMENTS = 1 << 0;

    // For UIDs

    static final byte FLAG_CACHED = 1 << 4;

    /*
     * If not a transactional object, then this tells the reader to stop reading.
     * Following data can be e.g. routing information used by transports so reader must
     * give back control.
     */
    static final byte FLAG_EOF = 1 << 6;

    /*
     * If not EOF, data is an ImmutableClass.
     */
    static final byte FLAG_IMMUTABLE = 1 << 5;

    //

    private final UIDCache _unknown;

    private final UIDCache _sessions;

    private final UIDCache _models;

    // Context shared with remote Reader

    private Version _currentSession;

    private Version _currentModel;

    // Debug

    static final int DEBUG_TAG_CODE = 1111111111;

    static final int DEBUG_TAG_CACHED_INDEX = 222222222;

    // TODO: remove, debug counters in immutable*
    private int _debugCommandCounter;

    //

    protected TObjectWriter(boolean allowResets) {
        _unknown = new UIDCache(allowResets);
        _sessions = new UIDCache(allowResets);
        _models = new UIDCache(allowResets);

        if (Debug.ENABLED) {
            // Leave one more free at the end to have an extension mechanism for more
            Debug.assertion(ImmutableClass.ALL.size() < FLAG_IMMUTABLE - 1 - 1);
        }
    }

    @Override
    void reset() {
        super.reset();

        _currentSession = null;
        _currentModel = null;

        _unknown.reset();
        _sessions.reset();
        _models.reset();

        if (Debug.COMMUNICATIONS) {
            _debugCommandCounter = 0;
            assertIdle();
        }
    }

    //

    public final void writeTObject(TObject object) {
        writeTObject(object != null ? object.getSharedVersion_objectfabric() : null);
    }

    final void writeTObject(Version shared) {
        if (shared == null)
            writeTObjectWithDescriptor(null);
        else {
            byte[] uid = shared.getUID();

            if (uid == null)
                writeTObjectWithDescriptor(shared);
            else
                writeTObjectWithUID(shared, uid, _unknown);
        }
    }

    private static final int TOBJECT_FLAGS = 0;

    private static final int TOBJECT_DEBUG_COUNTER = 1;

    private static final int TOBJECT_SESSION = 2;

    private static final int TOBJECT_ID = 3;

    private static final int TOBJECT_MODEL = 4;

    private static final int TOBJECT_CLASS_ID = 5;

    private static final int TOBJECT_GENERIC_ARGUMENTS = 6;

    @SuppressWarnings({ "fallthrough", "null" })
    private final void writeTObjectWithDescriptor(Version shared) {
        int step = TOBJECT_FLAGS;
        byte flags, id;
        byte[] sessionUID = null, modelUID = null;
        TType[] genericParameters = null;

        if (shared != null)
            genericParameters = shared.getGenericParameters();

        if (interrupted()) {
            step = resumeInt();
            flags = resumeByte();
            id = resumeByte();
            sessionUID = (byte[]) resume();
            modelUID = (byte[]) resume();
        } else {
            if (shared == null) {
                flags = UnknownObjectSerializer.FLAGS_NULL;
                id = 0;
            } else {
                flags = FLAG_TOBJECT;
                Descriptor descriptor = shared.getOrCreateDescriptor();
                id = descriptor.getId();
                Version session = descriptor.getSession().getSharedVersion_objectfabric();

                if (session != _currentSession) {
                    _currentSession = session;
                    flags |= FLAG_SESSION_CHANGE;
                    sessionUID = session.getUID();
                    int index = sessionUID[0] & 0xff;

                    if (_sessions.contains(session, index))
                        flags |= FLAG_SESSION_CACHED;
                    else
                        _sessions.add(session, index);
                }

                if (!isKnown(shared)) {
                    flags |= FLAG_NEW;
                    Version model = shared.getObjectModel().getSharedVersion_objectfabric();

                    if (model != _currentModel) {
                        _currentModel = model;
                        flags |= FLAG_MODEL_CHANGE;
                        modelUID = model.getUID();
                        int index = modelUID[0] & 0xff;

                        if (_models.contains(model, index))
                            flags |= FLAG_MODEL_CACHED;
                        else
                            _models.add(model, index);
                    }

                    if (genericParameters != null)
                        flags |= FLAG_GENERIC_ARGUMENTS;
                }
            }

            if (Debug.COMMUNICATIONS_LOG) {
                Helper.getInstance().getSB().setLength(0);
                Helper.getInstance().getSB().append(shared != null ? getClassName(shared.getOrRecreateTObject()) + " " : "null ");
            }
        }

        switch (step) {
            case TOBJECT_FLAGS: {
                if (!canWriteByte()) {
                    interrupt(modelUID);
                    interrupt(sessionUID);
                    interruptByte(id);
                    interruptByte(flags);
                    interruptInt(TOBJECT_FLAGS);
                    return;
                }

                if (Debug.COMMUNICATIONS_LOG)
                    Helper.getInstance().getSB().append("flags: " + writeFlags(flags) + ", ");

                if (Debug.ENABLED)
                    Debug.assertion((flags & FLAG_TOBJECT) != 0 || flags == UnknownObjectSerializer.FLAGS_NULL);

                writeByte(flags, DEBUG_TAG_CODE);
            }
            case TOBJECT_DEBUG_COUNTER: {
                if (Debug.COMMUNICATIONS) {
                    if (!canWriteInteger()) {
                        interrupt(modelUID);
                        interrupt(sessionUID);
                        interruptByte(id);
                        interruptByte(flags);
                        interruptInt(TOBJECT_DEBUG_COUNTER);
                        return;
                    }

                    writeInteger(_debugCommandCounter++);
                }

                if (shared == null)
                    break;
            }
            case TOBJECT_SESSION: {
                if ((flags & FLAG_SESSION_CHANGE) != 0) {
                    if ((flags & FLAG_SESSION_CACHED) != 0) {
                        if (!canWriteByte()) {
                            interrupt(modelUID);
                            interrupt(sessionUID);
                            interruptByte(id);
                            interruptByte(flags);
                            interruptInt(TOBJECT_SESSION);
                            return;
                        }

                        writeByte(sessionUID[0], DEBUG_TAG_CACHED_INDEX);

                        if (Stats.ENABLED)
                            Stats.getInstance().CachedUID.incrementAndGet();
                    } else {
                        writeTObjectWithUID(_currentSession, sessionUID, null);

                        if (interrupted()) {
                            interrupt(modelUID);
                            interrupt(sessionUID);
                            interruptByte(id);
                            interruptByte(flags);
                            interruptInt(TOBJECT_SESSION);
                            return;
                        }
                    }
                }
            }
            case TOBJECT_ID: {
                if (!canWriteByte()) {
                    interrupt(modelUID);
                    interrupt(sessionUID);
                    interruptByte(id);
                    interruptByte(flags);
                    interruptInt(TOBJECT_ID);
                    return;
                }

                if (Debug.COMMUNICATIONS_LOG)
                    Helper.getInstance().getSB().append("id: " + id + ", ");

                writeByte(id);

                if ((flags & FLAG_NEW) == 0)
                    break;
            }
            case TOBJECT_MODEL: {
                if ((flags & FLAG_MODEL_CHANGE) != 0) {
                    if ((flags & FLAG_MODEL_CACHED) != 0) {
                        if (!canWriteByte()) {
                            interrupt(modelUID);
                            interrupt(sessionUID);
                            interruptByte(id);
                            interruptByte(flags);
                            interruptInt(TOBJECT_MODEL);
                            return;
                        }

                        writeByte(modelUID[0], DEBUG_TAG_CACHED_INDEX);

                        if (Stats.ENABLED)
                            Stats.getInstance().CachedUID.incrementAndGet();
                    } else {
                        writeTObjectWithUID(_currentModel, modelUID, null);

                        if (interrupted()) {
                            interrupt(modelUID);
                            interrupt(sessionUID);
                            interruptByte(id);
                            interruptByte(flags);
                            interruptInt(TOBJECT_MODEL);
                            return;
                        }
                    }
                }
            }
            case TOBJECT_CLASS_ID: {
                if (!canWriteInteger()) {
                    interrupt(modelUID);
                    interrupt(sessionUID);
                    interruptByte(id);
                    interruptByte(flags);
                    interruptInt(TOBJECT_CLASS_ID);
                    return;
                }

                if (Debug.COMMUNICATIONS_LOG)
                    Helper.getInstance().getSB().append("class: " + shared.getClassId());

                writeInteger(shared.getClassId());
            }
            case TOBJECT_GENERIC_ARGUMENTS: {
                if ((flags & FLAG_GENERIC_ARGUMENTS) != 0) {
                    writeTypes(genericParameters);

                    if (interrupted()) {
                        interrupt(modelUID);
                        interrupt(sessionUID);
                        interruptByte(id);
                        interruptByte(flags);
                        interruptInt(TOBJECT_GENERIC_ARGUMENTS);
                        return;
                    }
                }

                break;
            }
            default:
                if (Debug.ENABLED)
                    Debug.fail();
        }

        if ((flags & FLAG_NEW) != 0 && shared != null)
            setCreated(shared);

        if (Debug.COMMUNICATIONS_LOG)
            log(Helper.getInstance().getSB().toString());
    }

    private static final int UID_FLAGS = 0;

    private static final int UID_DEBUG_COUNTER = 1;

    private static final int UID_CACHED = 2;

    private static final int UID_UID = 3;

    private static final int UID_CLASS_ID = 4;

    @SuppressWarnings("fallthrough")
    private final void writeTObjectWithUID(Version shared, byte[] uid, UIDCache cache) {
        if (Debug.ENABLED) {
            Debug.assertion(shared.isShared());
            Debug.assertion(shared.getUnion() instanceof DescriptorForUID);
            // Debug.assertion(TObject.getObjectWithUID(uid) != null);
        }

        int step = UID_FLAGS;
        byte flags;
        byte[] trunkUID = null;

        if (interrupted()) {
            step = resumeInt();
            flags = resumeByte();
            trunkUID = (byte[]) resume();
        } else {
            flags = FLAG_TOBJECT | FLAG_UID;
            int index = uid[0] & 0xff;

            if (cache != null && cache.contains(shared, index))
                flags |= FLAG_CACHED;
            else {
                if (cache != null)
                    cache.add(shared, index);

                if (!isKnown(shared))
                    flags |= FLAG_NEW;
            }

            if (Debug.COMMUNICATIONS_LOG) {
                Helper.getInstance().getSB2().setLength(0);
                Helper.getInstance().getSB2().append(getClassName(shared.getOrRecreateTObject()) + " ");
            }
        }

        switch (step) {
            case UID_FLAGS: {
                if (!canWriteByte()) {
                    interrupt(trunkUID);
                    interruptByte(flags);
                    interruptInt(UID_FLAGS);
                    return;
                }

                if (Debug.COMMUNICATIONS_LOG)
                    Helper.getInstance().getSB2().append("flags: " + writeFlags(flags) + ", ");

                if (Debug.ENABLED) {
                    Debug.assertion(flags < 0);
                    Debug.assertion((flags & FLAG_TOBJECT) != 0 || flags == UnknownObjectSerializer.FLAGS_NULL);
                }

                writeByte(flags, DEBUG_TAG_CODE);
            }
            case UID_DEBUG_COUNTER: {
                if (Debug.COMMUNICATIONS) {
                    if (!canWriteInteger()) {
                        interrupt(trunkUID);
                        interruptByte(flags);
                        interruptInt(UID_DEBUG_COUNTER);
                        return;
                    }

                    writeInteger(_debugCommandCounter++);
                }
            }
            case UID_CACHED: {
                if ((flags & FLAG_CACHED) != 0) {
                    if (!canWriteByte()) {
                        interrupt(trunkUID);
                        interruptByte(flags);
                        interruptInt(UID_CACHED);
                        return;
                    }

                    writeByte(uid[0], DEBUG_TAG_CACHED_INDEX);

                    if (Stats.ENABLED)
                        Stats.getInstance().CachedUID.incrementAndGet();

                    break;
                }
            }
            case UID_UID: {
                writeBinary(uid);

                if (interrupted()) {
                    interrupt(trunkUID);
                    interruptByte(flags);
                    interruptInt(UID_UID);
                    return;
                }

                if (Stats.ENABLED)
                    Stats.getInstance().WrittenUID.incrementAndGet();

                if (Debug.COMMUNICATIONS_LOG)
                    Helper.getInstance().getSB2().append("uid: " + new UID(uid).toShortString() + ", ");

                if ((flags & FLAG_NEW) == 0)
                    break;
            }
            case UID_CLASS_ID: {
                if (!canWriteInteger()) {
                    interrupt(trunkUID);
                    interruptByte(flags);
                    interruptInt(UID_CLASS_ID);
                    return;
                }

                writeInteger(shared.getClassId());

                if (Debug.COMMUNICATIONS_LOG)
                    Helper.getInstance().getSB2().append("class: " + shared.getClassId());

                break;
            }
            default:
                if (Debug.ENABLED)
                    Debug.fail();
        }

        if ((flags & FLAG_NEW) != 0)
            setCreated(shared);

        if (Debug.COMMUNICATIONS_LOG)
            log(Helper.getInstance().getSB2().toString());
    }

    private final void writeTypes(TType[] types) {
        int index = -1;

        if (interrupted())
            index = resumeInt();

        if (index < 0) {
            if (!canWriteByte()) {
                interruptInt(index);
                return;
            }

            if (types == null) {
                writeByte((byte) 0);
                return;
            }

            if (Debug.ENABLED)
                Debug.assertion(types.length > 0);

            writeByte((byte) types.length);
            index = types.length - 1;
        }

        for (; index >= 0; index--) {
            writeType(types[index]);

            if (interrupted()) {
                interruptInt(index);
                return;
            }
        }
    }

    private static final int TYPE_MODEL = 0;

    private static final int TYPE_CLASS_ID = 1;

    private static final int TYPE_CHILDREN = 2;

    @SuppressWarnings("fallthrough")
    private final void writeType(TType type) {
        int step = TYPE_MODEL;

        if (interrupted())
            step = resumeInt();

        switch (step) {
            case TYPE_MODEL: {
                writeTObject(type.getObjectModel());

                if (interrupted()) {
                    interruptInt(TYPE_MODEL);
                    return;
                }
            }
            case TYPE_CLASS_ID: {
                if (!canWriteInteger()) {
                    interruptInt(TYPE_CLASS_ID);
                    return;
                }

                writeInteger(type.getClassId());
            }
            case TYPE_CHILDREN: {
                writeTypes(type.getGenericParameters());

                if (interrupted()) {
                    interruptInt(TYPE_CHILDREN);
                    return;
                }
            }
        }
    }

    //

    abstract boolean isKnown(Version shared);

    abstract void setCreated(Version shared);

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

    static boolean isExitCode(byte code) {
        return (code & FLAG_TOBJECT) == 0 && (code & FLAG_EOF) != 0;
    }

    static final String writeFlags(byte flags) {
        StringBuilder sb = new StringBuilder();

        if (flags == UnknownObjectSerializer.FLAGS_NULL)
            sb.append("NULL, ");
        else {
            if (Debug.ENABLED)
                Debug.assertion((flags & FLAG_TOBJECT) != 0);

            if ((flags & FLAG_NEW) != 0)
                sb.append("NEW, ");

            if ((flags & FLAG_UID) != 0) {
                sb.append("UID, ");

                if ((flags & FLAG_CACHED) != 0)
                    sb.append("CACHED, ");
            } else {
                if ((flags & FLAG_SESSION_CHANGE) != 0)
                    sb.append("SESSION_CHANGE, ");

                if ((flags & FLAG_SESSION_CACHED) != 0)
                    sb.append("SESSION_CACHED, ");

                if ((flags & FLAG_MODEL_CHANGE) != 0)
                    sb.append("MODEL_CHANGE, ");

                if ((flags & FLAG_MODEL_CACHED) != 0)
                    sb.append("MODEL_CACHED, ");

                if ((flags & FLAG_GENERIC_ARGUMENTS) != 0)
                    sb.append("GENERIC_ARGUMENTS, ");
            }
        }

        return sb.length() != 0 ? sb.substring(0, sb.length() - 2) : "";
    }

    final void log(String event) {
        if (!Debug.ENABLED)
            throw new AssertionError();

        long counter = ThreadAssert.getOrCreateCurrent().getWriterDebugCounter(this);
        log(event, PlatformClass.getSimpleName(getClass()), "OUT", counter, _debugCommandCounter, _currentSession);
    }

    static String getClassName(Object object) {
        if (!Debug.ENABLED)
            throw new IllegalStateException();

        java.lang.Class c = PlatformClass.getClass(object);

        if (PlatformAdapter.PLATFORM != CompileTimeSettings.PLATFORM_GWT) {
            java.lang.Class enclosing = PlatformClass.getEnclosingClass(c);

            if (enclosing != null)
                return PlatformClass.getSimpleName(enclosing) + "$" + PlatformClass.getSimpleName(c);
        }

        return PlatformClass.getSimpleName(c);
    }

    final static void log(String event, String name, String direction, long counter, long commandCounter, TObject session) {
        if (!Debug.ENABLED)
            throw new IllegalStateException();

        String message = Utils.padRight(name + ", ", 25);
        message += direction + Utils.padLeft("" + commandCounter + ", ", 8);
        message += Utils.padLeft("" + counter + ", ", 12);
        message += Utils.padRight(event + ", ", 75);
        message += Utils.padRight("" + session, 14);
        Log.write(message);
    }

    @Override
    void addThreadContextObjects(List<Object> list) {
        super.addThreadContextObjects(list);

        list.add(_unknown);
        list.add(_sessions);
        list.add(_models);
    }

    void assertIdle() {
    }
}
