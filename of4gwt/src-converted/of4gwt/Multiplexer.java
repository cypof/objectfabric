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

package of4gwt;

import java.util.Arrays;

import of4gwt.MultiplexerWriter.Command;
import of4gwt.misc.Debug;
import of4gwt.misc.List;
import of4gwt.misc.Log;
import of4gwt.misc.PlatformAdapter;
import of4gwt.misc.PlatformConcurrentQueue;
import of4gwt.misc.ThreadAssert;

/**
 * Does multiplexing between a set of writers and readers. Designed to have one thread at
 * a time for read and one at a time for write.
 */
abstract class Multiplexer {

    /**
     * How much data a writer can put in the buffer before the multiplexing switches to
     * the next one.
     */
    private static final int SLICE;

    static {
        if (!Debug.RANDOMIZE_TRANSFER_LENGTHS)
            SLICE = 4000; // TODO tune
        else
            SLICE = Debug.RANDOMIZED_TRANSFER_LIMIT / 2;
    }

    private static final int ROOM_FOR_INDEX = Debug.COMMUNICATIONS ? 1 + ImmutableWriter.DEBUG_OVERHEAD : 1;

    private static final byte[] MAGIC_NUMBER = new byte[] { 96, 70, 104, -110, 122, 118, 67, -56 };

    private final MultiplexerReader[] _readers;

    private final MultiplexerWriter[] _writers;

    private final PlatformConcurrentQueue<Runnable> _writerCommands = new PlatformConcurrentQueue<Runnable>();

    //

    private int _magicNumberIndex;

    private int _currentReader = -2, _remainingToRead = SLICE - ROOM_FOR_INDEX;

    private int _currentWriter = -3, _remainingToWrite = SLICE - ROOM_FOR_INDEX;

    private boolean _wroteIndex;

    //

    private final byte[] _leftover = new byte[Reader.LARGEST_UNSPLITABLE];

    private int _leftoverLength;

    //

    private Connection _connection;

    // Debug

    private final DistributedHelper _helper;

    protected Multiplexer(int readers, int writers, boolean readMagicNumber, boolean writeMagicNumber) {
        if (Debug.ENABLED) {
            Debug.assertion((readers & TObjectWriter.FLAG_EOF) == 0);
            Debug.assertion((writers & TObjectWriter.FLAG_EOF) == 0);
            Debug.assertion(!(readMagicNumber && writeMagicNumber));
        }

        _readers = new MultiplexerReader[readers];
        _writers = new MultiplexerWriter[writers];

        if (readMagicNumber)
            _magicNumberIndex = MAGIC_NUMBER.length;

        if (writeMagicNumber)
            _magicNumberIndex = -MAGIC_NUMBER.length;

        if (Debug.ENABLED)
            _helper = new DistributedHelper();
        else
            _helper = null;
    }

    protected void threadExchange() {
        if (!Debug.THREADS)
            throw new IllegalStateException();

        ThreadAssert.exchangeGive(_readers, _helper.getChannelSwitchReader());
        ThreadAssert.exchangeGive(_writers, _helper.getChannelSwitchWriter());

        for (MultiplexerReader reader : _readers) {
            List<Object> list = reader.getThreadContextObjects();

            for (int i = 0; i < list.size(); i++)
                ThreadAssert.exchangeGive(_readers, list.get(i));
        }

        for (MultiplexerWriter writer : _writers) {
            List<Object> list = writer.getThreadContextObjects();

            for (int i = 0; i < list.size(); i++)
                ThreadAssert.exchangeGive(_writers, list.get(i));
        }
    }

    final Connection getConnection() {
        return _connection;
    }

    final void setConnection(Connection value) {
        _connection = value;
    }

    final DistributedHelper getHelper() {
        return _helper;
    }

    final Reader[] getReaders() {
        return _readers;
    }

    //

    final void setReader(int index, MultiplexerReader reader) {
        if (Debug.THREADS)
            assertReadThread();

        if (Debug.ENABLED) {
            Debug.assertion(!Arrays.asList(_readers).contains(reader));
            Debug.assertion(_readers[index] == null);
        }

        _readers[index] = reader;
    }

