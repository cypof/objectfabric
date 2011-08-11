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

package com.objectfabric;

import junit.framework.Assert;

import org.junit.Test;

import com.objectfabric.TObject.Version;
import com.objectfabric.Transaction.CommitStatus;
import com.objectfabric.VersionMap.Source;
import com.objectfabric.extensions.Logger;
import com.objectfabric.generated.SimpleClass;
import com.objectfabric.generated.SimpleClassAccessor;

public class PropagationTest extends TestsHelper {

    @Test
    public void run() {
        Transaction trunk = Site.getLocal().createTrunk();
        Transaction.setDefaultTrunk(trunk);

        Logger logger = new Logger();
        logger.log(trunk);
        final SimpleClass object = new SimpleClass();

        object.setText("A");
        object.setInt0(1);

        Snapshot snapshot = trunk.getSharedSnapshot();
        Interceptor.intercept(trunk);

        object.setInt0(1);

        Version version = object.getSharedVersion_objectfabric().createVersion();
        SimpleClassAccessor.setInt0(version, 2);
        Version[] versions = new Version[] { version };
        ConnectionBase.Version connection = new ConnectionBase.Version(null, ConnectionBase.FIELD_COUNT);
        TransactionManager.propagate(trunk, versions, new Source(connection, (byte) 0, true));
        Assert.assertEquals(1, object.getInt0());

        Transaction t = Transaction.start();
        Transaction.setCurrent(null);
        object.setInt0(3);

        snapshot = trunk.getSharedSnapshot();
        Interceptor.nack(trunk, CommitStatus.ABORT, null);
        Assert.assertEquals(2, object.getInt0());

        object.setInt0(3);
        snapshot = trunk.getSharedSnapshot();
        Interceptor.ack(trunk, snapshot.getLast().getInterception().getId(), true);
        Assert.assertEquals(3, object.getInt0());

        Transaction.setCurrent(t);
        object.setInt0(4);
        CommitStatus result = t.commit();
        Assert.assertEquals(CommitStatus.CONFLICT, result);
        Assert.assertEquals(3, object.getInt0());

        logger.stop();

        Transaction.setCurrent(null);
        Transaction.setDefaultTrunk(Site.getLocal().getTrunk());
    }

    public static void main(String[] args) throws Exception {
        PropagationTest test = new PropagationTest();
        test.before();
        test.run();
        test.after();
    }
}
