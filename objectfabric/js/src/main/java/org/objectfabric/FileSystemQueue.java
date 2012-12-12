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

import java.util.concurrent.ConcurrentHashMap;

import org.objectfabric.CloseCounter.Callback;

import com.google.gwt.core.client.JavaScriptObject;

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

            runMessages(false);

            while (_ongoing.size() < MAX_ONGOING) {
                final Block block = nextBlock();

                if (block == null)
                    break;

                final FileSystemView view = (FileSystemView) block.URI.getOrCreate(_location);
                final String file = view.folder() + "/" + Utils.getTickHex(block.Tick);
                _ongoing.put(file, block);
                Uint8Array buffer = ((GWTBuff) block.Buffs[0]).subarray();
                open(view, block, file, buffer);

                if (Debug.PERSISTENCE_LOG)
                    Log.write("File write " + file);

                if (Stats.ENABLED)
                    Stats.Instance.BlockWriteCount.incrementAndGet();
            }

            if (Debug.ENABLED)
                ThreadAssert.suspend(this);

            onRunEnded(false);
        }
    }

    private final native void open(FileSystemView view, Block block, String file, Uint8Array buffer) /*-{
    var this_ = this;

    fs.open(file, 'wx', function(err, fd) {
      if (err)
        throw err;

      this_.@org.objectfabric.FileSystemQueue::onWrite(Lorg/objectfabric/FileSystemView;Lorg/objectfabric/BlockQueue$Block;Ljava/lang/String;Lcom/google/gwt/core/client/JavaScriptObject;III) //
        (view, block, file, fd, -1, 0, 0);
    });
    }-*/;

    private final native void write(FileSystemView view, Block block, String file, JavaScriptObject fd, int index, Uint8Array buffer, int position) /*-{
    var this_ = this;

    // writeFile params seem ignored, slice beforehand
    var slice = buffer.buffer.slice(buffer.byteOffset, buffer.byteOffset + buffer.length);

    fs.writeFile(file, slice, 0,slice.length, position, function(err, written_, buffer_) {
      if (err)
        throw err;

      this_.@org.objectfabric.FileSystemQueue::onWrite(Lorg/objectfabric/FileSystemView;Lorg/objectfabric/BlockQueue$Block;Ljava/lang/String;Lcom/google/gwt/core/client/JavaScriptObject;III) //
        (view, block, file, fd, index, position, buffer.length);
    });
    }-*/;

    private void onWrite(FileSystemView view, Block block, String file, JavaScriptObject fd, int index, int position, int written) {
        index++;

        if (index < block.Buffs.length)
            write(view, block, file, fd, index, ((GWTBuff) block.Buffs[index]).subarray(), position + written);
        else
            fsync(view, block, file, fd);
    }

    private final native void fsync(FileSystemView view, Block block, String file, JavaScriptObject fd) /*-{
    var this_ = this;

    fs.fsync(fd, function(err) {
      if (err)
        throw err;

      fs.close(fd);
      this_.@org.objectfabric.FileSystemQueue::onFsync(Lorg/objectfabric/FileSystemView;Lorg/objectfabric/BlockQueue$Block;Ljava/lang/String;)(view, block, file);
    });
    }-*/;

    private void onFsync(FileSystemView view, Block block, String file) {
        block.URI.onAck(view, block.Tick);
        view.add(block.Tick, block.Removals);
        _ongoing.remove(file, block);

        for (int i = 0; i < block.Buffs.length; i++)
            block.Buffs[i].recycle();

        // In case blocks left in queue
        requestRun();

        if (block.Removals != null)
            for (int i = 0; i < block.Removals.length; i++)
                if (!Tick.isNull(block.Removals[i]))
                    delete(view.folder() + "/" + Utils.getTickHex(block.Removals[i]), 0);
    }

    private final native void delete(String file, int attempt) /*-{
    var this_ = this;

    fs.unlink(file, function(err) {
      this_.@org.objectfabric.FileSystemQueue::onDelete(Ljava/lang/String;ZI)(file, err, attempt);
    });
    }-*/;

    private final void onDelete(final String file, boolean error, final int attempt) {
        if (error) {
            if (attempt < 3) {
                Platform.get().schedule(new Runnable() {

                    @Override
                    public void run() {
                        delete(file, attempt + 1);
                    }
                }, 100);
            } else
                Log.write("Could not delete " + file);
        } else if (Debug.PERSISTENCE_LOG)
            Log.write("File delete " + file);
    }
}
