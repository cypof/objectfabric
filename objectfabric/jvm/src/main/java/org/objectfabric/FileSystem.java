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
import java.util.concurrent.atomic.AtomicBoolean;

import org.objectfabric.CloseCounter.Callback;

/**
 * Resolves URIs to folders and stores resource versions as files.
 */
public class FileSystem extends Origin implements URIHandler {

    // TODO inject exceptions for testing

    static {
        JVMPlatform.loadClass();
    }

    private final String _rootPath;

    private final File _root;

    private final FileSystemQueue _queue = new FileSystemQueue(this);

    public FileSystem(String root) {
        super(false);

        _rootPath = root;

        if (Debug.PERSISTENCE_LOG)
            Log.write("Folder open " + _rootPath);

        try {
            _root = new File(_rootPath).getCanonicalFile();

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
    public URI handle(Address address, String path) {
        return getURI(path);
    }

    @Override
    final View newView(URI uri) {
        try {
            File file = new File(_root, uri.path()).getCanonicalFile();

            if (file.getPath().startsWith(_root.getPath()))
                return new FileSystemView(this, file, _queue);
        } catch (IOException ex) {
            Log.write(ex);
        }

        return null;
    }

    @Override
    public String toString() {
        return "file://" + _rootPath;
    }

    // Debug

    final void close() {
        final AtomicBoolean done = new AtomicBoolean();

        _queue.requestClose(new Callback() {

            @Override
            public void call() {
                done.set(true);
            }
        });

        while (!done.get())
            Platform.get().sleep(1);
    }
}
