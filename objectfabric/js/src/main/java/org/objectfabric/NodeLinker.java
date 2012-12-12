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

import java.util.Set;

import com.google.gwt.core.ext.LinkerContext;
import com.google.gwt.core.ext.TreeLogger;
import com.google.gwt.core.ext.UnableToCompleteException;
import com.google.gwt.core.ext.linker.AbstractLinker;
import com.google.gwt.core.ext.linker.ArtifactSet;
import com.google.gwt.core.ext.linker.CompilationResult;
import com.google.gwt.core.ext.linker.LinkerOrder;
import com.google.gwt.core.ext.linker.LinkerOrder.Order;
import com.google.gwt.dev.util.DefaultTextOutput;

@LinkerOrder(Order.POST)
public class NodeLinker extends AbstractLinker {

    @Override
    public String getDescription() {
        return "NodeLinker";
    }

    @Override
    public ArtifactSet link(TreeLogger logger, LinkerContext context, ArtifactSet artifacts) throws UnableToCompleteException {
        DefaultTextOutput out = new DefaultTextOutput(true);
        Set<CompilationResult> results = artifacts.find(CompilationResult.class);
        CompilationResult result = null;

        if (results.size() > 1)
            throw new UnableToCompleteException();

        if (!results.isEmpty()) {
            result = results.iterator().next();
            String[] js = result.getJavaScript();

            if (js.length != 1)
                throw new UnableToCompleteException();

            out.print(js[0]);
            out.newline();
        }

        out.print("var $stats = function() { };\n");
        out.print("var $sessionId = function() { };\n");
        out.print("var window = { };\n");
        out.print("var navigator = { };\n");
        out.print("navigator.userAgent = 'webkit';\n");
        out.print("var $doc = { };\n");
        out.newline();
        out.print("var $wnd = window;\n");
        out.print("$wnd.setTimeout = setTimeout;\n");
        out.print("$wnd.clearTimeout = clearTimeout;\n");
        out.print("$wnd.clearInterval = clearInterval;\n");
        out.newline();
        out.print("gwtOnLoad(null, '" + context.getModuleName() + "', null);\n");
        out.print("var fs = require('fs');\n");
        out.print("var WebSocket = require('ws');\n");
        out.print("$wnd.org.objectfabric.node = { };\n");
        out.print("for(var f in $wnd.org.objectfabric)\n");
        out.print("  exports[f] = $wnd.org.objectfabric[f];");

        ArtifactSet set = new ArtifactSet(artifacts);
        set.add(emitString(logger, out.toString(), context.getModuleName() + ".js"));
        return set;
    }
}
