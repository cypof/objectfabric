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

import of4gwt.Helper;
import of4gwt.Writer;
import of4gwt.misc.Debug;
import of4gwt.misc.OverrideAssert;
import of4gwt.misc.ThreadAssert.SingleThreaded;

@SingleThreaded
abstract class MultiplexerWriter extends Writer {

    public MultiplexerWriter() {
        super(false);

        if (Debug.COMMUNICATIONS_LOG) {
            Boolean previous = Helper.getInstance().getWriters().put(this, true);
            Debug.assertion(previous == null);
        }
    }

    public final void start() {
        if (Debug.THREADS)
            if (this instanceof DistributedWriter)
                ((DistributedWriter) this).getEndpoint().assertWriteThread();

        OverrideAssert.add(this);
        onStarted();
        OverrideAssert.end(this);
    }

    protected void onStarted() {
        OverrideAssert.set(this);
    }

    protected abstract void write();

    public final void stop(Throwable t) {
        if (Debug.THREADS)
            if (this instanceof DistributedWriter)
                ((DistributedWriter) this).getEndpoint().assertWriteThread();

        if (Debug.COMMUNICATIONS_LOG && t != null) {
            Boolean previous = Helper.getInstance().getWriters().put(this, false);
            Debug.assertion(previous);
        }

        OverrideAssert.add(this);
        onStopped(t);
        OverrideAssert.end(this);
    }

    protected void onStopped(Throwable t) {
        OverrideAssert.set(this);
    }
}
