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

import org.objectfabric.ThreadAssert.SingleThreaded;

@SingleThreaded
class ImmutableReader extends Continuation {

    private Buff _buff;

    private byte _serializationVersion;

    private char[] _chars = new char[16];

    private int _charCount;

    protected ImmutableReader(List<Object> interruptionStack) {
        super(interruptionStack);
    }

    final Buff getBuff() {
        return _buff;
    }

    final void setBuff(Buff buff) {
        _buff = buff;
    }

    //

    void reset() {
        if (Debug.COMMUNICATIONS)
            ThreadAssert.getOrCreateCurrent().resetReaderDebugCounter(this);
    }

    void clean() {
        _chars = null;
        _charCount = 0;
    }

    private final int remaining() {
        /*
         * TODO: always say 0 the first time to test all interruption sites.
         */
        return _buff.limit() - _buff.position();
    }

    void startRead() {
        _serializationVersion = _buff.getByte();
    }

    private final boolean getBoolean() {
        return _buff.getByte() != 0;
    }

    /*
     * Fixed lengths.
     */

    private static final int BYTE_LENGTH = 1;

    public final boolean canReadByte() {
        int needed = BYTE_LENGTH;

        if (Debug.COMMUNICATIONS && ImmutableWriter.getCheckCommunications()) {
            Helper.instance().setExpectedClass(Immutable.BYTE_INDEX);
            needed += ImmutableWriter.DEBUG_OVERHEAD;
        }

        return remaining() >= needed;
    }

    public final byte readByte() {
        return readByte(0);
    }

    public final byte readByte(int tag) {
        if (Debug.COMMUNICATIONS && ImmutableWriter.getCheckCommunications())
            assertDebugInfo(Immutable.BYTE_INDEX, tag);

        return _buff.getByte();
    }

    public final boolean canReadByteBoxed() {
        int needed = BOOLEAN_LENGTH + BYTE_LENGTH;

        if (Debug.COMMUNICATIONS && ImmutableWriter.getCheckCommunications()) {
            Helper.instance().setExpectedClass(Immutable.BYTE_BOXED_INDEX);
            needed += ImmutableWriter.DEBUG_OVERHEAD;
        }

        return remaining() >= needed;
    }

    public final Byte readByteBoxed() {
        if (Debug.COMMUNICATIONS && ImmutableWriter.getCheckCommunications())
            assertDebugInfo(Immutable.BYTE_BOXED_INDEX, 0);

        if (getBoolean()) {
            byte value = _buff.getByte();
            return new Byte(value);
        }

        return null; // Marker Byte
    }

    //

    private static final int BOOLEAN_LENGTH = 1;

    public final boolean canReadBoolean() {
        int needed = BOOLEAN_LENGTH;

        if (Debug.COMMUNICATIONS && ImmutableWriter.getCheckCommunications()) {
            Helper.instance().setExpectedClass(Immutable.BOOLEAN_INDEX);
            needed += ImmutableWriter.DEBUG_OVERHEAD;
        }

        return remaining() >= needed;
    }

    public final boolean readBoolean() {
        if (Debug.COMMUNICATIONS && ImmutableWriter.getCheckCommunications())
            assertDebugInfo(Immutable.BOOLEAN_INDEX, 0);

        return getBoolean();
    }

    public final boolean canReadBooleanBoxed() {
        int needed = BOOLEAN_LENGTH + BOOLEAN_LENGTH;

        if (Debug.COMMUNICATIONS && ImmutableWriter.getCheckCommunications()) {
            Helper.instance().setExpectedClass(Immutable.BOOLEAN_BOXED_INDEX);
            needed += ImmutableWriter.DEBUG_OVERHEAD;
        }

        return remaining() >= needed;
    }

    public final Boolean readBooleanBoxed() {
        if (Debug.COMMUNICATIONS && ImmutableWriter.getCheckCommunications())
            assertDebugInfo(Immutable.BOOLEAN_BOXED_INDEX, 0);

        if (getBoolean())
            return new Boolean(getBoolean());

        return null; // Marker Boolean
    }

