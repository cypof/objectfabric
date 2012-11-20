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

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * List of supported primitive types. Transactional objects can only reference other
 * transactional objects, or the following primitive types. ObjectFabric sees those
 * primitives as immutable, i.e. changes made to an instance would not be replicated or
 * persisted. Most of those types are already made immutable by Java's type system, like
 * String, but others are not, like byte[]. If you modify a byte[] in on process, updates
 * will not be replicated to other processes.
 */
@SuppressWarnings({ "unchecked", "rawtypes" })
public final class Immutable {

    /*
     * Int constants not accessible in enums (?), needed for partial switch.
     */
    public static final int BOOLEAN_INDEX = 0;

    public static final Immutable BOOLEAN;

    public static final int BOOLEAN_BOXED_INDEX = 1;

    public static final Immutable BOOLEAN_BOXED;

    public static final int BYTE_INDEX = 2;

    public static final Immutable BYTE;

    public static final int BYTE_BOXED_INDEX = 3;

    public static final Immutable BYTE_BOXED;

    public static final int CHARACTER_INDEX = 4;

    public static final Immutable CHARACTER;

    public static final int CHARACTER_BOXED_INDEX = 5;

    public static final Immutable CHARACTER_BOXED;

    public static final int SHORT_INDEX = 6;

    public static final Immutable SHORT;

    public static final int SHORT_BOXED_INDEX = 7;

    public static final Immutable SHORT_BOXED;

    public static final int INTEGER_INDEX = 8;

    public static final Immutable INTEGER;

    public static final int INTEGER_BOXED_INDEX = 9;

    public static final Immutable INTEGER_BOXED;

    public static final int LONG_INDEX = 10;

    public static final Immutable LONG;

    public static final int LONG_BOXED_INDEX = 11;

    public static final Immutable LONG_BOXED;

    public static final int FLOAT_INDEX = 12;

    public static final Immutable FLOAT;

    public static final int FLOAT_BOXED_INDEX = 13;

    public static final Immutable FLOAT_BOXED;

    public static final int DOUBLE_INDEX = 14;

    public static final Immutable DOUBLE;

    public static final int DOUBLE_BOXED_INDEX = 15;

    public static final Immutable DOUBLE_BOXED;

    public static final int STRING_INDEX = 16;

    public static final Immutable STRING;

    public static final int DATE_INDEX = 17;

    public static final Immutable DATE;

    public static final int BIG_INTEGER_INDEX = 18;

    public static final Immutable BIG_INTEGER;

    public static final int DECIMAL_INDEX = 19;

    public static final Immutable DECIMAL;

    public static final int BINARY_INDEX = 20;

    public static final Immutable BINARY;

    //

    public static final int COUNT = 21;

    public static final List<Immutable> ALL;

