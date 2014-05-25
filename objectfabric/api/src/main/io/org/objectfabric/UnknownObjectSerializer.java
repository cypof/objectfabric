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

import org.objectfabric.Continuation.ByteBox;

/**
 * Reads and writes objects of unknown types.
 */
final class UnknownObjectSerializer {

    static final byte NULL = Immutable.COUNT;

    private static final byte REMOVAL = Immutable.COUNT + 1;

    private static final byte CUSTOM = Immutable.COUNT + 2;

    private static final byte MAX = CUSTOM;

    static {
        if (Debug.ENABLED)
            Debug.assertion(MAX < Writer.IMMUTABLE_MAX_COUNT);
    }

    private UnknownObjectSerializer() {
        throw new IllegalStateException();
    }

    // TODO: hash map of serializers based on class?
    @SuppressWarnings("fallthrough")
    static void write(ImmutableWriter writer, Object object) {
        if (Debug.ENABLED)
            Debug.assertion(!(object instanceof TObject.Version));

        if (object instanceof TObject) {
            ((Writer) writer).writeTObject((TObject) object);
            return;
        }

        boolean writtenCode = false;
        byte[] bytes = null;
        boolean resumed = false;

        if (writer.interrupted()) {
            writtenCode = writer.resumeBoolean();
            bytes = (byte[]) writer.resume();
            resumed = true;
        }

        if (!writtenCode) {
            if (!writer.canWriteByte()) {
                writer.interrupt(bytes);
                writer.interruptBoolean(false);
                return;
            }
        }

        if (object == null) {
            writer.writeByte(NULL, Writer.DEBUG_TAG_CODE);
            return;
        }

        if (object == TKeyedEntry.REMOVAL) {
            writer.writeByte(REMOVAL, Writer.DEBUG_TAG_CODE);
            return;
        }

        if (object instanceof String) {
            if (!writtenCode)
                writer.writeByte((byte) Immutable.STRING_INDEX, Writer.DEBUG_TAG_CODE);

            writer.writeString((String) object);

            if (writer.interrupted()) {
                writer.interrupt(bytes);
                writer.interruptBoolean(true);
            }

            return;
        }

        if (object instanceof java.util.Date) {
            if (!writtenCode)
                writer.writeByte((byte) Immutable.DATE_INDEX, Writer.DEBUG_TAG_CODE);

            if (!writer.canWriteDate()) {
                writer.interrupt(bytes);
                writer.interruptBoolean(true);
                return;
            }

            writer.writeDate((java.util.Date) object);
            return;
        }

        if (object instanceof byte[]) {
            if (!writtenCode)
                writer.writeByte((byte) Immutable.BINARY_INDEX, Writer.DEBUG_TAG_CODE);

            writer.writeBinary((byte[]) object);

            if (writer.interrupted()) {
                writer.interrupt(bytes);
                writer.interruptBoolean(true);
            }

            return;
        }

        if (object instanceof Integer) {
            if (!writtenCode)
                writer.writeByte((byte) Immutable.INTEGER_INDEX, Writer.DEBUG_TAG_CODE);

            if (!writer.canWriteInteger()) {
                writer.interrupt(bytes);
                writer.interruptBoolean(true);
                return;
            }

            writer.writeInteger(((Integer) object).intValue());
            return;
        }

        if (object instanceof Long) {
            if (!writtenCode)
                writer.writeByte((byte) Immutable.LONG_INDEX, Writer.DEBUG_TAG_CODE);

            if (!writer.canWriteLong()) {
                writer.interrupt(bytes);
                writer.interruptBoolean(true);
                return;
            }

            writer.writeLong(((Long) object).longValue());
            return;
        }

        if (object instanceof Float) {
            if (!writtenCode)
                writer.writeByte((byte) Immutable.FLOAT_INDEX, Writer.DEBUG_TAG_CODE);

            if (!writer.canWriteFloat()) {
                writer.interrupt(bytes);
                writer.interrupt(true);
                return;
            }

            writer.writeFloat(((Float) object).floatValue());
            return;
        }

        if (object instanceof Double) {
            if (!writtenCode)
                writer.writeByte((byte) Immutable.DOUBLE_INDEX, Writer.DEBUG_TAG_CODE);

            if (!writer.canWriteDouble()) {
                writer.interrupt(bytes);
                writer.interrupt(true);
                return;
            }

            writer.writeDouble(((Double) object).doubleValue());
            return;
        }

        if (object instanceof java.math.BigInteger) {
            if (!writtenCode)
                writer.writeByte((byte) Immutable.BIG_INTEGER_INDEX, Writer.DEBUG_TAG_CODE);

            writer.writeBigInteger((java.math.BigInteger) object);

            if (writer.interrupted()) {
                writer.interrupt(bytes);
                writer.interruptBoolean(true);
            }

            return;
        }

        if (object instanceof java.math.BigDecimal) {
            if (!writtenCode)
                writer.writeByte((byte) Immutable.DECIMAL_INDEX, Writer.DEBUG_TAG_CODE);

            writer.writeDecimal((java.math.BigDecimal) object);

            if (writer.interrupted()) {
                writer.interrupt(bytes);
                writer.interruptBoolean(true);
            }

            return;
        }

        if (object instanceof Boolean) {
            if (!writtenCode)
                writer.writeByte((byte) Immutable.BOOLEAN_INDEX, Writer.DEBUG_TAG_CODE);

            if (!writer.canWriteBoolean()) {
                writer.interrupt(bytes);
                writer.interruptBoolean(true);
                return;
            }

            writer.writeBoolean(((Boolean) object).booleanValue());
            return;
        }

        if (object instanceof Byte) {
            if (!writtenCode)
                writer.writeByte((byte) Immutable.BYTE_INDEX, Writer.DEBUG_TAG_CODE);

            if (!writer.canWriteByte()) {
                writer.interrupt(bytes);
                writer.interruptBoolean(true);
                return;
            }

            writer.writeByte(((Byte) object).byteValue());
            return;
        }

        if (object instanceof Short) {
            if (!writtenCode)
                writer.writeByte((byte) Immutable.SHORT_INDEX, Writer.DEBUG_TAG_CODE);

            if (!writer.canWriteShort()) {
                writer.interrupt(bytes);
                writer.interruptBoolean(true);
                return;
            }

            writer.writeShort(((Short) object).shortValue());
            return;
        }

        if (object instanceof Character) {
            if (!writtenCode)
                writer.writeByte((byte) Immutable.CHARACTER_INDEX, Writer.DEBUG_TAG_CODE);

            if (!writer.canWriteCharacter()) {
                writer.interrupt(bytes);
                writer.interruptBoolean(true);
                return;
            }

            writer.writeCharacter(((Character) object).charValue());
            return;
        }

        Serializer serializer = Workspace.getSerializer();

        if (serializer == null)
            throw new RuntimeException(Strings.UNSUPPORTED_TYPE + object);

        if (!writtenCode)
            writer.writeByte(CUSTOM, Writer.DEBUG_TAG_CODE);

        if (!resumed)
            bytes = serializer.serialize(object);

        writer.writeBinary(bytes);

        if (writer.interrupted()) {
            writer.interrupt(bytes);
            writer.interruptBoolean(true);
        }
    }

