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

public final class PlatformClass {

    public static String getSimpleName(Class c) {
        String name = c.getName();
        String[] parts = name.split("[.]");
        return parts.length <= 1 ? name : parts[parts.length - 1];
    }

    public static String getName(Class c) {
        return c.getName();
    }

    /**
     * @param c
     * @param o
     */
    public static boolean isInstance(Class c, Object o) {
        throw new UnsupportedOperationException();
    }

    public static Class getClass(Object o) {
        return o.getClass();
    }

    public static String getClassName(Object o) {
        return o.getClass().getName();
    }

    /**
     * @param c
     */
    public static Class getEnclosingClass(Class c) {
        throw new UnsupportedOperationException();
    }

    public static Class getObjectClass() {
        return Object.class;
    }

    public static Class getVoidClass() {
        return void.class;
    }
}
