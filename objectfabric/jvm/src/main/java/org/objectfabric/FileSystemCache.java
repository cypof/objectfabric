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
import java.io.IOException;
import java.io.RandomAccessFile;
import java.lang.ref.WeakReference;
import java.nio.channels.FileLock;

import org.objectfabric.WorkspaceSave.Callback;

/**
 * Cache backed by a file system.
 */
public class FileSystemCache extends Location {

    private static final String OBJS = "objs", UIDS = "uids", LOCK = ".lock";

    private static final int SHA1_HEX = SHA1Digest.LENGTH * 2;

    private static final Object _lock = new Object();

    static {
        JVMPlatform.loadClass();
    }

    private final String _path;

    private final File _root;

    private final FileQueue _queue = new FileQueue(this);

    public FileSystemCache(String path) {
        _path = path;

        if (Debug.PERSISTENCE_LOG)
            Log.write("Cache open " + _path);

        try {
            _root = new File(_path + File.separatorChar + OBJS);

            if (_root.exists()) {
                if (!_root.canWrite())
                    throw new IOException(Strings.CANNOT_OPEN + _root);
            } else {
                if (!_root.mkdirs())
                    throw new IOException(Strings.CANNOT_CREATE + _root);
            }
        } catch (Exception ex) {
            throw new RuntimeException(ex);
        }
    }

    @Override
    final View newView(URI uri) {
        SHA1Digest digest = new SHA1Digest();
        uri.origin().sha1(digest);
        digest.update(uri.path());
        ThreadContext thread = ThreadContext.get();
        byte[] sha1 = thread.Sha1;
        digest.doFinal(sha1, 0);
        char[] hex = thread.PathCache;
        hex[0] = Utils.HEX[(sha1[0] >>> 4) & 0xf];
        hex[1] = Utils.HEX[(sha1[0] >>> 0) & 0xf];
        hex[2] = Utils.FILE_SEPARATOR;
        hex[3] = Utils.HEX[(sha1[1] >>> 4) & 0xf];
        hex[4] = Utils.HEX[(sha1[1] >>> 0) & 0xf];
        hex[5] = Utils.HEX[(sha1[2] >>> 4) & 0xf];
        // Additional break to avoids sub-directory limits (e.g. ext3: 32000)
        hex[6] = Utils.FILE_SEPARATOR;
        hex[7] = Utils.HEX[(sha1[2] >>> 0) & 0xf];
        Utils.getBytesHex(sha1, 3, sha1.length - 3, hex, 8);
        return new FileView(this, new File(_root, new String(hex, 0, SHA1_HEX + 2)), _queue);
    }

    //

    @Override
    boolean start(final WorkspaceSave save) {
        ThreadPool.getInstance().execute(new Runnable() {

            @Override
            public void run() {
                if (save.start()) {
                    save.run(new Callback() {

                        @Override
                        void run(long tick, byte[] range, byte id) {
                            String a = Utils.getTickHex(tick);
                            char[] chars = ThreadContext.get().PathCache;
                            Utils.getBytesHex(range, 0, range.length, chars, 0);
                            chars[Utils.PEER_HEX + 0] = Utils.HEX[(id >>> 4) & 0xf];
                            chars[Utils.PEER_HEX + 1] = Utils.HEX[(id >>> 0) & 0xf];
                            String b = new String(chars, 0, Utils.PEER_HEX + 2);

                            try {
                                File uids = new File(_path + File.separatorChar + UIDS);
                                uids.mkdir();
                                RandomAccessFile file = new RandomAccessFile(new File(uids, a + b), "rw");
                                file.close();
                            } catch (Exception ex) {
                                Log.write(ex);
                            }

                            save.done();
                        }
                    });
                }
            }
        });

        return true;
    }

    @Override
    void start(final WorkspaceLoad load) {
        ThreadPool.getInstance().execute(new Runnable() {

            @Override
            public void run() {
                RandomAccessFile raf = null;
                FileLock lock = null;
                String file = null;

                try {
                    File uids = new File(_path + File.separatorChar + UIDS);

                    synchronized (_lock) {
                        if (uids.exists() && !load.isDone()) {
                            raf = new RandomAccessFile(new File(uids, LOCK), "rw");
                            lock = raf.getChannel().lock();
                            String[] files = uids.list();

                            if (files != null) {
                                for (int i = 0; i < files.length; i++) {
                                    if (!LOCK.equals(files[i]) && new File(uids, files[i]).delete()) {
                                        file = files[i];
                                        break;
                                    }
                                }
                            }
                        }
                    }
                } catch (Exception ex) {
                    Log.write(ex);
                } finally {
                    if (lock != null) {
                        try {
                            lock.release();
                        } catch (Exception _) {
                            // Ignore
                        }
                    }

                    if (raf != null) {
                        try {
                            raf.close();
                        } catch (Exception _) {
                            // Ignore
                        }
                    }
                }

                if (file == null)
                    load.onResponseNull();
                else {
                    long tick = Utils.getTick(file);
                    int offset = Utils.TIME_HEX + Utils.PEER_HEX;
                    byte[] range = Utils.getBytes(file, offset, UID.LENGTH);
                    offset += Utils.PEER_HEX;
                    int a = Utils.XEH[file.charAt(offset + 0) - '0'] << 4;
                    int b = Utils.XEH[file.charAt(offset + 1) - '0'] << 0;
                    byte id = (byte) (a | b);
                    load.onResponse(tick, range, id);
                }
            }
        });
    }

    /**
     * Clears the cache and deletes all backing files.
     * 
     * @throws RuntimeException
     *             if a file or folder cannot be deleted.
     */
    public final void clear() {
        File objs = new File(_path, OBJS);

        if (objs.exists())
            clearFolder(objs);

        for (WeakReference<Remote> ref : ClientURIHandler.remotes().values()) {
            Remote remote = ref.get();

            if (remote != null)
                for (URI uri : remote.uris().values())
                    ((ArrayView) uri.getOrCreate(this)).reset();
        }
    }

    private static void clearFolder(File folder) {
        for (File child : folder.listFiles()) {
            if (child.isDirectory())
                clearFolder(child);

            if (!child.delete())
                throw new RuntimeException("Could not delete " + child);
        }
    }

    @Override
    public String toString() {
        return "FileSystemCache (" + _path + ")";
    }
}
