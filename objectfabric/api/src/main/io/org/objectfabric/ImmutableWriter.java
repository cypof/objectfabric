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
class ImmutableWriter extends Continuation {

    private Buff _buff;

    // TODO add caches e.g. for long, String (add UID?) to avoid sending again

    // Debug

    static final int DEBUG_OVERHEAD;

    private static boolean _checkCommunications = true;

    protected ImmutableWriter(List<Object> interruptionStack) {
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
        if (Debug.COMMUNICATIONS && getCheckCommunications())
            ThreadAssert.getOrCreateCurrent().resetWriterDebugCounter(this);
    }

    private final int remaining() {
        /*
         * TODO: always say 0 the first time to test all interruption sites.
         */
        return _buff.limit() - _buff.position();
    }

    private final void putBoolean(boolean value) {
        _buff.putByte(value ? (byte) 1 : (byte) 0);
    }

    final void putByte(byte value) {
        _buff.putByte(value);
    }

    //

    /*
     * Fixed lengths.
     */

    private static final int BYTE_LENGTH = 1;

    public final boolean canWriteByte() {
        int needed = BYTE_LENGTH;

        if (Debug.COMMUNICATIONS && getCheckCommunications()) {
            Helper.instance().setExpectedClass(Immutable.BYTE_INDEX);
            needed += ImmutableWriter.DEBUG_OVERHEAD;
        }

        return remaining() >= needed;
    }

    public final void writeByte(byte value) {
        writeByte(value, 0);
    }

    public final void writeByte(byte value, int tag) {
        if (Debug.COMMUNICATIONS && getCheckCommunications())
            writeDebugInfo(Immutable.BYTE_INDEX, tag);

        _buff.putByte(value);
    }

    public final boolean canWriteByteBoxed() {
        int needed = BOOLEAN_LENGTH + BYTE_LENGTH;

        if (Debug.COMMUNICATIONS && getCheckCommunications()) {
            Helper.instance().setExpectedClass(Immutable.BYTE_BOXED_INDEX);
            needed += ImmutableWriter.DEBUG_OVERHEAD;
        }

        return remaining() >= needed;
    }

    public final void writeByteBoxed(Byte value) {
        if (Debug.COMMUNICATIONS && getCheckCommunications())
            writeDebugInfo(Immutable.BYTE_BOXED_INDEX, 0);

        if (value == null) // Marker
            putBoolean(false);
        else {
            putBoolean(true);
            _buff.putByte(value); // Marker
        }
    }

    //

    private static final int BOOLEAN_LENGTH = 1;

    public final boolean canWriteBoolean() {
        int needed = BOOLEAN_LENGTH;

        if (Debug.COMMUNICATIONS && getCheckCommunications()) {
            Helper.instance().setExpectedClass(Immutable.BOOLEAN_INDEX);
            needed += ImmutableWriter.DEBUG_OVERHEAD;
        }

        return remaining() >= needed;
    }

    public final void writeBoolean(boolean value) {
        if (Debug.COMMUNICATIONS && getCheckCommunications())
            writeDebugInfo(Immutable.BOOLEAN_INDEX, 0);

        putBoolean(value);
    }

    public final boolean canWriteBooleanBoxed() {
        int needed = BOOLEAN_LENGTH + BOOLEAN_LENGTH;

        if (Debug.COMMUNICATIONS && getCheckCommunications()) {
            Helper.instance().setExpectedClass(Immutable.BOOLEAN_BOXED_INDEX);
            needed += ImmutableWriter.DEBUG_OVERHEAD;
        }

        return remaining() >= needed;
    }

    public final void writeBooleanBoxed(Boolean value) {
        if (Debug.COMMUNICATIONS && getCheckCommunications())
            writeDebugInfo(Immutable.BOOLEAN_BOXED_INDEX, 0);

        if (value == null) // Marker
            putBoolean(false);
        else {
            putBoolean(true);
            putBoolean(value); // Marker
        }
    }

    //

    private static final int SHORT_LENGTH = Short.SIZE / 8;

    public final boolean canWriteShort() {
        int needed = SHORT_LENGTH;

        if (Debug.COMMUNICATIONS && getCheckCommunications()) {
            Helper.instance().setExpectedClass(Immutable.SHORT_INDEX);
            needed += ImmutableWriter.DEBUG_OVERHEAD;
        }

        return remaining() >= needed;
    }

