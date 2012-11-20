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

import java.io.File;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Properties;

final class PlatformGenerator {

    private static final Platform _platform = new JVMPlatform();

    private PlatformGenerator() {
    }

    static Platform getPlatform() {
        return _platform;
    }

    // Class

    static Class forName(String name) {
        if (name.equals("byte[]"))
            return byte[].class;

        try {
            return Class.forName(name);
        } catch (ClassNotFoundException ex) {
            return null;
        }
    }

    // TODO unify with .NET enums
    static boolean isJavaEnum(Class c) {
        return c.isEnum();
    }

    static boolean isInterface(Class c) {
        return c.isInterface();
    }

    @SuppressWarnings("unchecked")
    static boolean isAssignableFrom(Class c, Class o) {
        return c.isAssignableFrom(o);
    }

    static boolean isTObject(Class c) {
        return isAssignableFrom(TObject.class, c);
    }

    static boolean isTMap(Class c) {
        return isAssignableFrom(TMap.class, c);
    }

    @SuppressWarnings("unchecked")
    static boolean isCollection(Class c) {
        if (c != null)
            if (isAssignableFrom(TKeyed.class, c))
                return true;

        return false;
    }

    // File

    static void writeFile(String path, char[] text, int length) {
        File file = new File(path);
        FileWriter writer = null;

        try {
            writer = new FileWriter(file);
            writer.write(text, 0, length);
        } catch (IOException ex) {
            throw new RuntimeException(ex);
        } finally {
            try {
                if (writer != null)
                    writer.close();
            } catch (IOException ex) {
                // Ignore
            }
        }
    }

    static void clearFolder(String folder) {
        clearFolder(new File(folder));
    }

    static void clearFolder(File folder) {
        if (folder.exists()) {
            for (File child : folder.listFiles()) {
                if (child.isDirectory())
                    clearFolder(child);

                if (!child.delete())
                    throw new RuntimeException();
            }
        }
    }

    static boolean fileExists(String file) {
        return new File(file).exists();
    }

    static void mkdir(String folder) {
        new File(folder).mkdir();
    }

    static String readCopyright() {
        Properties jautodoc = new Properties();

        try {
            jautodoc.load(new FileInputStream("../api/.settings/net.sf.jautodoc.prefs"));
        } catch (Exception ex) {
            throw new RuntimeException(ex);
        }

        return jautodoc.getProperty("header_text");
    }
}
