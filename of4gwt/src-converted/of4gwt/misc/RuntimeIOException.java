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

/**
 * The .NET version has a problem with exception mapping, and persistence would force the
 * whole Visitor and serialization mechanisms to have IOException everywhere, so catch IO
 * problems only at top level.
 */
public class RuntimeIOException extends RuntimeException {

    public RuntimeIOException() {
    }

    public RuntimeIOException(Exception e) {
        super(e);
    }

    public RuntimeIOException(String message) {
        super(message);
    }

    public static final class StoreCloseException extends RuntimeIOException {
    }
}