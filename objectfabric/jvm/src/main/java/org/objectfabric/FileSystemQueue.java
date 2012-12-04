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
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.util.concurrent.ConcurrentHashMap;

import org.objectfabric.CloseCounter.Callback;

final class FileSystemQueue extends BlockQueue implements Runnable {

    // TODO limit pending reads too?
    private static final int MAX_ONGOING = 100;

    private final Location _location;

    private final ConcurrentHashMap<String, Object> _ongoing = new ConcurrentHashMap<String, Object>();

    FileSystemQueue(Location location) {
        _location = location;

        if (Debug.THREADS)
            ThreadAssert.exchangeGive(this, this);

        onStarted();
    }

    @Override
    void onClose(Callback callback) {
        Object key;

        if (Debug.ENABLED) {
            ThreadAssert.suspend(key = new Object());
            ThreadAssert.resume(this, false);
        }

        if (Debug.THREADS) {
            ThreadAssert.exchangeTake(this);
            ThreadAssert.removePrivate(this);
        }

        if (Debug.ENABLED)
            ThreadAssert.resume(key);

        super.onClose(callback);
    }

    final ConcurrentHashMap<String, Object> ongoing() {
        return _ongoing;
    }

    @Override
    protected void enqueue() {
        Platform.get().execute(this);
    }

    @Override
    public void run() {
        if (onRunStarting()) {
            if (Debug.ENABLED)
                ThreadAssert.resume(this, false);

            if (Debug.THREADS)
                ThreadAssert.exchangeTake(this);

            runMessages(false);

            while (_ongoing.size() < MAX_ONGOING) {
                final Block block = nextBlock();

                if (block == null)
                    break;

                final FileSystemView view = (FileSystemView) block.URI.getOrCreate(_location);
                final File file = new File(view.folder(), Utils.getTickHex(block.Tick));
                final Object write = new Object();
                _ongoing.put(file.getPath(), write);

                if (Debug.THREADS)
                    for (int i = 0; i < block.Buffs.length; i++)
                        ThreadAssert.exchangeGive(block, block.Buffs[i]);

                ThreadPool.getInstance().execute(new Runnable() {

                    @Override
                    public void run() {
                        if (Debug.THREADS)
                            ThreadAssert.exchangeTake(block);

                        if (Debug.ENABLED) {
                            Debug.assertion(block.Buffs.length > 0);
                            Debug.assertion(block.Buffs[0].remaining() > 0);
                        }

                        if (Debug.THREADS)
                            ThreadAssert.exchangeTake(block.Buffs);

                        boolean ok = write(view, file, block.Buffs, block.Removals);

                        if (ok) {
                            block.URI.onAck(view, block.Tick);
                            view.add(block.Tick, block.Removals);
                        }

                        _ongoing.remove(file.getPath(), write);

                        // In case blocks left in queue
                        requestRun();
                    }
                });
            }

            if (Debug.ENABLED)
                ThreadAssert.suspend(this);

            onRunEnded(false);
        }
    }

    private boolean write(FileSystemView view, File file, Buff[] buffs, long[] removals) {
        if (Debug.PERSISTENCE_LOG)
            Log.write("File write " + file.getPath());

        if (Stats.ENABLED)
            Stats.Instance.BlockWriteCount.incrementAndGet();

        RandomAccessFile raf = null;
        boolean ok = false;

        try {
            // TODO do earlier?
            file.getParentFile().mkdirs();

            raf = new RandomAccessFile(file, "rw");
            FileChannel channel = raf.getChannel();

            // TODO lock file for multi-process?
            // if (channel.tryLock() != null) {
            ByteBuffer[] buffers = new ByteBuffer[buffs.length];

            for (int i = 0; i < buffs.length; i++)
                buffers[i] = ((JVMBuff) buffs[i]).getByteBuffer();

            channel.write(buffers);

            // TODO remove? Maybe delay removals 1 minute instead?
            channel.force(false);

            if (removals != null)
                for (int i = 0; i < removals.length; i++)
                    if (!Tick.isNull(removals[i]))
                        delete(view.folder(), Utils.getTickHex(removals[i]), 0);

            ok = true;
            // }
        } catch (Exception ex) {
            Log.write(ex);
        }

        try {
            // Closes channel & lock
            if (raf != null)
                raf.close();
        } catch (Exception _) {
            // Ignore
        }

        for (int i = 0; i < buffs.length; i++)
            buffs[i].recycle();

        return ok;
    }

    private static void delete(final File folder, final String name, final int attempt) {
        File file = new File(folder, name);

        if (file.exists()) {
            if (Debug.PERSISTENCE_LOG)
                Log.write("File delete " + file);

            try {
                if (!file.delete()) {
                    if (attempt < 3) {
                        ThreadPool.scheduleOnce(new Runnable() {

                            @Override
                            public void run() {
                                delete(folder, name, attempt + 1);
                            }
                        }, 100);
                    } else
                        Log.write("Could not delete " + file);
                }
            } catch (Exception e) {
                Log.write(e);
            }
        }
    }
}
