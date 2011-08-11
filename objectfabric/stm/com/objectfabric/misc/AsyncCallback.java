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

package com.objectfabric.misc;

/**
 * Used when doing an operation in a synchronous way, like calling method on a remote
 * object.
 */
public interface AsyncCallback<T> {

    /**
     * The asynchronous call completed successfully, this is the result.
     */
    void onSuccess(T result);

    /**
     * The asynchronous call failed. Maybe an exception occurred during a method execution
     * or the transaction in which the method has been called has been aborted. If the
     * exception occurred on a remote site, the exception will be of type
     * com.objectfabric.misc.ReplicatedException.
     * 
     * @see com.objectfabric.misc.ReplicatedException
     */
    void onFailure(Throwable t);
}
