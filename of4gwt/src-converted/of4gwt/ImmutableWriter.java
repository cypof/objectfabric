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
import of4gwt.misc.Log;
import of4gwt.misc.PlatformAdapter;
import of4gwt.misc.PlatformClass;
import of4gwt.misc.ThreadAssert;
import of4gwt.misc.ThreadAssert.SingleThreaded;

@SingleThreaded
class ImmutableWriter extends BufferWriter {

    static final int DEBUG_OVERHEAD;

    private static boolean _checkCommunications = true;

    protected ImmutableWriter() {
    }

    void reset() {
        if (Debug.COMMUNICATIONS && getCheckCommunications())
            ThreadAssert.getOrCreateCurrent().resetWriterDebugCounter(this);
    }

    /*
     * Fixed lengths.
     */

    private static final int BYTE_LENGTH = 1;

    public final boolean canWriteByte() {
        int needed = BYTE_LENGTH;

        if (Debug.COMMUNICATIONS && getCheckCommunications()) {
            Helper.getInstance().setExpectedClass(ImmutableClass.BYTE_INDEX);
            needed += ImmutableWriter.DEBUG_OVERHEAD;
        }

        return remaining() >= needed;
    }

    public final void writeByte(byte value) {
        writeByte(value, 0);
    }

    public final void writeByte(byte value, int tag) {
        if (Debug.COMMUNICATIONS && getCheckCommunications())
            writeDebugInfo(ImmutableClass.BYTE_INDEX, tag);

        writeByteToBuffer(value);
    }

    public final boolean canWriteByteBoxed() {
        int needed = BOOLEAN_LENGTH + BYTE_LENGTH;

        if (Debug.COMMUNICATIONS && getCheckCommunications()) {
            Helper.getInstance().setExpectedClass(ImmutableClass.BYTE_BOXED_INDEX);
            needed += ImmutableWriter.DEBUG_OVERHEAD;
        }

        return remaining() >= needed;
    }

    public final void writeByteBoxed(Byte value) {
        if (Debug.COMMUNICATIONS && getCheckCommunications())
            writeDebugInfo(ImmutableClass.BYTE_BOXED_INDEX, 0);

        if (value == null) // Marker
            writeBooleanToBuffer(false);
        else {
            writeBooleanToBuffer(true);
            writeByteToBuffer(value); // Marker
        }
    }

    //

    private static final int BOOLEAN_LENGTH = 1;

    public final boolean canWriteBoolean() {
        int needed = BOOLEAN_LENGTH;

        if (Debug.COMMUNICATIONS && getCheckCommunications()) {
            Helper.getInstance().setExpectedClass(ImmutableClass.BOOLEAN_INDEX);
            needed += ImmutableWriter.DEBUG_OVERHEAD;
        }

        return remaining() >= needed;
    }

    public final void writeBoolean(boolean value) {
        if (Debug.COMMUNICATIONS && getCheckCommunications())
            writeDebugInfo(ImmutableClass.BOOLEAN_INDEX, 0);

        writeBooleanToBuffer(value);
    }

    public final boolean canWriteBooleanBoxed() {
        int needed = BOOLEAN_LENGTH + BOOLEAN_LENGTH;

        if (Debug.COMMUNICATIONS && getCheckCommunications()) {
            Helper.getInstance().setExpectedClass(ImmutableClass.BOOLEAN_BOXED_INDEX);
            needed += ImmutableWriter.DEBUG_OVERHEAD;
        }

        return remaining() >= needed;
    }

    public final void writeBooleanBoxed(Boolean value) {
        if (Debug.COMMUNICATIONS && getCheckCommunications())
            writeDebugInfo(ImmutableClass.BOOLEAN_BOXED_INDEX, 0);

        if (value == null) // Marker
            writeBooleanToBuffer(false);
        else {
            writeBooleanToBuffer(true);
            writeBooleanToBuffer(value); // Marker
        }
    }

    //

    private static final int SHORT_LENGTH = Short.SIZE / 8;

