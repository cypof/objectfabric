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

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import of4gwt.misc.Debug;
import of4gwt.misc.PlatformAdapter;

/**
 * Transactional objects can only reference other transactional objects or the following
 * primitive types. ObjectFabric sees those primitives as immutable, i.e. changes made to
 * an instance would not be replicated or persisted. Most of those types are already made
 * immutable by Java's type system, like String, but others are not, like byte[]. If you
 * modify a byte[] in on process, updates will not be replicated to other processes.
 */
@SuppressWarnings("unchecked")
public final class ImmutableClass {

    /*
     * Int constants not accessible in enums (?!), needed for partial switch.
     */
    public static final int BOOLEAN_INDEX = 0;

    public static final ImmutableClass BOOLEAN;

    public static final int BOOLEAN_BOXED_INDEX = 1;

    public static final ImmutableClass BOOLEAN_BOXED;

    public static final int BYTE_INDEX = 2;

    public static final ImmutableClass BYTE;

    public static final int BYTE_BOXED_INDEX = 3;

    public static final ImmutableClass BYTE_BOXED;

    public static final int CHARACTER_INDEX = 4;

    public static final ImmutableClass CHARACTER;

    public static final int CHARACTER_BOXED_INDEX = 5;

    public static final ImmutableClass CHARACTER_BOXED;

    public static final int SHORT_INDEX = 6;

    public static final ImmutableClass SHORT;

    public static final int SHORT_BOXED_INDEX = 7;

    public static final ImmutableClass SHORT_BOXED;

    public static final int INTEGER_INDEX = 8;

    public static final ImmutableClass INTEGER;

    public static final int INTEGER_BOXED_INDEX = 9;

    public static final ImmutableClass INTEGER_BOXED;

    public static final int LONG_INDEX = 10;

    public static final ImmutableClass LONG;

    public static final int LONG_BOXED_INDEX = 11;

    public static final ImmutableClass LONG_BOXED;

    public static final int FLOAT_INDEX = 12;

    public static final ImmutableClass FLOAT;

    public static final int FLOAT_BOXED_INDEX = 13;

    public static final ImmutableClass FLOAT_BOXED;

    public static final int DOUBLE_INDEX = 14;

    public static final ImmutableClass DOUBLE;

    public static final int DOUBLE_BOXED_INDEX = 15;

    public static final ImmutableClass DOUBLE_BOXED;

    public static final int STRING_INDEX = 16;

    public static final ImmutableClass STRING;

    public static final int DATE_INDEX = 17;

    public static final ImmutableClass DATE;

    public static final int BIG_INTEGER_INDEX = 18;

    public static final ImmutableClass BIG_INTEGER;

    public static final int DECIMAL_INDEX = 19;

    public static final ImmutableClass DECIMAL;

    public static final int BINARY_INDEX = 20;

    public static final ImmutableClass BINARY;

    //

    public static final int COUNT = 21;

    public static final List<ImmutableClass> ALL;

