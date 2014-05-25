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

import org.objectfabric.Counter.CounterRead;
import org.objectfabric.Counter.CounterSharedVersion;
import org.objectfabric.Counter.CounterVersion;
import org.objectfabric.Resource.ResourceRead;
import org.objectfabric.Resource.ResourceVersion;
import org.objectfabric.ThreadAssert.SingleThreaded;

/**
 * Visits versions of objects so processing can be done on them. E.g. a Notifier uses a
 * visitor to react to commits by raising events on changed fields.<br>
 */
@SingleThreaded
abstract class Visitor extends Continuation {

    Visitor(List<Object> interruptionStack) {
        super(interruptionStack);
    }

    abstract void visit(ResourceRead version);

    abstract void visit(ResourceVersion version);

    /*
     * Indexed 32.
     */

    abstract void visit(TIndexed32Read version);

    /*
     * Indexed N.
     */

    abstract void visit(TIndexedNRead version);

    /*
     * TKeyed.
     */

    abstract void visit(TKeyedRead read);

    abstract void visit(TKeyedVersion version);

    abstract void visit(TKeyedSharedVersion shared);

    /*
     * TCounter.
     */

    abstract void visit(CounterRead read);

    abstract void visit(CounterVersion version);

    abstract void visit(CounterSharedVersion version);
}
