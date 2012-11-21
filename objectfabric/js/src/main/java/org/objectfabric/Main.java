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

import org.timepedia.exporter.client.ExporterUtil;

import com.google.gwt.core.client.EntryPoint;

/**
 * Exports ObjectFabric as a JavaScript library. For simplicity the JavaScript version of
 * OF does not expose workspaces, instead creating one pre-configured global workspace
 * that lasts as long as the page.
 */
public class Main implements EntryPoint {

    static {
        JSPlatform.loadClass();
    }

    public void onModuleLoad() {
        ExporterUtil.exportAll();
        onLoad();
    }

    private native void onLoad() /*-{
		if ($wnd.onof)
			$wnd.onof($wnd.org.objectfabric.JSWorkspace.create());
    }-*/;
}
