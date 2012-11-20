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

public final class DataGen {

    private final int _readLimit, _writeLimit;

    private int _readTotal, _writtenTotal;

    private Counter _read = new Counter(), _write = new Counter();

    public DataGen(int readLimit, int writeLimit) {
        _readLimit = readLimit;
        _writeLimit = writeLimit;
    }

    public boolean isDone() {
        return _readTotal == _readLimit && _writtenTotal == _writeLimit;
    }

    public boolean isDoneWriting() {
        return _writtenTotal == _writeLimit;
    }

    public void read(Buff[] buffs) {
        for (int i = 0; i < buffs.length; i++) {
            int length = buffs[i].remaining();

            while (buffs[i].remaining() > 0) {
                byte val = buffs[i].getByte();
                byte ref = _read.next();
                Debug.assertAlways(val == ref);
            }

            _readTotal += length;
        }
    }

    public Buff[] write() {
        if (_writtenTotal >= _writeLimit)
            return null;

        int length;

        // switch (Platform.get().getRandomInt(2)) {
        // case 0:
        // length = Platform.get().getRandomInt(10);
        // break;
        // case 1:
        // length = Platform.get().getRandomInt(500);
        // break;
        // case 2:
        // length = Platform.get().getRandomInt(50000);
        // break;
        // default:
        // throw new RuntimeException();
        // }

        length = 4;

        if (length == 0)
            return null;

        length = Math.min(length, _writeLimit - _writtenTotal);
        List<Buff> list = new List<Buff>();
        Buff buff = Buff.getOrCreate();
        list.add(buff);
        int written = 0;

        for (;;) {
            if (written == length) {
                Buff[] buffs = new Buff[list.size()];

                for (int i = 0; i < buffs.length; i++) {
                    buffs[i] = list.get(i);
                    buffs[i].limit(buffs[i].position());
                    buffs[i].position(0);

                    if (Debug.ENABLED)
                        buffs[i].lock(buffs[i].limit());
                }

                _writtenTotal += written;
                return buffs;
            }

            if (buff.remaining() == 0)
                list.add(buff = Buff.getOrCreate());

            buff.putByte(_write.next());
            written++;
        }
    }

    private static final class Counter {

        private int _value;

        private byte[] _bytes = new byte[4];

        private int _index;

        public byte next() {
            if (_index == 4) {
                _bytes[0] = (byte) ((_value >>> 0) & 0xff);
                _bytes[1] = (byte) ((_value >>> 8) & 0xff);
                _bytes[2] = (byte) ((_value >>> 16) & 0xff);
                _bytes[3] = (byte) ((_value >>> 24) & 0xff);
                _value++;
                _index = 0;
            }

            return _bytes[_index++];
        }
    }
}
