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

import java.util.ArrayList;

final class FileSystemView extends ArrayView { // TODO share stuff with JVM

    private final FileSystemQueue _queue;

    private String _folder;

    private ArrayList<Runnable> _starting = new ArrayList<Runnable>();

    FileSystemView(Location location, String folder, FileSystemQueue queue) {
        super(location);

        _queue = queue;
        canonical(folder);
    }

    final String folder() {
        return _folder;
    }

    private final native void canonical(String path) /*-{
    var this_ = this;

    var realpath = function() {
      fs.realpath(path, function(err, resolvedPath) {
        if (err)
          throw err;

        this_.@org.objectfabric.FileSystemView::onCanonical(Ljava/lang/String;)(resolvedPath);
      });
    }

    fs.exists(path, function(exists, err) {
      if (err)
        throw err;

      if (exists)
        realpath();
      else {
        // TODO find way to do that without creating folder
        fs.mkdir(path, function(err) {
          if (err)
            throw err;

          realpath();
        });
      }
    });
    }-*/;

    private final void onCanonical(String value) {
        String root = ((FileSystem) location()).root();

        if (value.startsWith(root))
            _folder = value;
        else
            Log.write(value + " is outside " + root);

        if (_starting.size() > 0)
            for (Runnable runnable : _starting)
                runnable.run();

        _starting = null;
    }

    @Override
    final void getKnown(final URI uri) {
        long[] ticks = copy();

        if (ticks != null) {
            if (ticks.length != 0 || !location().isCache())
                uri.onKnown(this, ticks);
        } else {
            if (_starting != null) {
                _starting.add(new Runnable() {

                    @Override
                    public void run() {
                        list(uri, null);
                    }
                });
            } else
                list(uri, null);
        }
    }

    @Override
    final void onKnown(final URI uri, final long[] ticks) {
        boolean load;

        synchronized (this) {
            load = isNull();
        }

        if (load) {
            if (_starting != null) {
                _starting.add(new Runnable() {

                    @Override
                    public void run() {
                        list(uri, ticks);
                    }
                });
            } else
                list(uri, ticks);
        } else
            getUnknown(uri, ticks);
    }

    private final void list(URI uri, long[] compare) {
        if (_folder != null)
            list(uri, _folder, compare);
        else
            onFiles(uri, null, compare);
    }

    private final native void list(URI uri, String path, long[] compare) /*-{
    var this_ = this;

    fs.readdir(path, function(err, files) {
      if (err)
        throw err;

      this_.@org.objectfabric.FileSystemView::onFiles(Lorg/objectfabric/URI;[Ljava/lang/String;[J)(uri,files, compare);
    });
    }-*/;

    private final void onFiles(URI uri, String[] files, long[] compare) {
        if (Debug.PERSISTENCE_LOG)
            Log.write("Folder list  " + _folder);

        if (Stats.ENABLED)
            Stats.Instance.BlockListCount.incrementAndGet();

        long[] ticks = null;

        if (files != null && files.length > 0) {
            for (int i = 0; i < files.length; i++)
                ticks = Tick.add(ticks, Utils.getTick(files[i]));
        } else
            ticks = Tick.EMPTY;

        onLoad(uri, ticks, compare);
    }

    @Override
    void getBlock(URI uri, long tick) {
        if (contains(tick) && _folder != null) {
            String file = _folder + "/" + Utils.getTickHex(tick);

            if (Debug.PERSISTENCE_LOG)
                Log.write("File read " + file);

            tryRead(uri, tick, file);
        }
    }

    private void tryRead(final URI uri, final long tick, final String file) {
        // If file exists and a write is ongoing, delay
        if (_queue.ongoing().containsKey(file)) {
            // TODO do same for JVM?
            Platform.get().schedule(new Runnable() {

                @Override
                public void run() {
                    tryRead(uri, tick, file);
                }
            }, 1);
        } else
            read(uri, tick, file);
    }

    private final native void read(URI uri, Long tick, String path) /*-{
    var this_ = this;

    fs.readFile(path, function(err, data) {
      if (err)
        throw err;

      var typed = new Uint8Array(data);
      this_.@org.objectfabric.FileSystemView::onRead(Lorg/objectfabric/URI;Ljava/lang/Long;Lorg/objectfabric/Uint8Array;)(uri, tick, typed);
    });
    }-*/;

    private final void onRead(URI uri, Long tick, Uint8Array buffer) {
        GWTBuff buff = new GWTBuff(buffer);

        if (Debug.ENABLED)
            buff.lock(buff.limit());

        if (Stats.ENABLED)
            Stats.Instance.BlockReadCount.incrementAndGet();

        Buff[] buffs = new Buff[] { buff };
        Exception exception = uri.onBlock(this, tick, buffs, null, true, null, false, null);

        if (exception != null) {
            // TODO make sure exception is related to parsing
            Log.write("Corrupted block " + exception.toString());
            // TODO Make option or callback to clean corrupted
            // file.delete();
        }
    }

    @Override
    final void onBlock(final URI uri, final long tick, final Buff[] buffs, final long[] removals, final boolean requested) {
        if (_starting != null) {
            _starting.add(new Runnable() {

                @Override
                public void run() {
                    enqueue(uri, tick, buffs, removals, requested);
                }
            });
        } else
            enqueue(uri, tick, buffs, removals, requested);
    }

    private void enqueue(URI uri, long tick, Buff[] buffs, long[] removals, boolean requested) {
        if (_folder != null)
            _queue.enqueueBlock(uri, tick, buffs, removals, requested);
    }
}
