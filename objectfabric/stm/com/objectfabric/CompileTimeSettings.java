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
import com.objectfabric.misc.PlatformAdapter;

public final class CompileTimeSettings extends Privileged {

    public static final String OBJECTFABRIC_VERSION = "0.8";

    // Platforms

    public static final int PLATFORM_JAVA = 0;

    public static final int PLATFORM_DOT_NET = 1;

    public static final int PLATFORM_GWT = 2;

    /**
     * As an architectural decision for your application, or because it is not supported
     * on your platform (e.g. GWT), you can disallow threads from blocking. If a
     * synchronous operation would lead a thread to block, an exception will be raised. By
     * default, it is only true for GWT.
     */
    public static final boolean DISALLOW_THREAD_BLOCKING = !PlatformAdapter.THREADS_CAN_BLOCK;

    /*
     * Serialization.
     */

    public static final byte SERIALIZATION_VERSION_04 = 1;

    public static final byte SERIALIZATION_VERSION = SERIALIZATION_VERSION_04;

    /*
     * Flags describing a serialization stream.
     */

    public static final byte SERIALIZATION_NONE = 0;

    /**
     * If set, floats and doubles are serialized in a binary form (default between Java
     * and .NET processes), otherwise they are serialized as strings (mandatory if one end
     * runs on GWT as it does not allow to get bytes from floats and doubles).
     */
    public static final byte SERIALIZATION_BINARY_FLOAT_AND_DOUBLE = (byte) (1 << 7);

    // 2 flags left

    public static final int SERIALIZATION_FLAGS_OFFSET = 4;

    /**
     * This mask allows flags to be combined with the serialization version to save space
     * as this info is added to every object and version when writing to a store.
     */
    public static final byte SERIALIZATION_VERSION_MASK = (1 << SERIALIZATION_FLAGS_OFFSET) - 1;

    static {
        if (Debug.ENABLED) {
            Debug.assertion((SERIALIZATION_VERSION_MASK & SERIALIZATION_BINARY_FLOAT_AND_DOUBLE) == 0);
            Debug.assertion((SERIALIZATION_VERSION_MASK & SERIALIZATION_VERSION) == SERIALIZATION_VERSION);
        }
    }
}
