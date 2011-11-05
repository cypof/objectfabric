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
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;

import sql.generated.PagedResult;
import sql.generated.SQL;

/**
 * Overrides the generated SQL object to implement methods that clients call.
 */
public class SQLImpl extends SQL {

    // TODO
    // private final UniqueIdentityMap _map = new UniqueIdentityMap();

    @Override
    protected PagedResult queryImplementation(String name) {
        Connection connection = null;
        PreparedStatement statement = null;
        ResultSet result;

        try {
            connection = SQLServiceImpl.createConnection();
            statement = connection.prepareStatement("SELECT * FROM SAMPLE_TABLE WHERE STR_COL == ?");
            statement.setString(0, name);
            result = statement.executeQuery();
        } catch (Exception ex) {
            throw new RuntimeException(ex);
        } finally {
            if (statement != null) {
                try {
                    statement.close();
                } catch (SQLException e) {
                    // Ignore
                }
            }

            if (connection != null) {
                try {
                    connection.close();
                } catch (SQLException e) {
                    // Ignore
                }
            }
        }

        return new PagedResultImpl(result);
    }

//    // use for SQL commands CREATE, DROP, INSERT and UPDATE
//    public synchronized void update(String expression) throws SQLException {
//
//        Statement st = null;
//
//        st = _connection.createStatement(); // statements
//
//        int i = st.executeUpdate(expression); // run the query
//
//        if (i == -1) {
//            System.out.println("db error : " + expression);
//        }
//
//        st.close();
//    } // void update()
//
//    public static void dump(ResultSet rs) throws SQLException {
//
//        // the order of the rows in a cursor
//        // are implementation dependent unless you use the SQL ORDER statement
//        ResultSetMetaData meta = rs.getMetaData();
//        int colmax = meta.getColumnCount();
//        int i;
//        Object o = null;
//
//        // the result set is a cursor into the data. You can only
//        // point to one row at a time
//        // assume we are pointing to BEFORE the first row
//        // rs.next() points to next row and returns true
//        // or false if there is no next row, which breaks the loop
//        for (; rs.next();) {
//            for (i = 0; i < colmax; ++i) {
//                o = rs.getObject(i + 1); // Is SQL the first column is indexed
//
//                // with 1 not 0
//                System.out.print(o.toString() + " ");
//            }
//
//            System.out.println(" ");
//        }
//    } // void dump( ResultSet rs )
//
//    public static void main(String[] args) {
//
//        HSQLStore db = null;
//
//        try {
//            db = new HSQLStore("db_file");
//        } catch (Exception ex1) {
//            ex1.printStackTrace(); // could not start db
//
//            return; // bye bye
//        }
//
//        try {
//
//            // make an empty table
//            //
//            // by declaring the id column IDENTITY, the db will automatically
//            // generate unique values for new rows- useful for row keys
//            db.update("CREATE TABLE sample_table ( id INTEGER IDENTITY, str_col VARCHAR(256), num_col INTEGER)");
//        } catch (SQLException ex2) {
//
//            // ignore
//            // ex2.printStackTrace(); // second time we run program
//            // should throw execption since table
//            // already there
//            //
//            // this will have no effect on the db
//        }
//
//        try {
//
//            // add some rows - will create duplicates if run more then once
//            // the id column is automatically generated
//            db.update("INSERT INTO sample_table(str_col,num_col) VALUES('Ford', 100)");
//            db.update("INSERT INTO sample_table(str_col,num_col) VALUES('Toyota', 200)");
//            db.update("INSERT INTO sample_table(str_col,num_col) VALUES('Honda', 300)");
//            db.update("INSERT INTO sample_table(str_col,num_col) VALUES('GM', 400)");
//
//            // do a query
//            db.query("SELECT * FROM sample_table WHERE num_col < 250");
//
//            // at end of program
//            db.shutdown();
//        } catch (SQLException ex3) {
//            ex3.printStackTrace();
//        }
//    }
}
