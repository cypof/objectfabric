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

package com.objectfabric.transports;

import java.nio.ByteBuffer;

import com.objectfabric.misc.List;
import com.objectfabric.misc.NIOManager;
import com.objectfabric.misc.Queue;
import com.objectfabric.transports.filters.Filter;
import com.objectfabric.transports.filters.FilterFactory;

public class RandomSplitterFilter implements FilterFactory {

    public Filter createFilter(boolean clientSide) {
        return new Impl();
    }

    private static final class Impl implements Filter {

        private final RandomSplitter _reader = new RandomSplitter();

        private final ByteBuffer _readBuffer = ByteBuffer.allocate(NIOManager.SOCKET_BUFFER_SIZE);

        private final RandomSplitter _writer = new RandomSplitter();

        private final ByteBuffer _writeBuffer = ByteBuffer.allocate(NIOManager.SOCKET_BUFFER_SIZE);

        private Filter _previous, _next;

        public void init(List<FilterFactory> factories, int index, boolean clientSide) {
            _next = factories.get(index + 1).createFilter(clientSide);
            _next.init(factories, index + 1, clientSide);
            _next.setPrevious(this);
        }

        public Filter getPrevious() {
            return _previous;
        }

        public void setPrevious(Filter value) {
            _previous = value;
        }

        public Filter getNext() {
            return _next;
        }

        public void setNext(Filter value) {
            _next = value;
        }

        //

        public void close() {
            _previous.close();
        }

        public void requestWrite() {
            _previous.requestWrite();
        }

        //

        public void onReadStarted() {
            _next.onReadStarted();
        }

        public void onReadStopped(Throwable t) {
            _next.onReadStopped(t);
        }

        public void onWriteStarted() {
            _next.onWriteStarted();
        }

        public void onWriteStopped(Throwable t) {
            _next.onWriteStopped(t);
        }

        public void read(ByteBuffer buffer) {
            for (;;) {
                byte[] data = _reader.read(buffer.array(), buffer.position(), buffer.limit(), _readBuffer.remaining());
                buffer.position(buffer.limit());

                _readBuffer.put(data);
                _readBuffer.flip();
                _next.read(_readBuffer);
                _readBuffer.clear();

                if (_reader.getRemaining() == 0)
                    break;
            }
        }

        public boolean write(ByteBuffer buffer, Queue<ByteBuffer> headers) {
            byte[] data;

            if (_writer.getRemaining() > 100000)
                data = _writer.read(new byte[0], 0, 0, buffer.remaining());
            else {
                _next.write(_writeBuffer, null);
                data = _writer.read(_writeBuffer.array(), 0, _writeBuffer.position(), buffer.remaining());
                _writeBuffer.clear();
            }

            buffer.put(data);
            return _writer.getRemaining() == 0;
        }
    }
}
