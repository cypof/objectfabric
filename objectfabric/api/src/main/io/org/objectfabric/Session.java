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

/**
 * Server-side request notifications and permission settings.
 */
public interface Session {

    /**
     * Request notification, and allows setting a permission on the requested resource.
     * The callback must be invoked for each request with a permission decision. This
     * method is invoked on one of the transport threads and ideally should not block. If
     * a database call or other long running process is required, it should be done
     * asynchronously or in another thread, invoking the callback later when the decision
     * is taken.
     */
    void onRequest(URI uri, PermissionCallback callback);

    /**
     * Optional, return null if no headers are needed.
     */
    Headers sendResponseHeaders();
}
