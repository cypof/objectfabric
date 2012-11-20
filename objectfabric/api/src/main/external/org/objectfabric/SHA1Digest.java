/**
 * The Bouncy Castle License
 *
 * Copyright (c) 2000-2008 The Legion Of The Bouncy Castle (http://www.bouncycastle.org)
 * <p>
 * Permission is hereby granted, free of charge, to any person obtaining a copy of this software 
 * and associated documentation files (the "Software"), to deal in the Software without restriction, 
 * including without limitation the rights to use, copy, modify, merge, publish, distribute, sublicense, 
 * and/or sell copies of the Software, and to permit persons to whom the Software is furnished to do so,
 * subject to the following conditions:
 * <p>
 * The above copyright notice and this permission notice shall be included in all copies or substantial
 * portions of the Software.
 * <p>
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED,
 * INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR
 * PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE
 * LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR
 * OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER
 * DEALINGS IN THE SOFTWARE.
 */

package org.objectfabric;

/**
 * implementation of SHA-1 as outlined in "Handbook of Applied Cryptography", pages 346 -
 * 349. It is interesting to ponder why the, apart from the extra IV, the other difference
 * here from MD5 is the "endianness" of the word processing!
 * <nl>
 * ObjectFabric modifications: <br>
 * - Copied GeneralDigest.java<br>
 * - Added update(String)<br>
 * - Private & final.
 */
final class SHA1Digest {

    static final int LENGTH = 20;

    private int H1, H2, H3, H4, H5;

    private int[] X = new int[80];

    private int xOff;

    public SHA1Digest() {
        ctor();
        reset();
    }

    /*
     * ObjectFabric: Copied from GeneralDigest.java.
     */

    private byte[] xBuf;

    private int xBufOff;

    private long byteCount;

    /**
     * Standard constructor
     */
    private void ctor() {
        xBuf = new byte[4];
        xBufOff = 0;
    }

    public void update(byte in) {
        xBuf[xBufOff++] = in;

        if (xBufOff == xBuf.length) {
            processWord(xBuf, 0);
            xBufOff = 0;
        }

        byteCount++;
    }

    public void update(String s) {
        int index = 0, end = s.length() & ~1;
        char c;

        if (xBufOff == 2) {
            c = s.charAt(index++);
            xBuf[xBufOff++] = (byte) (c >> 8);
            xBuf[xBufOff++] = (byte) c;
            processWord(xBuf, 0);
            xBufOff = 0;
        }

        if (Debug.ENABLED)
            Debug.assertion(xBufOff == 0);

        while (index < end) {
            c = s.charAt(index++);
            xBuf[0] = (byte) (c >> 8);
            xBuf[1] = (byte) c;
            c = s.charAt(index++);
            xBuf[2] = (byte) (c >> 8);
            xBuf[3] = (byte) c;
            processWord(xBuf, 0);
        }

        if (end != s.length()) {
            c = s.charAt(s.length() - 1);
            xBuf[xBufOff++] = (byte) (c >> 8);
            xBuf[xBufOff++] = (byte) c;
        }

        byteCount += s.length() * 2;
    }

    public void update(byte[] in, int inOff, int len) {
        //
        // fill the current word
        //
        while ((xBufOff != 0) && (len > 0)) {
            update(in[inOff]);

            inOff++;
            len--;
        }

        //
        // process whole words.
        //
        while (len > xBuf.length) {
            processWord(in, inOff);

            inOff += xBuf.length;
            len -= xBuf.length;
            byteCount += xBuf.length;
        }

        //
        // load in the remainder.
        //
        while (len > 0) {
            update(in[inOff]);

            inOff++;
            len--;
        }
    }

    private void finish() {
        long bitLength = (byteCount << 3);

        //
        // add the pad bytes.
        //
        update((byte) 128);

        while (xBufOff != 0) {
            update((byte) 0);
        }

        processLength(bitLength);

        processBlock();
    }

    private void resetGeneralDigest() {
        byteCount = 0;

        xBufOff = 0;
        for (int i = 0; i < xBuf.length; i++) {
            xBuf[i] = 0;
        }
    }

    /*
     * 
     */

    private void processWord(byte[] in, int inOff) {
        int n = in[inOff] << 24;
        n |= (in[++inOff] & 0xff) << 16;
        n |= (in[++inOff] & 0xff) << 8;
        n |= (in[++inOff] & 0xff);
        X[xOff] = n;

        if (++xOff == 16) {
            processBlock();
        }
    }

    private void processLength(long bitLength) {
        if (xOff > 14) {
            processBlock();
        }

        X[14] = (int) (bitLength >>> 32);
        X[15] = (int) (bitLength & 0xffffffff);
    }

    public void doFinal(byte[] out, int outOff) {
        finish();

        intToBigEndian(H1, out, outOff);
        intToBigEndian(H2, out, outOff + 4);
        intToBigEndian(H3, out, outOff + 8);
        intToBigEndian(H4, out, outOff + 12);
        intToBigEndian(H5, out, outOff + 16);
    }

    private static void intToBigEndian(int n, byte[] bs, int off) {
        bs[off] = (byte) (n >>> 24);
        bs[++off] = (byte) (n >>> 16);
        bs[++off] = (byte) (n >>> 8);
        bs[++off] = (byte) (n);
    }

