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

import of4gwt.TObject.UserTObject;
import of4gwt.TObject.UserTObject.Method;
import of4gwt.misc.Debug;
import of4gwt.misc.PlatformAdapter;
import of4gwt.misc.PlatformClass;

/**
 * Represents a type. This class is needed to unify Java's java.lang.Class and .NET's
 * System.Type, and because GWT support for reflection is too limited.
 * <nl>
 * TODO no referential identity, merge with TypeDef.
 */
public final class TType {

    public static final TType OBJECT = new TType(PlatformClass.getObjectClass());

    public static final TType VOID = new TType(PlatformClass.getVoidClass());

    // Goes around cases where DefaultObjectModel is not initialized
    private static final Object DEFAULT_MODEL = new Object();

    private static final Object CUSTOM_CLASS = new Object();

    private final Object _model;

    private final int _classId;

    private final TType[] _genericParameters;

    private final Class _custom;

    public TType(ImmutableClass immutable) {
        this(null, immutable.ordinal());
    }

    public TType(ObjectModel model, int classId, TType... genericParameters) {
        _model = model;
        _classId = classId;

        if (genericParameters != null && genericParameters.length == 0)
            genericParameters = null;

        _genericParameters = genericParameters;
        _custom = null;
    }

    public TType(Class c) {
        _model = CUSTOM_CLASS;
        _classId = 0;
        _genericParameters = null;
        _custom = c;
    }

    TType(int classId) {
        _model = DEFAULT_MODEL;
        _classId = classId;
        _genericParameters = null;
        _custom = null;
    }

    public ImmutableClass getImmutableClass() {
        if (_model == null)
            return ImmutableClass.ALL.get(_classId);

        return null;
    }

    public final ObjectModel getObjectModel() {
        ObjectModel model;

        if (_model == DEFAULT_MODEL) {
            model = DefaultObjectModel.getInstance();

            if (Debug.ENABLED)
                Debug.assertion(model != null);
        } else if (_model == CUSTOM_CLASS)
            model = null;
        else
            model = (ObjectModel) _model;

        return model;
    }

    public final int getClassId() {
        if (_model == null || _model == CUSTOM_CLASS)
            return 0;

        return _classId;
    }

    public final TType[] getGenericParameters() {
        return _genericParameters;
    }

    public final Class getCustomClass() {
        return _custom;
    }

    // Debug

    static void checkTType(UserTObject object) {
        if (!Debug.ENABLED)
            throw new IllegalStateException();

        if (PlatformAdapter.PLATFORM != CompileTimeSettings.PLATFORM_GWT) {
            if (!(object instanceof Method)) {
                TType type = PlatformAdapter.getTypeField(object.getClass());
                Debug.assertion(type.getObjectModel() == object.getSharedVersion_objectfabric().getObjectModel());
                int id = type.getClassId();

                if (id >= 0) // Otherwise array, ignore
                    Debug.assertion(id == object.getSharedVersion_objectfabric().getClassId());
            }
        }
    }
}
