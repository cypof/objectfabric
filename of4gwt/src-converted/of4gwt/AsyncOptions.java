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

import of4gwt.misc.Executor;

import of4gwt.Transaction.Granularity;
import of4gwt.misc.PlatformThreadPool;

/**
 * Modifies how a callback or listener is called to notify the user of an event. An
 * instance of this class is set by default on AsyncManager.getDefaultOptions, and can be
 * replaced to change default values. An instance an also be specified when registering a
 * listener or callback.
 */
public class AsyncOptions {

    /**
     * Callbacks and listeners are executed by default on ObjectFabric's thread pool. Overriding
     * this method allows to change this executor. Graphical applications for example can
     * require callbacks and listeners to be executed by the UI thread.
     */
    public Executor getExecutor() {
        return PlatformThreadPool.getInstance();
    }

    /**
     * Forces a Granularity for listeners, i.e. is it OK to skip intermediary changes when
     * an object changes several times, or does the listener have to be invoked at each
     * update? @see Transaction.Granularity. Default is null, which lets the branch
     * specify its granularity.
     */
    public Granularity getForcedGranularity() {
        return null;
    }

    /**
     * Should the addListener method return only when the ChangeNotifier is done
     * registering the listener. Default is true. If set to false, and the object is then
     * immediately updated, the first updates might be missed.
     */
    public boolean waitForListenersRegistration() {
        return true;
    }

    /**
     * Return true if changes created in the current process should raise events. If
     * false, listeners will only be invoked for event created on remote processes.
     */
    public boolean notifyLocalEvents() {
        return true;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == this)
            return true;

        if (obj instanceof AsyncOptions) {
            AsyncOptions other = (AsyncOptions) obj;

            if (getExecutor() != other.getExecutor())
                return false;

            if (getForcedGranularity() != other.getForcedGranularity())
                return false;

            if (waitForListenersRegistration() != other.waitForListenersRegistration())
                return false;

            return true;
        }

        return false;
    }

    @Override
    public int hashCode() {
        // Not hashCode as its possible that getExecutor() == this
        int a = System.identityHashCode(getExecutor());
        int b = getForcedGranularity() != null ? getForcedGranularity().hashCode() : 0;
        int c = waitForListenersRegistration() ? 1 : 0;
        return a ^ b ^ c;
    }
}
