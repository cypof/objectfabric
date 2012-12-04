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

package examples.client;

import org.objectfabric.AsyncCallback;
import org.objectfabric.GWTWorkspace;
import org.objectfabric.Resource;
import org.objectfabric.WebSocketURIHandler;
import org.objectfabric.Workspace;

import com.google.gwt.core.client.EntryPoint;
import com.google.gwt.user.client.ui.HTML;
import com.google.gwt.user.client.ui.RootPanel;

public class Main implements EntryPoint {

    static final String URI = "ws://localhost:8888/helloworld";

    public void onModuleLoad() {
        final Workspace workspace = new GWTWorkspace();

        if (WebSocketURIHandler.isSupported())
            workspace.addURIHandler(new WebSocketURIHandler());
        // else // TODO
        // workspace.addURIHandler(new CometURIHandler());

        /*
         * Get resource from server. (Java examples server must be running)
         */
        workspace.openAsync(URI, new AsyncCallback<Resource>() {

            @Override
            public void onSuccess(Resource result) {
                RootPanel.get("text").add(new HTML((String) result.get()));
            }

            @Override
            public void onFailure(Exception e) {
            }
        });
    }
}