    //

    private static final int SHORT_LENGTH = Short.SIZE / 8;

    public final boolean canReadShort() {
        int needed = SHORT_LENGTH;

        if (Debug.COMMUNICATIONS && ImmutableWriter.getCheckCommunications()) {
            Helper.instance().setExpectedClass(Immutable.SHORT_INDEX);
            needed += ImmutableWriter.DEBUG_OVERHEAD;
        }

        return remaining() >= needed;
    }

    public final short readShort() {
        if (Debug.COMMUNICATIONS && ImmutableWriter.getCheckCommunications())
            assertDebugInfo(Immutable.SHORT_INDEX, 0);

        return _buff.getShort();
    }

    public final boolean canReadShortBoxed() {
        int needed = BOOLEAN_LENGTH + SHORT_LENGTH;

        if (Debug.COMMUNICATIONS && ImmutableWriter.getCheckCommunications()) {
            Helper.instance().setExpectedClass(Immutable.SHORT_BOXED_INDEX);
            needed += ImmutableWriter.DEBUG_OVERHEAD;
        }

        return remaining() >= needed;
    }

    public final Short readShortBoxed() {
        if (Debug.COMMUNICATIONS && ImmutableWriter.getCheckCommunications())
            assertDebugInfo(Immutable.SHORT_BOXED_INDEX, 0);

        if (getBoolean())
            return new Short(_buff.getShort());

        return null; // Marker Short
    }

    //

    private static final int CHARACTER_LENGTH = Character.SIZE / 8;

    public final boolean canReadCharacter() {
        int needed = CHARACTER_LENGTH;

        if (Debug.COMMUNICATIONS && ImmutableWriter.getCheckCommunications()) {
            Helper.instance().setExpectedClass(Immutable.CHARACTER_INDEX);
            needed += ImmutableWriter.DEBUG_OVERHEAD;
        }

        return remaining() >= needed;
    }

    public final char readCharacter() {
        if (Debug.COMMUNICATIONS && ImmutableWriter.getCheckCommunications())
            assertDebugInfo(Immutable.CHARACTER_INDEX, 0);

        return _buff.getChar();
    }

    public final boolean canReadCharacterBoxed() {
        int needed = BOOLEAN_LENGTH + CHARACTER_LENGTH;

        if (Debug.COMMUNICATIONS && ImmutableWriter.getCheckCommunications()) {
            Helper.instance().setExpectedClass(Immutable.CHARACTER_BOXED_INDEX);
            needed += ImmutableWriter.DEBUG_OVERHEAD;
        }

        return remaining() >= needed;
    }

    public final Character readCharacterBoxed() {
        if (Debug.COMMUNICATIONS && ImmutableWriter.getCheckCommunications())
            assertDebugInfo(Immutable.CHARACTER_BOXED_INDEX, 0);

        if (getBoolean())
            return new Character(_buff.getChar());

        return null; // Marker Character
    }

    //

    private static final int INTEGER_LENGTH = Integer.SIZE / 8;

    public final boolean canReadInteger() {
        int needed = INTEGER_LENGTH;

        if (Debug.COMMUNICATIONS && ImmutableWriter.getCheckCommunications()) {
            Helper.instance().setExpectedClass(Immutable.INTEGER_INDEX);
            needed += ImmutableWriter.DEBUG_OVERHEAD;
        }

        return remaining() >= needed;
    }

    public final int readInteger() {
        if (Debug.COMMUNICATIONS && ImmutableWriter.getCheckCommunications())
            assertDebugInfo(Immutable.INTEGER_INDEX, 0);

        return _buff.getInt();
    }

    public final boolean canReadIntegerBoxed() {
        int needed = BOOLEAN_LENGTH + INTEGER_LENGTH;

        if (Debug.COMMUNICATIONS && ImmutableWriter.getCheckCommunications()) {
            Helper.instance().setExpectedClass(Immutable.INTEGER_BOXED_INDEX);
            needed += ImmutableWriter.DEBUG_OVERHEAD;
        }

        return remaining() >= needed;
    }

