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

public class VMURIHandler extends ClientURIHandler {

    private static VMURIHandler _instance = new VMURIHandler();

    private VMURIHandler() {
    }

    public static VMURIHandler getInstance() {
        return _instance;
    }

    @Override
    public URI handle(Address address, String path) {
        String s = address.Scheme;

        if ("vm".equals(s)) {
            Remote remote = get(address);
            return remote.getURI(path);
        }

        return null;
    }

    @Override
    Remote createRemote(Address address) {
        return new VMRemote(address);
    }
}
