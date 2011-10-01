/* (PD) 2006 The Bitzi Corporation
 * Please see http://bitzi.com/publicdomain for more info.
 *
 * $Id: Base32.java,v 1.2 2006/07/14 04:58:39 gojomo Exp $
 */

package chess.client;

/**
 * Base32 - encodes and decodes RFC3548 Base32 (see http://www.faqs.org/rfcs/rfc3548.html
 * )
 * 
 * @author Robert Kaye
 * @author Gordon Mohr
 */
public class Base32 {

    private static final String base32Chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567";

    /**
     * Encodes byte array to Base32 String.
     * 
     * @param bytes
     *            Bytes to encode.
     * @return Encoded byte array <code>bytes</code> as a String.
     */
    public static String encode(final byte[] bytes) {
        int i = 0, index = 0, digit = 0;
        int currByte, nextByte;
        StringBuffer base32 = new StringBuffer((bytes.length + 7) * 8 / 5);

        while (i < bytes.length) {
            currByte = (bytes[i] >= 0) ? bytes[i] : (bytes[i] + 256); // unsign

            /* Is the current digit going to span a byte boundary? */
            if (index > 3) {
                if ((i + 1) < bytes.length) {
                    nextByte = (bytes[i + 1] >= 0) ? bytes[i + 1] : (bytes[i + 1] + 256);
                } else {
                    nextByte = 0;
                }

                digit = currByte & (0xFF >> index);
                index = (index + 5) % 8;
                digit <<= index;
                digit |= nextByte >> (8 - index);
                i++;
            } else {
                digit = (currByte >> (8 - (index + 5))) & 0x1F;
                index = (index + 5) % 8;
                if (index == 0)
                    i++;
            }
            base32.append(base32Chars.charAt(digit));
        }

        return base32.toString();
    }
}
