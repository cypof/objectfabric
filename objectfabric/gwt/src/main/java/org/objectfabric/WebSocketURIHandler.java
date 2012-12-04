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

public class WebSocketURIHandler extends ClientURIHandler {

    public static native boolean isSupported() /*-{
		if ($wnd.WebSocket) {
			return true;
		} else {
			return false;
		}
    }-*/;

    @Override
    public URI handle(Address address, String path) {
        if (address.Host != null && address.Host.length() > 0) {
            String s = address.Scheme;

            if (Remote.WS.equals(s) || Remote.WSS.equals(s)) {
                Remote remote = get(address);
                return remote.getURI(path);
            }
        }

        return null;
    }

    @Override
    Remote createRemote(Address address) {
        return new Remote(false, address) {

            @Override
            ConnectionAttempt createAttempt() {
                final WebSocketConnection connection = new WebSocketConnection(this);

                return new ConnectionAttempt() {

                    @Override
                    public void start() {
                        connection.start();
                    }

                    @Override
                    public void cancel() {
                        connection.requestClose(null);
                    }
                };
            }

            @Override
            Headers headers() {
                return null;
            }
        };
    }
}
