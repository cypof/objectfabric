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

package sql;

import java.sql.Connection;
import java.sql.DriverManager;

/**
 * The server side implementation of the RPC service.
 */
@SuppressWarnings("serial")
public class SQLServiceImpl {

    private static final String FILE = "db/db";

    public static Connection createConnection() throws Exception {
        /*
         * Load the HSQL Database Engine JDBC driver hsqldb.jar should be in the class
         * path or made part of the current jar.
         */
        Class.forName("org.hsqldb.jdbc.JDBCDriver");

        /*
         * Load the DB files and start the database if it is not already running.
         */
        return DriverManager.getConnection("jdbc:hsqldb:" + FILE, "SA", "");
    }
}
