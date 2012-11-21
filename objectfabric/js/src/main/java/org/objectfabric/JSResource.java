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

import org.timepedia.exporter.client.Export;
import org.timepedia.exporter.client.Exportable;

@Export
public class JSResource implements Exportable {

    static final class ResourceInternal extends Resource implements Internal {

        JSResource _js;

        ResourceInternal(Workspace workspace, URI uri) {
            super(workspace, uri);
        }

        @Override
        public Exportable getOrCreateJS() {
            if (_js == null)
                _js = new JSResource(this);

            return _js;
        }
    }

    final ResourceInternal _internal;

    JSResource(ResourceInternal internal) {
        _internal = internal;
    }

    public Resource internal() {
        return _internal;
    }

    public Permission getPermission() {
        return _internal.permission();
    }

    public void get(final Closure closure) {
        _internal.getAsync(new AsyncCallback<Object>() {

            @Override
            public void onSuccess(Object result) {
                if (result instanceof Internal)
                    closure.runExportable(((Internal) result).getOrCreateJS());
                else
                    closure.runImmutable(result);
            }

            @Override
            public void onFailure(Exception e) {
                Log.write("Could not get " + _internal, e);
            }
        });
    }
}