    final void setWriter(int index, MultiplexerWriter writer) {
        if (Debug.THREADS)
            assertWriteThread();

        if (Debug.ENABLED) {
            Debug.assertion(!Arrays.asList(_readers).contains(writer));
            Debug.assertion(_writers[index] == null);
        }

        _writers[index] = writer;
    }

    //

    protected final void startRead() {
        if (Debug.THREADS) {
            ThreadAssert.exchangeTake(_readers);
            assertReadThread();
        }

        for (MultiplexerReader reader : _readers)
            reader.start();
    }

    protected final void stopRead(Throwable t) {
        for (MultiplexerReader reader : _readers)
            reader.stop(t);

        if (Debug.THREADS) {
            for (MultiplexerReader reader : _readers) {
                List<Object> list = reader.getThreadContextObjects();

                for (int i = 0; i < list.size(); i++)
                    ThreadAssert.removePrivate(list.get(i));
            }

            ThreadAssert.removePrivate(_helper.getChannelSwitchReader());
        }
    }

    //

    protected final void startWrite() {
        for (MultiplexerWriter writer : _writers)
            writer.start();
    }

    protected final void stopWrite(Throwable t) {
        for (MultiplexerWriter writer : _writers)
            writer.stop(t);

        if (Debug.THREADS) {
            for (MultiplexerWriter writer : _writers) {
                List<Object> list = writer.getThreadContextObjects();

                for (int i = 0; i < list.size(); i++)
                    ThreadAssert.removePrivate(list.get(i));
            }

            ThreadAssert.removePrivate(_helper.getChannelSwitchWriter());
        }
    }

    //

    protected final void enqueueOnWriterThread(Runnable runnable) {
        _writerCommands.add(runnable);

        if (Debug.ENABLED && !Debug.TESTING)
            Debug.assertion(getConnection() != null);

        if (getConnection() != null)
            getConnection().requestWrite();
    }

    //

