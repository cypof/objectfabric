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

package com.objectfabric;

import java.sql.Types;

import com.objectfabric.ImmutableClass;

/**
 * Conversion SQL <-> ObjectFabric.
 */
public class SQLAdapter {

    public static int getSQLType(ImmutableClass c) {
        switch (c.ordinal()) {
            case ImmutableClass.BOOLEAN_INDEX:
                return Types.BOOLEAN;
            case ImmutableClass.BYTE_INDEX:
                return Types.TINYINT;
            case ImmutableClass.CHARACTER_INDEX:
                return Types.NCHAR;
            case ImmutableClass.SHORT_INDEX:
                return Types.SMALLINT;
            case ImmutableClass.INTEGER_INDEX:
                return Types.INTEGER;
            case ImmutableClass.LONG_INDEX:
                return Types.BIGINT;
            case ImmutableClass.FLOAT_INDEX:
                return Types.REAL;
            case ImmutableClass.DOUBLE_INDEX:
                return Types.DOUBLE;
            case ImmutableClass.STRING_INDEX:
                return Types.NVARCHAR;
            case ImmutableClass.DATE_INDEX:
                return Types.TIMESTAMP;
            case ImmutableClass.BIG_INTEGER_INDEX:
                return Types.NVARCHAR;
            case ImmutableClass.DECIMAL_INDEX:
                return Types.DECIMAL;
            case ImmutableClass.BINARY_INDEX:
                return Types.BLOB;
            default:
                throw new IllegalArgumentException("Unsupported type: " + c.getJava());
        }
    }

    public static ImmutableClass getImmutableClass(int type) {
        switch (type) {
            case Types.BOOLEAN:
                return ImmutableClass.BOOLEAN;
            case Types.TINYINT:
                return ImmutableClass.BYTE;
            case Types.NCHAR:
                return ImmutableClass.CHARACTER;
            case Types.SMALLINT:
                return ImmutableClass.SHORT;
            case Types.INTEGER:
                return ImmutableClass.INTEGER;
            case Types.BIGINT:
                return ImmutableClass.LONG;
            case Types.REAL:
                return ImmutableClass.FLOAT;
            case Types.DOUBLE:
                return ImmutableClass.DOUBLE;
            case Types.NVARCHAR:
            case Types.VARCHAR:
                return ImmutableClass.STRING;
            case Types.TIMESTAMP:
                return ImmutableClass.DATE;
            case Types.DECIMAL:
                return ImmutableClass.DECIMAL;
            case Types.BLOB:
                return ImmutableClass.BINARY;
            default:
                throw new IllegalArgumentException("Unsupported type: " + type);
        }
    }
}
