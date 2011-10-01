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
 * $Id: BlockIo.java,v 1.4 2008/05/30 05:16:14 trumpetinc Exp $
 */

package com.objectfabric;

import com.objectfabric.misc.Debug;
import com.objectfabric.misc.Utils;

/**
 * This class wraps a page-sized byte array and provides methods to read and write data to
 * and from it. The readers and writers are just the ones that the rest of the toolkit
 * needs, nothing else. Values written are compatible with java.io routines.
 * 
 * @see java.io.DataInput
 * @see java.io.DataOutput
 */
final class BlockIo {

    public static final int LENGTH = 1024; // 8192; // 16384; // 4096; TODO tune

    public static final int ID_LENGTH = 8;

    private long _id;

    private final byte[] _data = new byte[LENGTH];

    private BlockView _view = null;

    private boolean _dirty = false;

    private int _transactionCount = 0;

    public byte[] getData() {
        return _data;
    }

    public long getId() {
        return _id;
    }

    public void setId(long id) {
        _id = id;
    }

    public BlockView getView() {
        return _view;
    }

    public void setView(BlockView view) {
        this._view = view;
    }

    public boolean isDirty() {
        return _dirty;
    }

    public void setDirty() {
        _dirty = true;
    }

    public void setClean() {
        _dirty = false;
    }

    /**
     * Returns true if the block is still dirty with respect to the transaction log.
     */
    boolean isInTransaction() {
        return _transactionCount != 0;
    }

    /**
     * Increments transaction count for this block, to signal that this block is in the
     * log but not yet in the data file. The method also takes a snapshot so that the data
     * may be modified in new transactions.
     */
    void incrementTransactionCount() {
        _transactionCount++;
        // @fixme(alex)
        setClean();
    }

    /**
     * Decrements transaction count for this block, to signal that this block has been
     * written from the log to the data file.
     */
    void decrementTransactionCount() {
        _transactionCount--;

        if (Debug.ENABLED)
            Debug.assertion(_transactionCount >= 0);
    }

    /**
     * Reads a byte from the indicated position
     */
    public byte readByte(int pos) {
        return readByte(_data, pos);
    }

    public static byte readByte(byte[] data, int pos) {
        return data[pos];
    }

    /**
     * Writes a byte to the indicated position
     */
    public void writeByte(int pos, byte value) {
        writeByte(_data, pos, value);
        setDirty();
    }

    public static void writeByte(byte[] data, int pos, byte value) {
        data[pos] = value;
    }

    /**
     * Reads a short from the indicated position
     */
    public short readShort(int pos) {
        return Utils.readShort(_data, pos);
    }

    /**
     * Writes a short to the indicated position
     */
    public void writeShort(int pos, short value) {
        Utils.writeShort(_data, pos, value);
        setDirty();
    }

    /**
     * Reads an int from the indicated position
     */
    public int readInt(int pos) {
        return Utils.readInt(_data, pos);
    }

    /**
     * Writes an int to the indicated position
     */
    public void writeInt(int pos, int value) {
        Utils.writeInt(_data, pos, value);
        setDirty();
    }

    /**
     * Reads a long from the indicated position
     */
    public long readLong(int pos) {
        return Utils.readLong(_data, pos);
    }

    /**
     * Writes a long to the indicated position
     */
    public void writeLong(int pos, long value) {
        Utils.writeLong(_data, pos, value);
        setDirty();
    }

    @Override
    public String toString() {
        if (Debug.ENABLED)
            return "BlockIO(" + _id + "," + _dirty + "," + _view + ")";

        return super.toString();
    }
}