    protected final void read(byte[] buffer, int offset, final int limit) {
        if (Debug.THREADS)
            assertReadThread();

        if (offset >= limit)
            return;

        offset = loadLeftover(buffer, offset);

        if (_currentReader < 0) {
            if (_currentReader == -2) {
                byte value = buffer[offset++];
                int flags = value & ~CompileTimeSettings.SERIALIZATION_VERSION_MASK;
                int version = value & CompileTimeSettings.SERIALIZATION_VERSION_MASK;
                flags &= PlatformAdapter.SUPPORTED_SERIALIZATION_FLAGS;
                version = Math.min(version, CompileTimeSettings.SERIALIZATION_VERSION);
                final byte shared = (byte) (flags | version);

                for (MultiplexerReader reader : _readers)
                    reader.setFlags(shared);

                if (Debug.COMMUNICATIONS)
                    getHelper().getChannelSwitchReader().setFlags(shared);

                enqueueOnWriterThread(new Runnable() {

                    public void run() {
                        for (MultiplexerWriter writer : _writers)
                            writer.setFlags(shared);

                        if (Debug.COMMUNICATIONS)
                            getHelper().getChannelSwitchWriter().setFlags(shared);
                    }
                });

                _currentReader++;
            }

            if (_currentReader == -1) {
                while (_magicNumberIndex > 0) {
                    if (offset == limit)
                        return;

                    int index = MAGIC_NUMBER.length - _magicNumberIndex--;

                    if (buffer[offset++] != MAGIC_NUMBER[index])
                        throw new RuntimeException(Strings.INVALID_MAGIC_NUMBER);
                }
            }

            if (_readers.length == 0)
                return;

            int currentReader = 0;

            if (Debug.COMMUNICATIONS) {
                if (_currentReader == -3) {
                    ImmutableReader switcher = getHelper().getChannelSwitchReader();
                    switcher.setBuffer(buffer);
                    switcher.setOffset(offset);
                    switcher.setLimit(limit);

                    if (!switcher.canReadByte()) {
                        saveLeftover(buffer, offset, limit);
                        return;
                    }

                    byte code = switcher.readByte(TObjectWriter.DEBUG_TAG_CODE);
                    currentReader = code & ~TObjectWriter.FLAG_EOF;
                    _remainingToRead = SLICE - ROOM_FOR_INDEX;

                    if (Debug.COMMUNICATIONS_LOG)
                        Log.write("Multiplexer.read, code: " + code);

                    offset = switcher.getOffset();
                }
            }

            _currentReader = currentReader;
        }

        for (;;) {
            int previousOffset = offset;
            int idealLimit = offset + _remainingToRead;
            int actualLimit = Math.min(limit, idealLimit);
            MultiplexerReader reader = _readers[_currentReader];
            reader.setBuffer(buffer);
            reader.setOffset(offset);
            reader.setLimit(actualLimit);

            if (Debug.THREADS)
                if (reader instanceof DistributedReader)
                    ((DistributedReader) reader).getEndpoint().assertReadThread();

            byte code = reader.read();
            offset = reader.getOffset();

            if (reader.interrupted()) {
                if (limit == actualLimit) {
                    _remainingToRead -= reader.getOffset() - previousOffset;
                    saveLeftover(buffer, offset, limit);
                    break;
                }

                ImmutableReader switcher;

                if (Debug.COMMUNICATIONS) {
                    switcher = getHelper().getChannelSwitchReader();
                    switcher.setBuffer(buffer);
                    switcher.setOffset(offset);
                    switcher.setLimit(limit);

                    if (!switcher.canReadByte()) {
                        _currentReader = -3;
                        saveLeftover(buffer, offset, limit);
                        break;
                    }
                } else {
                    reader.setLimit(actualLimit + 1);
                    switcher = reader;
                }

                code = switcher.readByte(TObjectWriter.DEBUG_TAG_CODE);

                if (Debug.COMMUNICATIONS_LOG)
                    Log.write("Multiplexer.read, code: " + code);

                offset = switcher.getOffset();
            }

            if (Debug.ENABLED)
                Debug.assertion(TObjectWriter.isExitCode(code));

            /*
             * This data is for us, so it is index of next reader.
             */
            _currentReader = code & ~TObjectWriter.FLAG_EOF;
            _remainingToRead = SLICE - ROOM_FOR_INDEX;
        }
    }

    private void saveLeftover(byte[] buffer, int offset, int limit) {
        _leftoverLength = limit - offset;
        PlatformAdapter.arraycopy(buffer, offset, _leftover, 0, _leftoverLength);
    }

    private int loadLeftover(byte[] buffer, int offset) {
        if (Debug.ENABLED)
            Debug.assertion(offset >= Reader.LARGEST_UNSPLITABLE);

        int newOffset = offset - _leftoverLength;
        PlatformAdapter.arraycopy(_leftover, 0, buffer, newOffset, _leftoverLength);
        _leftoverLength = 0;
        return newOffset;
    }

