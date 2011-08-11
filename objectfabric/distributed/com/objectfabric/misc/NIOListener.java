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

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.channels.ServerSocketChannel;

import com.objectfabric.Strings;

public abstract class NIOListener {

    private NIOTask.Listen _task;

    /**
     * Create an instance of your own NIOConnection class, this method must not block as
     * it is called on a thread dedicated to a core.
     */
    protected abstract NIOConnection createConnection();

    public final void start(int port) throws IOException {
        start(null, port);
    }

    public final void start(InetAddress host, int port) throws IOException {
        if (_task != null)
            throw new RuntimeException(Strings.ALREADY_STARTED);

        ServerSocketChannel channel = null;
        boolean close = true;

        try {
            InetSocketAddress address = new InetSocketAddress(host, port);
            channel = ServerSocketChannel.open();
            channel.configureBlocking(false);
            channel.socket().bind(address);

            _task = new NIOTask.Listen(this, channel);
            NIOManager.getInstance().execute(_task);

            close = false;
        } finally {
            if (close && channel != null) {
                try {
                    channel.close();
                } catch (IOException i) {
                    // Ignore
                }

                _task = null;
            }
        }
    }

    public final void stop() {
        if (_task == null)
            throw new RuntimeException(Strings.NOT_STARTED);

        _task.stop(null);
        _task = null;
    }
}
