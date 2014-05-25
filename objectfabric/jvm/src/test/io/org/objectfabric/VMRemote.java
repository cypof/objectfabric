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

class VMRemote extends Remote {

    protected VMRemote(Address address) {
        super(false, address);
    }

    @Override
    ConnectionAttempt createAttempt() {
        return new Attempt();
    }

    private final class Attempt implements ConnectionAttempt {

        @Override
        public void start() {
            if (ClientURIHandler.isEnabled())
                onConnection(new VMConnection(VMRemote.this));
        }

        @Override
        public void cancel() {
        }
    }

    @Override
    Headers headers() {
        return null;
    }
}