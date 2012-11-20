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

import java.security.SecureRandom;

import org.junit.Assert;
import org.junit.Test;

public class HEXTest {

    static {
        JVMPlatform.loadClass();
    }

    @Test
    public void test1() throws Exception {
        SecureRandom rand = new SecureRandom();
        byte[] bytes = new byte[1000];
        rand.nextBytes(bytes);

        char[] chars = new char[bytes.length * 2];
        Utils.getBytesHex(bytes, 0, bytes.length, chars, 0);
        String hex = new String(chars);

        byte[] back = Utils.getBytes(hex, 0, hex.length() / 2);
        Assert.assertArrayEquals(bytes, back);
    }

    @Test
    public void test2() throws Exception {
        SecureRandom rand = new SecureRandom();

        for (int i = 0; i < 1000; i++) {
            int value = rand.nextInt();

            byte[] bytes = new byte[4];
            bytes[0] = (byte) ((value >>> 24) & 0xff);
            bytes[1] = (byte) ((value >>> 16) & 0xff);
            bytes[2] = (byte) ((value >>> 8) & 0xff);
            bytes[3] = (byte) ((value >>> 0) & 0xff);

            char[] chars = new char[bytes.length * 2];
            Utils.getBytesHex(bytes, 0, bytes.length, chars, 0);
            String hex = new String(chars);
            String ref = Utils.padLeft(Integer.toHexString(value), 8, '0');
            Assert.assertEquals(ref, hex);

            byte[] back = Utils.getBytes(hex, 0, hex.length() / 2);
            Assert.assertArrayEquals(bytes, back);
        }
    }

    @Test
    public void test3() throws Exception {
        SecureRandom rand = new SecureRandom();

        for (int i = 0; i < 1000; i++) {
            long value = rand.nextLong();

            char[] chars = new char[10];
            Utils.getTimeHex(value, chars);
            String hex = new String(chars);
            String ref = Utils.padLeft(Long.toHexString(value), 16, '0').substring(6, 16);
            Assert.assertEquals(ref, hex);

            long back = Utils.getTime(hex);
            Assert.assertEquals(value & 0xffffffffffL, back);
        }
    }
}
