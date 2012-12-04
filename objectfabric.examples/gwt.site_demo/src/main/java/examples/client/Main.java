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
import org.objectfabric.IndexListener;
import org.objectfabric.Resource;
import org.objectfabric.TArrayLong;
import org.objectfabric.WebSocketURIHandler;
import org.objectfabric.Workspace;

import com.google.gwt.core.client.EntryPoint;
import com.google.gwt.dom.client.Document;
import com.google.gwt.dom.client.Element;
import com.google.gwt.i18n.client.NumberFormat;

/**
 * Code for the project's home page.
 */
@SuppressWarnings("unchecked")
public class Main implements EntryPoint {

    public void onModuleLoad() {
        // Like opening a browser
        Workspace w = new GWTWorkspace();

        // Enables WebSocket connections
        w.addURIHandler(new WebSocketURIHandler());

        // Get array of long and stay connected through WebSocket
        String uri = "ws://test.objectfabric.org/array";

        w.openAsync(uri, new AsyncCallback<Resource>() {

            @Override
            public void onSuccess(Resource result) {
                final TArrayLong array = (TArrayLong) result.get();
                final NumberFormat format = NumberFormat.getDecimalFormat();

                // Called when an array element is set
                array.addListener(new IndexListener() {

                    @Override
                    public void onSet(int i) {
                        Element div = Document.get().getElementById("div" + i);
                        div.setInnerHTML(format.format(array.get(i)));
                    }
                });
            }

            @Override
            public void onFailure(Exception e) {
            }
        });
    }
}
