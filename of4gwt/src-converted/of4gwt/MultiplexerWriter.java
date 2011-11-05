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

import of4gwt.misc.Debug;
import of4gwt.misc.OverrideAssert;
import of4gwt.misc.Queue;
import of4gwt.misc.ThreadAssert.SingleThreaded;

@SingleThreaded
abstract class MultiplexerWriter extends Writer {

    public interface Command extends Runnable {

        MultiplexerWriter getWriter();
    }

    private final Queue<Runnable> _runnables = new Queue<Runnable>();

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

    public final void enqueue(Runnable runnable) {
        if (Debug.THREADS)
            if (this instanceof DistributedWriter)
                ((DistributedWriter) this).getEndpoint().assertWriteThread();

        _runnables.add(runnable);
    }

    public final boolean hasRunnables() {
        return _runnables.size() > 0;
    }

    //

    protected void write() {
        Runnable runnable = null;

        if (interrupted())
            runnable = (Runnable) resume();

        for (;;) {
            if (runnable == null)
                runnable = _runnables.poll();

            if (runnable == null)
                break;

            runnable.run();

            if (interrupted()) {
                interrupt(runnable);
                return;
            }

            runnable = null;
        }
    }

    public final void stop(Exception e) {
        if (Debug.THREADS)
            if (this instanceof DistributedWriter)
                ((DistributedWriter) this).getEndpoint().assertWriteThread();

        if (Debug.COMMUNICATIONS_LOG && e != null) {
            Boolean previous = Helper.getInstance().getWriters().put(this, false);
            Debug.assertion(previous);
        }

        OverrideAssert.add(this);
        onStopped(e);
        OverrideAssert.end(this);
    }

    /**
     * @param e
     */
    protected void onStopped(Exception e) {
        OverrideAssert.set(this);
    }
}
