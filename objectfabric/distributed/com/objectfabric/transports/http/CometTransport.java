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

package com.objectfabric.transports.http;

import com.objectfabric.misc.ConnectionState;
import com.objectfabric.misc.Debug;
import com.objectfabric.misc.PlatformAdapter;
import com.objectfabric.misc.ThreadAssert;

/**
 * HTTP requires two socket connections for true bidirectional communication as the Comet
 * request cannot send data while it is waiting for server chunks. This class implements a
 * simple protocol where each request has a type byte to specify if it is an initial
 * connection or on which side it is. Values are irrelevant, but using uncommon values
 * allows the HTTP filter to reject non-ObjectFabric requests faster.
 */
abstract class CometTransport extends ConnectionState {

    public static final byte CONNECTION = 42;

    public static final byte SERVER_TO_CLIENT = 43;

    public static final byte CLIENT_TO_SERVER = 44;

    public static final byte HEARTBEAT = 45;

    public static final byte MIN = 42, MAX = 45;

    /**
     * Otherwise browsers wait for several chunks to arrive instead of invoking client
     * callback immediately. Max seems to be Chrome, does not work with 1000.
     */
    static final int MIN_CHUNK_SIZE = 1024;

    static final int MAX_CHUNK_SIZE = 65535; // For HEX encoding & read length

    /**
     * Offset of fields in a request.
     */
    public static final int FIELD_TYPE = 0;

    public static final int FIELD_IS_ENCODED = 1;

    public static final int FIELD_ID = 2;

    //

    public static final int SERVER_HEARTBEAT = 15;

    public static final int CLIENT_TIMEOUT = 20;

    public static final int CLIENT_HEARTBEAT = 55;

    public static final int SERVER_TIMEOUT = 60;

    protected interface HTTPRequestBase {

        void setCallback(HTTPRequestCallback value);

        void close();

        byte[] getBuffer();

        void connect();
    }

    protected interface HTTPRequestCallback {

        int onWrite();

        void onRead(byte[] buffer, int length);

        void onDone();

        void onError(Throwable t);
    }

    private final HTTPRequestBase _reader, _writer;

    private byte[] _id;

    private byte[] _seed = new byte[PlatformAdapter.UID_BYTES_COUNT];

    private int _chunkLength, _chunkOffset = MIN_CHUNK_SIZE;

    protected CometTransport(final boolean encoded) {
        _reader = createRequest(true);

        if (Debug.ENABLED) {
            if (Debug.THREADS) {
                ThreadAssert.assertCurrentIsEmpty();
                ThreadAssert.addPrivate(_reader);
            }

            ThreadAssert.suspend(_reader);
        }

        _reader.setCallback(new HTTPRequestCallback() {

            public int onWrite() {
                return write(_reader.getBuffer(), true, encoded);
            }

            public void onRead(byte[] buffer, int length) {
                read(buffer, length);
            }

            public void onDone() {
            }

            public void onError(Throwable t) {
                CometTransport.this.onError(t);
            }
        });

        _writer = createRequest(false);

        if (Debug.ENABLED) {
            if (Debug.THREADS) {
                ThreadAssert.assertCurrentIsEmpty();
                ThreadAssert.addPrivate(_writer);
            }

            ThreadAssert.suspend(_writer);
        }

        _writer.setCallback(new HTTPRequestCallback() {

            public int onWrite() {
                if (Debug.ENABLED)
                    assertNotified();

                onWriteStarting();
                return write(_writer.getBuffer(), false, encoded);
            }

            public void onRead(byte[] buffer, int length) {
                throw new UnsupportedOperationException();
            }

            public void onDone() {
                endWrite();
            }

            public void onError(Throwable t) {
                CometTransport.this.onError(t);
            }
        });
    }

    //

    protected abstract HTTPRequestBase createRequest(boolean serverToClient);

    protected abstract void read(byte[] buffer, int offset, int limit);

    protected abstract int write(byte[] buffer, int offset, int limit);

    protected abstract void onError(Throwable t);

    //

    protected void connect() {
        onStarting();
        _reader.connect();
    }

    protected void close() {
        _reader.close();
        _writer.close();
    }

    //

