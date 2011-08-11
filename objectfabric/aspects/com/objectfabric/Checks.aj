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

import com.objectfabric.misc.Debug;
import com.objectfabric.misc.ThreadContext;
import com.objectfabric.misc.ThreadContext.AllowSharedRead;
import com.objectfabric.misc.ThreadContext.AllowSharedWrite;
import com.objectfabric.misc.ThreadContext.SingleThreaded;
import com.objectfabric.misc.ThreadContext.SingleThreadedOrShared;

public aspect Checks {

    pointcut warningEnabled(): 
        staticinitialization(TObject);

    after(): warningEnabled() {
        System.out.println("!!! AspectJ Checks Enabled !!!");
    }

    // Threads

    pointcut add():
        execution(((@SingleThreaded *) || (@SingleThreadedOrShared *)).new(..));

    before(): add() {
        if (Debug.THREADS)
            ThreadContext.addPrivateIfNot(thisJoinPoint.getTarget());
    }

    // pointcut remove():
    // execution(void (@SingleThreaded *).dispose());
    //
    // before(): remove() {
    // if (Debug.THREADS)
    // ThreadContext.removePrivate(thisJoinPoint.getTarget());
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
            ThreadContext.assertPrivate(thisJoinPoint.getTarget());
    }

    pointcut checkPrivateOrSharedRead():
        get(!@AllowSharedRead !static !volatile * (@SingleThreadedOrShared *).*);

    before(): checkPrivateOrSharedRead() {
        if (Debug.THREADS)
            ThreadContext.assertPrivateOrShared(thisJoinPoint.getTarget());
    }

    pointcut checkWrite():
        set(!@AllowSharedWrite !static !volatile * ((@SingleThreaded *) || (@SingleThreadedOrShared *)).*);

    before(): checkWrite() {
        if (Debug.THREADS)
            ThreadContext.assertPrivate(thisJoinPoint.getTarget());
    }

    pointcut checkMethod():
        call(@SingleThreaded * *.*(..));

    before(): checkMethod() {
        if (Debug.THREADS)
            ThreadContext.assertPrivateOrShared(thisJoinPoint.getTarget());
    }
}
