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

import org.junit.Test;

import com.objectfabric.misc.Debug;
import com.objectfabric.misc.PlatformFile;

public class JdbmTest {

    public static final String TEMP = "temp";

    public static final String FILE = TEMP + "/test.db";

    @Test
    public void test() {
        write();
        read();
        update();
        read2();
    }

    private void write() {
        PlatformFile.mkdir(TEMP);

        PlatformFile.deleteFileIfExists(FILE);
        PlatformFile.deleteFileIfExists(FILE + FileStore.LOG_EXTENSION);

        PlatformFile db = new PlatformFile(FILE);
        PlatformFile lg = new PlatformFile(FILE + FileStore.LOG_EXTENSION);

        RecordManager manager = new RecordManager(db, lg);

        long root = manager.getRoot(0);
        Debug.assertAlways(root == 0);

        long id = manager.insert(new byte[] { 0, 4, 2 });
        manager.setRoot(0, id);
        manager.close();
    }

    private void read() {
        PlatformFile db = new PlatformFile(FILE);
        PlatformFile lg = new PlatformFile(FILE + FileStore.LOG_EXTENSION);
        RecordManager manager = new RecordManager(db, lg);

        long root = manager.getRoot(0);
        byte[] read = manager.fetch(root);
        Debug.assertAlways(read[0] == 0 && read[1] == 4 && read[2] == 2);
        manager.close();
    }

    private void update() {
        PlatformFile db = new PlatformFile(FILE);
        PlatformFile lg = new PlatformFile(FILE + FileStore.LOG_EXTENSION);
        RecordManager manager = new RecordManager(db, lg);

        long root = manager.getRoot(0);
        manager.update(root, new byte[] { 0, 7, 2 });
        manager.close();
    }

    private void read2() {
        PlatformFile db = new PlatformFile(FILE);
        PlatformFile lg = new PlatformFile(FILE + FileStore.LOG_EXTENSION);
        RecordManager manager = new RecordManager(db, lg);

        long root = manager.getRoot(0);
        byte[] read = manager.fetch(root);
        Debug.assertAlways(read[0] == 0 && read[1] == 7 && read[2] == 2);
        manager.close();
    }

    public static void main(String[] args) {
        JdbmTest test = new JdbmTest();
        test.test();
    }
}
