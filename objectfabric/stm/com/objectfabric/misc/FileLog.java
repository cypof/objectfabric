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

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;

public class FileLog extends Log {

    private final FileWriter _writer;

    public FileLog(String name) {
        try {
            File file = new File(name);
            _writer = new FileWriter(file, true);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    protected void onWrite(String message) {
        try {
            _writer.write(message + Utils.NEW_LINE);
            _writer.flush();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
