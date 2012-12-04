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

import org.junit.Test;

import com.almworks.sqlite4java.SQLiteConnection;
import com.almworks.sqlite4java.SQLiteStatement;

public class SQLiteTest extends TestsHelper {

    @Test
    public void test() throws Exception {
        File file = new File("temp/db");
        file.getParentFile().mkdirs();
        file.delete();

        SQLiteConnection t = new SQLiteConnection(file);
        t.open(true);
        t.exec(Shared.INIT);

        SQLiteConnection db = new SQLiteConnection(file);
        db.open(true);
        db.exec(Shared.INIT);
        db.exec("BEGIN IMMEDIATE");

        SQLiteStatement st = db.prepare("INSERT INTO blocks VALUES (?, ?, ?, ?);");
        st.bind(1, 42);
        st.bind(2, 43);
        st.bind(3, 44);
        st.bind(4, 44);
        st.step();
        st.dispose();

        db.exec("COMMIT");
        db.dispose();
    }
}
