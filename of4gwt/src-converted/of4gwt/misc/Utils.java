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

package of4gwt.misc;

public final class Utils {

    public static final String NEW_LINE = PlatformAdapter.getLineSeparator();

    private Utils() {
    }

    public static int nextPowerOf2(int value) {
        value--;
        value |= value >> 1;
        value |= value >> 2;
        value |= value >> 4;
        value |= value >> 8;
        value |= value >> 16;
        value++;
        return value;
    }

    public static void replace(StringBuilder sb, String a, String b) {
        int index = 0;
        boolean one = false;

        for (;;) {
            index = sb.indexOf(a, index);

            if (index == -1)
                break;

            sb.replace(index, index + a.length(), b);
            index += b.length();
            one = true;
        }

        Debug.assertAlways(one);
    }

    public static byte[] extend(byte[] array) {
        byte[] result = new byte[array.length << SparseArrayHelper.TIMES_TWO_SHIFT];
        PlatformAdapter.arraycopy(array, 0, result, 0, array.length);
        return result;
    }

    public static int[] extend(int[] array) {
        int[] result = new int[array.length << SparseArrayHelper.TIMES_TWO_SHIFT];
        PlatformAdapter.arraycopy(array, 0, result, 0, array.length);
        return result;
    }

    public static long[] extend(long[] array) {
        long[] result = new long[array.length << SparseArrayHelper.TIMES_TWO_SHIFT];
        PlatformAdapter.arraycopy(array, 0, result, 0, array.length);
        return result;
    }

    public static Object[] extend(Object[] array) {
        Object[] result = new Object[array.length << SparseArrayHelper.TIMES_TWO_SHIFT];
        PlatformAdapter.arraycopy(array, 0, result, 0, array.length);
        return result;
    }

    public static String padLeft(String stringToPad, int size, char pad) {
        StringBuilder strb = new StringBuilder(size);

        while (strb.length() < (size - stringToPad.length()))
            if (strb.length() < size - stringToPad.length())
                strb.insert(strb.length(), pad);

        return strb.append(stringToPad).toString();
    }

    public static String padLeft(String stringToPad, int size) {
        return padLeft(stringToPad, size, ' ');
    }

    public static String padRight(String stringToPad, int size, char pad) {
        StringBuilder strb = new StringBuilder(stringToPad);

        while (strb.length() < size)
            if (strb.length() < size)
                strb.append(pad);

        return strb.toString();
    }

    public static String padRight(String stringToPad, int size) {
        return padRight(stringToPad, size, ' ');
    }

    public static String getWithFirstLetterDown(String s) {
        return s.substring(0, 1).toLowerCase() + s.substring(1);
    }

    public static String getWithFirstLetterUp(String s) {
        return s.substring(0, 1).toUpperCase() + s.substring(1);
    }

    public static String getNameAsConstant(String name) {
        StringBuffer sb = new StringBuffer(name.length());

        for (int i = 0; i < name.length(); i++) {
            char c = name.charAt(i);

            if (Character.isUpperCase(c))
                if (sb.length() > 0)
                    sb.append('_');

            sb.append(Character.toUpperCase(c));
        }

        return sb.toString();
    }

    //

    public static short readShort(byte[] data, int pos) {
        return (short) (((short) (data[pos + 0] & 0xff) << 8) | ((short) (data[pos + 1] & 0xff) << 0));
    }

    public static void writeShort(byte[] data, int pos, short value) {
        data[pos + 0] = (byte) (0xff & (value >> 8));
        data[pos + 1] = (byte) (0xff & (value >> 0));
    }

    public static int readInt(byte[] data, int pos) {
        int value = 0;
        value |= (data[pos + 0] & 0xff) << 24;
        value |= (data[pos + 1] & 0xff) << 16;
        value |= (data[pos + 2] & 0xff) << 8;
        value |= (data[pos + 3] & 0xff) << 0;
        return value;
    }

    public static void writeInt(byte[] data, int pos, int value) {
        data[pos + 0] = (byte) (0xff & (value >> 24));
        data[pos + 1] = (byte) (0xff & (value >> 16));
        data[pos + 2] = (byte) (0xff & (value >> 8));
        data[pos + 3] = (byte) (0xff & (value >> 0));
    }

    public static long readLong(byte[] data, int pos) {
        long value = 0;
        value |= (long) (data[pos + 0] & 0xff) << 56;
        value |= (long) (data[pos + 1] & 0xff) << 48;
        value |= (long) (data[pos + 2] & 0xff) << 40;
        value |= (long) (data[pos + 3] & 0xff) << 32;
        value |= (long) (data[pos + 4] & 0xff) << 24;
        value |= (long) (data[pos + 5] & 0xff) << 16;
        value |= (long) (data[pos + 6] & 0xff) << 8;
        value |= (long) (data[pos + 7] & 0xff) << 0;
        return value;
    }

    public static void writeLong(byte[] data, int pos, long value) {
        data[pos + 0] = (byte) (0xff & (value >> 56));
        data[pos + 1] = (byte) (0xff & (value >> 48));
        data[pos + 2] = (byte) (0xff & (value >> 40));
        data[pos + 3] = (byte) (0xff & (value >> 32));
        data[pos + 4] = (byte) (0xff & (value >> 24));
        data[pos + 5] = (byte) (0xff & (value >> 16));
        data[pos + 6] = (byte) (0xff & (value >> 8));
        data[pos + 7] = (byte) (0xff & (value >> 0));
    }
}
