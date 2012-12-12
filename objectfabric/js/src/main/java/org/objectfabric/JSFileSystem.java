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

import org.objectfabric.JS.External;
import org.objectfabric.JS.Internal;
import org.timepedia.exporter.client.Export;

@Export("filesystem")
public class JSFileSystem implements External {

    static final class FileSystemInternal extends FileSystem implements Internal {

        JSFileSystem _js;

        FileSystemInternal(String root) {
            super(root);
        }

        @Override
        public External external() {
            if (_js == null) {
                _js = new JSFileSystem();
                _js._internal = this;
            }

            return _js;
        }
    }

    private FileSystemInternal _internal;

    public JSFileSystem() {
        this("./");
    }

    public JSFileSystem(String root) {
        _internal = new FileSystemInternal(root);
    }

    @Override
    public FileSystemInternal internal() {
        return _internal;
    }
}
