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

abstract class Utils {

    static final char[] HEX = new char[] { '0', '1', '2', '3', '4', '5', '6', '7', '8', '9', 'a', 'b', 'c', 'd', 'e', 'f' };

    static final byte[] XEH = new byte[] { 0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 10, 11, 12, 13, 14, 15 };

    static final int TIME_HEX = Tick.TIME_BITS / 8 * 2;

    static final int PEER_HEX = UID.LENGTH * 2;

    static final char FILE_SEPARATOR = Platform.get().fileSeparator();

    private Utils() {
    }

    static int nextPowerOf2(int value) {
        value--;
        value |= value >> 1;
        value |= value >> 2;
        value |= value >> 4;
        value |= value >> 8;
        value |= value >> 16;
        value++;
        return value;
    }

    static void replace(StringBuilder sb, String a, String b) {
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

    static String padLeft(String stringToPad, int size, char pad) {
        StringBuilder strb = new StringBuilder(size);

        while (strb.length() < (size - stringToPad.length()))
            if (strb.length() < size - stringToPad.length())
                strb.insert(strb.length(), pad);

        return strb.append(stringToPad).toString();
    }

    static String padLeft(String stringToPad, int size) {
        return padLeft(stringToPad, size, ' ');
    }

    static String padRight(String stringToPad, int size, char pad) {
        StringBuilder strb = new StringBuilder(stringToPad);

        while (strb.length() < size)
            if (strb.length() < size)
                strb.append(pad);

        return strb.toString();
    }

    static String padRight(String stringToPad, int size) {
        return padRight(stringToPad, size, ' ');
    }

    static String getWithFirstLetterDown(String s) {
        return s.substring(0, 1).toLowerCase() + s.substring(1);
    }

    static String getWithFirstLetterUp(String s) {
        return s.substring(0, 1).toUpperCase() + s.substring(1);
    }

    static String getNameAsConstant(String name) {
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

    // HEX

    static final void getBytesHex(byte[] source, int offset, int length, char[] target, int offsetHex) {
        for (int i = 0; i < length; i++) {
            target[offsetHex + (i << 1) + 0] = HEX[(source[i + offset] >>> 4) & 0xf];
            target[offsetHex + (i << 1) + 1] = HEX[(source[i + offset] >>> 0) & 0xf];
        }
    }

    static final byte[] getBytes(String hex, int hexOffset, int byteCount) {
        byte[] result = new byte[byteCount];

        for (int i = 0; i < result.length; i++) {
            int a = XEH[hex.charAt(hexOffset + (i << 1) + 0) - '0'] << 4;
            int b = XEH[hex.charAt(hexOffset + (i << 1) + 1) - '0'] << 0;
            result[i] = (byte) (a | b);
        }

        return result;
    }

    static final void getTimeHex(long source, char[] target) {
        for (int i = 0; i < TIME_HEX; i++)
            target[i] = HEX[(int) (source >>> ((TIME_HEX - 1 - i) << 2)) & 0xf];
    }

    static final long getTime(String hex) {
        long result = 0;

        for (int i = 0; i < TIME_HEX; i++)
            result |= (long) XEH[hex.charAt(i) - '0'] << ((TIME_HEX - 1 - i) << 2);

        return result;
    }

    static String getTickHex(long value) {
        char[] chars = ThreadContext.get().PathCache;
        getTimeHex(Tick.time(value), chars);
        getBytesHex(Peer.get(Tick.peer(value)).uid(), 0, UID.LENGTH, chars, TIME_HEX);
        return new String(chars);
    }

    static long getTick(String hex) {
        long time = getTime(hex);
        Peer peer = Peer.get(new UID(getBytes(hex, TIME_HEX, UID.LENGTH)));
        return Tick.get(peer.index(), time);
    }
}