    public final void writeShort(short value) {
        if (Debug.COMMUNICATIONS && getCheckCommunications())
            writeDebugInfo(Immutable.SHORT_INDEX, 0);

        _buff.putShort(value);
    }

    public final boolean canWriteShortBoxed() {
        int needed = BOOLEAN_LENGTH + SHORT_LENGTH;

        if (Debug.COMMUNICATIONS && getCheckCommunications()) {
            Helper.instance().setExpectedClass(Immutable.SHORT_BOXED_INDEX);
            needed += ImmutableWriter.DEBUG_OVERHEAD;
        }

        return remaining() >= needed;
    }

    public final void writeShortBoxed(Short value) {
        if (Debug.COMMUNICATIONS && getCheckCommunications())
            writeDebugInfo(Immutable.SHORT_BOXED_INDEX, 0);

        if (value == null) // Marker
            putBoolean(false);
        else {
            putBoolean(true);
            _buff.putShort(value); // Marker
        }
    }

    //

    private static final int CHARACTER_LENGTH = Character.SIZE / 8;

    public final boolean canWriteCharacter() {
        int needed = CHARACTER_LENGTH;

        if (Debug.COMMUNICATIONS && getCheckCommunications()) {
            Helper.instance().setExpectedClass(Immutable.CHARACTER_INDEX);
            needed += ImmutableWriter.DEBUG_OVERHEAD;
        }

        return remaining() >= needed;
    }

    public final void writeCharacter(char value) {
        if (Debug.COMMUNICATIONS && getCheckCommunications())
            writeDebugInfo(Immutable.CHARACTER_INDEX, 0);

        _buff.putChar(value);
    }

    public final boolean canWriteCharacterBoxed() {
        int needed = BOOLEAN_LENGTH + CHARACTER_LENGTH;

        if (Debug.COMMUNICATIONS && getCheckCommunications()) {
            Helper.instance().setExpectedClass(Immutable.CHARACTER_BOXED_INDEX);
            needed += ImmutableWriter.DEBUG_OVERHEAD;
        }

        return remaining() >= needed;
    }

    public final void writeCharacterBoxed(Character value) {
        if (Debug.COMMUNICATIONS && getCheckCommunications())
            writeDebugInfo(Immutable.CHARACTER_BOXED_INDEX, 0);

        if (value == null) // Marker
            putBoolean(false);
        else {
            putBoolean(true);
            _buff.putChar(value); // Marker
        }
    }

    //

    private static final int INTEGER_LENGTH = Integer.SIZE / 8;

    public final boolean canWriteInteger() {
        int needed = INTEGER_LENGTH;

        if (Debug.COMMUNICATIONS && getCheckCommunications()) {
            Helper.instance().setExpectedClass(Immutable.INTEGER_INDEX);
            needed += ImmutableWriter.DEBUG_OVERHEAD;
        }

        return remaining() >= needed;
    }

    public final void writeInteger(int value) {
        if (Debug.COMMUNICATIONS && getCheckCommunications())
            writeDebugInfo(Immutable.INTEGER_INDEX, 0);

        _buff.putInt(value);
    }

    public final boolean canWriteIntegerBoxed() {
        int needed = BOOLEAN_LENGTH + INTEGER_LENGTH;

        if (Debug.COMMUNICATIONS && getCheckCommunications()) {
            Helper.instance().setExpectedClass(Immutable.INTEGER_BOXED_INDEX);
            needed += ImmutableWriter.DEBUG_OVERHEAD;
        }

        return remaining() >= needed;
    }

    public final void writeIntegerBoxed(Integer value) {
        if (Debug.COMMUNICATIONS && getCheckCommunications())
            writeDebugInfo(Immutable.INTEGER_BOXED_INDEX, 0);

        if (value == null) // Marker
            putBoolean(false);
        else {
            putBoolean(true);
            _buff.putInt(value); // Marker
        }
    }

    //

    private static final int LONG_LENGTH = Long.SIZE / 8;

    public final boolean canWriteLong() {
        int needed = LONG_LENGTH;

        if (Debug.COMMUNICATIONS && getCheckCommunications()) {
            Helper.instance().setExpectedClass(Immutable.LONG_INDEX);
            needed += ImmutableWriter.DEBUG_OVERHEAD;
        }

        return remaining() >= needed;
    }

