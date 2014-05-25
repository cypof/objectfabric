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

public abstract class Log {

    private static volatile Log _instance;

    protected abstract void log(String message);

    public static Log get() {
        return _instance;
    }

    public static void set(Log value) {
        _instance = value;
    }

    static void write(Throwable t) {
        write(null, t);
    }

    static void write(String message, Throwable t) {
        if (t != null) {
            if (message != null)
                message += " " + Platform.get().getStackAsString(t);
            else
                message = Platform.get().getStackAsString(t);
        }

        write(message);
    }

    static void write(String message) {
        message = Platform.get().formatLog(message);
        Platform.get().logDefault(message);

        Log instance = _instance;

        if (instance != null)
            instance.log(message);
    }

    static void userCodeException(Exception e) {
        write(Strings.USER_CODE_RAISED_AN_EXCEPTION, e);
    }
}