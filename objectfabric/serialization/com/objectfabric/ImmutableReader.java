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

import com.objectfabric.misc.Debug;
import com.objectfabric.misc.Log;
import com.objectfabric.misc.PlatformAdapter;
import com.objectfabric.misc.PlatformClass;
import com.objectfabric.misc.ThreadAssert;
import com.objectfabric.misc.ThreadAssert.SingleThreaded;

@SingleThreaded
class ImmutableReader extends BufferReader {

    private char[] _chars = new char[16];

    private int _charCount;

    protected ImmutableReader() {
    }

    void reset() {
        if (Debug.COMMUNICATIONS)
            ThreadAssert.getOrCreateCurrent().resetReaderDebugCounter(this);
    }

    /*
     * Fixed lengths.
     */

    private static final int BYTE_LENGTH = 1;

    public final boolean canReadByte() {
        int needed = BYTE_LENGTH;

        if (Debug.COMMUNICATIONS && ImmutableWriter.getCheckCommunications()) {
            Helper.getInstance().setExpectedClass(ImmutableClass.BYTE_INDEX);
            needed += ImmutableWriter.DEBUG_OVERHEAD;
        }

        return remaining() >= needed;
    }

    public final byte readByte() {
        return readByte(0);
    }

    public final byte readByte(int tag) {
        if (Debug.COMMUNICATIONS && ImmutableWriter.getCheckCommunications())
            assertDebugInfo(ImmutableClass.BYTE_INDEX, tag);

        return readByteFromBuffer();
    }

    public final boolean canReadByteBoxed() {
        int needed = BOOLEAN_LENGTH + BYTE_LENGTH;

        if (Debug.COMMUNICATIONS && ImmutableWriter.getCheckCommunications()) {
            Helper.getInstance().setExpectedClass(ImmutableClass.BYTE_BOXED_INDEX);
            needed += ImmutableWriter.DEBUG_OVERHEAD;
        }

        return remaining() >= needed;
    }

    public final Byte readByteBoxed() {
        if (Debug.COMMUNICATIONS && ImmutableWriter.getCheckCommunications())
            assertDebugInfo(ImmutableClass.BYTE_BOXED_INDEX, 0);

        if (readBooleanFromBuffer())
            return new Byte(readByteFromBuffer());

        return null; // Marker Byte
    }

    //

    private static final int BOOLEAN_LENGTH = 1;

    public final boolean canReadBoolean() {
        int needed = BOOLEAN_LENGTH;

        if (Debug.COMMUNICATIONS && ImmutableWriter.getCheckCommunications()) {
            Helper.getInstance().setExpectedClass(ImmutableClass.BOOLEAN_INDEX);
            needed += ImmutableWriter.DEBUG_OVERHEAD;
        }

        return remaining() >= needed;
    }

    public final boolean readBoolean() {
        if (Debug.COMMUNICATIONS && ImmutableWriter.getCheckCommunications())
            assertDebugInfo(ImmutableClass.BOOLEAN_INDEX, 0);

        return readBooleanFromBuffer();
    }

    public final boolean canReadBooleanBoxed() {
        int needed = BOOLEAN_LENGTH + BOOLEAN_LENGTH;

        if (Debug.COMMUNICATIONS && ImmutableWriter.getCheckCommunications()) {
            Helper.getInstance().setExpectedClass(ImmutableClass.BOOLEAN_BOXED_INDEX);
            needed += ImmutableWriter.DEBUG_OVERHEAD;
        }

        return remaining() >= needed;
    }

    public final Boolean readBooleanBoxed() {
        if (Debug.COMMUNICATIONS && ImmutableWriter.getCheckCommunications())
            assertDebugInfo(ImmutableClass.BOOLEAN_BOXED_INDEX, 0);

        if (readBooleanFromBuffer())
            return new Boolean(readBooleanFromBuffer());

        return null; // Marker Boolean
    }

    //

    private static final int SHORT_LENGTH = Short.SIZE / 8;

    public final boolean canReadShort() {
        int needed = SHORT_LENGTH;

        if (Debug.COMMUNICATIONS && ImmutableWriter.getCheckCommunications()) {
            Helper.getInstance().setExpectedClass(ImmutableClass.SHORT_INDEX);
            needed += ImmutableWriter.DEBUG_OVERHEAD;
        }

        return remaining() >= needed;
    }