    public final boolean canWriteShort() {
        int needed = SHORT_LENGTH;

        if (Debug.COMMUNICATIONS && getCheckCommunications()) {
            Helper.getInstance().setExpectedClass(ImmutableClass.SHORT_INDEX);
            needed += ImmutableWriter.DEBUG_OVERHEAD;
        }

        return remaining() >= needed;
    }

    public final void writeShort(short value) {
        if (Debug.COMMUNICATIONS && getCheckCommunications())
            writeDebugInfo(ImmutableClass.SHORT_INDEX, 0);

        writeShortToBuffer(value);
    }

    public final boolean canWriteShortBoxed() {
        int needed = BOOLEAN_LENGTH + SHORT_LENGTH;

        if (Debug.COMMUNICATIONS && getCheckCommunications()) {
            Helper.getInstance().setExpectedClass(ImmutableClass.SHORT_BOXED_INDEX);
            needed += ImmutableWriter.DEBUG_OVERHEAD;
        }

        return remaining() >= needed;
    }

    public final void writeShortBoxed(Short value) {
        if (Debug.COMMUNICATIONS && getCheckCommunications())
            writeDebugInfo(ImmutableClass.SHORT_BOXED_INDEX, 0);

        if (value == null) // Marker
            writeBooleanToBuffer(false);
        else {
            writeBooleanToBuffer(true);
            writeShortToBuffer(value); // Marker
        }
    }

    //

    private static final int CHARACTER_LENGTH = Character.SIZE / 8;

    public final boolean canWriteCharacter() {
        int needed = CHARACTER_LENGTH;

        if (Debug.COMMUNICATIONS && getCheckCommunications()) {
            Helper.getInstance().setExpectedClass(ImmutableClass.CHARACTER_INDEX);
            needed += ImmutableWriter.DEBUG_OVERHEAD;
        }

        return remaining() >= needed;
    }

    public final void writeCharacter(char value) {
        if (Debug.COMMUNICATIONS && getCheckCommunications())
            writeDebugInfo(ImmutableClass.CHARACTER_INDEX, 0);

        writeCharacterToBuffer(value);
    }

    public final boolean canWriteCharacterBoxed() {
        int needed = BOOLEAN_LENGTH + CHARACTER_LENGTH;

        if (Debug.COMMUNICATIONS && getCheckCommunications()) {
            Helper.getInstance().setExpectedClass(ImmutableClass.CHARACTER_BOXED_INDEX);
            needed += ImmutableWriter.DEBUG_OVERHEAD;
        }

        return remaining() >= needed;
    }

    public final void writeCharacterBoxed(Character value) {
        if (Debug.COMMUNICATIONS && getCheckCommunications())
            writeDebugInfo(ImmutableClass.CHARACTER_BOXED_INDEX, 0);

        if (value == null) // Marker
            writeBooleanToBuffer(false);
        else {
            writeBooleanToBuffer(true);
            writeCharacterToBuffer(value); // Marker
        }
    }

    //

    private static final int INTEGER_LENGTH = Integer.SIZE / 8;

    public final boolean canWriteInteger() {
        int needed = INTEGER_LENGTH;

        if (Debug.COMMUNICATIONS && getCheckCommunications()) {
            Helper.getInstance().setExpectedClass(ImmutableClass.INTEGER_INDEX);
            needed += ImmutableWriter.DEBUG_OVERHEAD;
        }

        return remaining() >= needed;
    }

    public final void writeInteger(int value) {
        if (Debug.COMMUNICATIONS && getCheckCommunications())
            writeDebugInfo(ImmutableClass.INTEGER_INDEX, 0);

        writeIntegerToBuffer(value);
    }

    public final boolean canWriteIntegerBoxed() {
        int needed = BOOLEAN_LENGTH + INTEGER_LENGTH;

        if (Debug.COMMUNICATIONS && getCheckCommunications()) {
            Helper.getInstance().setExpectedClass(ImmutableClass.INTEGER_BOXED_INDEX);
            needed += ImmutableWriter.DEBUG_OVERHEAD;
        }

        return remaining() >= needed;
    }

    public final void writeIntegerBoxed(Integer value) {
        if (Debug.COMMUNICATIONS && getCheckCommunications())
            writeDebugInfo(ImmutableClass.INTEGER_BOXED_INDEX, 0);

        if (value == null) // Marker
            writeBooleanToBuffer(false);
        else {
            writeBooleanToBuffer(true);
            writeIntegerToBuffer(value); // Marker
        }
    }