    public final Integer readIntegerBoxed() {
        if (Debug.COMMUNICATIONS && ImmutableWriter.getCheckCommunications())
            assertDebugInfo(Immutable.INTEGER_BOXED_INDEX, 0);

        if (getBoolean())
            return new Integer(_buff.getInt());

        return null; // Marker Integer
    }

    //

    private static final int LONG_LENGTH = Long.SIZE / 8;

    public final boolean canReadLong() {
        int needed = LONG_LENGTH;

        if (Debug.COMMUNICATIONS && ImmutableWriter.getCheckCommunications()) {
            Helper.instance().setExpectedClass(Immutable.LONG_INDEX);
            needed += ImmutableWriter.DEBUG_OVERHEAD;
        }

        return remaining() >= needed;
    }

    public final long readLong() {
        if (Debug.COMMUNICATIONS && ImmutableWriter.getCheckCommunications())
            assertDebugInfo(Immutable.LONG_INDEX, 0);

        return _buff.getLong();
    }

    public final boolean canReadLongBoxed() {
        int needed = BOOLEAN_LENGTH + LONG_LENGTH;

        if (Debug.COMMUNICATIONS && ImmutableWriter.getCheckCommunications()) {
            Helper.instance().setExpectedClass(Immutable.LONG_BOXED_INDEX);
            needed += ImmutableWriter.DEBUG_OVERHEAD;
        }

        return remaining() >= needed;
    }

    public final Long readLongBoxed() {
        if (Debug.COMMUNICATIONS && ImmutableWriter.getCheckCommunications())
            assertDebugInfo(Immutable.LONG_BOXED_INDEX, 0);

        if (getBoolean())
            return new Long(_buff.getLong());

        return null; // Marker Long
    }

    //

    private static final int FLOAT_LENGTH = INTEGER_LENGTH;

    public final boolean canReadFloat() {
        int needed = FLOAT_LENGTH;

        if (Debug.COMMUNICATIONS && ImmutableWriter.getCheckCommunications()) {
            Helper.instance().setExpectedClass(Immutable.FLOAT_INDEX);
            needed += ImmutableWriter.DEBUG_OVERHEAD;
        }

        return remaining() >= needed;
    }

    public final float readFloat() {
        if (Debug.COMMUNICATIONS && ImmutableWriter.getCheckCommunications())
            assertDebugInfo(Immutable.FLOAT_INDEX, 0);

        int value = _buff.getInt();
        return Platform.get().intToFloat(value);
    }

    public final boolean canReadFloatBoxed() {
        int needed = BOOLEAN_LENGTH + FLOAT_LENGTH;

        if (Debug.COMMUNICATIONS && ImmutableWriter.getCheckCommunications()) {
            Helper.instance().setExpectedClass(Immutable.FLOAT_BOXED_INDEX);
            needed += ImmutableWriter.DEBUG_OVERHEAD;
        }

        return remaining() >= needed;
    }

    public final Float readFloatBoxed() {
        if (Debug.COMMUNICATIONS && ImmutableWriter.getCheckCommunications())
            assertDebugInfo(Immutable.FLOAT_BOXED_INDEX, 0);

        if (getBoolean()) {
            int bits = _buff.getInt();
            float value = Platform.get().intToFloat(bits);
            return new Float(value); // Converter
        }

        return null; // Marker Float
    }

    //

    private static final int DOUBLE_LENGTH = LONG_LENGTH;

    public final boolean canReadDouble() {
        int needed = DOUBLE_LENGTH;

        if (Debug.COMMUNICATIONS && ImmutableWriter.getCheckCommunications()) {
            Helper.instance().setExpectedClass(Immutable.DOUBLE_INDEX);
            needed += ImmutableWriter.DEBUG_OVERHEAD;
        }

        return remaining() >= needed;
    }

