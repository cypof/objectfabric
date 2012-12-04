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

@SingleThreaded
abstract class TObjectReader extends ImmutableReader {

    private final List<Resource> _resources = new List<Resource>();

    // TODO clear after each read for GC?
    private TObject[][] _objects = new TObject[0xff + 1][];

    private Id[] _ranges = new Id[0xff + 1];

    private ObjectModel[] _models = new ObjectModel[0xff + 1];

    private Id _range;

    private ObjectModel _model;

    private TObject[][] _refs;

    private int _refCount;

    TObjectReader(List<Object> interruptionStack) {
        super(interruptionStack);
    }

    @Override
    void clean() {
        super.clean();

        _refs = null;
        _refCount = 0;
    }

    final List<Resource> resources() {
        return _resources;
    }

    final void recordRefs() {
        if (Debug.ENABLED)
            Debug.assertion(_refs == null && _refCount == 0);

        _refs = new TObject[_resources.size()][];
    }

    final TObject[][] takeRefs() {
        if (Debug.ENABLED)
            Debug.assertion(_refs.length == _resources.size());

        TObject[][] result = _refs;
        _refs = null;
        _refCount = 0;
        return result;
    }

    private static final int FLAGS = 0;

    private static final int VALUE = 1;

    @SuppressWarnings("fallthrough")
    public final TObject[] readTObject() {
        int step = FLAGS;
        byte flags = 0;

        if (interrupted()) {
            step = resumeInt();
            flags = resumeByte();
        }

        TObject[] objects = null;

        switch (step) {
            case FLAGS: {
                if (!canReadByte()) {
                    interruptByte(flags);
                    interruptInt(FLAGS);
                    return null;
                }

                flags = readByte(Writer.DEBUG_TAG_CODE);
            }
            case VALUE: {
                objects = readTObject(flags);

                if (interrupted()) {
                    if (Debug.ENABLED)
                        Debug.assertion(objects == null);

                    interruptByte(flags);
                    interruptInt(VALUE);
                    return null;
                }

                break;
            }
            default:
                throw new IllegalStateException();
        }

        return objects;
    }

    private static final int TOBJECT_ID = 0;

    private static final int TOBJECT_RANGE_PEER = 1;

    private static final int TOBJECT_RANGE_ID = 2;

    private static final int TOBJECT_MODEL = 3;

    private static final int TOBJECT_CLASS_ID = 4;

    private static final int TOBJECT_GENERIC_ARGUMENTS = 5;

    @SuppressWarnings("fallthrough")
    final TObject[] readTObject(byte flags) {
        TObject[] objects = readTObjectImpl(flags);

        if (objects != null && _refs != null) {
            for (int uri = 0; uri < _refs.length; uri++) {
                if (_refs[uri] == null)
                    _refs[uri] = new TObject[2];
                else if (_refCount == _refs[uri].length) {
                    TObject[] temp = _refs[uri];
                    _refs[uri] = new TObject[_refs.length << OpenMap.TIMES_TWO_SHIFT];
                    Platform.arraycopy(temp, 0, _refs[uri], 0, _refCount);
                }

                _refs[uri][_refCount] = objects[uri];
            }

            _refCount++;
        }

        return objects;
    }