    //

    private static final int LONG_LENGTH = Long.SIZE / 8;

    public final boolean canWriteLong() {
        int needed = LONG_LENGTH;

        if (Debug.COMMUNICATIONS && getCheckCommunications()) {
            Helper.getInstance().setExpectedClass(ImmutableClass.LONG_INDEX);
            needed += ImmutableWriter.DEBUG_OVERHEAD;
        }

        return remaining() >= needed;
    }

    public final void writeLong(long value) {
        if (Debug.COMMUNICATIONS && getCheckCommunications())
            writeDebugInfo(ImmutableClass.LONG_INDEX, 0);

        writeLongToBuffer(value);
    }

    public final boolean canWriteLongBoxed() {
        int needed = BOOLEAN_LENGTH + LONG_LENGTH;

        if (Debug.COMMUNICATIONS && getCheckCommunications()) {
            Helper.getInstance().setExpectedClass(ImmutableClass.LONG_BOXED_INDEX);
            needed += ImmutableWriter.DEBUG_OVERHEAD;
        }

        return remaining() >= needed;
    }

    public final void writeLongBoxed(Long value) {
        if (Debug.COMMUNICATIONS && getCheckCommunications())
            writeDebugInfo(ImmutableClass.LONG_BOXED_INDEX, 0);

        if (value == null) // Marker
            writeBooleanToBuffer(false);
        else {
            writeBooleanToBuffer(true);
            writeLongToBuffer(value); // Marker
        }
    }

    //

    private static final int DATE_LENGTH = LONG_LENGTH;

    public final boolean canWriteDate() {
        int needed = DATE_LENGTH;

        if (Debug.COMMUNICATIONS && getCheckCommunications()) {
            Helper.getInstance().setExpectedClass(ImmutableClass.DATE_INDEX);
            needed += ImmutableWriter.DEBUG_OVERHEAD;
        }

        return remaining() >= needed;
    }

    public final void writeDate(java.util.Date value) {
        if (Debug.COMMUNICATIONS && getCheckCommunications())
            writeDebugInfo(ImmutableClass.DATE_INDEX, 0);

        if (value == null) // Marker
            writeLongToBuffer(-1);
        else
            writeLongToBuffer(value.getTime() * 10000 + 621355968000000000L);
    }

    /*
     * Non fixed lengths.
     */

    private static final int FLOAT_DEBUG = 0;

    private static final int FLOAT_VALUE = 1;

    @SuppressWarnings("fallthrough")
    public final void writeFloat(float value) {
        if (Debug.COMMUNICATIONS && getCheckCommunications()) {
            int step = FLOAT_DEBUG;

            if (interrupted())
                step = resumeInt();

            switch (step) {
                case FLOAT_DEBUG: {
                    if (!canWriteDebugInfo()) {
                        interruptInt(FLOAT_DEBUG);
                        return;
                    }

                    Helper.getInstance().setExpectedClass(ImmutableClass.FLOAT_INDEX);
                    writeDebugInfo(ImmutableClass.FLOAT_INDEX, 0);
                }
                case FLOAT_VALUE: {
                    writeFloatNoDebug(value);

                    if (interrupted()) {
                        interruptInt(FLOAT_VALUE);
                        return;
                    }
                }
            }
        } else
            writeFloatNoDebug(value);
    }

    private final void writeFloatNoDebug(float value) {
        if (interrupted())
            resume();

        if ((getFlags() & CompileTimeSettings.SERIALIZATION_BINARY_FLOAT_AND_DOUBLE) == 0) {
            writeString(PlatformAdapter.floatToString(value), 0, false);

            if (interrupted()) {
                interrupt(null);
                return;
            }
        } else {
            if (remaining() < INTEGER_LENGTH) {
                interrupt(null);
                return;
            }

            int i = PlatformAdapter.floatToInt(value);
            writeIntegerToBuffer(i);
        }
    }

    private static final int FLOAT_BOXED_DEBUG = -1;

