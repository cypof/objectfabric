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


import of4gwt.ImmutableClass;
import of4gwt.TKeyedEntry;
import of4gwt.TObject;
import of4gwt.Visitor.ByteBox;
import of4gwt.misc.Debug;

/**
 * Reads and writes objects of unknown types.
 */
final class UnknownObjectSerializer {

    private static final byte TOBJECT = ImmutableClass.COUNT;

    private static final byte NULL = ImmutableClass.COUNT + 1;

    private static final byte REMOVAL = ImmutableClass.COUNT + 2;

    protected static final byte FLAGS_NULL = (byte) (TObjectWriter.FLAG_IMMUTABLE | NULL);

    protected static final byte FLAGS_REMOVAL = (byte) (TObjectWriter.FLAG_IMMUTABLE | REMOVAL);

    static {
        if (Debug.ENABLED)
            Debug.assertion(NULL < TObjectWriter.FLAG_IMMUTABLE - 1);
    }

    private UnknownObjectSerializer() {
        throw new IllegalStateException();
    }

    private enum WriteStep {
        CODE, DEBUG_COUNTER, VALUE
    }

    // TODO: hash map of serializers based on class?
    @SuppressWarnings("fallthrough")
    public static void write(TObjectWriter writer, Object object, int debugCounter) {
        // TODO: avoid passing debug pointer, get it from writer
        WriteStep step = WriteStep.CODE;

        if (writer.interrupted())
            step = (WriteStep) writer.resume();

        if (step == WriteStep.CODE) {
            if (!writer.canWriteByte()) {
                writer.interrupt(WriteStep.CODE);
                return;
            }
        }

        if (object == null) {
            switch (step) {
                case CODE: {
                    writer.writeByte(FLAGS_NULL, TObjectWriter.DEBUG_TAG_CODE);
                }
                case DEBUG_COUNTER: {
                    if (Debug.ENABLED && debugCounter != -1) {
                        if (!writer.canWriteInteger()) {
                            writer.interrupt(WriteStep.DEBUG_COUNTER);
                            return;
                        }

                        writer.writeInteger(debugCounter);
                    }
                }
                case VALUE: {
                }
            }

            return;
        }

        if (object == TKeyedEntry.REMOVAL) {
            switch (step) {
                case CODE: {
                    writer.writeByte(FLAGS_REMOVAL, TObjectWriter.DEBUG_TAG_CODE);
                }
                case DEBUG_COUNTER: {
                    if (Debug.ENABLED && debugCounter != -1) {
                        if (!writer.canWriteInteger()) {
                            writer.interrupt(WriteStep.DEBUG_COUNTER);
                            return;
                        }

                        writer.writeInteger(debugCounter);
                    }
                }
                case VALUE: {
                }
            }

            return;
        }

        if (object instanceof TObject) {
            switch (step) {
                case CODE: {
                    byte value = (byte) (TObjectWriter.FLAG_IMMUTABLE | TOBJECT);
                    writer.writeByte(value, TObjectWriter.DEBUG_TAG_CODE);
                }
                case DEBUG_COUNTER: {
                    if (Debug.ENABLED && debugCounter != -1) {
                        if (!writer.canWriteInteger()) {
                            writer.interrupt(WriteStep.DEBUG_COUNTER);
                            return;
                        }

                        writer.writeInteger(debugCounter);
                    }
                }
                case VALUE: {
                    writer.writeTObject((TObject) object);

                    if (writer.interrupted())
                        writer.interrupt(WriteStep.VALUE);
                }
            }

            return;
        }

        if (object instanceof String) {
            switch (step) {
                case CODE: {
                    byte value = (byte) (TObjectWriter.FLAG_IMMUTABLE | ImmutableClass.STRING_INDEX);
                    writer.writeByte(value, TObjectWriter.DEBUG_TAG_CODE);
                }
                case DEBUG_COUNTER: {
                    if (Debug.ENABLED && debugCounter != -1) {
                        if (!writer.canWriteInteger()) {
                            writer.interrupt(WriteStep.DEBUG_COUNTER);
                            return;
                        }

                        writer.writeInteger(debugCounter);
                    }
                }
                case VALUE: {
                    writer.writeString((String) object);

                    if (writer.interrupted())
                        writer.interrupt(WriteStep.VALUE);
                }
            }

            return;
        }

        if (object instanceof Integer) {
            switch (step) {
                case CODE: {
                    byte value = (byte) (TObjectWriter.FLAG_IMMUTABLE | ImmutableClass.INTEGER_INDEX);
                    writer.writeByte(value, TObjectWriter.DEBUG_TAG_CODE);
                }
                case DEBUG_COUNTER: {
                    if (Debug.ENABLED && debugCounter != -1) {
                        if (!writer.canWriteInteger()) {
                            writer.interrupt(WriteStep.DEBUG_COUNTER);
                            return;
                        }

                        writer.writeInteger(debugCounter);
                    }
                }
                case VALUE: {
                    if (!writer.canWriteInteger()) {
                        writer.interrupt(WriteStep.VALUE);
                        return;
                    }

                    writer.writeInteger(((Integer) object).intValue());
                }
            }

            return;
        }

        if (object instanceof Long) {
            switch (step) {
                case CODE: {
                    byte value = (byte) (TObjectWriter.FLAG_IMMUTABLE | ImmutableClass.LONG_INDEX);
                    writer.writeByte(value, TObjectWriter.DEBUG_TAG_CODE);
                }
                case DEBUG_COUNTER: {
                    if (Debug.ENABLED && debugCounter != -1) {
                        if (!writer.canWriteInteger()) {
                            writer.interrupt(WriteStep.DEBUG_COUNTER);
                            return;
                        }

                        writer.writeInteger(debugCounter);
                    }
                }
                case VALUE: {
                    if (!writer.canWriteLong()) {
                        writer.interrupt(WriteStep.VALUE);
                        return;
                    }

                    writer.writeLong(((Long) object).longValue());
                }
            }

            return;
        }

        if (object instanceof Boolean) {
            switch (step) {
                case CODE: {
                    byte value = (byte) (TObjectWriter.FLAG_IMMUTABLE | ImmutableClass.BOOLEAN_INDEX);
                    writer.writeByte(value, TObjectWriter.DEBUG_TAG_CODE);
                }
                case DEBUG_COUNTER: {
                    if (Debug.ENABLED && debugCounter != -1) {
                        if (!writer.canWriteInteger()) {
                            writer.interrupt(WriteStep.DEBUG_COUNTER);
                            return;
                        }

                        writer.writeInteger(debugCounter);
                    }
                }
                case VALUE: {
                    if (!writer.canWriteBoolean()) {
                        writer.interrupt(WriteStep.VALUE);
                        return;
                    }

                    writer.writeBoolean(((Boolean) object).booleanValue());
                }
            }

            return;
        }

        if (object instanceof Byte) {
            switch (step) {
                case CODE: {
                    byte value = (byte) (TObjectWriter.FLAG_IMMUTABLE | ImmutableClass.BYTE_INDEX);
                    writer.writeByte(value, TObjectWriter.DEBUG_TAG_CODE);
                }
                case DEBUG_COUNTER: {
                    if (Debug.ENABLED && debugCounter != -1) {
                        if (!writer.canWriteInteger()) {
                            writer.interrupt(WriteStep.DEBUG_COUNTER);
                            return;
                        }

                        writer.writeInteger(debugCounter);
                    }
                }
                case VALUE: {
                    if (!writer.canWriteByte()) {
                        writer.interrupt(WriteStep.VALUE);
                        return;
                    }

                    writer.writeByte(((Byte) object).byteValue());
                }
            }

            return;
        }

        if (object instanceof Short) {
            switch (step) {
                case CODE: {
                    byte value = (byte) (TObjectWriter.FLAG_IMMUTABLE | ImmutableClass.SHORT_INDEX);
                    writer.writeByte(value, TObjectWriter.DEBUG_TAG_CODE);
                }
                case DEBUG_COUNTER: {
                    if (Debug.ENABLED && debugCounter != -1) {
                        if (!writer.canWriteInteger()) {
                            writer.interrupt(WriteStep.DEBUG_COUNTER);
                            return;
                        }

                        writer.writeInteger(debugCounter);
                    }
                }
                case VALUE: {
                    if (!writer.canWriteShort()) {
                        writer.interrupt(WriteStep.VALUE);
                        return;
                    }

                    writer.writeShort(((Short) object).shortValue());
                }
            }

            return;
        }

        if (object instanceof Character) {
            switch (step) {
                case CODE: {
                    byte value = (byte) (TObjectWriter.FLAG_IMMUTABLE | ImmutableClass.CHARACTER_INDEX);
                    writer.writeByte(value, TObjectWriter.DEBUG_TAG_CODE);
                }
                case DEBUG_COUNTER: {
                    if (Debug.ENABLED && debugCounter != -1) {
                        if (!writer.canWriteInteger()) {
                            writer.interrupt(WriteStep.DEBUG_COUNTER);
                            return;
                        }

                        writer.writeInteger(debugCounter);
                    }
                }
                case VALUE: {
                    if (!writer.canWriteCharacter()) {
                        writer.interrupt(WriteStep.VALUE);
                        return;
                    }

                    writer.writeCharacter(((Character) object).charValue());
                }
            }

            return;
        }

        if (object instanceof Float) {
            switch (step) {
                case CODE: {
                    byte value = (byte) (TObjectWriter.FLAG_IMMUTABLE | ImmutableClass.FLOAT_INDEX);
                    writer.writeByte(value, TObjectWriter.DEBUG_TAG_CODE);
                }
                case DEBUG_COUNTER: {
                    if (Debug.ENABLED && debugCounter != -1) {
                        if (!writer.canWriteInteger()) {
                            writer.interrupt(WriteStep.DEBUG_COUNTER);
                            return;
                        }

                        writer.writeInteger(debugCounter);
                    }
                }
                case VALUE: {
                    writer.writeFloat(((Float) object).floatValue());

                    if (writer.interrupted())
                        writer.interrupt(WriteStep.VALUE);
                }
            }

            return;
        }

        if (object instanceof Double) {
            switch (step) {
                case CODE: {
                    byte value = (byte) (TObjectWriter.FLAG_IMMUTABLE | ImmutableClass.DOUBLE_INDEX);
                    writer.writeByte(value, TObjectWriter.DEBUG_TAG_CODE);
                }
                case DEBUG_COUNTER: {
                    if (Debug.ENABLED && debugCounter != -1) {
                        if (!writer.canWriteInteger()) {
                            writer.interrupt(WriteStep.DEBUG_COUNTER);
                            return;
                        }

                        writer.writeInteger(debugCounter);
                    }
                }
                case VALUE: {
                    writer.writeDouble(((Double) object).doubleValue());

                    if (writer.interrupted())
                        writer.interrupt(WriteStep.VALUE);
                }
            }

            return;
        }

        if (object instanceof java.util.Date) {
            switch (step) {
                case CODE: {
                    byte value = (byte) (TObjectWriter.FLAG_IMMUTABLE | ImmutableClass.DATE_INDEX);
                    writer.writeByte(value, TObjectWriter.DEBUG_TAG_CODE);
                }
                case DEBUG_COUNTER: {
                    if (Debug.ENABLED && debugCounter != -1) {
                        if (!writer.canWriteInteger()) {
                            writer.interrupt(WriteStep.DEBUG_COUNTER);
                            return;
                        }

                        writer.writeInteger(debugCounter);
                    }
                }
                case VALUE: {
                    if (!writer.canWriteDate()) {
                        writer.interrupt(WriteStep.VALUE);
                        return;
                    }

                    writer.writeDate((java.util.Date) object);
                }
            }

            return;
        }

        if (object instanceof java.math.BigInteger) {
            switch (step) {
                case CODE: {
                    byte value = (byte) (TObjectWriter.FLAG_IMMUTABLE | ImmutableClass.BIG_INTEGER_INDEX);
                    writer.writeByte(value, TObjectWriter.DEBUG_TAG_CODE);
                }
                case DEBUG_COUNTER: {
                    if (Debug.ENABLED && debugCounter != -1) {
                        if (!writer.canWriteInteger()) {
                            writer.interrupt(WriteStep.DEBUG_COUNTER);
                            return;
                        }

                        writer.writeInteger(debugCounter);
                    }
                }
                case VALUE: {
                    writer.writeBigInteger((java.math.BigInteger) object);

                    if (writer.interrupted())
                        writer.interrupt(WriteStep.VALUE);
                }
            }

            return;
        }

        if (object instanceof java.math.BigDecimal) {
            switch (step) {
                case CODE: {
                    byte value = (byte) (TObjectWriter.FLAG_IMMUTABLE | ImmutableClass.DECIMAL_INDEX);
                    writer.writeByte(value, TObjectWriter.DEBUG_TAG_CODE);
                }
                case DEBUG_COUNTER: {
                    if (Debug.ENABLED && debugCounter != -1) {
                        if (!writer.canWriteInteger()) {
                            writer.interrupt(WriteStep.DEBUG_COUNTER);
                            return;
                        }

                        writer.writeInteger(debugCounter);
                    }
                }
                case VALUE: {
                    writer.writeDecimal((java.math.BigDecimal) object);

                    if (writer.interrupted())
                        writer.interrupt(WriteStep.VALUE);
                }
            }

            return;
        }

        if (object instanceof byte[]) {
            switch (step) {
                case CODE: {
                    byte value = (byte) (TObjectWriter.FLAG_IMMUTABLE | ImmutableClass.BINARY_INDEX);
                    writer.writeByte(value, TObjectWriter.DEBUG_TAG_CODE);
                }
                case DEBUG_COUNTER: {
                    if (Debug.ENABLED && debugCounter != -1) {
                        if (!writer.canWriteInteger()) {
                            writer.interrupt(WriteStep.DEBUG_COUNTER);
                            return;
                        }

                        writer.writeInteger(debugCounter);
                    }
                }
                case VALUE: {
                    writer.writeBinary((byte[]) object);

                    if (writer.interrupted())
                        writer.interrupt(WriteStep.VALUE);
                }
            }

            return;
        }

        throw new java.lang.IllegalArgumentException();
    }

