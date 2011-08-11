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

import of4gwt.misc.RuntimeIOException;

public final class PlatformFile {

    public PlatformFile(String name) {
        throw new UnsupportedOperationException();
    }

    public long length() {
        throw new UnsupportedOperationException();
    }

    //

    public void readOrZero(byte[] buffer) {
        readOrZero(buffer, 0, buffer.length);
    }

    public void readOrZero(byte[] buffer, int offset, int length) {
        throw new UnsupportedOperationException();
    }

    /**
     * @throws RuntimeIOException
     *             if less data than requested
     */
    public void readFull(byte[] buffer) {
        readFull(buffer, 0, buffer.length);
    }

    /**
     * @throws RuntimeIOException
     *             if less data than requested
     */
    public void readFull(byte[] buffer, int offset, int length) {
        throw new UnsupportedOperationException();
    }

    public short readShort() {
        throw new UnsupportedOperationException();
    }

    public int readInt() {
        throw new UnsupportedOperationException();
    }

    public long readLong() {
        throw new UnsupportedOperationException();
    }

    //

    public void write(byte[] buffer) {
        throw new UnsupportedOperationException();
    }

    public void write(byte[] buffer, int offset, int length) {
        throw new UnsupportedOperationException();
    }

    public void writeShort(short value) {
        throw new UnsupportedOperationException();
    }

    public void writeInt(int value) {
        throw new UnsupportedOperationException();
    }

    public void writeLong(long value) {
        throw new UnsupportedOperationException();
    }

    //

    public long remaining() {
        return length() - getOffset();
    }

    public long getOffset() {
        throw new UnsupportedOperationException();
    }

    public void setOffset(long offset) {
        throw new UnsupportedOperationException();
    }

    public void setLength(long value) {
        throw new UnsupportedOperationException();
    }

    public void sync() {
        throw new UnsupportedOperationException();
    }

    public void close() {
        throw new UnsupportedOperationException();
    }
}
