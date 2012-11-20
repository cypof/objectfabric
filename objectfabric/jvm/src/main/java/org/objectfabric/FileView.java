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

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.channels.FileChannel;

final class FileView extends ArrayView {

    private final File _folder;

    private final FileQueue _queue;

    FileView(Location location, File folder, FileQueue queue) {
        super(location);

        _folder = folder;
        _queue = queue;
    }

    final File folder() {
        return _folder;
    }

    @Override
    final void getKnown(URI uri) {
        long[] ticks = copy();

        if (ticks != null)
            uri.onKnown(this, ticks);
        else
            list(uri, null);
    }

    @Override
    final void onKnown(URI uri, long[] ticks) {
        boolean load;

        synchronized (this) {
            load = isNull();
        }

        if (load)
            list(uri, ticks);
        else
            getUnknown(uri, ticks);
    }

    private final void list(final URI uri, final long[] compare) {
        ThreadPool.getInstance().execute(new Runnable() {

            @Override
            public void run() {
                if (Debug.PERSISTENCE_LOG)
                    Log.write("Folder list  " + _folder.getPath());

                if (Stats.ENABLED)
                    Stats.Instance.FileListCount.incrementAndGet();

                String[] files;

                try {
                    files = _folder.list();
                } catch (Exception ex) {
                    Log.write(ex);
                    files = null;
                }

                long[] ticks = null;
                long[] updated = null;

                if (files != null && files.length > 0) {
                    for (int i = 0; i < files.length; i++)
                        ticks = Tick.add(ticks, Utils.getTick(files[i]));
                } else if (!location().isCache())
                    ticks = Tick.EMPTY;

                if (ticks != null) {
                    synchronized (this) {
                        updated = merge(ticks);
                    }

                    if (updated != null) {
                        uri.onKnown(FileView.this, updated);
                        ticks = updated;
                    }
                }

                if (compare != null)
                    for (int i = 0; i < compare.length; i++)
                        if (!Tick.isNull(compare[i]))
                            if (!Tick.contains(ticks, compare[i]))
                                uri.getBlock(FileView.this, compare[i]);
            }
        });
    }

    @Override
    void getBlock(final URI uri, final long tick) {
        ThreadPool.getInstance().execute(new Runnable() {

            @Override
            public void run() {
                if (InFlight.awaits(uri, tick)) {
                    File file = new File(_folder, Utils.getTickHex(tick));

                    if (Debug.PERSISTENCE_LOG)
                        Log.write("File read " + file.getPath());

                    List<JVMBuff> list = new List<JVMBuff>();
                    RandomAccessFile raf = null;
                    JVMBuff[] buffs = null;
                    Exception ex = null;

                    try {
                        raf = new RandomAccessFile(file, "r");
                        FileChannel channel = raf.getChannel();

                        // If file exists and a write is ongoing, wait
                        while (_queue.ongoing().containsKey(file.getPath())) {
                            int todo;
                            Log.write("Waiting on file read!");
                            Platform.get().sleep(1);
                        }

                        // TODO lock file for multi-process?
                        // if (channel.tryLock(0, Long.MAX_VALUE, true) != null) {
                        JVMBuff buff = getNewBuff(0);
                        int position = buff.position();
                        int offset = 0;

                        for (;;) {
                            int read = channel.read(buff.getByteBuffer(), offset);

                            if (read < 0) {
                                if (buff.position() > position) {
                                    buff.limit(buff.position());
                                    buff.position(position);
                                    buff.mark();
                                    list.add(buff);
                                } else {
                                    if (Debug.ENABLED)
                                        buff.lock(0);

                                    buff.recycle();
                                }

                                break;
                            } else if (buff.remaining() == 0) {
                                buff.position(position);
                                buff.mark();
                                list.add(buff);
                                buff = getNewBuff(Buff.getLargestUnsplitable());
                                position = buff.position();
                            }

                            offset += read;
                        }

                        buffs = new JVMBuff[list.size()];
                        list.copyToFixed(buffs);

                        if (Debug.ENABLED)
                            for (int i = 0; i < buffs.length; i++)
                                buffs[i].lock(buffs[i].limit());
                        // }
                    } catch (Exception e) {
                        ex = e;
                    } finally {
                        try {
                            // Closes channel & lock
                            if (raf != null)
                                raf.close();
                        } catch (IOException _) {
                            // Ignore
                        }
                    }

                    if (Stats.ENABLED)
                        Stats.Instance.FileReadCount.incrementAndGet();

                    if (buffs != null) {
                        if (Stats.ENABLED) {
                            int bytes = 0;

                            for (int i = 0; i < buffs.length; i++)
                                bytes += buffs[i].remaining();

                            Stats.Instance.FileReadBytes.addAndGet(bytes);
                        }

                        if (Debug.RANDOMIZE_FILE_LOAD_ORDER)
                            Platform.get().sleep(Platform.get().randomInt(100));

                        if (Debug.THREADS)
                            for (int i = 0; i < buffs.length; i++)
                                ThreadAssert.exchangeGive(buffs, buffs[i]);

                        if (buffs.length == 0)
                            file.delete();
                        else {
                            Exception exception = uri.onBlock(FileView.this, tick, buffs, null, true, null, false);

                            if (Debug.THREADS)
                                ThreadAssert.exchangeTake(buffs);

                            if (exception != null) {
                                // TODO make sure exception is related to parsing
                                Log.write("Corrupted file " + file + ": " + exception.toString());
                                // TODO Make option or callback to clean corrupted
                                // file.delete();
                            }
                        }
                    }

                    if (ex != null && !(ex instanceof FileNotFoundException))
                        Log.write(ex);

                    for (int i = 0; i < list.size(); i++)
                        list.get(i).recycle();
                }
            }
        });
    }

    private static JVMBuff getNewBuff(int position) {
        JVMBuff buff = (JVMBuff) Buff.getOrCreate();
        buff.position(position);

        if (Debug.RANDOMIZE_TRANSFER_LENGTHS) {
            int rand = Platform.get().randomInt(buff.remaining() + 1);
            buff.limit(position + rand);
        }

        return buff;
    }

    @Override
    final void onBlock(URI uri, long tick, Buff[] buffs, long[] removals, boolean requested) {
        _queue.enqueueBlock(uri, tick, buffs, removals, requested);
    }
}