    protected final int write(byte[] buffer, int offset, int limit) {
        if (Debug.THREADS)
            assertWriteThread();

        for (;;) {
            Runnable runnable = _writerCommands.poll();

            if (runnable == null)
                break;

            if (Debug.THREADS)
                ThreadAssert.exchangeTake(_writers);

            if (runnable instanceof Command) {
                MultiplexerWriter writer = ((Command) runnable).getWriter();
                writer.enqueue(runnable);
            } else
                runnable.run();
        }

        final int initialOffset = offset;

        /*
         * First roundtrip exchanges magic, serialization version and flags.
         */
        if (_currentWriter < 0) {
            if (_currentWriter == -3) {
                if (offset >= limit)
                    return offset - initialOffset;

                buffer[offset++] = CompileTimeSettings.SERIALIZATION_VERSION | PlatformAdapter.SUPPORTED_SERIALIZATION_FLAGS;
                _currentWriter++;
            }

            if (_currentWriter == -2) {
                while (_magicNumberIndex < 0) {
                    if (offset >= limit)
                        return offset - initialOffset;

                    int index = MAGIC_NUMBER.length + _magicNumberIndex++;
                    buffer[offset++] = MAGIC_NUMBER[index];
                }

                _currentWriter++;
            }

            if (_currentWriter == -1)
                if (_writers.length > 0 && _writers[0].getFlags() != 0)
                    _currentWriter++;
        }

        boolean interrupted = false;

        if (_currentWriter >= 0) {
            int emptyChannelsInARow = 0;

            for (;;) {
                final int startOffset = _wroteIndex ? offset : offset + ROOM_FOR_INDEX;

                if (startOffset >= limit) {
                    // No room left -> leave
                    interrupted = true;
                    break;
                }

                final int indexOffset = offset;
                offset = startOffset;
                final int idealLimit = offset + _remainingToWrite;
                final int actualLimit = Math.min(limit, idealLimit);
                final MultiplexerWriter writer = _writers[_currentWriter];

                if (Debug.THREADS)
                    if (writer instanceof DistributedWriter)
                        ((DistributedWriter) writer).getEndpoint().assertWriteThread();

                writer.setBuffer(buffer);
                writer.setOffset(offset);
                writer.setLimit(actualLimit);
                writer.write();
                final int written = writer.getOffset() - offset;
                offset = writer.getOffset();

                if (!_wroteIndex) {
                    if (written > 0) {
                        if (Debug.ENABLED) {
                            Debug.assertion((_currentWriter & TObjectWriter.FLAG_TOBJECT) == 0);
                            Debug.assertion((_currentWriter & TObjectWriter.FLAG_EOF) == 0);
                        }

                        final byte code = (byte) (_currentWriter | TObjectWriter.FLAG_EOF);

                        if (Debug.COMMUNICATIONS) {
                            ImmutableWriter switcher = getHelper().getChannelSwitchWriter();
                            switcher.setBuffer(buffer);
                            switcher.setOffset(indexOffset);
                            switcher.setLimit(indexOffset + ROOM_FOR_INDEX);
                            Debug.assertion(switcher.canWriteByte());

                            if (Debug.COMMUNICATIONS_LOG)
                                Log.write("Multiplexer.write, code: " + code);

                            switcher.writeByte(code, TObjectWriter.DEBUG_TAG_CODE);
                        } else
                            buffer[indexOffset] = code;

                        _wroteIndex = true;
                        emptyChannelsInARow = 0;
                    } else
                        offset = indexOffset;
                }

                /*
                 * Not enough room to finish channel, leave.
                 */
                if (writer.interrupted() && actualLimit == limit) {
                    _remainingToWrite -= written;
                    interrupted = true;
                    break;
                }

                if (!_wroteIndex) {
                    /*
                     * ++ at the end to move one too far each time, otherwise next run
                     * would start with the same channel.
                     */
                    if (emptyChannelsInARow++ == _writers.length) {
                        boolean empty = true;

                        for (int i = _writers.length - 1; i >= 0; i--)
                            if (_writers[i].hasRunnables())
                                empty = false;

                        if (empty) {
                            // All writers idle -> leave
                            break;
                        }
                    }
                }

                _currentWriter++;

                if (_currentWriter == _writers.length)
                    _currentWriter = 0;

                _remainingToWrite = SLICE - ROOM_FOR_INDEX;
                _wroteIndex = false;
            }
        }

        int written = offset - initialOffset;
        return interrupted ? written : -written - 1;
    }

    // Debug

    void assertCommandsDone() {
        if (!Debug.TESTING)
            throw new RuntimeException();

        Debug.assertion(_writerCommands.peek() == null);
    }

    final void assertReadThread() {
        if (!Debug.THREADS)
            throw new IllegalStateException();

        ThreadAssert.assertPrivate(_helper.getChannelSwitchReader());
    }

    final void assertWriteThread() {
        if (!Debug.THREADS)
            throw new IllegalStateException();

        ThreadAssert.assertPrivate(_helper.getChannelSwitchWriter());
    }

    final void assertNotWriteThread() {
        if (!Debug.THREADS)
            throw new IllegalStateException();

        Debug.assertion(!ThreadAssert.isPrivate(_helper.getChannelSwitchWriter()));
    }
}
