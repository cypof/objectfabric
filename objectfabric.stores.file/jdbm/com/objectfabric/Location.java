/**
 * JDBM LICENSE v1.00
 *
 * Redistribution and use of this software and associated documentation
 * ("Software"), with or without modification, are permitted provided
 * that the following conditions are met:
 *
 * 1. Redistributions of source code must retain copyright
 *    statements and notices.  Redistributions must also contain a
 *    copy of this document.
 *
 * 2. Redistributions in binary form must reproduce the
 *    above copyright notice, this list of conditions and the
 *    following disclaimer in the documentation and/or other
 *    materials provided with the distribution.
 *
 * 3. The name "JDBM" must not be used to endorse or promote
 *    products derived from this Software without prior written
 *    permission of Cees de Groot.  For written permission,
 *    please contact cg@cdegroot.com.
 *
 * 4. Products derived from this Software may not be called "JDBM"
 *    nor may "JDBM" appear in their names without prior written
 *    permission of Cees de Groot. 
 *
 * 5. Due credit should be given to the JDBM Project
 *    (http://jdbm.sourceforge.net/).
 *
 * THIS SOFTWARE IS PROVIDED BY THE JDBM PROJECT AND CONTRIBUTORS
 * ``AS IS'' AND ANY EXPRESSED OR IMPLIED WARRANTIES, INCLUDING, BUT
 * NOT LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND
 * FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED.  IN NO EVENT SHALL
 * CEES DE GROOT OR ANY CONTRIBUTORS BE LIABLE FOR ANY DIRECT,
 * INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
 * (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR
 * SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION)
 * HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT,
 * STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED
 * OF THE POSSIBILITY OF SUCH DAMAGE.
 *
 * Copyright 2000 (C) Cees de Groot. All Rights Reserved.
 * Contributions are Copyright (C) 2000 by their associated contributors.
 *
 * $Id: Location.java,v 1.3 2006/05/31 22:29:44 thompsonbry Exp $
 */

package com.objectfabric;

import com.objectfabric.misc.Debug;

/**
 * This class represents a location within a file. Both physical and logical row ids are
 * based on locations internally - this version is used when there is no file block to
 * back the location's data.
 */
final class Location {

    private final long _block;

    private final short _offset;

    /**
     * Creates a location from a (block, offset) tuple.
     */
    Location(long block, short offset) {
        this._block = block;
        this._offset = offset;
    }

    /**
     * Creates a location from a combined block/offset long, as used in the external
     * representation of logical row ids.
     * 
     * @see #toLong()
     */
    Location(long blockOffset) {
        this._offset = (short) (blockOffset & 0xffff);
        this._block = blockOffset >> 16;
    }

    /**
     * Creates a location based on the data of the physical row id.
     */
    Location(PhysicalRowId src) {
        _block = src.getBlock();
        _offset = src.getOffset();
    }

    /**
     * Returns the file block of the location
     */
    long getBlock() {
        return _block;
    }

    /**
     * Returns the offset within the block of the location
     */
    short getOffset() {
        return _offset;
    }

    /**
     * Returns the external representation of a location when used as a logical row id,
     * which combines the block and the offset in a single long.
     */
    long toLong() {
        return (_block << 16) + _offset;
    }

    @Override
    public int hashCode() {
        // TODO remove once sure not used
        throw new AssertionError();
    }

    @Override
    public boolean equals(Object o) {
        // TODO remove once sure not used
        throw new AssertionError();
    }

    @Override
    public String toString() {
        if (Debug.ENABLED)
            return "PL(" + _block + ":" + _offset + ")";

        return super.toString();
    }
}
