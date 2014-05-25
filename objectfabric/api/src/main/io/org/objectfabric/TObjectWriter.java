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

import org.objectfabric.Range.Id;
import org.objectfabric.ThreadAssert.SingleThreaded;

@SuppressWarnings("rawtypes")
@SingleThreaded
abstract class TObjectWriter extends ImmutableWriter {

    private final Watcher _watcher;

    private final IndexedCache _objects = new IndexedCache();

    private final IndexedCache _ranges = new IndexedCache();

    private final IndexedCache _models = new IndexedCache();

    private Range _currentRange;

    private ObjectModel _currentModel;

    private TObject[] _refs;

    protected TObjectWriter(Watcher watcher, List<Object> interruptionStack) {
        super(interruptionStack);

        _watcher = watcher;
    }

    final Watcher watcher() {
        return _watcher;
    }

    @Override
    void reset() {
        super.reset();

        _objects.reset();
        _ranges.reset();
        _models.reset();

        _currentRange = null;
        _currentModel = null;

        if (Debug.ENABLED)
            Debug.assertAlways(_refs == null);
    }

    //

    private static final int TOBJECT_FLAGS = 0;

    private static final int TOBJECT_ID = 1;

    private static final int TOBJECT_RANGE_PEER = 2;

    private static final int TOBJECT_RANGE_ID = 3;

    private static final int TOBJECT_MODEL = 4;

    private static final int TOBJECT_CLASS_ID = 5;

    private static final int TOBJECT_GENERIC_ARGUMENTS = 6;

