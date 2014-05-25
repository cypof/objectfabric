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

/**
 * Represents a type. This class is needed to unify Java's java.lang.Class and .NET's
 * System.Type, and because GWT support for reflection is too limited. <br>
 * <br>
 * TODO no referential identity, merge with TypeDef.
 */
@SuppressWarnings("rawtypes")
public final class TType {

    private static final int IMMUTABLE_CLASS_OBJECT = -1;

    private static final int IMMUTABLE_CLASS_VOID = -2;

    public static final TType OBJECT = Platform.newTType(null, IMMUTABLE_CLASS_OBJECT);

    public static final TType VOID = Platform.newTType(null, IMMUTABLE_CLASS_VOID);

    private final ObjectModel _model;

    private final int _classId;

    private final TType[] _genericParameters;

    private final Class _custom;

    public TType(ObjectModel model, int classId, TType... genericParameters) {
        _model = model;
        _classId = classId;

        if (genericParameters != null && genericParameters.length == 0)
            genericParameters = null;

        _genericParameters = genericParameters;
        _custom = null;
    }

    public TType(Class c) {
        _model = null;
        _classId = 0;
        _genericParameters = null;
        _custom = c;
    }

    public Immutable getImmutable() {
        if (_model == null && _custom == null)
            return Immutable.ALL.get(_classId);

        return null;
    }

    public ObjectModel getObjectModel() {
        return _model;
    }

    public int getClassId() {
        return _classId;
    }

    public TType[] getGenericParameters() {
        return _genericParameters;
    }

    public final Class getCustomClass() {
        return _custom;
    }

    // Debug

    static void checkTType(TObject object) {
        if (!Debug.ENABLED)
            throw new IllegalStateException();

        if (Platform.get().value() != Platform.GWT) {
            TType type = Platform.get().getTypeField(Platform.get().getClass(object));

            Debug.assertion(type.getObjectModel() == object.objectModel_());
            int id = type.getClassId();

            if (id >= 0) // Otherwise array, ignore
                Debug.assertion(id == object.classId_());
        }
    }
}