    public final short readShort() {
        if (Debug.COMMUNICATIONS && ImmutableWriter.getCheckCommunications())
            assertDebugInfo(ImmutableClass.SHORT_INDEX, 0);

        return readShortFromBuffer();
    }

    public final boolean canReadShortBoxed() {
        int needed = BOOLEAN_LENGTH + SHORT_LENGTH;

        if (Debug.COMMUNICATIONS && ImmutableWriter.getCheckCommunications()) {
            Helper.getInstance().setExpectedClass(ImmutableClass.SHORT_BOXED_INDEX);
            needed += ImmutableWriter.DEBUG_OVERHEAD;
        }

        return remaining() >= needed;
    }

    public final Short readShortBoxed() {
        if (Debug.COMMUNICATIONS && ImmutableWriter.getCheckCommunications())
            assertDebugInfo(ImmutableClass.SHORT_BOXED_INDEX, 0);

        if (readBooleanFromBuffer())
            return new Short(readShortFromBuffer());

        return null; // Marker Short
    }

    //

    private static final int CHARACTER_LENGTH = Character.SIZE / 8;

    public final boolean canReadCharacter() {
        int needed = CHARACTER_LENGTH;

        if (Debug.COMMUNICATIONS && ImmutableWriter.getCheckCommunications()) {
            Helper.getInstance().setExpectedClass(ImmutableClass.CHARACTER_INDEX);
            needed += ImmutableWriter.DEBUG_OVERHEAD;
        }

        return remaining() >= needed;
    }

    public final char readCharacter() {
        if (Debug.COMMUNICATIONS && ImmutableWriter.getCheckCommunications())
            assertDebugInfo(ImmutableClass.CHARACTER_INDEX, 0);

        return readCharacterFromBuffer();
    }

    public final boolean canReadCharacterBoxed() {
        int needed = BOOLEAN_LENGTH + CHARACTER_LENGTH;

        if (Debug.COMMUNICATIONS && ImmutableWriter.getCheckCommunications()) {
            Helper.getInstance().setExpectedClass(ImmutableClass.CHARACTER_BOXED_INDEX);
            needed += ImmutableWriter.DEBUG_OVERHEAD;
        }

        return remaining() >= needed;
    }

    public final Character readCharacterBoxed() {
        if (Debug.COMMUNICATIONS && ImmutableWriter.getCheckCommunications())
            assertDebugInfo(ImmutableClass.CHARACTER_BOXED_INDEX, 0);

        if (readBooleanFromBuffer())
            return new Character(readCharacterFromBuffer());

        return null; // Marker Character
    }

    //

    private static final int INTEGER_LENGTH = Integer.SIZE / 8;

    public final boolean canReadInteger() {
        int needed = INTEGER_LENGTH;

        if (Debug.COMMUNICATIONS && ImmutableWriter.getCheckCommunications()) {
            Helper.getInstance().setExpectedClass(ImmutableClass.INTEGER_INDEX);
            needed += ImmutableWriter.DEBUG_OVERHEAD;
        }

        return remaining() >= needed;
    }

    public final int readInteger() {
        if (Debug.COMMUNICATIONS && ImmutableWriter.getCheckCommunications())
            assertDebugInfo(ImmutableClass.INTEGER_INDEX, 0);

        return readIntegerFromBuffer();
    }

    public final boolean canReadIntegerBoxed() {
        int needed = BOOLEAN_LENGTH + INTEGER_LENGTH;

        if (Debug.COMMUNICATIONS && ImmutableWriter.getCheckCommunications()) {
            Helper.getInstance().setExpectedClass(ImmutableClass.INTEGER_BOXED_INDEX);
            needed += ImmutableWriter.DEBUG_OVERHEAD;
        }

        return remaining() >= needed;
    }

    public final Integer readIntegerBoxed() {
        if (Debug.COMMUNICATIONS && ImmutableWriter.getCheckCommunications())
            assertDebugInfo(ImmutableClass.INTEGER_BOXED_INDEX, 0);

        if (readBooleanFromBuffer())
            return new Integer(readIntegerFromBuffer());

        return null; // Marker Integer
    }

    //

    private static final int LONG_LENGTH = Long.SIZE / 8;

    public final boolean canReadLong() {
        int needed = LONG_LENGTH;

        if (Debug.COMMUNICATIONS && ImmutableWriter.getCheckCommunications()) {
            Helper.getInstance().setExpectedClass(ImmutableClass.LONG_INDEX);
            needed += ImmutableWriter.DEBUG_OVERHEAD;
        }

        return remaining() >= needed;
    }

