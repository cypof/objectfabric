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

import org.objectfabric.generated.JSArray;
import org.objectfabric.generated.JSArrayBigDecimal;
import org.objectfabric.generated.JSArrayBigInteger;
import org.objectfabric.generated.JSArrayBinary;
import org.objectfabric.generated.JSArrayBoolean;
import org.objectfabric.generated.JSArrayByte;
import org.objectfabric.generated.JSArrayCharacter;
import org.objectfabric.generated.JSArrayDate;
import org.objectfabric.generated.JSArrayDouble;
import org.objectfabric.generated.JSArrayFloat;
import org.objectfabric.generated.JSArrayInteger;
import org.objectfabric.generated.JSArrayLong;
import org.objectfabric.generated.JSArrayShort;
import org.objectfabric.generated.JSArrayString;

class JSDefaultObjectModel extends ObjectModel {

    static final ObjectModel Instance = new JSDefaultObjectModel();

    @Override
    protected byte[] uid_() {
        throw new IllegalStateException();
    }

    @Override
    protected java.lang.String objectFabricVersion() {
        throw new IllegalStateException();
    }

    @Override
    protected java.lang.Class getClass(int classId, TType[] genericParameters) {
        if (classId < 0)
            return JSArray.ArrayInternal.class;

        switch (classId) {
            case BuiltInClass.TOBJECT_CLASS_ID:
                return TObject.class;
            case BuiltInClass.RESOURCE_CLASS_ID:
                return JSResource.ResourceInternal.class;
            case BuiltInClass.TSET_CLASS_ID:
                return JSSet.SetInternal.class;
            case BuiltInClass.TMAP_CLASS_ID:
                return JSMap.MapInternal.class;
            case BuiltInClass.COUNTER_CLASS_ID:
                return Counter.class;
        }

        return super.getClass(classId, genericParameters);
    }

    @Override
    protected TObject createInstance(Resource resource, int classId, TType[] genericParameters) {
        if (classId < 0) {
            if (genericParameters == null)
                return new JSArray.ArrayInternal(resource, -classId - 1);

            if (Debug.ENABLED)
                Debug.assertion(genericParameters.length == 1);

            if (genericParameters[0].getObjectModel() == null) {
                switch (genericParameters[0].getClassId()) {
                    case Immutable.BOOLEAN_INDEX:
                        return new JSArrayBoolean.ArrayInternal(resource, -classId - 1);
                    case Immutable.BYTE_INDEX:
                        return new JSArrayByte.ArrayInternal(resource, -classId - 1);
                    case Immutable.CHARACTER_INDEX:
                        return new JSArrayCharacter.ArrayInternal(resource, -classId - 1);
                    case Immutable.SHORT_INDEX:
                        return new JSArrayShort.ArrayInternal(resource, -classId - 1);
                    case Immutable.INTEGER_INDEX:
                        return new JSArrayInteger.ArrayInternal(resource, -classId - 1);
                    case Immutable.LONG_INDEX:
                        return new JSArrayLong.ArrayInternal(resource, -classId - 1);
                    case Immutable.FLOAT_INDEX:
                        return new JSArrayFloat.ArrayInternal(resource, -classId - 1);
                    case Immutable.DOUBLE_INDEX:
                        return new JSArrayDouble.ArrayInternal(resource, -classId - 1);
                    case Immutable.STRING_INDEX:
                        return new JSArrayString.ArrayInternal(resource, -classId - 1);
                    case Immutable.DATE_INDEX:
                        return new JSArrayDate.ArrayInternal(resource, -classId - 1);
                    case Immutable.BIG_INTEGER_INDEX:
                        return new JSArrayBigInteger.ArrayInternal(resource, -classId - 1);
                    case Immutable.DECIMAL_INDEX:
                        return new JSArrayBigDecimal.ArrayInternal(resource, -classId - 1);
                    case Immutable.BINARY_INDEX:
                        return new JSArrayBinary.ArrayInternal(resource, -classId - 1);
                    default:
                        throw new IllegalStateException(Strings.INVALID_ELEMENT_TYPE + genericParameters[0].getClassId());
                }
            }

            return new TArrayTObject(resource, -classId - 1, genericParameters[0]);
        }

        switch (classId) {
            case BuiltInClass.TOBJECT_CLASS_ID:
                return new TObject(resource);
            case BuiltInClass.RESOURCE_CLASS_ID:
                return resource;
            case BuiltInClass.TSET_CLASS_ID:
                if (genericParameters == null)
                    return new JSSet.SetInternal(resource, null);

                return new JSSet.SetInternal(resource, genericParameters[0]);
            case BuiltInClass.TMAP_CLASS_ID:
                if (genericParameters == null)
                    return new JSMap.MapInternal(resource, null, null);

                return new JSMap.MapInternal(resource, genericParameters[0], genericParameters[1]);
            case BuiltInClass.COUNTER_CLASS_ID:
                return new JSCounter.CounterInternal(resource);
        }

        return super.createInstance(resource, classId, genericParameters);
    }
}