    private final TObject[] readTObjectImpl(byte flags) {
        if (flags == UnknownObjectSerializer.NULL) {
            if (Debug.COMMUNICATIONS_LOG)
                log("null flags " + TObjectWriter.writeFlags(flags));

            return null;
        }

        int step = TOBJECT_ID;
        int id = 0;
        byte[] rangePeer = null;
        int classId = 0;
        ObjectModel model = null;
        TType[] genericParameters = null;

        if (interrupted()) {
            step = resumeInt();
            id = resumeInt();
            rangePeer = (byte[]) resume();
            classId = resumeInt();
            model = (ObjectModel) resume();
        } else {
            if (Debug.COMMUNICATIONS_LOG) {
                Helper.instance().getSB().setLength(0);
                Helper.instance().getSB().append(" flags " + TObjectWriter.writeFlags(flags) + ", ");
            }
        }

        switch (step) {
            case TOBJECT_ID: {
                if (!canReadByte()) {
                    interrupt(model);
                    interruptInt(classId);
                    interrupt(rangePeer);
                    interruptInt(id);
                    interruptInt(TOBJECT_ID);
                    return null;
                }

                id = readByte() & 0xff;

                if (Debug.COMMUNICATIONS_LOG)
                    Helper.instance().getSB().append("id: " + id + ", ");

                if ((flags & Writer.TOBJECT_CACHED) != 0) {
                    TObject[] objects = _objects[id];

                    if (Debug.COMMUNICATIONS_LOG)
                        log(TObjectWriter.getClassName(objects[0]) + Helper.instance().getSB().toString());

                    return objects;
                }
            }
            case TOBJECT_RANGE_PEER: {
                if ((flags & Writer.TOBJECT_RANGE_CHANGE) != 0) {
                    if ((flags & Writer.TOBJECT_RANGE_CACHED) != 0) {
                        if (!canReadByte()) {
                            interrupt(model);
                            interruptInt(classId);
                            interrupt(rangePeer);
                            interruptInt(id);
                            interruptInt(TOBJECT_RANGE_PEER);
                            return null;
                        }

                        int index = readByte() & 0xff;
                        _range = _ranges[index];
                    } else {
                        rangePeer = readBinary();

                        if (interrupted()) {
                            interrupt(model);
                            interruptInt(classId);
                            interrupt(rangePeer);
                            interruptInt(id);
                            interruptInt(TOBJECT_RANGE_PEER);
                            return null;
                        }
                    }
                }
            }
            case TOBJECT_RANGE_ID: {
                if ((flags & Writer.TOBJECT_RANGE_CHANGE) != 0) {
                    if ((flags & Writer.TOBJECT_RANGE_CACHED) == 0) {
                        if (!canReadLong()) {
                            interrupt(model);
                            interruptInt(classId);
                            interrupt(rangePeer);
                            interruptInt(id);
                            interruptInt(TOBJECT_RANGE_ID);
                            return null;
                        }

                        _range = new Id(Peer.get(new UID(rangePeer)), readLong());
                        int index = TObjectWriter.getCacheIndex(_range) & 0xff;
                        _ranges[index] = _range;

                        if (Debug.COMMUNICATIONS_LOG)
                            Helper.instance().getSB().append("range: " + _range + ", ");
                    }
                }
            }
            case TOBJECT_MODEL: {
                if ((flags & Writer.TOBJECT_MODEL_CHANGE) == 0) {
                    if ((flags & Writer.TOBJECT_MODEL_CACHED) != 0)
                        model = Platform.get().defaultObjectModel();
                    else
                        model = _model;
                } else {
                    if ((flags & Writer.TOBJECT_MODEL_CACHED) != 0) {
                        if (!canReadByte()) {
                            interrupt(model);
                            interruptInt(classId);
                            interrupt(rangePeer);
                            interruptInt(id);
                            interruptInt(TOBJECT_MODEL);
                            return null;
                        }

                        int index = readByte() & 0xff;
                        model = _models[index];
                    } else {
                        byte[] uid = readBinary();

                        if (interrupted()) {
                            interrupt(model);
                            interruptInt(classId);
                            interrupt(rangePeer);
                            interruptInt(id);
                            interruptInt(TOBJECT_MODEL);
                            return null;
                        }

                        model = ObjectModel.get(uid);

                        if (model == null)
                            throw new RuntimeException(Strings.UNKNOWN_OBJECT_MODEL + " " + new UID(uid));

                        _models[uid[0] & 0xff] = model;

                        if (Debug.COMMUNICATIONS_LOG)
                            Helper.instance().getSB().append("model: " + new UID(uid) + ", ");
                    }

                    _model = model;
                }

                if (Debug.ENABLED)
                    Debug.assertion(model != null);
            }
            case TOBJECT_CLASS_ID: {
                if (!canReadInteger()) {
                    interrupt(model);
                    interruptInt(classId);
                    interrupt(rangePeer);
                    interruptInt(id);
                    interruptInt(TOBJECT_CLASS_ID);
                    return null;
                }

                classId = readInteger();

                if (Debug.COMMUNICATIONS_LOG)
                    Helper.instance().getSB().append("class: " + classId);
            }
            case TOBJECT_GENERIC_ARGUMENTS: {
                if ((flags & Writer.TOBJECT_GENERIC_ARGUMENTS) != 0) {
                    genericParameters = readTypes();

                    if (interrupted()) {
                        interrupt(model);
                        interruptInt(classId);
                        interrupt(rangePeer);
                        interruptInt(id);
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

        TObject[] objects = new TObject[_resources.size()];

        for (int i = 0; i < objects.length; i++) {
            Range range = _resources.get(i).workspaceImpl().getOrCreateRange(_range);
            objects[i] = range.getOrCreateTObject(_resources.get(i), id, model, classId, genericParameters);

            if (Debug.ENABLED)
                Debug.assertion(objects[i].resource() == _resources.get(i));
        }

        if (Debug.COMMUNICATIONS_LOG)
            log(TObjectWriter.getClassName(objects[0]) + Helper.instance().getSB().toString());

        _objects[id] = objects;
        return objects;
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

            types = Platform.newTTypeArray(index--);
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

    private static final int TYPE_MODE = 0;

    private static final int TYPE_MODEL = 1;

    private static final int TYPE_CLASS_ID = 2;

    private static final int TYPE_CHILDREN = 3;

    @SuppressWarnings("fallthrough")
    private final TType readType() {
        int step = TYPE_MODE;
        int mode = 0;
        ObjectModel model = null;
        int classId = 0;
        TType[] types = null;

        if (interrupted()) {
            step = resumeInt();
            mode = resumeInt();
            model = (ObjectModel) resume();
            classId = resumeInt();
        }

        switch (step) {
            case TYPE_MODE: {
                if (!canReadByte()) {
                    interruptInt(classId);
                    interrupt(model);
                    interruptInt(mode);
                    interruptInt(TYPE_MODE);
                    return null;
                }

                mode = readByte();
            }
            case TYPE_MODEL: {
                if (mode == 0)
                    model = null;
                else if (mode == 1)
                    model = Platform.get().defaultObjectModel();
                else {
                    byte[] uid = readBinary();

                    if (interrupted()) {
                        interruptInt(classId);
                        interrupt(model);
                        interruptInt(mode);
                        interruptInt(TYPE_MODEL);
                        return null;
                    }

                    if (uid != null) {
                        model = ObjectModel.get(uid);

                        if (model == null)
                            Log.write(Strings.UNKNOWN_OBJECT_MODEL + " " + new UID(uid));
                    }
                }
            }
            case TYPE_CLASS_ID: {
                if (!canReadInteger()) {
                    interruptInt(classId);
                    interrupt(model);
                    interruptInt(mode);
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
                    interruptInt(mode);
                    interruptInt(TYPE_CHILDREN);
                    return null;
                }
            }
        }

        return Platform.newTType(model, classId, types);
    }

    // Debug

    final void log(String event) {
        if (!Debug.ENABLED)
            throw new AssertionError();

        long counter = ThreadAssert.getOrCreateCurrent().getReaderDebugCounter(this);
        String c = Platform.get().simpleClassName(this);
        TObjectWriter.log(event, c, "IN ", counter, _range);
    }
}