    public final void writeLong(long value) {
        if (Debug.COMMUNICATIONS && getCheckCommunications())
            writeDebugInfo(Immutable.LONG_INDEX, 0);

        _buff.putLong(value);
    }

    public final boolean canWriteLongBoxed() {
        int needed = BOOLEAN_LENGTH + LONG_LENGTH;

        if (Debug.COMMUNICATIONS && getCheckCommunications()) {
            Helper.instance().setExpectedClass(Immutable.LONG_BOXED_INDEX);
            needed += ImmutableWriter.DEBUG_OVERHEAD;
        }

        return remaining() >= needed;
    }

    public final void writeLongBoxed(Long value) {
        if (Debug.COMMUNICATIONS && getCheckCommunications())
            writeDebugInfo(Immutable.LONG_BOXED_INDEX, 0);

        if (value == null) // Marker
            putBoolean(false);
        else {
            putBoolean(true);
            _buff.putLong(value); // Marker
        }
    }

    //

    private static final int FLOAT_LENGTH = Float.SIZE / 8;

    public final boolean canWriteFloat() {
        int needed = FLOAT_LENGTH;

        if (Debug.COMMUNICATIONS && getCheckCommunications()) {
            Helper.instance().setExpectedClass(Immutable.FLOAT_INDEX);
            needed += ImmutableWriter.DEBUG_OVERHEAD;
        }

        return remaining() >= needed;
    }

    public final void writeFloat(float value) {
        if (Debug.COMMUNICATIONS && getCheckCommunications())
            writeDebugInfo(Immutable.FLOAT_INDEX, 0);

        int i = Platform.get().floatToInt(value);
        _buff.putInt(i);
    }

    public final boolean canWriteFloatBoxed() {
        int needed = BOOLEAN_LENGTH + FLOAT_LENGTH;

        if (Debug.COMMUNICATIONS && getCheckCommunications()) {
            Helper.instance().setExpectedClass(Immutable.FLOAT_BOXED_INDEX);
            needed += ImmutableWriter.DEBUG_OVERHEAD;
        }

        return remaining() >= needed;
    }

    public final void writeFloatBoxed(Float value) {
        if (Debug.COMMUNICATIONS && getCheckCommunications())
            writeDebugInfo(Immutable.FLOAT_BOXED_INDEX, 0);

        if (value == null) // Marker
            putBoolean(false);
        else {
            putBoolean(true);
            int i = Platform.get().floatToInt(value); // Marker
            _buff.putInt(i);
        }
    }

    //

    private static final int DOUBLE_LENGTH = Double.SIZE / 8;

    public final boolean canWriteDouble() {
        int needed = DOUBLE_LENGTH;

        if (Debug.COMMUNICATIONS && getCheckCommunications()) {
            Helper.instance().setExpectedClass(Immutable.DOUBLE_INDEX);
            needed += ImmutableWriter.DEBUG_OVERHEAD;
        }

        return remaining() >= needed;
    }

    public final void writeDouble(double value) {
        if (Debug.COMMUNICATIONS && getCheckCommunications())
            writeDebugInfo(Immutable.DOUBLE_INDEX, 0);

        long l = Platform.get().doubleToLong(value);
        _buff.putLong(l);
    }

    public final boolean canWriteDoubleBoxed() {
        int needed = BOOLEAN_LENGTH + DOUBLE_LENGTH;

        if (Debug.COMMUNICATIONS && getCheckCommunications()) {
            Helper.instance().setExpectedClass(Immutable.DOUBLE_BOXED_INDEX);
            needed += ImmutableWriter.DEBUG_OVERHEAD;
        }

        return remaining() >= needed;
    }

    public final void writeDoubleBoxed(Double value) {
        if (Debug.COMMUNICATIONS && getCheckCommunications())
            writeDebugInfo(Immutable.DOUBLE_BOXED_INDEX, 0);

        if (value == null) // Marker
            putBoolean(false);
        else {
            putBoolean(true);
            long l = Platform.get().doubleToLong(value); // Marker
            _buff.putLong(l);
        }
    }

    //

    private static final int DATE_LENGTH = LONG_LENGTH;

    public final boolean canWriteDate() {
        int needed = DATE_LENGTH;

        if (Debug.COMMUNICATIONS && getCheckCommunications()) {
            Helper.instance().setExpectedClass(Immutable.DATE_INDEX);
            needed += ImmutableWriter.DEBUG_OVERHEAD;
        }

        return remaining() >= needed;
    }

