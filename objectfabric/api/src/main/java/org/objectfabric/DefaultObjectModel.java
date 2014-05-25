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

@SuppressWarnings("rawtypes")
final class DefaultObjectModel extends ObjectModel {

    static final ObjectModel Instance = new DefaultObjectModel();

    DefaultObjectModel() {
    }

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
        if (classId < 0) {
            if (genericParameters == null)
                return TArray.class;

            if (Debug.ENABLED)
                Debug.assertion(genericParameters.length == 1);

            if (genericParameters[0].getObjectModel() == null) {
                switch (genericParameters[0].getClassId()) {
                    case Immutable.BOOLEAN_INDEX:
                        return TArrayBoolean.class;
                    case Immutable.BYTE_INDEX:
                        return TArrayByte.class;
                    case Immutable.CHARACTER_INDEX:
                        return TArrayCharacter.class;
                    case Immutable.SHORT_INDEX:
                        return TArrayShort.class;
                    case Immutable.INTEGER_INDEX:
                        return TArrayInteger.class;
                    case Immutable.LONG_INDEX:
                        return TArrayLong.class;
                    case Immutable.FLOAT_INDEX:
                        return TArrayFloat.class;
                    case Immutable.DOUBLE_INDEX:
                        return TArrayDouble.class;
                    case Immutable.STRING_INDEX:
                        return TArrayString.class;
                    case Immutable.DATE_INDEX:
                        return TArrayDate.class;
                    case Immutable.BIG_INTEGER_INDEX:
                        return TArrayBigInteger.class;
                    case Immutable.DECIMAL_INDEX:
                        return TArrayBigDecimal.class;
                    case Immutable.BINARY_INDEX:
                        return TArrayBinary.class;
                    default:
                        throw new IllegalStateException(Strings.INVALID_ELEMENT_TYPE + genericParameters[0].getClassId());
                }
            }

            return TArrayTObject.class;
        }

        switch (classId) {
            case BuiltInClass.TOBJECT_CLASS_ID:
                return TObject.class;
            case BuiltInClass.RESOURCE_CLASS_ID:
                return Resource.class;
            case BuiltInClass.TSET_CLASS_ID:
                return TSet.class;
            case BuiltInClass.TMAP_CLASS_ID:
                return TMap.class;
            case BuiltInClass.COUNTER_CLASS_ID:
                return Counter.class;
        }

        return super.getClass(classId, genericParameters);
    }

    @Override
    protected TObject createInstance(Resource resource, int classId, TType[] genericParameters) {
        if (classId < 0) {
            if (genericParameters == null)
                return new TArray(resource, -classId - 1);

            if (Debug.ENABLED)
                Debug.assertion(genericParameters.length == 1);

            if (genericParameters[0].getObjectModel() == null) {
                switch (genericParameters[0].getClassId()) {
                    case Immutable.BOOLEAN_INDEX:
                        return new TArrayBoolean(resource, -classId - 1);
                    case Immutable.BYTE_INDEX:
                        return new TArrayByte(resource, -classId - 1);
                    case Immutable.CHARACTER_INDEX:
                        return new TArrayCharacter(resource, -classId - 1);
                    case Immutable.SHORT_INDEX:
                        return new TArrayShort(resource, -classId - 1);
                    case Immutable.INTEGER_INDEX:
                        return new TArrayInteger(resource, -classId - 1);
                    case Immutable.LONG_INDEX:
                        return new TArrayLong(resource, -classId - 1);
                    case Immutable.FLOAT_INDEX:
                        return new TArrayFloat(resource, -classId - 1);
                    case Immutable.DOUBLE_INDEX:
                        return new TArrayDouble(resource, -classId - 1);
                    case Immutable.STRING_INDEX:
                        return new TArrayString(resource, -classId - 1);
                    case Immutable.DATE_INDEX:
                        return new TArrayDate(resource, -classId - 1);
                    case Immutable.BIG_INTEGER_INDEX:
                        return new TArrayBigInteger(resource, -classId - 1);
                    case Immutable.DECIMAL_INDEX:
                        return new TArrayBigDecimal(resource, -classId - 1);
                    case Immutable.BINARY_INDEX:
                        return new TArrayBinary(resource, -classId - 1);
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
                    return new TSet(resource);

                return new TSet(resource, genericParameters[0]);
            case BuiltInClass.TMAP_CLASS_ID:
                if (genericParameters == null)
                    return new TMap(resource);

                return new TMap(resource, genericParameters[0], genericParameters[1]);
            case BuiltInClass.COUNTER_CLASS_ID:
                return new Counter(resource);
        }

        return super.createInstance(resource, classId, genericParameters);
    }
}