    static {
        of4gwt.misc.List<ImmutableClass> all = new of4gwt.misc.List<ImmutableClass>();
        all.add(BOOLEAN = new ImmutableClass(BOOLEAN_INDEX, true, false, true, "Boolean", "boolean", "bool"));
        all.add(BOOLEAN_BOXED = new ImmutableClass(BOOLEAN_BOXED_INDEX, true, true, true, "BooleanBoxed", "java.lang.Boolean", "bool?"));
        all.add(BYTE = new ImmutableClass(BYTE_INDEX, true, false, true, "Byte", "byte", "byte"));
        all.add(BYTE_BOXED = new ImmutableClass(BYTE_BOXED_INDEX, true, true, true, "ByteBoxed", "java.lang.Byte", "byte?"));
        all.add(CHARACTER = new ImmutableClass(CHARACTER_INDEX, true, false, true, "Character", "char", "char"));
        all.add(CHARACTER_BOXED = new ImmutableClass(CHARACTER_BOXED_INDEX, true, true, true, "CharacterBoxed", "java.lang.Character", "char?"));
        all.add(SHORT = new ImmutableClass(SHORT_INDEX, true, false, true, "Short", "short", "short"));
        all.add(SHORT_BOXED = new ImmutableClass(SHORT_BOXED_INDEX, true, true, true, "ShortBoxed", "java.lang.Short", "short?"));
        all.add(INTEGER = new ImmutableClass(INTEGER_INDEX, true, false, true, "Integer", "int", "int"));
        all.add(INTEGER_BOXED = new ImmutableClass(INTEGER_BOXED_INDEX, true, true, true, "IntegerBoxed", "java.lang.Integer", "int?"));
        all.add(LONG = new ImmutableClass(LONG_INDEX, true, false, true, "Long", "long", "long"));
        all.add(LONG_BOXED = new ImmutableClass(LONG_BOXED_INDEX, true, true, true, "LongBoxed", "java.lang.Long", "long?"));
        all.add(FLOAT = new ImmutableClass(FLOAT_INDEX, true, false, false, "Float", "float", "float"));
        all.add(FLOAT_BOXED = new ImmutableClass(FLOAT_BOXED_INDEX, true, true, false, "FloatBoxed", "java.lang.Float", "float?"));
        all.add(DOUBLE = new ImmutableClass(DOUBLE_INDEX, true, false, false, "Double", "double", "double"));
        all.add(DOUBLE_BOXED = new ImmutableClass(DOUBLE_BOXED_INDEX, true, true, false, "DoubleBoxed", "java.lang.Double", "double?"));
        all.add(STRING = new ImmutableClass(STRING_INDEX, false, false, false, "String", "java.lang.String", "string"));
        all.add(DATE = new ImmutableClass(DATE_INDEX, false, false, true, "Date", "java.util.Date", "System.DateTime?"));
        all.add(BIG_INTEGER = new ImmutableClass(BIG_INTEGER_INDEX, false, false, false, "BigInteger", "java.math.BigInteger", "System.Numerics.BigInteger?"));
        all.add(DECIMAL = new ImmutableClass(DECIMAL_INDEX, false, false, false, "Decimal", "java.math.BigDecimal", "decimal?"));
        all.add(BINARY = new ImmutableClass(BINARY_INDEX, false, false, false, "Binary", "byte[]", "byte[]"));
        ALL = (List) Collections.unmodifiableList(Arrays.asList(all.toArray()));

        if (Debug.ENABLED) {
            for (int i = 0; i < all.size(); i++)
                Debug.assertion(all.get(i)._ordinal == i);

            Debug.assertion(ALL.size() == COUNT);
        }
    }

    private final int _ordinal;

    private final boolean _primitive, _boxed, _fixedLength;

    private final String _name, _java, _csharp;

    private final TType _type;

    private ImmutableClass(int ordinal, boolean primitive, boolean boxed, boolean fixedLength, String name, String java, String csharp) {
        _ordinal = ordinal;
        _primitive = primitive;
        _boxed = boxed;
        _fixedLength = fixedLength;
        _name = name;
        _java = java;
        _csharp = csharp;

        _type = new TType(this);
    }

    public int ordinal() {
        return _ordinal;
    }

    public boolean isPrimitive() {
        return _primitive;
    }

    public boolean isBoxed() {
        if (!isPrimitive())
            throw new AssertionError();

        return _boxed;
    }

    public boolean fixedLength() {
        return _fixedLength;
    }

    @Override
    public String toString() {
        return _name;
    }

    public String getJava() {
        return _java;
    }

    public String getCSharp() {
        return _csharp;
    }

    public TType getType() {
        return _type;
    }

    public ImmutableClass getBoxed() {
        if (isBoxed())
            throw new AssertionError();

        return ImmutableClass.ALL.get(_ordinal + 1);
    }

    public ImmutableClass getNonBoxed() {
        if (!isBoxed())
            throw new AssertionError();

        return ImmutableClass.ALL.get(_ordinal - 1);
    }

    public String getDefaultString() {
        if (this == ImmutableClass.BOOLEAN)
            return "false";

        if (this == ImmutableClass.BYTE)
            return "((byte) 0)";

        if (this == ImmutableClass.CHARACTER)
            return "'\\0'";

        if (this == ImmutableClass.SHORT)
            return "((short) 0)";

        if (this == ImmutableClass.INTEGER)
            return "0";

        if (this == ImmutableClass.LONG)
            return "0";

        if (this == ImmutableClass.FLOAT)
            return "0";

        if (this == ImmutableClass.DOUBLE)
            return "0";

        return "null";
    }

    public static ImmutableClass parse(String name) {
        if ("[B".equals(name))
            return BINARY;

        for (int i = 0; i < ImmutableClass.ALL.size(); i++) {
            String java = ImmutableClass.ALL.get(i).getJava();
            String[] javaSplit = PlatformAdapter.split(java, '.');

            if (java.equals(name))
                return ImmutableClass.ALL.get(i);

            if (javaSplit.length > 0 && javaSplit[javaSplit.length - 1].equals(name))
                return ImmutableClass.ALL.get(i);

            String cSharp = ImmutableClass.ALL.get(i).getCSharp();
            String[] cSharpSplit = PlatformAdapter.split(cSharp, '.');

            if (cSharp.equals(name))
                return ImmutableClass.ALL.get(i);

            if (cSharpSplit.length > 0 && cSharpSplit[cSharpSplit.length - 1].equals(name))
                return ImmutableClass.ALL.get(i);

        }

        return null;
    }
}