    private final void read(byte[] buffer, int length) {
        if (Debug.ENABLED)
            ThreadAssert.resume(_reader);

        int offset = 0;
        boolean started = false;

        for (;;) {
            if (_chunkOffset == Math.max(_chunkLength, MIN_CHUNK_SIZE)) {
                _chunkOffset = 0;
                _chunkLength = 0;
            }

            if (offset == length)
                break;

            if (_chunkOffset == 0) {
                _chunkLength |= (buffer[offset++] & 0xff) << 8;
                _chunkOffset++;
            }

            if (offset == length)
                break;

            if (_chunkOffset == 1) {
                _chunkLength |= (buffer[offset++] & 0xff);
                _chunkOffset++;
            }

            if (_seed != null) {
                final int LENGTH_FIELD = 2;
                final int ID_START = LENGTH_FIELD;
                final int ID_END = LENGTH_FIELD + PlatformAdapter.UID_BYTES_COUNT;
                final int SEED_START = ID_END;
                final int SEED_END = ID_END + PlatformAdapter.UID_BYTES_COUNT;

                if (_chunkOffset >= ID_START && _chunkOffset < ID_END) {
                    if (_id == null)
                        _id = new byte[PlatformAdapter.UID_BYTES_COUNT];

                    int read = Math.min(ID_END - _chunkOffset, length - offset);
                    PlatformAdapter.arraycopy(buffer, offset, _id, _chunkOffset - ID_START, read);
                    offset += read;
                    _chunkOffset += read;
                }

                if (_chunkOffset >= SEED_START && _chunkOffset < SEED_END) {
                    int read = Math.min(SEED_END - _chunkOffset, length - offset);
                    PlatformAdapter.arraycopy(buffer, offset, _seed, _chunkOffset - SEED_START, read);
                    offset += read;
                    _chunkOffset += read;
                }

                if (_chunkOffset == SEED_END) {
                    PlatformAdapter.initializeUIDGenerator(_seed);
                    _seed = null;
                    started = true;
                }
            }

            if (_chunkOffset < _chunkLength) {
                int needed = _chunkLength - _chunkOffset;
                int available = length - offset;

                if (needed < available) {
                    read(buffer, offset, offset + needed);
                    offset += needed;
                    _chunkOffset += needed;
                } else {
                    read(buffer, offset, length);
                    _chunkOffset += available;
                    break;
                }
            }

            if (_chunkOffset < MIN_CHUNK_SIZE) {
                if (Debug.ENABLED)
                    Debug.assertion(_chunkLength < MIN_CHUNK_SIZE);

                int needed = MIN_CHUNK_SIZE - _chunkOffset;
                int available = length - offset;

                if (needed < available) {
                    offset += needed;
                    _chunkOffset += needed;
                } else {
                    _chunkOffset += available;
                    break;
                }
            }
        }

        if (started)
            onStarted();

        if (Debug.ENABLED)
            ThreadAssert.suspend(_reader);
    }

    @Override
    protected void startWrite() {
        _writer.connect();
    }

    private final int write(byte[] buffer, boolean connection, boolean encoded) {
        if (Debug.ENABLED)
            ThreadAssert.resume(_writer);

        int result = writeImpl(buffer, connection, encoded);

        if (Debug.ENABLED)
            ThreadAssert.suspend(_writer);

        return result;
    }

    private final int writeImpl(byte[] buffer, boolean connection, boolean encoded) {
        byte command;

        if (connection)
            command = _id == null ? CONNECTION : SERVER_TO_CLIENT;
        else
            command = CLIENT_TO_SERVER;

        buffer[FIELD_TYPE] = command;
        buffer[FIELD_IS_ENCODED] = encoded ? (byte) 1 : 0;
        int start = FIELD_IS_ENCODED + 1;

        if (_id != null) {
            for (int i = 0; i < PlatformAdapter.UID_BYTES_COUNT; i++)
                buffer[start + i] = _id[i];

            start += PlatformAdapter.UID_BYTES_COUNT;
        }

        if (command == SERVER_TO_CLIENT)
            return start;

        int written = write(buffer, start + 2, buffer.length);

        if (written < 0)
            written = -written - 1;
        else
            requestWrite();

        if (command == CLIENT_TO_SERVER && written == 0)
            return 0;

        if (Debug.ENABLED)
            Debug.assertion(buffer.length <= MAX_CHUNK_SIZE);

        buffer[start + 0] = (byte) (written >> 8);
        buffer[start + 1] = (byte) (written & 0xff);

        return start + 2 + written;
    }
}
