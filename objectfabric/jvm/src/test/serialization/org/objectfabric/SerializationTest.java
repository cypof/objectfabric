/**
 * This file is part of ObjectFabric (http://objectfabric.org).
 *
 * ObjectFabric is licensed under the Apache License, Version 2.0, the terms
 * of which may be found at http://www.apache.org/licenses/LICENSE-2.0.html.
 * 
 * Copyright ObjectFabric Inc.
 * 
 * This file is provided AS IS with NO WARRANTY OF ANY KIND, INCLUDING THE
 * WARRANTY OF DESIGN, MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE.
 */

package org.objectfabric;

public abstract class SerializationTest extends TestsHelper {

    protected abstract TestWriter createTestWriter(boolean unknown);

    protected abstract TestReader createTestReader(boolean unknown);

    public void run(boolean check, boolean unknown) {
        if (Debug.ENABLED)
            ImmutableWriter.setCheckCommunications(check);

        Buff buff = Buff.getOrCreate();
        byte[] leftover = new byte[Buff.getLargestUnsplitable()];
        int leftoverSize = 0;

        for (int i = 0; i < 10; i++) {
            TestWriter writer = createTestWriter(unknown);
            TestReader reader = createTestReader(unknown);
            writer.setBuff(buff);
            reader.setBuff(buff);
            buff.position(0);
            buff.limit(buff.capacity());
            buff.putByte(TObject.SERIALIZATION_VERSION);
            buff.position(0);
            reader.startRead();

            while (!reader.isDone()) {
                int write = Platform.get().randomInt(50);
                // length = 40;

                buff.position(Buff.getLargestUnsplitable());
                buff.limit(Buff.getLargestUnsplitable() + write);
                writer.run();
                final int limit = buff.position();
                buff.position(Buff.getLargestUnsplitable());

                while (buff.position() < limit) {
                    int read = Platform.get().randomInt(limit - buff.position() + 1);
                    buff.limit(buff.position() + read);
                    buff.position(buff.position() - leftoverSize);
                    buff.putImmutably(leftover, 0, leftoverSize);

                    reader.run();

                    leftoverSize = buff.remaining();
                    buff.getImmutably(leftover, 0, leftoverSize);
                }
            }

            if (Debug.ENABLED) {
                ThreadAssert.getOrCreateCurrent().resetReaderDebugCounter(reader);
                ThreadAssert.getOrCreateCurrent().resetWriterDebugCounter(reader);
            }

            if (Debug.THREADS) {
                List<Object> writers = writer.getThreadContextObjects();
                List<Object> readers = reader.getThreadContextObjects();

                for (int t = 0; t < writers.size(); t++)
                    ThreadAssert.removePrivate(writers.get(t));

                for (int t = 0; t < readers.size(); t++)
                    ThreadAssert.removePrivate(readers.get(t));
            }
        }

        if (Debug.ENABLED)
            buff.lock(buff.position());

        buff.recycle();
    }

    public abstract static class TestWriter extends ImmutableWriter {

        public TestWriter() {
            super(new List<Object>());
        }

        public abstract void run();
    }

    public static abstract class TestReader extends ImmutableReader {

        public TestReader() {
            super(new List<Object>());
        }

        public abstract void run();

        public abstract boolean isDone();
    }
}