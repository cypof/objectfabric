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

import com.objectfabric.TGeneratedFields;
import com.objectfabric.TKeyed;
import com.objectfabric.TList;
import com.objectfabric.TMap;
import com.objectfabric.TObject;

/**
 * Platform classes allow other implementations to be used for ports like GWT and .NET.
 * The .NET specific implementations make it possible to remove Java components like
 * Reflection and Security from the ObjectFabric dll.
 */
public final class PlatformClass {

    public static String getSimpleName(Class c) {
        return c.getSimpleName();
    }

    public static String getName(Class c) {
        return c.getName();
    }

    public static Class forName(String name) {
        if (name.equals("byte[]"))
            return byte[].class;

        try {
            return Class.forName(name);
        } catch (ClassNotFoundException ex) {
            return null;
        }
    }

    public static boolean isInstance(Class c, Object o) {
        return c.isInstance(o);
    }

    @SuppressWarnings("unchecked")
    public static boolean isAssignableFrom(Class c, Class o) {
        return c.isAssignableFrom(o);
    }

    @SuppressWarnings("unchecked")
    public static boolean isCollection(Class c) {
        if (c != null)
            for (Class coll : new Class[] { TList.class, TKeyed.class })
                if (coll.isAssignableFrom(c))
                    return true;

        return false;
    }

    public static Class getClass(Object o) {
        return o.getClass();
    }

    public static String getClassName(Object o) {
        return o.getClass().getName();
    }

    public static Class getSuperclass(Class c) {
        return c.getSuperclass();
    }

    public static Class getEnclosingClass(Class c) {
        return c.getEnclosingClass();
    }

    public static Class getObjectClass() {
        return Object.class;
    }

    public static Class getStringClass() {
        return String.class;
    }

    public static Class getVoidClass() {
        return void.class;
    }

    public static boolean isJavaEnum(Class c) {
        return c.isEnum();
    }

    public static boolean isTObject(Class c) {
        return TObject.class.isAssignableFrom(c);
    }

    public static boolean isTMap(Class c) {
        return TMap.class.isAssignableFrom(c);
    }

    public static Class getTObjectClass() {
        return TObject.class;
    }

    public static Class getTGeneratedFieldsClass() {
        return TGeneratedFields.class;
    }
}
