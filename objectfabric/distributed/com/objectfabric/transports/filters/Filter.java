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

package com.objectfabric.transports.filters;

import java.nio.ByteBuffer;

import com.objectfabric.Connection;
import com.objectfabric.misc.List;
import com.objectfabric.misc.Queue;

/**
 * Filters can be chained between a physical transport and a logical connection, e.g. to
 * encrypt data as it is sent and received. HTTP support is implemented as a filter.
 */
public interface Filter {

    // Initialization

    void init(List<FilterFactory> factories, int index, boolean clientSide);

    Filter getPrevious();

    void setPrevious(Filter value);

    Filter getNext();

    void setNext(Filter value);

    Connection getConnection();

    // Called by next filter on previous one

    void close();

    void requestWrite();

    // Called by previous filter on next one

    void onReadStarted();

    void onReadStopped(Exception e);

    void onWriteStarted();

    void onWriteStopped(Exception e);

    void read(ByteBuffer buffer);

    boolean write(ByteBuffer buffer, Queue<ByteBuffer> headers);
}