    public final long readLong() {
        if (Debug.COMMUNICATIONS && ImmutableWriter.getCheckCommunications())
            assertDebugInfo(ImmutableClass.LONG_INDEX, 0);

        return readLongFromBuffer();
    }

    public final boolean canReadLongBoxed() {
        int needed = BOOLEAN_LENGTH + LONG_LENGTH;

        if (Debug.COMMUNICATIONS && ImmutableWriter.getCheckCommunications()) {
            Helper.getInstance().setExpectedClass(ImmutableClass.LONG_BOXED_INDEX);
            needed += ImmutableWriter.DEBUG_OVERHEAD;
        }

        return remaining() >= needed;
    }

    public final Long readLongBoxed() {
        if (Debug.COMMUNICATIONS && ImmutableWriter.getCheckCommunications())
            assertDebugInfo(ImmutableClass.LONG_BOXED_INDEX, 0);

        if (readBooleanFromBuffer())
            return new Long(readLongFromBuffer());

        return null; // Marker Long
    }

    //

    private static final int DATE_LENGTH = LONG_LENGTH;

    public final boolean canReadDate() {
        int needed = DATE_LENGTH;

        if (Debug.COMMUNICATIONS && ImmutableWriter.getCheckCommunications()) {
            Helper.getInstance().setExpectedClass(ImmutableClass.DATE_INDEX);
            needed += ImmutableWriter.DEBUG_OVERHEAD;
        }

        return remaining() >= needed;
    }

    public final java.util.Date readDate() {
        if (Debug.COMMUNICATIONS && ImmutableWriter.getCheckCommunications())
            assertDebugInfo(ImmutableClass.DATE_INDEX, 0);

        long ticks = readLongFromBuffer();

        if (ticks >= 0)
            return new java.util.Date((ticks - 621355968000000000L) / 10000);

        return null; // Marker Date
    }

    /*
     * Non fixed lengths.
     */

    private static final int FLOAT_DEBUG = 0;

    private static final int FLOAT_VALUE = 1;

    @SuppressWarnings("fallthrough")
    public final float readFloat() {
        if (Debug.COMMUNICATIONS && ImmutableWriter.getCheckCommunications()) {
            int step = FLOAT_DEBUG;

            if (interrupted())
                step = resumeInt();

            switch (step) {
                case FLOAT_DEBUG: {
                    if (!canReadDebugInfo()) {
                        interruptInt(FLOAT_DEBUG);
                        return 0;
                    }

                    Helper.getInstance().setExpectedClass(ImmutableClass.FLOAT_INDEX);
                    assertDebugInfo(ImmutableClass.FLOAT_INDEX, 0);
                }
                case FLOAT_VALUE: {
                    float value = readFloatNoDebug();

                    if (interrupted()) {
                        interruptInt(FLOAT_VALUE);
                        return 0;
                    }

                    return value;
                }
                default:
                    throw new IllegalStateException();
            }
        }

        return readFloatNoDebug();
    }

    private final float readFloatNoDebug() {
        if (interrupted())
            resume();

        if ((getFlags() & CompileTimeSettings.SERIALIZATION_BINARY_FLOAT_AND_DOUBLE) == 0) {
            String value = readString(false, 0);

            if (interrupted()) {
                interrupt(null);
                return 0;
            }

            return PlatformAdapter.stringToFloat(value);
        }

        if (remaining() < INTEGER_LENGTH) {
            interrupt(null);
            return 0;
        }

        int i = readIntegerFromBuffer();
        return PlatformAdapter.intToFloat(i);
    }

    private static final int FLOAT_BOXED_DEBUG = -1;

    private static final int FLOAT_BOXED_NULL = 0;

    private static final int FLOAT_BOXED_VALUE = 1;

