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
 * Defines ObjectFabric built-in classes, like transactions or collections.
 */
public class DefaultObjectModel extends DefaultObjectModelBase {

    /*
     * Int constants not accessible in enums (?!), needed for partial switch.
     */
    private static final int FIRST_INDEX = DefaultObjectModelBase.CLASS_COUNT + DefaultObjectModelBase.METHOD_COUNT;

    public static final int COM_OBJECTFABRIC_TOBJECT_CLASS_ID = FIRST_INDEX + 0;

    public static final int COM_OBJECTFABRIC_OBJECT_MODEL_CLASS_ID = FIRST_INDEX + 1;

    public static final int COM_OBJECTFABRIC_TLIST_CLASS_ID = FIRST_INDEX + 2;

    public static final int COM_OBJECTFABRIC_TMAP_CLASS_ID = FIRST_INDEX + 3;

    public static final int COM_OBJECTFABRIC_TSET_CLASS_ID = FIRST_INDEX + 4;

    protected DefaultObjectModel() {
        super(new Version(null));
    }

    public static DefaultObjectModel getInstance() {
        return DefaultObjectModelBase.getInstance();
    }

    @Override
    public Class getClass(int classId, TType[] genericParameters) {
        if (classId < 0) {
            if (genericParameters == null)
                return TArray.class;

            if (Debug.ENABLED)
                Debug.assertion(genericParameters.length == 1);

            if (genericParameters[0].getObjectModel() == null) {
                switch (genericParameters[0].getClassId()) {
                    case ImmutableClass.BOOLEAN_INDEX:
                        return TArrayBoolean.class;
                    case ImmutableClass.BYTE_INDEX:
                        return TArrayByte.class;
                    case ImmutableClass.CHARACTER_INDEX:
                        return TArrayCharacter.class;
                    case ImmutableClass.SHORT_INDEX:
                        return TArrayShort.class;
                    case ImmutableClass.INTEGER_INDEX:
                        return TArrayInteger.class;
                    case ImmutableClass.LONG_INDEX:
                        return TArrayLong.class;
                    case ImmutableClass.FLOAT_INDEX:
                        return TArrayFloat.class;
                    case ImmutableClass.DOUBLE_INDEX:
                        return TArrayDouble.class;
                    case ImmutableClass.STRING_INDEX:
                        return TArrayString.class;
                    case ImmutableClass.DATE_INDEX:
                        return TArrayDate.class;
                    case ImmutableClass.BIG_INTEGER_INDEX:
                        return TArrayBigInteger.class;
                    case ImmutableClass.DECIMAL_INDEX:
                        return TArrayBigDecimal.class;
                    case ImmutableClass.BINARY_INDEX:
                        return TArrayBinary.class;
                    default:
                        throw new IllegalStateException(Strings.UNKNOWN_ELEMENT_TYPE + genericParameters[0].getClassId());
                }
            }

            return TArrayTObject.class;
        }

        switch (classId) {
            case COM_OBJECTFABRIC_TOBJECT_CLASS_ID:
                return UserTObject.class;
            case COM_OBJECTFABRIC_OBJECT_MODEL_CLASS_ID:
                return ObjectModel.class;
            case COM_OBJECTFABRIC_TLIST_CLASS_ID:
                return TList.class;
            case COM_OBJECTFABRIC_TMAP_CLASS_ID:
                return TMap.class;
            case COM_OBJECTFABRIC_TSET_CLASS_ID:
                return TSet.class;
        }

        return super.getClass(classId, genericParameters);
    }

    @Override
    public UserTObject createInstance(Transaction trunk, int classId, TType[] genericParameters) {
        if (classId < 0) {
            if (genericParameters == null)
                return new TArray(trunk, -classId - 1);

            if (Debug.ENABLED)
                Debug.assertion(genericParameters.length == 1);

            if (genericParameters[0].getObjectModel() == null) {
                switch (genericParameters[0].getClassId()) {
                    case ImmutableClass.BOOLEAN_INDEX:
                        return new TArrayBoolean(trunk, -classId - 1);
                    case ImmutableClass.BYTE_INDEX:
                        return new TArrayByte(trunk, -classId - 1);
                    case ImmutableClass.CHARACTER_INDEX:
                        return new TArrayCharacter(trunk, -classId - 1);
                    case ImmutableClass.SHORT_INDEX:
                        return new TArrayShort(trunk, -classId - 1);
                    case ImmutableClass.INTEGER_INDEX:
                        return new TArrayInteger(trunk, -classId - 1);
                    case ImmutableClass.LONG_INDEX:
                        return new TArrayLong(trunk, -classId - 1);
                    case ImmutableClass.FLOAT_INDEX:
                        return new TArrayFloat(trunk, -classId - 1);
                    case ImmutableClass.DOUBLE_INDEX:
                        return new TArrayDouble(trunk, -classId - 1);
                    case ImmutableClass.STRING_INDEX:
                        return new TArrayString(trunk, -classId - 1);
                    case ImmutableClass.DATE_INDEX:
                        return new TArrayDate(trunk, -classId - 1);
                    case ImmutableClass.BIG_INTEGER_INDEX:
                        return new TArrayBigInteger(trunk, -classId - 1);
                    case ImmutableClass.DECIMAL_INDEX:
                        return new TArrayBigDecimal(trunk, -classId - 1);
                    case ImmutableClass.BINARY_INDEX:
                        return new TArrayBinary(trunk, -classId - 1);
                    default:
                        throw new IllegalStateException(Strings.UNKNOWN_ELEMENT_TYPE + genericParameters[0].getClassId());
                }
            }

            return new TArrayTObject(trunk, -classId - 1, genericParameters[0]);
        }

        switch (classId) {
            case COM_OBJECTFABRIC_TOBJECT_CLASS_ID:
                return new UserTObject(trunk);
            case COM_OBJECTFABRIC_OBJECT_MODEL_CLASS_ID:
                throw new IllegalStateException();
            case COM_OBJECTFABRIC_TLIST_CLASS_ID:
                return new TList(trunk);
            case COM_OBJECTFABRIC_TMAP_CLASS_ID:
                return new TMap(trunk);
            case COM_OBJECTFABRIC_TSET_CLASS_ID:
                return new TSet(trunk);
        }

        return super.createInstance(trunk, classId, genericParameters);
    }
}