    @SuppressWarnings({ "fallthrough", "null" })
    public final void writeTObject(TObject object) {
        int step = TOBJECT_FLAGS;
        byte flags;

        TType[] genericParameters = null;

        if (object != null)
            genericParameters = object.genericParameters();

        if (interrupted()) {
            step = resumeInt();
            flags = resumeByte();
        } else {
            if (object == null)
                flags = UnknownObjectSerializer.NULL;
            else {
                flags = (byte) Writer.VALUE_IS_TOBJECT;

                if (object.range() == null)
                    _watcher.clock().assignId(object);

                if (_objects.contains(object, object.id()))
                    flags |= Writer.TOBJECT_CACHED;
                else {
                    _watcher.onWriting(object);

                    _objects.add(object, object.id());

                    if (object.range() != _currentRange) {
                        _currentRange = object.range();
                        flags |= Writer.TOBJECT_RANGE_CHANGE;
                        int index = getCacheIndex(object.range().id()) & 0xff;

                        if (_ranges.contains(object.range(), index))
                            flags |= Writer.TOBJECT_RANGE_CACHED;
                        else
                            _ranges.add(object.range(), index);
                    }

                    ObjectModel model = object.objectModel_();

                    if (model == Platform.get().defaultObjectModel()) {
                        // Cached but no change means default
                        // TODO do same for range, e.g. meaning current URI
                        flags |= Writer.TOBJECT_MODEL_CACHED;
                    } else if (model != _currentModel) {
                        _currentModel = model;
                        flags |= Writer.TOBJECT_MODEL_CHANGE;
                        int index = model.uid_()[0] & 0xff;

                        if (_models.contains(model, index))
                            flags |= Writer.TOBJECT_MODEL_CACHED;
                        else
                            _models.add(model, index);
                    }

                    if (genericParameters != null)
                        flags |= Writer.TOBJECT_GENERIC_ARGUMENTS;
                }
            }

            if (Debug.COMMUNICATIONS_LOG) {
                Helper.instance().getSB().setLength(0);
                String s = object != null ? getClassName(Platform.get().getClass(object)) + " " : "null ";
                Helper.instance().getSB().append(s);
            }
        }

        switch (step) {
            case TOBJECT_FLAGS: {
                if (!canWriteByte()) {
                    interruptByte(flags);
                    interruptInt(TOBJECT_FLAGS);
                    return;
                }

                if (Debug.COMMUNICATIONS_LOG)
                    Helper.instance().getSB().append("flags: " + writeFlags(flags) + ", ");

                if (Debug.ENABLED)
                    Debug.assertion((flags & Writer.VALUE_IS_TOBJECT) != 0 || flags == UnknownObjectSerializer.NULL);

                writeByte(flags, Writer.DEBUG_TAG_CODE);

                if (object == null)
                    break;
            }
            case TOBJECT_ID: {
                if (!canWriteByte()) {
                    interruptByte(flags);
                    interruptInt(TOBJECT_ID);
                    return;
                }

                if (Debug.COMMUNICATIONS_LOG)
                    Helper.instance().getSB().append("id: " + object.id() + ", ");

                writeByte((byte) object.id());

                if ((flags & Writer.TOBJECT_CACHED) != 0)
                    break;
            }
            case TOBJECT_RANGE_PEER: {
                if ((flags & Writer.TOBJECT_RANGE_CHANGE) != 0) {
                    if ((flags & Writer.TOBJECT_RANGE_CACHED) != 0) {
                        if (!canWriteByte()) {
                            interruptByte(flags);
                            interruptInt(TOBJECT_RANGE_PEER);
                            return;
                        }

                        writeByte((byte) getCacheIndex(object.range().id()));
                    } else {
                        writeBinary(_currentRange.id().Peer.uid());

                        if (interrupted()) {
                            interruptByte(flags);
                            interruptInt(TOBJECT_RANGE_PEER);
                            return;
                        }
                    }
                }
            }
            case TOBJECT_RANGE_ID: {
                if ((flags & Writer.TOBJECT_RANGE_CHANGE) != 0) {
                    if ((flags & Writer.TOBJECT_RANGE_CACHED) == 0) {
                        if (!canWriteLong()) {
                            interruptByte(flags);
                            interruptInt(TOBJECT_RANGE_ID);
                            return;
                        }

                        writeLong(object.range().id().Value);

                        if (Debug.COMMUNICATIONS_LOG)
                            Helper.instance().getSB().append("range: " + _currentRange.id().Value + ", ");
                    }
                }
            }
            case TOBJECT_MODEL: {
                if ((flags & Writer.TOBJECT_MODEL_CHANGE) != 0) {
                    if ((flags & Writer.TOBJECT_MODEL_CACHED) != 0) {
                        if (!canWriteByte()) {
                            interruptByte(flags);
                            interruptInt(TOBJECT_MODEL);
                            return;
                        }

                        writeByte(_currentModel.uid_()[0]);
                    } else {
                        writeBinary(_currentModel.uid_());

                        if (interrupted()) {
                            interruptByte(flags);
                            interruptInt(TOBJECT_MODEL);
                            return;
                        }

                        if (Debug.COMMUNICATIONS_LOG)
                            Helper.instance().getSB().append("model: " + new UID(_currentModel.uid_()) + ", ");
                    }
                }
            }
            case TOBJECT_CLASS_ID: {
                if (!canWriteInteger()) {
                    interruptByte(flags);
                    interruptInt(TOBJECT_CLASS_ID);
                    return;
                }

                if (Debug.COMMUNICATIONS_LOG)
                    Helper.instance().getSB().append("class: " + object.classId_());

                writeInteger(object.classId_());
            }
            case TOBJECT_GENERIC_ARGUMENTS: {
                if ((flags & Writer.TOBJECT_GENERIC_ARGUMENTS) != 0) {
                    writeTypes(genericParameters);

                    if (interrupted()) {
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

        if (Debug.COMMUNICATIONS_LOG)
            log(Helper.instance().getSB().toString());
    }

    static int getCacheIndex(Id id) {
        return id.Peer.uid()[0] ^ (int) id.Value;
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

    private static final int TYPE_MODE = 0;

    private static final int TYPE_MODEL = 1;

    private static final int TYPE_CLASS_ID = 2;

    private static final int TYPE_CHILDREN = 3;

    @SuppressWarnings("fallthrough")
    private final void writeType(TType type) {
        int step = TYPE_MODE;

        if (interrupted())
            step = resumeInt();

        byte mode;

        if (type.getObjectModel() == null)
            mode = 0;
        else if (type.getObjectModel() == Platform.get().defaultObjectModel())
            mode = 1;
        else
            mode = 2;

        switch (step) {
            case TYPE_MODE: {
                if (!canWriteByte()) {
                    interruptInt(TYPE_MODE);
                    return;
                }

                writeByte(mode);
            }
            case TYPE_MODEL: {
                if (mode == 2) {
                    byte[] uid = null;

                    if (type.getObjectModel() != null)
                        uid = type.getObjectModel().uid_();

                    writeBinary(uid);

                    if (interrupted()) {
                        interruptInt(TYPE_MODEL);
                        return;
                    }
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

    // Debug

    static final String writeFlags(byte flags) {
        StringBuilder sb = new StringBuilder();

        if (flags == UnknownObjectSerializer.NULL)
            sb.append("NULL, ");
        else {
            if (Debug.ENABLED)
                Debug.assertion((flags & Writer.VALUE_IS_TOBJECT) != 0);

            if ((flags & Writer.TOBJECT_CACHED) != 0)
                sb.append("TOBJECT_CACHED, ");

            if ((flags & Writer.TOBJECT_RANGE_CHANGE) != 0)
                sb.append("RANGE_CHANGE, ");

            if ((flags & Writer.TOBJECT_RANGE_CACHED) != 0)
                sb.append("RANGE_CACHED, ");

            if ((flags & Writer.TOBJECT_MODEL_CHANGE) != 0)
                sb.append("MODEL_CHANGE, ");

            if ((flags & Writer.TOBJECT_MODEL_CACHED) != 0)
                sb.append("MODEL_CACHED, ");

            if ((flags & Writer.TOBJECT_GENERIC_ARGUMENTS) != 0)
                sb.append("GENERIC_ARGUMENTS, ");
        }

        return sb.length() != 0 ? sb.substring(0, sb.length() - 2) : "";
    }

    final void log(String event) {
        if (!Debug.ENABLED)
            throw new AssertionError();

        long counter = ThreadAssert.getOrCreateCurrent().getWriterDebugCounter(this);
        String c = Platform.get().simpleClassName(this);
        log(event, c, "OUT", counter, _currentRange.id());
    }

    static String getClassName(Object object) {
        if (!Debug.ENABLED)
            throw new IllegalStateException();

        java.lang.Class c = Platform.get().getClass(object);

        if (Platform.get().value() != Platform.GWT) {
            java.lang.Class enclosing = Platform.get().enclosingClass(c);

            if (enclosing != null)
                return Platform.get().simpleName(enclosing) + "$" + Platform.get().simpleName(c);
        }

        return Platform.get().simpleName(c);
    }

    final static void log(String event, String name, String direction, long counter, Id id) {
        if (!Debug.ENABLED)
            throw new IllegalStateException();

        String message = Utils.padRight(name + ", ", 25) + direction;
        message += Utils.padLeft("" + counter + ", ", 12);
        message += Utils.padRight(event + ", ", 75);
        message += Utils.padRight("Range " + id, 14);
        Log.write(message);
    }

    @Override
    void addThreadContextObjects(List<Object> list) {
        super.addThreadContextObjects(list);

        list.add(_objects);
        list.add(_ranges);
        list.add(_models);
    }
}
