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
import org.objectfabric.JS.Internal;
import org.timepedia.exporter.client.Export;
import org.timepedia.exporter.client.Exportable;

@Export
public class JSWorkspace implements Exportable {

    static class WorkspaceInternal extends GWTWorkspace {

        private WorkspaceInternal() {
            addURIHandler(new WebSocketURIHandler());
        }

        @Override
        Resource newResource(URI uri) {
            return new JSResource.ResourceInternal(this, uri);
        }
    }

    static final WorkspaceInternal Instance = new WorkspaceInternal();

    // @ExportStaticMethod
    public static JSWorkspace create() {
        return new JSWorkspace();
    }

    public void open(final String uri, final Closure closure) {
        Instance.openAsync(uri, new AsyncCallback<Resource>() {

            @Override
            public void onSuccess(Resource result) {
                closure.runExportable(((Internal) result).external());
            }

            @Override
            public void onFailure(Exception e) {
                Log.write("Could not get " + uri, e);
            }
        });
    }
}
