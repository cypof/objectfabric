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

import java.util.concurrent.atomic.AtomicBoolean;

import org.objectfabric.CloseCounter.Callback;

/**
 * Resolves URIs to folders and stores resource versions as files.
 */
public class FileSystem extends Origin implements URIHandler {

    // TODO inject exceptions for testing

    static {
        GWTPlatform.loadClass();
    }

    private final String _givenRoot;

    private final String _root;

    private final FileSystemQueue _queue = new FileSystemQueue(this);

    public FileSystem(String root) {
        super(false);

        _givenRoot = root;
        _root = init(root);

        if (Debug.PERSISTENCE_LOG)
            Log.write("Folder open " + root);
    }

    private final native String init(String path) /*-{
    if (!fs.existsSync(path))
      fs.mkdirSync(path);

    return fs.realpathSync(path);
    }-*/;

    final String root() {
        return _root;
    }

    @Override
    public URI handle(Address address, String path) {
        return getURI(path);
    }

    @Override
    final View newView(URI uri) {
        return new FileSystemView(this, _root + "/" + uri.path(), _queue);
    }

    @Override
    public String toString() {
        return "file://" + _givenRoot;
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
