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

import org.objectfabric.JS.Closure;
import org.objectfabric.JS.External;
import org.objectfabric.JS.Internal;
import org.objectfabric.JSResource.ResourceInternal;
import org.timepedia.exporter.client.Export;

@Export("workspace")
public class JSWorkspace implements External {

    static final class WorkspaceInternal extends GWTWorkspace implements Internal {

        JSWorkspace _js;

        @Override
        public External external() {
            if (_js == null) {
                _js = new JSWorkspace();
                _js._internal = this;
            }

            return _js;
        }

        @Override
        Resource newResource(URI uri) {
            return new JSResource.ResourceInternal(this, uri);
        }
    }

    private WorkspaceInternal _internal;

    public JSWorkspace() {
        _internal = new WorkspaceInternal();
    }

    @Override
    public Internal internal() {
        return _internal;
    }

    public void open(final String uri, final Closure closure) {
        _internal.openAsync(uri, new AsyncCallback<Resource>() {

            @Override
            public void onSuccess(Resource result) {
                closure.runExportable(null, ((ResourceInternal) result).external());
            }

            @Override
            public void onFailure(Exception e) {
                closure.runExportable(e.toString(), null);
            }
        });
    }

    public void close() {
        _internal.close();
    }

    // Apparently gwt-exporter needs explicit type here
    // (maybe because URIHandler is only an interface?)

    public void addURIHandler(JSMemory value) {
        _internal.addURIHandler(value.internal());
    }

    public void addURIHandler(JSFileSystem value) {
        _internal.addURIHandler(value.internal());
    }

    public void addURIHandler(JSWebSocket value) {
        _internal.addURIHandler(value.internal());
    }
}