    private static final int FLOAT_BOXED_NULL = 0;

    private static final int FLOAT_BOXED_VALUE = 1;

    @SuppressWarnings("fallthrough")
    public final void writeFloatBoxed(Float value) {
        int step = Debug.COMMUNICATIONS && getCheckCommunications() ? FLOAT_BOXED_DEBUG : FLOAT_BOXED_NULL;

        if (interrupted())
            step = resumeInt();

        if (Debug.COMMUNICATIONS && getCheckCommunications()) {
            if (step == FLOAT_BOXED_DEBUG) {
                if (!canWriteDebugInfo()) {
                    interruptInt(FLOAT_BOXED_DEBUG);
                    return;
                }

                Helper.getInstance().setExpectedClass(ImmutableClass.FLOAT_BOXED_INDEX);
                writeDebugInfo(ImmutableClass.FLOAT_BOXED_INDEX, 0);
                step = FLOAT_BOXED_NULL;
            }
        }

        switch (step) {
            case FLOAT_BOXED_NULL: {
                if (!canWriteBoolean()) {
                    interruptInt(FLOAT_BOXED_NULL);
                    return;
                }

                writeBooleanToBuffer(value == null); // Marker

                if (value == null) // Marker
                    return;
            }
            case FLOAT_BOXED_VALUE: {
                writeFloatNoDebug(value); // Marker

                if (interrupted()) {
                    interruptInt(FLOAT_BOXED_VALUE);
                    return;
                }
            }
        }
    }

    //

    private static final int DOUBLE_DEBUG = 0;

    private static final int DOUBLE_VALUE = 1;

    @SuppressWarnings("fallthrough")
    public final void writeDouble(double value) {
        if (Debug.COMMUNICATIONS && getCheckCommunications()) {
            int step = DOUBLE_DEBUG;

            if (interrupted())
                step = resumeInt();

            switch (step) {
                case DOUBLE_DEBUG: {
                    if (!canWriteDebugInfo()) {
                        interruptInt(DOUBLE_DEBUG);
                        return;
                    }

                    Helper.getInstance().setExpectedClass(ImmutableClass.DOUBLE_INDEX);
                    writeDebugInfo(ImmutableClass.DOUBLE_INDEX, 0);
                }
                case DOUBLE_VALUE: {
                    writeDoubleNoDebug(value);

                    if (interrupted()) {
                        interruptInt(DOUBLE_VALUE);
                        return;
                    }
                }
            }
        } else
            writeDoubleNoDebug(value);
    }

    private final void writeDoubleNoDebug(double value) {
        if (interrupted())
            resume();

        if ((getFlags() & CompileTimeSettings.SERIALIZATION_BINARY_FLOAT_AND_DOUBLE) == 0) {
            writeString(PlatformAdapter.doubleToString(value), 0, false);

            if (interrupted()) {
                interrupt(null);
                return;
            }
        } else {
            if (remaining() < LONG_LENGTH) {
                interrupt(null);
                return;
            }

            writeLongToBuffer(PlatformAdapter.doubleToLong(value));
        }
    }

    private static final int DOUBLE_BOXED_DEBUG = -1;

    private static final int DOUBLE_BOXED_NULL = 0;

    private static final int DOUBLE_BOXED_VALUE = 1;

    @SuppressWarnings("fallthrough")
    public final void writeDoubleBoxed(Double value) {
        int step = Debug.COMMUNICATIONS && getCheckCommunications() ? DOUBLE_BOXED_DEBUG : DOUBLE_BOXED_NULL;

        if (interrupted())
            step = resumeInt();

        if (Debug.COMMUNICATIONS && getCheckCommunications()) {
            if (step == DOUBLE_BOXED_DEBUG) {
                if (!canWriteDebugInfo()) {
                    interruptInt(DOUBLE_BOXED_DEBUG);
                    return;
                }

                Helper.getInstance().setExpectedClass(ImmutableClass.DOUBLE_BOXED_INDEX);
                writeDebugInfo(ImmutableClass.DOUBLE_BOXED_INDEX, 0);
                step = DOUBLE_BOXED_NULL;
            }
        }

        switch (step) {
            case DOUBLE_BOXED_NULL: {
                if (!canWriteBoolean()) {
                    interruptInt(DOUBLE_BOXED_NULL);
                    return;
                }

                writeBooleanToBuffer(value == null); // Marker

                if (value == null) // Marker
                    return;
            }
            case DOUBLE_BOXED_VALUE: {
                writeDoubleNoDebug(value); // Marker

                if (interrupted()) {
                    interruptInt(DOUBLE_BOXED_VALUE);
                    return;
                }
            }
        }
    }

