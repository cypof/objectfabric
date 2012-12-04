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

public class VMConnection extends Connection {

    public static final int CLOSED = Integer.MAX_VALUE;

    private final RandomSplitter _randomSplitter = new RandomSplitter();

    private SeparateCL _classLoader;

    private static VMConnection _clientInstance;

    private byte[] _buffer;

    protected VMConnection(Location location) {
        super(location, null);

        onStarted();

        if (location instanceof Remote)
            _clientInstance = this;
    }

    static final Object _static = new Object();

    public void read(byte[] buffer, int length) {
        if (resumeRead()) {
            Buff buff = Buff.getOrCreate();
            buff.position(Buff.getLargestUnsplitable());
            byte[] temp = _randomSplitter.read(buffer, 0, length, buff.remaining());
            buff.putImmutably(temp, 0, temp.length);
            buff.limit(buff.position() + temp.length);

            if (Debug.ENABLED && buff.remaining() == 0)
                buff.lock(buff.limit());

            read(buff);
            buff.recycle();

            suspendRead();
        }
    }

    public int write(byte[] buffer) {
        int offset = 0;

        if (isScheduled()) {
            _buffer = buffer;
            run();
        }

        return offset;
    }

    @Override
    protected void write() {
        Queue<Buff> buffs = fill(_buffer.length);

        if (buffs != null) {
            int offset = 0;

            for (;;) {
                Buff buff = buffs.poll();

                if (buff == null)
                    break;

                int remaining = buff.remaining();
                buff.getImmutably(_buffer, offset, remaining);
                buff.recycle();
                offset += remaining;
            }

            writeComplete();
        }
    }

    public boolean serverTransfer(byte[] buffer) {
        boolean idle = true;
        int length = write(buffer);
        idle &= length == 0;
        length = (Integer) _classLoader.invoke(VMConnection.class.getName(), "clientTransfer", new Class[] { byte[].class, int.class }, buffer, length);
        idle &= length == 0;

        if (length == CLOSED)
            requestClose(null);
        else
            read(buffer, length);

        return idle;
    }

    @SuppressWarnings("unused")
    public static int clientTransfer(byte[] buffer, int length) {
        if (_clientInstance == null)
            System.err.println("ff");
        if (_clientInstance.isClosed())
            return CLOSED;

        _clientInstance.read(buffer, length);
        return _clientInstance.write(buffer);
    }

    public SeparateCL getClassLoader() {
        return _classLoader;
    }

    public void setClassLoader(SeparateCL value) {
        _classLoader = value;
    }

    @Override
    protected void enqueue() {
    }
}