    static Object read(ImmutableReader reader) {
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

            code = reader.readByte(Writer.DEBUG_TAG_CODE);
        }

        Object value = read(reader, code);

        if (reader.interrupted()) {
            ByteBox box = new ByteBox();
            box.Value = code;
            reader.interrupt(box);
        }

        return value;
    }

    private static Object read(ImmutableReader reader, byte code) {
        if ((code & Writer.VALUE_IS_TOBJECT) != 0)
            return ((Reader) reader).readTObject(code);

        if (reader.interrupted())
            reader.resume();

        switch (code) {
            case Immutable.BOOLEAN_INDEX: {
                if (!reader.canReadBoolean()) {
                    reader.interrupt(null);
                    return null;
                }

                return reader.readBoolean();
            }
            case Immutable.BOOLEAN_BOXED_INDEX:
                throw new IllegalStateException();
            case Immutable.BYTE_INDEX: {
                if (!reader.canReadByte()) {
                    reader.interrupt(null);
                    return null;
                }

                return reader.readByte();
            }
            case Immutable.BYTE_BOXED_INDEX:
                throw new IllegalStateException();
            case Immutable.CHARACTER_INDEX: {
                if (!reader.canReadCharacter()) {
                    reader.interrupt(null);
                    return null;
                }

                return reader.readCharacter();
            }
            case Immutable.CHARACTER_BOXED_INDEX:
                throw new IllegalStateException();
            case Immutable.SHORT_INDEX: {
                if (!reader.canReadShort()) {
                    reader.interrupt(null);
                    return null;
                }

                return reader.readShort();
            }
            case Immutable.SHORT_BOXED_INDEX:
                throw new IllegalStateException();
            case Immutable.INTEGER_INDEX: {
                if (!reader.canReadInteger()) {
                    reader.interrupt(null);
                    return null;
                }

                return reader.readInteger();
            }
            case Immutable.INTEGER_BOXED_INDEX:
                throw new IllegalStateException();
            case Immutable.LONG_INDEX: {
                if (!reader.canReadLong()) {
                    reader.interrupt(null);
                    return null;
                }

                return reader.readLong();
            }
            case Immutable.LONG_BOXED_INDEX:
                throw new IllegalStateException();
            case Immutable.FLOAT_INDEX: {
                if (!reader.canReadFloat()) {
                    reader.interrupt(null);
                    return null;
                }

                return reader.readFloat();
            }
            case Immutable.FLOAT_BOXED_INDEX:
                throw new IllegalStateException();
            case Immutable.DOUBLE_INDEX: {
                if (!reader.canReadDouble()) {
                    reader.interrupt(null);
                    return null;
                }

                return reader.readDouble();
            }
            case Immutable.DOUBLE_BOXED_INDEX:
                throw new IllegalStateException();
            case Immutable.STRING_INDEX: {
                String value = reader.readString();

                if (reader.interrupted())
                    reader.interrupt(null);

                return value;
            }
            case Immutable.DATE_INDEX: {
                if (!reader.canReadDate()) {
                    reader.interrupt(null);
                    return null;
                }

                return reader.readDate();
            }
            case Immutable.BIG_INTEGER_INDEX: {
                java.math.BigInteger value = reader.readBigInteger();

                if (reader.interrupted())
                    reader.interrupt(null);

                return value; // BigInteger
            }
            case Immutable.DECIMAL_INDEX: {
                java.math.BigDecimal value = reader.readDecimal();

                if (reader.interrupted())
                    reader.interrupt(null);

                return value; // BigDecimal
            }
            case Immutable.BINARY_INDEX: {
                byte[] value = reader.readBinary();

                if (reader.interrupted())
                    reader.interrupt(null);

                return value;
            }
            case NULL:
                return null;
            case REMOVAL:
                return TKeyedEntry.REMOVAL;
            case CUSTOM: {
                byte[] value = reader.readBinary();

                if (reader.interrupted())
                    reader.interrupt(null);

                Serializer serializer = Workspace.getSerializer();

                if (serializer == null)
                    throw new RuntimeException(Strings.MISSING_CUSTOM_SERIALIZER);

                return serializer.deserialize(value);
            }
            default:
                throw new IllegalStateException();
        }
    }
}