    //

    public final void writeString(String value) {
        writeString(value, 0, true);
    }

    public final void writeString(String value, int tag) {
        writeString(value, tag, true);
    }

    /*
     * Interruptible string encoding with null & EOF codes to avoid sending length.
     */

    static final int STRING_ENC_DOES_NOT_FIT_ON_1_BYTE_MASK = 1 << 7;

    static final int STRING_ENC_DOES_NOT_FIT_ON_2_BYTES_MASK = 1 << 6;

    static final int STRING_ENC_EOF_MASK = 1 << 5;

    static final int STRING_ENC_NULL_MASK = 1 << 4;

    private final void writeString(String value, int tag, boolean debug) {
        int index = Debug.COMMUNICATIONS && getCheckCommunications() && debug ? -1 : 0;

        if (interrupted())
            index = resumeInt();

        if (Debug.COMMUNICATIONS && getCheckCommunications() && debug) {
            if (index < 0) {
                if (!canWriteDebugInfo()) {
                    interruptInt(-1);
                    return;
                }

                Helper.getInstance().setExpectedClass(ImmutableClass.STRING_INDEX);
                writeDebugInfo(ImmutableClass.STRING_INDEX, tag);
                index = 0;
            }
        }

        if (value == null) {
            if (remaining() == 0) {
                interruptInt(0);
                return;
            }

            int mask = STRING_ENC_DOES_NOT_FIT_ON_1_BYTE_MASK;
            mask |= STRING_ENC_DOES_NOT_FIT_ON_2_BYTES_MASK;
            mask |= STRING_ENC_EOF_MASK;
            mask |= STRING_ENC_NULL_MASK;
            writeByteToBuffer((byte) mask);
            return;
        }

        for (;;) {
            if (index == value.length()) {
                if (remaining() == 0) {
                    interruptInt(index);
                    return;
                }

                int mask = STRING_ENC_DOES_NOT_FIT_ON_1_BYTE_MASK;
                mask |= STRING_ENC_DOES_NOT_FIT_ON_2_BYTES_MASK;
                mask |= STRING_ENC_EOF_MASK;
                writeByteToBuffer((byte) mask);
                return;
            }

            char c = value.charAt(index);

            if (c < 128) { // Char fits in 7 bits -> 1 byte
                if (remaining() == 0) {
                    interruptInt(index);
                    return;
                }

                writeByteToBuffer((byte) c);
            } else if (c < 16384) { // Char fits in 14 bits -> 2 bytes
                if (remaining() < 2) {
                    interruptInt(index);
                    return;
                }

                writeByteToBuffer((byte) (STRING_ENC_DOES_NOT_FIT_ON_1_BYTE_MASK | (c >> 8)));
                writeByteToBuffer((byte) (c & 0xff));
            } else { // Others -> 3 bytes
                if (remaining() < 3) {
                    interruptInt(index);
                    return;
                }

                int mask = STRING_ENC_DOES_NOT_FIT_ON_1_BYTE_MASK;
                mask |= STRING_ENC_DOES_NOT_FIT_ON_2_BYTES_MASK;
                writeByteToBuffer((byte) mask);
                writeCharacterToBuffer(c);
            }

            index++;
        }
    }

    //

    public final void writeBinary(byte[] value) {
        writeBinary(value, ImmutableClass.BINARY_INDEX);
    }

