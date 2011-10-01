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

package of4gwt;

import of4gwt.TObject.UserTObject;
import of4gwt.misc.Debug;
import of4gwt.misc.List;
import of4gwt.misc.OverrideAssert;
import of4gwt.misc.ThreadAssert.SingleThreaded;

@SingleThreaded
abstract class MultiplexerReader extends Reader {

    public MultiplexerReader(List<UserTObject> newTObjects) {
        super(newTObjects);

        if (Debug.COMMUNICATIONS_LOG) {
            Boolean previous = Helper.getInstance().getReaders().put(this, true);
            Debug.assertion(previous == null);
        }
    }

    public final void start() {
        if (Debug.THREADS)
            if (this instanceof DistributedReader)
                ((DistributedReader) this).getEndpoint().assertReadThread();

        OverrideAssert.add(this);
        onStarted();
        OverrideAssert.end(this);
    }

    protected void onStarted() {
        OverrideAssert.set(this);
    }

    protected abstract byte read();

    public final void stop(Exception e) {
        if (Debug.THREADS)
            if (this instanceof DistributedReader)
                ((DistributedReader) this).getEndpoint().assertReadThread();

        if (Debug.COMMUNICATIONS_LOG && e != null) {
            Boolean previous = Helper.getInstance().getReaders().put(this, false);
            Debug.assertion(previous);
        }

        OverrideAssert.add(this);
        onStopped(e);
        OverrideAssert.end(this);
    }

    protected void onStopped(Exception e) {
        OverrideAssert.set(this);
    }
}
