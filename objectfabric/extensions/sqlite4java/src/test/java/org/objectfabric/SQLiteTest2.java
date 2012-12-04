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

import org.junit.Assert;
import org.junit.Test;

public class SQLiteTest2 extends TestsHelper {

    public static final String TEMP = "temp/db";

    @Test
    public void test() {
        File file = new File("temp/db");
        file.getParentFile().mkdirs();
        file.delete();

        for (int i = 0; i < 1; i++) {
            Workspace workspace = new JVMWorkspace();
            SQLite db = new SQLite(TEMP, false);
            workspace.addURIHandler(db);

            for (int j = 0; j < 1000; j++)
                workspace.open("any:///test").set("data" + j);

            workspace.close();
            db.close();

            workspace = new JVMWorkspace();
            db = new SQLite(TEMP, false);
            workspace.addURIHandler(db);
            Resource test = workspace.open("any:///test");
            Assert.assertEquals("data999", test.get());
            test.set("update");
            workspace.close();
            db.close();

            workspace = new JVMWorkspace();
            db = new SQLite(TEMP, false);
            workspace.addURIHandler(db);
            String value = (String) workspace.open("any:///test").get();
            Assert.assertEquals("update", value);
            workspace.close();
            db.close();
        }
    }
}
