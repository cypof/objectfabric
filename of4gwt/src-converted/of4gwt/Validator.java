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

/**
 * Validates operations from remote sites. This class is mainly used by servers to
 * validate transactions received from clients. If an illegal operation is attempted, this
 * class must throw an exception. ObjectFabric's current policy is then to close the
 * corresponding connection.
 * <nl>
 * Exceptions will be logged, so they should be descriptive. E.g. by using connection or
 * session information like user, socket descriptor, or other fields depending on the
 * particular transport used by your server.
 */
public interface Validator<C extends Connection> {

    /**
     * This method must throw an exception if the connection is not allowed to read this
     * object, with a message describing what the problem was.
     */
    void validateRead(C connection, TObject object);

    /**
     * This method must throw an exception if the connection is not allowed to write this
     * object, with a message describing what the problem was.
     */
    void validateWrite(C connection, TObject object);

    /**
     * This method must throw an exception if the connection is not allowed to invoke this
     * method on this object, with a message describing what the problem was.
     */
    void validateMethodCall(C connection, TObject object, String methodName);
}
