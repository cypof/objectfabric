/**
 * Copyright (c) ObjectFabric Inc. All rights reserved.
 *
 * This file is part of ObjectFabric (objectfabric.com).
 *
 * ObjectFabric is licensed under the Apache License, Version 2.0, the terms
 * of which may be found at http://www.apache.org/licenses/LICENSE-2.0.html.
 *
 * This file is provided AS IS with NO WARRANTY OF ANY KIND, INCLUDING THE
 * WARRANTY OF DESIGN, MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE.
 */

package com.objectfabric.transports;

import java.util.Collections;
import java.util.Set;

import com.objectfabric.Connection;
import com.objectfabric.Privileged;
import com.objectfabric.Site;
import com.objectfabric.TObject;
import com.objectfabric.Transaction;
import com.objectfabric.misc.Debug;
import com.objectfabric.misc.PlatformConcurrentMap;
import com.objectfabric.misc.PlatformThread;

public final class VMServer extends Privileged {

    private final PlatformConcurrentMap<VMConnection, VMConnection> _sessions = new PlatformConcurrentMap<VMConnection, VMConnection>();

    private final TObject _share;

    public VMServer(TObject share) {
        _share = share;
    }

    public VMConnection createConnection() {
        return new Session();
    }

    public void stop() {
        Transaction.run(new Runnable() {

            public void run() {
                for (Connection connection : getSessions())
                    connection.close();
            }
        });

        // Sessions must clean themselves

        while (getSessions().size() > 0)
            PlatformThread.sleep(1);
    }

    public Set<VMConnection> getSessions() {
        return Collections.unmodifiableSet(_sessions.keySet());
    }

    private final class Session extends VMConnection {

        public Session() {
            // Trunk will be replaced by client's once connected
            super(Site.getLocal().getTrunk(), Site.getLocal(), null);
        }

        @Override
        protected void onDialogEstablished() {
            super.onDialogEstablished();

            if (Debug.ENABLED)
                disableEqualsOrHashCheck();

            _sessions.put(this, this);

            if (Debug.ENABLED)
                enableEqualsOrHashCheck();

            send(_share);
        }

        @Override
        protected void onWriteStopped(Throwable t) {
            super.onWriteStopped(t);

            if (Debug.ENABLED)
                disableEqualsOrHashCheck();

            _sessions.remove(this);

            if (Debug.ENABLED)
                enableEqualsOrHashCheck();
        }
    }
}