    @SuppressWarnings("fallthrough")
    public final Float readFloatBoxed() {
        int step = Debug.COMMUNICATIONS && ImmutableWriter.getCheckCommunications() ? FLOAT_BOXED_DEBUG : FLOAT_BOXED_NULL;

        if (interrupted())
            step = resumeInt();

        if (Debug.COMMUNICATIONS && ImmutableWriter.getCheckCommunications()) {
            if (step == FLOAT_BOXED_DEBUG) {
                if (!canReadDebugInfo()) {
                    interruptInt(FLOAT_BOXED_DEBUG);
                    return null; // Marker Float
                }

                Helper.getInstance().setExpectedClass(ImmutableClass.FLOAT_BOXED_INDEX);
                assertDebugInfo(ImmutableClass.FLOAT_BOXED_INDEX, 0);
                step = FLOAT_BOXED_NULL;
            }
        }

        switch (step) {
            case FLOAT_BOXED_NULL: {
                if (!canReadBoolean()) {
                    interruptInt(FLOAT_BOXED_NULL);
                    return null; // Marker Float
                }

                if (readBooleanFromBuffer())
                    return null; // Marker Float
            }
            case FLOAT_BOXED_VALUE: {
                float value = readFloatNoDebug();

                if (interrupted()) {
                    interruptInt(FLOAT_BOXED_VALUE);
                    return null; // Marker Float
                }

                return new Float(value); // Converter
            }
            default:
                throw new IllegalStateException();
        }
    }

    //

    private static final int DOUBLE_DEBUG = 0;

    private static final int DOUBLE_VALUE = 1;

    @SuppressWarnings("fallthrough")
    public final double readDouble() {
        if (Debug.COMMUNICATIONS && ImmutableWriter.getCheckCommunications()) {
            int step = DOUBLE_DEBUG;

            if (interrupted())
                step = resumeInt();

            switch (step) {
                case DOUBLE_DEBUG: {
                    if (!canReadDebugInfo()) {
                        interruptInt(DOUBLE_DEBUG);
                        return 0;
                    }

                    Helper.getInstance().setExpectedClass(ImmutableClass.DOUBLE_INDEX);
                    assertDebugInfo(ImmutableClass.DOUBLE_INDEX, 0);
                }
                case DOUBLE_VALUE: {
                    double value = readDoubleNoDebug();

                    if (interrupted()) {
                        interruptInt(DOUBLE_VALUE);
                        return 0;
                    }

                    return value;
                }
                default:
                    throw new IllegalStateException();
            }
        }

        return readDoubleNoDebug();
    }

    private final double readDoubleNoDebug() {
        if (interrupted())
            resume();

        if ((getFlags() & CompileTimeSettings.SERIALIZATION_BINARY_FLOAT_AND_DOUBLE) == 0) {
            String value = readString(false, 0);

            if (interrupted()) {
                interrupt(null);
                return 0;
            }

            return PlatformAdapter.stringToDouble(value);
        }

        if (remaining() < LONG_LENGTH) {
            interrupt(null);
            return 0;
        }

        return PlatformAdapter.longToDouble(readLongFromBuffer());
    }

    private static final int DOUBLE_BOXED_DEBUG = -1;

    private static final int DOUBLE_BOXED_NULL = 0;

    private static final int DOUBLE_BOXED_VALUE = 1;

    @SuppressWarnings("fallthrough")
    public final Double readDoubleBoxed() {
        int step = Debug.COMMUNICATIONS && ImmutableWriter.getCheckCommunications() ? DOUBLE_BOXED_DEBUG : DOUBLE_BOXED_NULL;

        if (interrupted())
            step = resumeInt();

        if (Debug.COMMUNICATIONS && ImmutableWriter.getCheckCommunications()) {
            if (step == DOUBLE_BOXED_DEBUG) {
                if (!canReadDebugInfo()) {
                    interruptInt(DOUBLE_BOXED_DEBUG);
                    return null; // Marker Double
                }

                Helper.getInstance().setExpectedClass(ImmutableClass.DOUBLE_BOXED_INDEX);
                assertDebugInfo(ImmutableClass.DOUBLE_BOXED_INDEX, 0);
                step = DOUBLE_BOXED_NULL;
            }
        }

        switch (step) {
            case DOUBLE_BOXED_NULL: {
                if (!canReadBoolean()) {
                    interruptInt(DOUBLE_BOXED_NULL);
                    return null; // Marker Double
                }

                if (readBooleanFromBuffer())
                    return null; // Marker Double
            }
            case DOUBLE_BOXED_VALUE: {
                double value = readDoubleNoDebug();

                if (interrupted()) {
                    interruptInt(DOUBLE_BOXED_VALUE);
                    return null; // Marker Double
                }

                return new Double(value); // Converter
            }
            default:
                throw new IllegalStateException();
        }
    }

    //

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

