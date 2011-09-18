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

package com.objectfabric;

import java.util.ArrayList;

import junit.framework.Assert;

import org.junit.Test;

import com.objectfabric.TObject.UserTObject;
import com.objectfabric.TObject.Version;
import com.objectfabric.misc.Debug;
import com.objectfabric.misc.List;
import com.objectfabric.misc.PlatformAdapter;
import com.objectfabric.transports.RandomSplitter;

public class MultiplexerStrings extends TestsHelper {

    private static final int COUNT = 1000;

    private final ArrayList<TestReader> _readers = new ArrayList<TestReader>();

    private final ArrayList<TestWriter> _writers = new ArrayList<TestWriter>();

    private Multiplexer _a, _b;

    private final RandomSplitter _ab = new RandomSplitter();

    private final RandomSplitter _ba = new RandomSplitter();

    private byte[] _buffer;

    public MultiplexerStrings() {
    }

    private void create(int channelCount) {
        _a = new Multiplexer(0, channelCount, true, false) {
        };
        _b = new Multiplexer(channelCount, 0, false, true) {
        };

        for (int i = 0; i < channelCount; i++) {
            TestReader reader = new TestReader();
            TestWriter writer = new TestWriter();
            _readers.add(reader);
            _writers.add(writer);

            _a.setWriter(i, writer);
            _b.setReader(i, reader);
        }

        _a.startRead();
        _a.startWrite();

        _b.startRead();
        _b.startWrite();
    }

    private void dispose(boolean assertIdle) {
        if (assertIdle) {
            Debug.assertAlways(_ab.getRemaining() == 0);
            Debug.assertAlways(_ba.getRemaining() == 0);
        } else {
            _ab.clear();
            _ba.clear();
        }

        _a.stopRead(null);
        _a.stopWrite(null);

        _b.stopRead(null);
        _b.stopWrite(null);

        _a = null;
        _b = null;

        _readers.clear();
        _writers.clear();
    }

    private void transfer() {
        int length = com.objectfabric.misc.PlatformAdapter.getRandomInt(1000);

        if (_buffer == null || _buffer.length < length)
            _buffer = new byte[length];

        int ab = _a.write(_buffer, 0, length);

        if (ab < 0)
            ab = -ab - 1;

        byte[] t1 = _ab.read(_buffer, 0, ab, 0);
        _b.read(t1, Reader.LARGEST_UNSPLITABLE, t1.length);

        length = com.objectfabric.misc.PlatformAdapter.getRandomInt(1000);

        if (_buffer.length < length)
            _buffer = new byte[length];

        int ba = _b.write(_buffer, 0, length);

        if (ba < 0)
            ba = -ba - 1;

        byte[] t2 = _ba.read(_buffer, 0, ba, 0);
        _a.read(t2, Reader.LARGEST_UNSPLITABLE, t2.length);
    }

    @Test
    public void run1() {
        create(1);

        while (_readers.get(0)._readCounter < COUNT)
            transfer();

        dispose(true);
    }

    @Test
    public void run2() {
        create(2);

        TestReader a = _readers.get(0);
        TestReader b = _readers.get(1);

        while (a._readCounter < COUNT || b._readCounter < COUNT)
            transfer();

        System.out.println("Reader 0: " + a._readCounter + ", Reader 1: " + b._readCounter);

        dispose(true);
    }

    @Test
    public void run3() {
        create(63);

        TestReader first = _readers.get(0);

        while (first._readCounter < COUNT)
            transfer();

        StringBuilder sb = new StringBuilder();

        for (TestReader reader : _readers)
            sb.append(", " + reader._readCounter + " (" + reader._readCalls + " calls)");

        System.out.println("Readers: " + sb);
        sb.setLength(0);

        for (TestWriter writer : _writers)
            sb.append(", " + writer._writeCounter + " (" + writer._writeCalls + " calls)");

        System.out.println("Writers: " + sb);
        dispose(false);
    }

    public static void main(String[] args) throws Exception {
        MultiplexerStrings test = new MultiplexerStrings();

        for (int i = 0; i < 100; i++)
            test.run3();
    }

    private static final class TestReader extends MultiplexerReader {

        int _readCalls, _readCounter;

        public TestReader() {
            super(new List<UserTObject>());
        }

        private enum ReadCodeStep {
            CODE, OBJECT
        }

        @SuppressWarnings("fallthrough")
        @Override
        public byte read() {
            _readCalls++;

            for (;;) {
                ReadCodeStep step = ReadCodeStep.CODE;
                byte code = 0;

                if (interrupted()) {
                    step = (ReadCodeStep) resume();
                    code = (Byte) resume();
                }

                switch (step) {
                    case CODE: {
                        if (!canReadByte()) {
                            interrupt(code);
                            interrupt(ReadCodeStep.CODE);
                            return 0;
                        }

                        code = readByte(TObjectWriter.DEBUG_TAG_CODE);

                        if (TObjectWriter.isExitCode(code))
                            return code;
                    }
                    case OBJECT: {
                        Debug.assertAlways((code & ~TObjectWriter.FLAG_IMMUTABLE) == ImmutableClass.STRING_INDEX);
                        String text = readString();

                        if (interrupted()) {
                            interrupt(code);
                            interrupt(ReadCodeStep.OBJECT);
                            return 0;
                        }

                        for (String n : text.split(",")) {
                            if (n.length() > 0) {
                                int expected = _readCounter++;
                                int value = Integer.parseInt(n);
                                Assert.assertEquals(expected, value);
                            }
                        }
                    }
                }
            }
        }
    }

    private static final class TestWriter extends MultiplexerWriter {

        private int _writeCalls, _writeCounter;

        private int _count;

        private String _text;

        @Override
        public void write() {
            if (_writeCounter == COUNT)
                return;

            _writeCalls++;

            if (!interrupted()) {
                _count = PlatformAdapter.getRandomInt(400);

                if (_count > COUNT - _writeCounter)
                    _count = COUNT - _writeCounter;

                _text = "";

                for (int i = 0; i < _count; i++)
                    _text += (_writeCounter + i) + ",";
            }

            writeObject(_text);

            if (!interrupted())
                _writeCounter += _count;
        }

        @Override
        boolean isKnown(Version shared) {
            throw new IllegalStateException();
        }

        @Override
        void setCreated(Version shared) {
            throw new IllegalStateException();
        }

        @Override
        public void writeCommand(byte command) {
            throw new IllegalStateException();
        }
    }
}
