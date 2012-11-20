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

import org.junit.Test;

@SuppressWarnings("unchecked")
public class BlockWriteTest extends TestsHelper {

    @Test
    public void run1() throws Exception {
        for (int test = 0; test < 10; test++) {
            Queue<Buff> queue = new Queue<Buff>();
            int room = 1000;
            Buff[] buffs = new Buff[10];

            for (int i = 0; i < buffs.length; i++) {
                buffs[i] = Buff.getOrCreate();
                buffs[i].putLong(42);
                buffs[i].limit(buffs[i].position());
                buffs[i].position(0);

                if (Debug.ENABLED)
                    buffs[i].lock(buffs[i].position());
            }

            Buff buff = Buff.getOrCreate();
            buff.putByte(TObject.SERIALIZATION_VERSION);
            ImmutableWriter writer = new ImmutableWriter(new List<Object>());
            writer.setBuff(buff);
            Peer peer = Peer.get(new UID(Platform.get().newUID()));
            Serialization.writeTick(writer, Tick.get(peer.index(), 10));
            Serialization.enqueueWritten(queue, buff);

            for (;;) {
                room = Platform.get().randomInt(100);
                buff.limit(Math.min(buff.position() + room, buff.capacity()));
                Serialization.writeBlock(writer, queue, room, buffs, null, true);

                if (!writer.interrupted())
                    break;
            }

            buff.limit(buff.position());
            buff.reset();

            if (Debug.ENABLED)
                buff.lock(buff.position());

            queue.add(buff);

            //

            ImmutableReader reader = new ImmutableReader(new List<Object>());
            buff = Buff.getOrCreate();

            for (int i = 0; i < queue.size(); i++) {
                byte[] temp = new byte[queue.get(i).remaining()];
                queue.get(i).getBytes(temp, 0, temp.length);
                queue.get(i).recycle();
                buff.putBytes(temp, 0, temp.length);
            }

            int limit = buff.position();
            buff.position(0);
            buff.limit(limit);
            reader.setBuff(buff);
            reader.startRead();

            while (buff.position() < limit) {
                int rand = Platform.get().randomInt(limit - buff.position() + 1);
                buff.limit(Math.min(buff.position() + rand, limit));
                Serialization.readBlock(reader, null, null, null);
            }

            if (Debug.ENABLED)
                buff.lock(buff.position());

            buff.recycle();

            if (Debug.THREADS) {
                ThreadAssert.removePrivateList(reader.getThreadContextObjects());
                ThreadAssert.removePrivateList(writer.getThreadContextObjects());
            }
        }
    }
}