    /**
     * reset the chaining variables
     */
    public void reset() {
        resetGeneralDigest();

        H1 = 0x67452301;
        H2 = 0xefcdab89;
        H3 = 0x98badcfe;
        H4 = 0x10325476;
        H5 = 0xc3d2e1f0;

        xOff = 0;
        for (int i = 0; i != X.length; i++) {
            X[i] = 0;
        }
    }

    //
    // Additive constants
    //
    private static final int Y1 = 0x5a827999;

    private static final int Y2 = 0x6ed9eba1;

    private static final int Y3 = 0x8f1bbcdc;

    private static final int Y4 = 0xca62c1d6;

    private int f(int u, int v, int w) {
        return ((u & v) | ((~u) & w));
    }

    private int h(int u, int v, int w) {
        return (u ^ v ^ w);
    }

    private int g(int u, int v, int w) {
        return ((u & v) | (u & w) | (v & w));
    }

    private void processBlock() {
        //
        // expand 16 word block into 80 word block.
        //
        for (int i = 16; i < 80; i++) {
            int t = X[i - 3] ^ X[i - 8] ^ X[i - 14] ^ X[i - 16];
            X[i] = t << 1 | t >>> 31;
        }

        //
        // set up working variables.
        //
        int A = H1;
        int B = H2;
        int C = H3;
        int D = H4;
        int E = H5;

        //
        // round 1
        //
        int idx = 0;

        for (int j = 0; j < 4; j++) {
            // E = rotateLeft(A, 5) + f(B, C, D) + E + X[idx++] + Y1
            // B = rotateLeft(B, 30)
            E += (A << 5 | A >>> 27) + f(B, C, D) + X[idx++] + Y1;
            B = B << 30 | B >>> 2;

            D += (E << 5 | E >>> 27) + f(A, B, C) + X[idx++] + Y1;
            A = A << 30 | A >>> 2;

            C += (D << 5 | D >>> 27) + f(E, A, B) + X[idx++] + Y1;
            E = E << 30 | E >>> 2;

            B += (C << 5 | C >>> 27) + f(D, E, A) + X[idx++] + Y1;
            D = D << 30 | D >>> 2;

            A += (B << 5 | B >>> 27) + f(C, D, E) + X[idx++] + Y1;
            C = C << 30 | C >>> 2;
        }

        //
        // round 2
        //
        for (int j = 0; j < 4; j++) {
            // E = rotateLeft(A, 5) + h(B, C, D) + E + X[idx++] + Y2
            // B = rotateLeft(B, 30)
            E += (A << 5 | A >>> 27) + h(B, C, D) + X[idx++] + Y2;
            B = B << 30 | B >>> 2;

            D += (E << 5 | E >>> 27) + h(A, B, C) + X[idx++] + Y2;
            A = A << 30 | A >>> 2;

            C += (D << 5 | D >>> 27) + h(E, A, B) + X[idx++] + Y2;
            E = E << 30 | E >>> 2;

            B += (C << 5 | C >>> 27) + h(D, E, A) + X[idx++] + Y2;
            D = D << 30 | D >>> 2;

            A += (B << 5 | B >>> 27) + h(C, D, E) + X[idx++] + Y2;
            C = C << 30 | C >>> 2;
        }

        //
        // round 3
        //
        for (int j = 0; j < 4; j++) {
            // E = rotateLeft(A, 5) + g(B, C, D) + E + X[idx++] + Y3
            // B = rotateLeft(B, 30)
            E += (A << 5 | A >>> 27) + g(B, C, D) + X[idx++] + Y3;
            B = B << 30 | B >>> 2;

            D += (E << 5 | E >>> 27) + g(A, B, C) + X[idx++] + Y3;
            A = A << 30 | A >>> 2;

            C += (D << 5 | D >>> 27) + g(E, A, B) + X[idx++] + Y3;
            E = E << 30 | E >>> 2;

            B += (C << 5 | C >>> 27) + g(D, E, A) + X[idx++] + Y3;
            D = D << 30 | D >>> 2;

            A += (B << 5 | B >>> 27) + g(C, D, E) + X[idx++] + Y3;
            C = C << 30 | C >>> 2;
        }

        //
        // round 4
        //
        for (int j = 0; j <= 3; j++) {
            // E = rotateLeft(A, 5) + h(B, C, D) + E + X[idx++] + Y4
            // B = rotateLeft(B, 30)
            E += (A << 5 | A >>> 27) + h(B, C, D) + X[idx++] + Y4;
            B = B << 30 | B >>> 2;

            D += (E << 5 | E >>> 27) + h(A, B, C) + X[idx++] + Y4;
            A = A << 30 | A >>> 2;

            C += (D << 5 | D >>> 27) + h(E, A, B) + X[idx++] + Y4;
            E = E << 30 | E >>> 2;

            B += (C << 5 | C >>> 27) + h(D, E, A) + X[idx++] + Y4;
            D = D << 30 | D >>> 2;

            A += (B << 5 | B >>> 27) + h(C, D, E) + X[idx++] + Y4;
            C = C << 30 | C >>> 2;
        }

        H1 += A;
        H2 += B;
        H3 += C;
        H4 += D;
        H5 += E;

        //
        // reset start of the buffer.
        //
        xOff = 0;
        for (int i = 0; i < 16; i++) {
            X[i] = 0;
        }
    }
}