    public static Object read(Reader reader) {
        byte code;
        ByteBox resumed = null;

        if (reader.interrupted())
            resumed = (ByteBox) reader.resume();

        if (resumed != null)
            code = resumed.Value;
        else {
            if (!reader.canReadByte()) {
                reader.interrupt(null);
                return null;
            }

            code = reader.readByte(TObjectWriter.DEBUG_TAG_CODE);
        }

        Object value = read(reader, code);

        if (reader.interrupted()) {
            ByteBox box = new ByteBox();
            box.Value = code;
            reader.interrupt(box);
        }

        return value;
    }

    public static Object read(Reader reader, byte code) {
        if (reader.interrupted())
            reader.resume();

        switch (code & ~TObjectWriter.FLAG_IMMUTABLE) {
            case ImmutableClass.BOOLEAN_INDEX: {
                if (!reader.canReadBoolean()) {
                    reader.interrupt(null);
                    return null;
                }

                return reader.readBoolean();
            }
            case ImmutableClass.BOOLEAN_BOXED_INDEX:
                throw new IllegalStateException();
            case ImmutableClass.BYTE_INDEX: {
                if (!reader.canReadByte()) {
                    reader.interrupt(null);
                    return null;
                }

                return reader.readByte();
            }
            case ImmutableClass.BYTE_BOXED_INDEX:
                throw new IllegalStateException();
            case ImmutableClass.CHARACTER_INDEX: {
                if (!reader.canReadCharacter()) {
                    reader.interrupt(null);
                    return null;
                }

                return reader.readCharacter();
            }
            case ImmutableClass.CHARACTER_BOXED_INDEX:
                throw new IllegalStateException();
            case ImmutableClass.SHORT_INDEX: {
                if (!reader.canReadShort()) {
                    reader.interrupt(null);
                    return null;
                }

                return reader.readShort();
            }
            case ImmutableClass.SHORT_BOXED_INDEX:
                throw new IllegalStateException();
            case ImmutableClass.INTEGER_INDEX: {
                if (!reader.canReadInteger()) {
                    reader.interrupt(null);
                    return null;
                }

                return reader.readInteger();
            }
            case ImmutableClass.INTEGER_BOXED_INDEX:
                throw new IllegalStateException();
            case ImmutableClass.LONG_INDEX: {
                if (!reader.canReadLong()) {
                    reader.interrupt(null);
                    return null;
                }

                return reader.readLong();
            }
            case ImmutableClass.LONG_BOXED_INDEX:
                throw new IllegalStateException();
            case ImmutableClass.FLOAT_INDEX: {
                float value = reader.readFloat();

                if (reader.interrupted())
                    reader.interrupt(null);

                return new Float(value); // Box
            }
            case ImmutableClass.FLOAT_BOXED_INDEX:
                throw new IllegalStateException();
            case ImmutableClass.DOUBLE_INDEX: {
                double value = reader.readDouble();

                if (reader.interrupted())
                    reader.interrupt(null);

                return new Double(value); // Box
            }
            case ImmutableClass.DOUBLE_BOXED_INDEX:
                throw new IllegalStateException();
            case ImmutableClass.STRING_INDEX: {
                String value = reader.readString();

                if (reader.interrupted())
                    reader.interrupt(null);

                return value;
            }
            case ImmutableClass.DATE_INDEX: {
                if (!reader.canReadDate()) {
                    reader.interrupt(null);
                    return null;
                }

                return reader.readDate();
            }
            case ImmutableClass.BIG_INTEGER_INDEX: {
                java.math.BigInteger value = reader.readBigInteger();

                if (reader.interrupted())
                    reader.interrupt(null);

                return value; // BigInteger
            }
            case ImmutableClass.DECIMAL_INDEX: {
                java.math.BigDecimal value = reader.readDecimal();

                if (reader.interrupted())
                    reader.interrupt(null);

                return value; // BigDecimal
            }
            case ImmutableClass.BINARY_INDEX: {
                byte[] value = reader.readBinary();

                if (reader.interrupted())
                    reader.interrupt(null);

                return value;
            }
            case TOBJECT: {
                TObject value = reader.readTObject();

                if (reader.interrupted())
                    reader.interrupt(null);

                return value;
            }
            case NULL:
                return null;
            case REMOVAL:
                return TKeyedEntry.REMOVAL;
            default:
                throw new IllegalStateException();
        }
    }
}
