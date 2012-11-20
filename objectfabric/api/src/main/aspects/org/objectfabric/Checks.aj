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

import org.objectfabric.ThreadAssert.AllowSharedRead;
import org.objectfabric.ThreadAssert.AllowSharedWrite;
import org.objectfabric.ThreadAssert.SingleThreaded;
import org.objectfabric.ThreadAssert.SingleThreadedThenShared;

/**
 * Only for debug, not used by regular build.
 */
public aspect Checks {

	pointcut warningEnabled(): 
        staticinitialization(TObject);

    after(): warningEnabled() {
        System.out.println("!!! AspectJ Checks Enabled !!!");
    }

    // Threads

    pointcut add():
        execution(((@SingleThreaded *) || (@SingleThreadedThenShared *)).new(..));

    before(): add() {
        if (Debug.THREADS)
            ThreadAssert.addPrivateIfNot(thisJoinPoint.getTarget());
    }

    // pointcut remove():
    // execution(void (@SingleThreaded *).dispose());
    //
    // before(): remove() {
    // if (Debug.THREADS)
    // ThreadAssert.removePrivate(thisJoinPoint.getTarget());
    // }

    //

    // pointcut check():
    // execution(!static * *.*(..)) &&
    // @within(SingleThreaded) &&
    // !withincode(String *.toString());

    pointcut checkPrivateRead():
        get(!@AllowSharedRead !static !volatile * (@SingleThreaded *).*);

    before(): checkPrivateRead() {
        if (Debug.THREADS)
            ThreadAssert.assertPrivate(thisJoinPoint.getTarget());
    }

    pointcut checkPrivateOrSharedRead():
        get(!@AllowSharedRead !static !volatile * (@SingleThreadedThenShared *).*);

    before(): checkPrivateOrSharedRead() {
        if (Debug.THREADS)
            ThreadAssert.assertPrivateOrShared(thisJoinPoint.getTarget());
    }

    pointcut checkWrite():
        set(!@AllowSharedWrite !static !volatile * ((@SingleThreaded *) || (@SingleThreadedThenShared *)).*);

    before(): checkWrite() {
        if (Debug.THREADS)
            ThreadAssert.assertPrivate(thisJoinPoint.getTarget());
    }

    pointcut checkMethod():
        call(@SingleThreaded * *.*(..));

    before(): checkMethod() {
        if (Debug.THREADS)
            ThreadAssert.assertPrivateOrShared(thisJoinPoint.getTarget());
    }
}
