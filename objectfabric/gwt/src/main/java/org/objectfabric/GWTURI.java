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

import com.google.gwt.core.client.JavaScriptObject;

final class GWTURI {

    private final JavaScriptObject _parsed;

    GWTURI(String uri) {
        _parsed = parse(uri);
    }

    private static native JavaScriptObject parse(String uri) /*-{
		// parseUri 1.2.2
		// (c) Steven Levithan <stevenlevithan.com>
		// MIT License

		function parseUri(str) {
			var o = parseUri.options, m = o.parser[o.strictMode ? "strict"
					: "loose"].exec(str), uri = {}, i = 14;

			while (i--)
				uri[o.key[i]] = m[i] || "";

			uri[o.q.name] = {};
			uri[o.key[12]].replace(o.q.parser, function($0, $1, $2) {
				if ($1)
					uri[o.q.name][$1] = $2;
			});

			return uri;
		}

		parseUri.options = {
			strictMode : false,
			key : [ "source", "protocol", "authority", "userInfo", "user",
					"password", "host", "port", "relative", "path",
					"directory", "file", "query", "anchor" ],
			q : {
				name : "queryKey",
				parser : /(?:^|&)([^&=]*)=?([^&]*)/g
			},
			parser : {
				strict : /^(?:([^:\/?#]+):)?(?:\/\/((?:(([^:@]*)(?::([^:@]*))?)?@)?([^:\/?#]*)(?::(\d*))?))?((((?:[^?#\/]*\/)*)([^?#]*))(?:\?([^#]*))?(?:#(.*))?)/,
				loose : /^(?:(?![^:@]+:[^:@\/]*@)([^:\/?#.]+):)?(?:\/\/)?((?:(([^:@]*)(?::([^:@]*))?)?@)?([^:\/?#]*)(?::(\d*))?)(((\/(?:[^?#](?![^?#\/]*\.[^?#\/.]+(?:[?#]|$)))*\/?)?([^?#\/]*))(?:\?([^#]*))?(?:#(.*))?)/
			}
		};

		return parseUri(uri);
    }-*/;

    private String nullIfEmpty(String value) {
        return value != null && value.length() > 0 ? value : null;
    }

    public String getFragment() {
        return nullIfEmpty(getFragment(_parsed));
    }

    private static native String getFragment(JavaScriptObject parsed) /*-{
		return parsed.anchor;
    }-*/;

    //

    public String getHost() {
        return nullIfEmpty(getHost(_parsed));
    }

    private static native String getHost(JavaScriptObject parsed) /*-{
		return parsed.host;
    }-*/;

    //

    public String getPath() {
        return nullIfEmpty(getPath(_parsed));
    }

    private static native String getPath(JavaScriptObject parsed) /*-{
		return parsed.path;
    }-*/;

    //

    public int getPort() {
        String port  = getPort(_parsed);
        return port == null || port.length() == 0 ? Address.NULL_PORT : Integer.parseInt(port);
    }

    private static native String getPort(JavaScriptObject parsed) /*-{
		return parsed.port;
    }-*/;

    //

    public String getQuery() {
        return nullIfEmpty(getQuery(_parsed));
    }

    private static native String getQuery(JavaScriptObject parsed) /*-{
		return parsed.query;
    }-*/;

    //

    public String getScheme() {
        return nullIfEmpty(getScheme(_parsed));
    }

    private static native String getScheme(JavaScriptObject parsed) /*-{
		return parsed.protocol;
    }-*/;
}