    public final double readDouble() {
        if (Debug.COMMUNICATIONS && ImmutableWriter.getCheckCommunications())
            assertDebugInfo(Immutable.DOUBLE_INDEX, 0);

        long value = _buff.getLong();
        return Platform.get().longToDouble(value);
    }

    public final boolean canReadDoubleBoxed() {
        int needed = BOOLEAN_LENGTH + DOUBLE_LENGTH;

        if (Debug.COMMUNICATIONS && ImmutableWriter.getCheckCommunications()) {
            Helper.instance().setExpectedClass(Immutable.DOUBLE_BOXED_INDEX);
            needed += ImmutableWriter.DEBUG_OVERHEAD;
        }

        return remaining() >= needed;
    }

    public final Double readDoubleBoxed() {
        if (Debug.COMMUNICATIONS && ImmutableWriter.getCheckCommunications())
            assertDebugInfo(Immutable.DOUBLE_BOXED_INDEX, 0);

        if (getBoolean()) {
            long bits = _buff.getLong();
            double value = Platform.get().longToDouble(bits);
            return new Double(value); // Converter
        }

        return null; // Marker Double
    }

    //

    private static final int DATE_LENGTH = LONG_LENGTH;

    public final boolean canReadDate() {
        int needed = DATE_LENGTH;

        if (Debug.COMMUNICATIONS && ImmutableWriter.getCheckCommunications()) {
            Helper.instance().setExpectedClass(Immutable.DATE_INDEX);
            needed += ImmutableWriter.DEBUG_OVERHEAD;
        }

        return remaining() >= needed;
    }

    public final java.util.Date readDate() {
        if (Debug.COMMUNICATIONS && ImmutableWriter.getCheckCommunications())
            assertDebugInfo(Immutable.DATE_INDEX, 0);

        long ticks = _buff.getLong();

        if (ticks >= 0)
            return new java.util.Date((ticks - 621355968000000000L) / 10000);

        return null; // Marker Date
    }

    /*
     * Non fixed lengths.
     */

    public final String readString() {
        return readString(true, 0);
    }

    public final String readString(boolean debug, int tag) {
        int value;

        if (interrupted())
            value = resumeInt();
        else {
            value = Debug.COMMUNICATIONS && ImmutableWriter.getCheckCommunications() && debug ? -2 : -1;
            _charCount = 0;
        }

        if (Debug.COMMUNICATIONS && ImmutableWriter.getCheckCommunications() && debug) {
            if (value == -2) {
                if (!canReadDebugInfo()) {
                    interruptInt(-2);
                    return null;
                }

                Helper.instance().setExpectedClass(Immutable.STRING_INDEX);
                assertDebugInfo(Immutable.STRING_INDEX, tag);
            }
        }

        for (;;) {
            if (value < 0) {
                if (remaining() == 0) {
                    interruptInt(-1);
                    return null;
                }

                value = _buff.getByte() & 0xff;
            }

            if ((value & ImmutableWriter.STRING_ENC_DOES_NOT_FIT_ON_1_BYTE_MASK) == 0) {
                append((char) value);
            } else if ((value & ImmutableWriter.STRING_ENC_DOES_NOT_FIT_ON_2_BYTES_MASK) == 0) {
                if (remaining() == 0) {
                    interruptInt(value);
                    return null;
                }

                value &= ~ImmutableWriter.STRING_ENC_DOES_NOT_FIT_ON_1_BYTE_MASK;
                value = value << 8 | _buff.getByte() & 0xff;
                append((char) value);
            } else if ((value & ImmutableWriter.STRING_ENC_EOF_MASK) == 0) {
                if (remaining() < CHARACTER_LENGTH) {
                    interruptInt(value);
                    return null;
                }

                char c = _buff.getChar();
                append(c);
            } else if ((value & ImmutableWriter.STRING_ENC_NULL_MASK) == 0)
                return new String(_chars, 0, _charCount);
            else
                return null;

            value = -1;
        }
    }

