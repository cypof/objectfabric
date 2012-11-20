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

import cli.System.Type;

final class PlatformGenerator {

    // Class

    static Type forName(String name) {
        if (name.equals("byte[]"))
            return Platform.get().byteArrayClass();

        return Type.GetType(name);
    }

    @SuppressWarnings("unused")
    static boolean isJavaEnum(Type _) {
        return false;
    }

    static boolean isInterface(Type c) {
        return c.get_IsInterface();
    }

    static boolean isAssignableFrom(Type c, Type o) {
        return c.IsAssignableFrom(o);
    }

    static boolean isTObject(Type c) {
        return isAssignableFrom(Platform.get().tObjectClass(), c);
    }

    static boolean isTMap(Type c) {
        return isAssignableFrom(Platform.get().tMapClass(), c);
    }

    static boolean isCollection(Type c) {
        if (isAssignableFrom(Platform.get().tKeyedClass(), c))
            return true;

        return false;
    }

    // File

    static void writeFile(String path, char[] text, int length) {
        Platform.get().writeFile(path, text, length);
    }

    static void clearFolder(String folder) {
        Platform.get().clearFolder(folder);
    }

    static boolean fileExists(String file) {
        return Platform.get().fileExists(file);
    }

    static void mkdir(String folder) {
        Platform.get().mkdir(folder);
    }
}
