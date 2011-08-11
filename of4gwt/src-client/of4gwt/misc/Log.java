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

import java.util.ArrayList;
import java.util.Date;

import of4gwt.misc.Debug;
import of4gwt.misc.Utils;

public abstract class Log {

    private static final ArrayList<Log> _logs = new ArrayList<Log>();

    static {
        _logs.add(new PlatformConsole());
    }

    public static void add(Log log) {
        _logs.add(log);
    }

    public static void remove(Log log) {
        _logs.remove(log);
    }

    public static String format(String message) {
        String header = new Date() + ", ";
        String process = Debug.ProcessName.length() > 0 ? Debug.ProcessName + " " : "";
        header += Utils.padRight(process + ", ", 25);
        return header + message;
    }

    public static void write(Throwable t) {
        write(PlatformAdapter.getStackAsString(t));
    }

    public static void write(String message) {
        String result = format(message);

        for (Log log : _logs)
            log.onWrite(result);
    }

    protected abstract void onWrite(String message);
}
