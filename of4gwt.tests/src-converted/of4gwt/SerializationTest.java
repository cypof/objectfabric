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

import of4gwt.TObject.UserTObject;
import of4gwt.TObject.Version;
import of4gwt.misc.Debug;
import of4gwt.misc.List;
import of4gwt.misc.PlatformAdapter;
import of4gwt.misc.ThreadAssert;
import of4gwt.transports.RandomSplitter;

public abstract class SerializationTest {

    protected abstract TestWriter createTestWriter(boolean unknown);

    protected abstract TestReader createTestReader(boolean unknown);

    public void run(boolean check, boolean unknown, String assembly) {
        if (Debug.ENABLED)
            ImmutableWriter.setCheckCommunications(check);

        RandomSplitter randomLengthReader = new RandomSplitter();

        for (int serializationVersion = CompileTimeSettings.SERIALIZATION_VERSION_04; serializationVersion <= CompileTimeSettings.SERIALIZATION_VERSION; serializationVersion++) {
            for (int serializationFlags = 0; serializationFlags < 1 << (Byte.SIZE - CompileTimeSettings.SERIALIZATION_FLAGS_OFFSET); serializationFlags++) {
                byte flags = (byte) ((serializationFlags << CompileTimeSettings.SERIALIZATION_FLAGS_OFFSET) | serializationVersion);

                for (int i = 0; i < 10; i++) {
                    TestWriter writer = createTestWriter(unknown);
                    TestReader reader = createTestReader(unknown);
                    writer.setFlags(flags);
                    reader.setFlags(flags);
                    Debug.assertAlways(randomLengthReader.getRemaining() == 0);

                    byte[] buffer = null;

                    while (!reader.isDone()) {
                        int length = PlatformAdapter.getRandomInt(50);
                        // length = 40;

                        if (buffer == null || buffer.length < length)
                            buffer = new byte[length];

                        writer.setBuffer(buffer);
                        writer.setOffset(0);
                        writer.setLimit(length);
                        writer.run();

                        byte[] temp = randomLengthReader.read(buffer, 0, writer.getOffset(), 0);

                        temp = exchange(temp);

                        reader.setBuffer(temp);
                        reader.setOffset(0);
                        reader.setLimit(temp.length);
                        reader.run();
                        reader.saveRemaining();
                    }

                    ThreadAssert.getOrCreateCurrent().resetReaderDebugCounter(reader);
                    ThreadAssert.getOrCreateCurrent().resetWriterDebugCounter(reader);

                    if (Debug.THREADS) {
                        List<Object> writers = writer.getThreadContextObjects();
                        List<Object> readers = reader.getThreadContextObjects();

                        for (int t = 0; t < writers.size(); t++)
                            ThreadAssert.removePrivate(writers.get(t));

                        for (int t = 0; t < readers.size(); t++)
                            ThreadAssert.removePrivate(readers.get(t));
                    }
                }
            }
        }
    }

    protected byte[] exchange(byte[] buffer) {
        return buffer;
    }

    public abstract static class TestWriter extends Writer {

        public TestWriter() {
            super(false);
        }

        public abstract void run();

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

    public static abstract class TestReader extends Reader {

        public TestReader() {
            super(new List<UserTObject>());
        }

        public abstract void run();

        public abstract boolean isDone();
    }
}