    public final void writeDate(java.util.Date value) {
        if (Debug.COMMUNICATIONS && getCheckCommunications())
            writeDebugInfo(Immutable.DATE_INDEX, 0);

        if (value == null) // Marker
            _buff.putLong(-1);
        else
            _buff.putLong(value.getTime() * 10000 + 621355968000000000L);
    }

    /*
     * Non fixed lengths.
     */

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

                Helper.instance().setExpectedClass(Immutable.STRING_INDEX);
                writeDebugInfo(Immutable.STRING_INDEX, tag);
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
            _buff.putByte((byte) mask);
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
                _buff.putByte((byte) mask);
                return;
            }

            char c = value.charAt(index);

            if (c < 128) { // Char fits in 7 bits -> 1 byte
                if (remaining() == 0) {
                    interruptInt(index);
                    return;
                }

                _buff.putByte((byte) c);
            } else if (c < 16384) { // Char fits in 14 bits -> 2 bytes
                if (remaining() < 2) {
                    interruptInt(index);
                    return;
                }

                _buff.putByte((byte) (STRING_ENC_DOES_NOT_FIT_ON_1_BYTE_MASK | (c >> 8)));
                _buff.putByte((byte) (c & 0xff));
            } else { // Others -> 3 bytes
                if (remaining() < 3) {
                    interruptInt(index);
                    return;
                }

                int mask = STRING_ENC_DOES_NOT_FIT_ON_1_BYTE_MASK;
                mask |= STRING_ENC_DOES_NOT_FIT_ON_2_BYTES_MASK;
                _buff.putByte((byte) mask);
                _buff.putChar(c);
            }

            index++;
        }
    }

    //

    public final void writeBinary(byte[] value) {
        writeBinary(value, Immutable.BINARY_INDEX);
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

                Helper.instance().setExpectedClass(classId);
                writeDebugInfo(classId, 0);
            }
        }

        if (index < 0) {
            if (remaining() < INTEGER_LENGTH) {
                interruptInt(-1);
                return;
            }

            if (value == null) {
                _buff.putInt(-1);
                return;
            }

            _buff.putInt(value.length);
            index = 0;
        }

        // TODO use put(byte[])

        for (; index < value.length; index++) {
            if (remaining() == 0) {
                interruptInt(index);
                return;
            }

            _buff.putByte(value[index]);
        }
    }

    //

    public final void writeBigInteger(java.math.BigInteger value) {
        if (value == null) // Marker
            writeBinary(null, Immutable.BIG_INTEGER_INDEX);
        else
            writeBinary(value.toByteArray(), Immutable.BIG_INTEGER_INDEX);
    }

    //

    public final void writeDecimal(java.math.BigDecimal value) {
        if (value == null) // Marker
            writeBinary(null, Immutable.DECIMAL_INDEX);
        else {
            byte[] array;

            if (interrupted())
                array = (byte[]) resume();
            else {
                byte[] t = value.unscaledValue().toByteArray();
                array = new byte[t.length + 4];
                Platform.arraycopy(t, 0, array, 0, t.length);
                int scale = value.scale();
                array[array.length - 4] = (byte) (scale & 0xff);
                array[array.length - 3] = (byte) ((scale >>> 8) & 0xff);
                array[array.length - 2] = (byte) ((scale >>> 16) & 0xff);
                array[array.length - 1] = (byte) ((scale >>> 24) & 0xff);
            }

            writeBinary(array, Immutable.DECIMAL_INDEX);

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
        DEBUG_OVERHEAD = value;
    }

    private final void writeDebugInfo(int classId, int tag) {
        if (!Debug.COMMUNICATIONS || !getCheckCommunications())
            throw new IllegalStateException();

        if (Debug.COMMUNICATIONS_LOG_ALL) {
            String name = Immutable.ALL.get(classId).toString();
            long counter = ThreadAssert.getOrCreateCurrent().getWriterDebugCounter(this);
            String c = Platform.get().simpleClassName(this);
            Log.write(c + ", " + counter + ", class: " + name + ", tag: " + tag);
        }

        Debug.assertion(classId == Helper.instance().getExpectedClass());
        long debugCounter = ThreadAssert.getOrCreateCurrent().getAndIncrementWriterDebugCounter(this);
        _buff.putLong(debugCounter);
        _buff.putByte((byte) classId);
        _buff.putInt(tag);
    }
}