    private final void writeBinary(byte[] value, int classId) {
        int index = Debug.COMMUNICATIONS && getCheckCommunications() ? -2 : -1;

        if (interrupted())
            index = resumeInt();

        if (Debug.COMMUNICATIONS && getCheckCommunications()) {
            if (index == -2) {
                if (!canWriteDebugInfo()) {
                    interruptInt(-2);
                    return;
                }

                Helper.getInstance().setExpectedClass(classId);
                writeDebugInfo(classId, 0);
            }
        }

        if (index < 0) {
            if (remaining() < INTEGER_LENGTH) {
                interruptInt(-1);
                return;
            }

            if (value == null) {
                writeIntegerToBuffer(-1);
                return;
            }

            writeIntegerToBuffer(value.length);
            index = 0;
        }

        for (; index < value.length; index++) {
            if (remaining() == 0) {
                interruptInt(index);
                return;
            }

            writeByteToBuffer(value[index]);
        }
    }

    //

    public final void writeBigInteger(java.math.BigInteger value) {
        if (value == null) // Marker
            writeBinary(null, ImmutableClass.BIG_INTEGER_INDEX);
        else
            writeBinary(value.toByteArray(), ImmutableClass.BIG_INTEGER_INDEX);
    }

    //

    public final void writeDecimal(java.math.BigDecimal value) {
        if (value == null) // Marker
            writeBinary(null, ImmutableClass.DECIMAL_INDEX);
        else {
            byte[] array;

            if (interrupted())
                array = (byte[]) resume();
            else {
                byte[] t = value.unscaledValue().toByteArray();
                array = new byte[t.length + 4];
                PlatformAdapter.arraycopy(t, 0, array, 0, t.length);
                int scale = value.scale();
                array[array.length - 4] = (byte) (scale & 0xff);
                array[array.length - 3] = (byte) ((scale >>> 8) & 0xff);
                array[array.length - 2] = (byte) ((scale >>> 16) & 0xff);
                array[array.length - 1] = (byte) ((scale >>> 24) & 0xff);
            }

            writeBinary(array, ImmutableClass.DECIMAL_INDEX);

            if (interrupted())
                interrupt(array);
        }
    }

    // Debug

    static boolean getCheckCommunications() {
        if (!Debug.COMMUNICATIONS)
            throw new IllegalStateException();

        return _checkCommunications;
    }

    static void setCheckCommunications(boolean value) {
        if (!Debug.COMMUNICATIONS)
            throw new IllegalStateException();

        _checkCommunications = value;
    }

    private final boolean canWriteDebugInfo() {
        if (!Debug.COMMUNICATIONS || !getCheckCommunications())
            throw new IllegalStateException();

        return remaining() >= DEBUG_OVERHEAD;
    }

    static {
        int value = 0;
        value += LONG_LENGTH; // debugCounter
        value += BYTE_LENGTH; // classId
        value += INTEGER_LENGTH; // tag
        value += INTEGER_LENGTH; // getCustomDebugInfo1
        value += INTEGER_LENGTH; // getCustomDebugInfo2
        DEBUG_OVERHEAD = value;
    }

    private final void writeDebugInfo(int classId, int tag) {
        if (!Debug.COMMUNICATIONS || !getCheckCommunications())
            throw new IllegalStateException();

        if (Debug.COMMUNICATIONS_LOG_ALL) {
            String name = ImmutableClass.ALL.get(classId).toString();
            Log.write(PlatformClass.getSimpleName(getClass()) + ", " + ThreadAssert.getOrCreateCurrent().getWriterDebugCounter(this) + ", class: " + name + ", tag: " + tag);
        }

        Debug.assertion(classId == Helper.getInstance().getExpectedClass());
        long debugCounter = ThreadAssert.getOrCreateCurrent().getAndIncrementWriterDebugCounter(this);
        writeLongToBuffer(debugCounter);
        writeByteToBuffer((byte) classId);
        writeIntegerToBuffer(tag);
        writeIntegerToBuffer(getCustomDebugInfo1());
        writeIntegerToBuffer(getCustomDebugInfo2());
    }

    int getCustomDebugInfo1() {
        if (!Debug.COMMUNICATIONS || !getCheckCommunications())
            throw new IllegalStateException();

        return 0;
    }

    int getCustomDebugInfo2() {
        if (!Debug.COMMUNICATIONS || !getCheckCommunications())
            throw new IllegalStateException();

        return 0;
    }
}
