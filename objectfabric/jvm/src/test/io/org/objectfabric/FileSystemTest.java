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

import org.junit.Assert;
import org.junit.Test;

public class FileSystemTest extends TestsHelper {

    public static final String TEMP = "temp";

    @Test
    public void test() {
        PlatformGenerator.mkdir(TEMP);

        for (int i = 0; i < 1; i++) {
            PlatformGenerator.clearFolder(TEMP);

            SeparateCL test = new SeparateCL(TestWrite.class.getName());
            test.run();

            SeparateCL update = new SeparateCL(TestUpdate.class.getName());
            update.run();

            SeparateCL read = new SeparateCL(TestRead.class.getName());
            read.run();
        }
    }

    public static final class TestWrite {

        public static void main(String[] args) {
            JVMPlatform.loadClass();
            Workspace workspace = Platform.newTestWorkspace();
            setUID1(workspace);
            FileSystem system = (FileSystem) Platform.get().newTestStore(TEMP);
            workspace.addURIHandler(system);
            workspace.resolve("file:///test").set("data");
            workspace.close();

            if (Debug.ENABLED)
                system.close();
        }
    }

    public static final class TestUpdate {

        public static void main(String[] args) {
            JVMPlatform.loadClass();
            Workspace workspace = Platform.newTestWorkspace();
            setUID2(workspace);
            FileSystem system = (FileSystem) Platform.get().newTestStore(TEMP);
            workspace.addURIHandler(system);
            Resource test = workspace.resolve("file:///test");
            Assert.assertEquals("data", test.get());
            test.set("update");
            workspace.close();

            if (Debug.ENABLED)
                system.close();
        }
    }

    public static final class TestRead {

        public static void main(String[] args) {
            JVMPlatform.loadClass();
            Workspace workspace = Platform.newTestWorkspace();
            setUID3(workspace);
            FileSystem system = (FileSystem) Platform.get().newTestStore(TEMP);
            workspace.addURIHandler(system);
            String value = (String) workspace.resolve("file:///test").get();
            Assert.assertEquals("update", value);
            workspace.close();

            if (Debug.ENABLED)
                system.close();
        }
    }
}