    private final void append(char value) {
        if (_charCount >= _chars.length) {
            char[] temp = new char[_chars.length << 1];
            Platform.arraycopy(_chars, 0, temp, 0, _chars.length);
            _chars = temp;
        }

        _chars[_charCount++] = value;
    }

    //

    public final byte[] readBinary() {
        return readBinary(Immutable.BINARY_INDEX);
    }

    @SuppressWarnings("null")
    private final byte[] readBinary(int classId) {
        int index = Debug.COMMUNICATIONS && ImmutableWriter.getCheckCommunications() ? -2 : -1;
        byte[] array = null;

        if (interrupted()) {
            index = resumeInt();
            array = (byte[]) resume();
        }

        if (Debug.COMMUNICATIONS && ImmutableWriter.getCheckCommunications()) {
            if (index == -2) {
                if (!canReadDebugInfo()) {
                    interrupt(null);
                    interruptInt(-2);
                    return null;
                }

                Helper.instance().setExpectedClass(classId);
                assertDebugInfo(classId, 0);
            }
        }

        if (index < 0) {
            if (remaining() < INTEGER_LENGTH) {
                interrupt(null);
                interruptInt(-1);
                return null;
            }

            int length = _buff.getInt();

            if (length < 0)
                return null;

            array = new byte[length];
            index = 0;
        }

        // TODO: use get(byte[])

        for (; index < array.length; index++) {
            if (remaining() == 0) {
                interrupt(array);
                interruptInt(index);
                return null;
            }

            array[index] = _buff.getByte();
        }

        return array;
    }

    //

    public final java.math.BigInteger readBigInteger() {
        byte[] data = readBinary(Immutable.BIG_INTEGER_INDEX);

        if (data != null)
            return new java.math.BigInteger(data);

        return null; // Marker BigInteger
    }

    //

    public final java.math.BigDecimal readDecimal() {
        byte[] a = readBinary(Immutable.DECIMAL_INDEX);

        if (a != null) {
            byte[] b = new byte[a.length - 4];
            Platform.arraycopy(a, 0, b, 0, b.length);
            int b0 = a[a.length - 4] & 0x000000ff;
            int b1 = (a[a.length - 3] << 8) & 0x0000ff00;
            int b2 = (a[a.length - 2] << 16) & 0x00ff0000;
            int b3 = (a[a.length - 1] << 24) & 0xff000000;
            int scale = b3 | b2 | b1 | b0;
            return new java.math.BigDecimal(new java.math.BigInteger(b), scale);
        }

        return null; // Marker BigDecimal
    }

    // Debug

    public final boolean canReadDebugInfo() {
        if (!Debug.COMMUNICATIONS && ImmutableWriter.getCheckCommunications())
            throw new IllegalStateException();

        return remaining() >= ImmutableWriter.DEBUG_OVERHEAD;
    }

    private final void assertDebugInfo(int classId, int tag) {
        if (!Debug.COMMUNICATIONS && ImmutableWriter.getCheckCommunications())
            throw new IllegalStateException();

        if (Debug.COMMUNICATIONS_LOG_ALL) {
            String name = Immutable.ALL.get(classId).toString();
            long counter = ThreadAssert.getOrCreateCurrent().getReaderDebugCounter(this);
            String c = Platform.get().simpleClassName(this);
            Log.write(c + ", " + counter + ", class: " + name + ", tag: " + tag);
        }

        Debug.assertion(_serializationVersion != 0);

        long readDebugCounter = _buff.getLong();
        byte readClassId = _buff.getByte();
        int readTag = _buff.getInt();
        long debugCounter = ThreadAssert.getOrCreateCurrent().getAndIncrementReaderDebugCounter(this);
        Debug.assertion(classId == Helper.instance().getExpectedClass());
        Debug.assertion(readTag == tag);
        Debug.assertion(readClassId == (byte) classId);
        Debug.assertion(readDebugCounter == debugCounter);
    }
}
