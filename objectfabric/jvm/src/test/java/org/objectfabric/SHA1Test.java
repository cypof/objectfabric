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

import org.junit.Assert;
import org.junit.Test;

public class SHA1Test {

    @Test
    public void test1() throws Exception {
        JVMPlatform.loadClass();

        /*
         * Wikipedia examples.
         */

        SHA1Digest sha = new SHA1Digest();
        byte[] ascii = "The quick brown fox jumps over the lazy dog".getBytes("US-ASCII");
        sha.update(ascii, 0, ascii.length);
        byte[] hash = new byte[SHA1Digest.LENGTH];
        sha.doFinal(hash, 0);

        char[] chars = new char[hash.length * 2];
        Utils.getBytesHex(hash, 0, hash.length, chars, 0);
        String hex = new String(chars);
        Assert.assertEquals("2fd4e1c67a2d28fced849ee1bb76e7391b93eb12", hex);

        //

        ascii = "The quick brown fox jumps over the lazy cog".getBytes("US-ASCII");
        sha.reset();
        sha.update(ascii, 0, ascii.length);
        sha.doFinal(hash, 0);

        chars = new char[hash.length * 2];
        Utils.getBytesHex(hash, 0, hash.length, chars, 0);
        hex = new String(chars);
        Assert.assertEquals("de9f2c7fd25e1b3afad3e85a0bd17d9b100db4b3", hex);

        //

        ascii = "".getBytes("US-ASCII");
        sha.reset();
        sha.update(ascii, 0, ascii.length);
        sha.doFinal(hash, 0);

        chars = new char[hash.length * 2];
        Utils.getBytesHex(hash, 0, hash.length, chars, 0);
        hex = new String(chars);
        Assert.assertEquals("da39a3ee5e6b4b0d3255bfef95601890afd80709", hex);

        /*
         * Custom string encoding.
         */

        sha.reset();
        sha.update("The quick brown fox jumps over the lazy dog");
        sha.doFinal(hash, 0);

        byte[] java = "The quick brown fox jumps over the lazy dog".getBytes("UTF-16BE");
        sha.reset();
        sha.update(java, 0, java.length);
        byte[] expect = new byte[SHA1Digest.LENGTH];
        sha.doFinal(expect, 0);
        Assert.assertArrayEquals(expect, hash);
    }
}
