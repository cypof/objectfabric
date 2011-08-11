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

import of4gwt.misc.Debug;

/**
 * Represents a type. This class is needed to unify Java's java.lang.Class and .NET's
 * System.Type, and because GWT support for reflection is too limited.
 * <nl>
 * Warning, no referential identity.
 */
public class TType {

    // Goes around cases where DefaultObjectModel is not initialized
    private static final Object DEFAULT_MODEL = new Object();

    private final Object _model;

    private final int _classId;

    private final TType[] _genericParameters;

    public TType(ImmutableClass immutable) {
        this(null, immutable.ordinal());
    }

    public TType(ObjectModel model, int classId, TType... genericParameters) {
        _model = model;
        _classId = classId;

        if (genericParameters != null && genericParameters.length == 0)
            genericParameters = null;

        _genericParameters = genericParameters;
    }

    TType(int classId) {
        _model = DEFAULT_MODEL;
        _classId = classId;
        _genericParameters = null;
    }

    @SuppressWarnings("static-access")
    final ObjectModel getObjectModel() {
        ObjectModel model;

        if (_model == DEFAULT_MODEL) {
            model = DefaultObjectModel.getInstance();

            if (Debug.ENABLED)
                Debug.assertion(model != null);
        } else
            model = (ObjectModel) _model;

        return model;
    }

    final int getClassId() {
        return _classId;
    }

    final TType[] getGenericParameters() {
        return _genericParameters;
    }
}