    static {
        org.objectfabric.List<Immutable> all = new org.objectfabric.List<Immutable>();
        all.add(BOOLEAN = new Immutable(BOOLEAN_INDEX, true, false, true, "Boolean", "boolean", "bool"));
        all.add(BOOLEAN_BOXED = new Immutable(BOOLEAN_BOXED_INDEX, true, true, true, "BooleanBoxed", "java.lang.Boolean", "bool?"));
        all.add(BYTE = new Immutable(BYTE_INDEX, true, false, true, "Byte", "byte", "byte"));
        all.add(BYTE_BOXED = new Immutable(BYTE_BOXED_INDEX, true, true, true, "ByteBoxed", "java.lang.Byte", "byte?"));
        all.add(CHARACTER = new Immutable(CHARACTER_INDEX, true, false, true, "Character", "char", "char"));
        all.add(CHARACTER_BOXED = new Immutable(CHARACTER_BOXED_INDEX, true, true, true, "CharacterBoxed", "java.lang.Character", "char?"));
        all.add(SHORT = new Immutable(SHORT_INDEX, true, false, true, "Short", "short", "short"));
        all.add(SHORT_BOXED = new Immutable(SHORT_BOXED_INDEX, true, true, true, "ShortBoxed", "java.lang.Short", "short?"));
        all.add(INTEGER = new Immutable(INTEGER_INDEX, true, false, true, "Integer", "int", "int"));
        all.add(INTEGER_BOXED = new Immutable(INTEGER_BOXED_INDEX, true, true, true, "IntegerBoxed", "java.lang.Integer", "int?"));
        all.add(LONG = new Immutable(LONG_INDEX, true, false, true, "Long", "long", "long"));
        all.add(LONG_BOXED = new Immutable(LONG_BOXED_INDEX, true, true, true, "LongBoxed", "java.lang.Long", "long?"));
        all.add(FLOAT = new Immutable(FLOAT_INDEX, true, false, true, "Float", "float", "float"));
        all.add(FLOAT_BOXED = new Immutable(FLOAT_BOXED_INDEX, true, true, true, "FloatBoxed", "java.lang.Float", "float?"));
        all.add(DOUBLE = new Immutable(DOUBLE_INDEX, true, false, true, "Double", "double", "double"));
        all.add(DOUBLE_BOXED = new Immutable(DOUBLE_BOXED_INDEX, true, true, true, "DoubleBoxed", "java.lang.Double", "double?"));
        all.add(STRING = new Immutable(STRING_INDEX, false, false, false, "String", "java.lang.String", "string"));
        all.add(DATE = new Immutable(DATE_INDEX, false, false, true, "Date", "java.util.Date", "System.DateTime?"));
        all.add(BIG_INTEGER = new Immutable(BIG_INTEGER_INDEX, false, false, false, "BigInteger", "java.math.BigInteger", "System.Numerics.BigInteger?"));
        all.add(DECIMAL = new Immutable(DECIMAL_INDEX, false, false, false, "Decimal", "java.math.BigDecimal", "decimal?"));
        all.add(BINARY = new Immutable(BINARY_INDEX, false, false, false, "Binary", "byte[]", "byte[]"));
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

    private Immutable(int ordinal, boolean primitive, boolean boxed, boolean fixedLength, String name, String java, String csharp) {
        _ordinal = ordinal;
        _primitive = primitive;
        _boxed = boxed;
        _fixedLength = fixedLength;
        _name = name;
        _java = java;
        _csharp = csharp;

        _type = Platform.newTType(null, ordinal());
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

    public String java() {
        return _java;
    }

    public String csharp() {
        return _csharp;
    }

    public TType type() {
        return _type;
    }

    public Immutable boxed() {
        if (isBoxed())
            throw new AssertionError();

        return Immutable.ALL.get(_ordinal + 1);
    }

    public Immutable nonBoxed() {
        if (!isBoxed())
            throw new AssertionError();

        return Immutable.ALL.get(_ordinal - 1);
    }

    public String defaultString() {
        if (this == Immutable.BOOLEAN)
            return "false";

        if (this == Immutable.BYTE)
            return "((byte) 0)";

        if (this == Immutable.CHARACTER)
            return "'\\0'";

        if (this == Immutable.SHORT)
            return "((short) 0)";

        if (this == Immutable.INTEGER)
            return "0";

        if (this == Immutable.LONG)
            return "0";

        if (this == Immutable.FLOAT)
            return "0";

        if (this == Immutable.DOUBLE)
            return "0";

        return "null";
    }

    public static Immutable parse(String name) {
        if ("[B".equals(name))
            return BINARY;

        for (int i = 0; i < Immutable.ALL.size(); i++) {
            String java = Immutable.ALL.get(i).java();
            String[] javaSplit = Platform.get().split(java, '.');

            if (java.equals(name))
                return Immutable.ALL.get(i);

            if (javaSplit.length > 0 && javaSplit[javaSplit.length - 1].equals(name))
                return Immutable.ALL.get(i);

            String cSharp = Immutable.ALL.get(i).csharp();
            String[] cSharpSplit = Platform.get().split(cSharp, '.');

            if (cSharp.equals(name))
                return Immutable.ALL.get(i);

            if (cSharpSplit.length > 0 && cSharpSplit[cSharpSplit.length - 1].equals(name))
                return Immutable.ALL.get(i);

        }

        return null;
    }
}
