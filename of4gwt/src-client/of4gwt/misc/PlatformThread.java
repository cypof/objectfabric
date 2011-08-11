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

package of4gwt.misc;

import of4gwt.Strings;

public class PlatformThread {

    public static boolean holdsLock(Object obj) {
        throw new UnsupportedOperationException();
    }

    public static void sleep(long millis) {
        throw new IllegalStateException(Strings.THREAD_BLOCKING_DISALLOWED);
    }
}