                Helper.getInstance().setExpectedClass(ImmutableClass.STRING_INDEX);
                assertDebugInfo(ImmutableClass.STRING_INDEX, tag);
            }
        }

        for (;;) {
            if (value < 0) {
                if (remaining() == 0) {
                    interruptInt(-1);
                    return null;
                }

                value = readByteFromBuffer() & 0xff;
            }

            if ((value & ImmutableWriter.STRING_ENC_DOES_NOT_FIT_ON_1_BYTE_MASK) == 0) {
                append((char) value);
            } else if ((value & ImmutableWriter.STRING_ENC_DOES_NOT_FIT_ON_2_BYTES_MASK) == 0) {
                if (remaining() == 0) {
                    interruptInt(value);
                    return null;
                }

                value &= ~ImmutableWriter.STRING_ENC_DOES_NOT_FIT_ON_1_BYTE_MASK;
                value = value << 8 | readByteFromBuffer() & 0xff;
                append((char) value);
            } else if ((value & ImmutableWriter.STRING_ENC_EOF_MASK) == 0) {
                if (remaining() < CHARACTER_LENGTH) {
                    interruptInt(value);
                    return null;
                }

                append(readCharacterFromBuffer());
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
            PlatformAdapter.arraycopy(_chars, 0, temp, 0, _chars.length);
            _chars = temp;
        }

        _chars[_charCount++] = value;
    }

    //

    public final byte[] readBinary() {
        return readBinary(ImmutableClass.BINARY_INDEX);
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

                Helper.getInstance().setExpectedClass(classId);
                assertDebugInfo(classId, 0);
            }
        }

        if (index < 0) {
            if (remaining() < INTEGER_LENGTH) {
                interrupt(null);
                interruptInt(-1);
                return null;
            }

            int length = readIntegerFromBuffer();

            if (length < 0)
                return null;

            array = new byte[length];
            index = 0;
        }

        for (; index < array.length; index++) {
            if (remaining() == 0) {
                interrupt(array);
                interruptInt(index);
                return null;
            }

            array[index] = readByteFromBuffer();
        }

        return array;
    }

    //

    public final java.math.BigInteger readBigInteger() {
        byte[] data = readBinary(ImmutableClass.BIG_INTEGER_INDEX);

        if (data != null)
            return new java.math.BigInteger(data);

        return null; // Marker BigInteger
    }

    //

    public final java.math.BigDecimal readDecimal() {
        byte[] a = readBinary(ImmutableClass.DECIMAL_INDEX);

        if (a != null) {
            byte[] b = new byte[a.length - 4];
            PlatformAdapter.arraycopy(a, 0, b, 0, b.length);
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
            String name = ImmutableClass.ALL.get(classId).toString();
            Log.write(PlatformClass.getSimpleName(getClass()) + ", " + ThreadAssert.getOrCreateCurrent().getReaderDebugCounter(this) + ", class: " + name + ", tag: " + tag);
        }

        Debug.assertion(classId == Helper.getInstance().getExpectedClass());
        long readDebugCounter = readLongFromBuffer();
        byte readClassId = readByteFromBuffer();
        int readTag = readIntegerFromBuffer();
        int custom1 = readIntegerFromBuffer();
        int custom2 = readIntegerFromBuffer();

        Debug.assertion(readTag == tag);
        Debug.assertion(readClassId == (byte) classId);

        /*
         * Ignore when exit code as it is written by another writer.
         */
        if (tag != TObjectWriter.DEBUG_TAG_CODE || !TObjectWriter.isExitCode(peekByteFromBuffer())) {
            long debugCounter = ThreadAssert.getOrCreateCurrent().getAndIncrementReaderDebugCounter(this);
            Debug.assertion(readDebugCounter == debugCounter);
            int localCustom1 = getCustomDebugInfo1();

            if (custom1 > 0 && localCustom1 > 0)
                Debug.assertion(custom1 == localCustom1);

            int localCustom2 = getCustomDebugInfo2();
            Debug.assertion(custom2 == localCustom2);
        }
    }

    int getCustomDebugInfo1() {
        if (!Debug.COMMUNICATIONS && ImmutableWriter.getCheckCommunications())
            throw new IllegalStateException();

        return 0;
    }

    int getCustomDebugInfo2() {
        if (!Debug.COMMUNICATIONS && ImmutableWriter.getCheckCommunications())
            throw new IllegalStateException();

        return 0;
    }
}
