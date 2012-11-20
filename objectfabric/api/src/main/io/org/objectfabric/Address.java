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

public final class Address {

    public static final int NULL_PORT = 0;

    public final String Scheme, Host;

    public final int Port;

    Address(String scheme, String host, int port) {
        Scheme = scheme;
        Host = host;
        Port = port;
    }

    @Override
    public int hashCode() {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean equals(Object obj) {
        throw new UnsupportedOperationException();
    }

    @Override
    public String toString() {
        String address = "";

        if (Scheme != null)
            address += Scheme + "://";

        if (Host != null)
            address += Host;

        if (Port > 0)
            address += ":" + Port;

        return address;
    }
}
