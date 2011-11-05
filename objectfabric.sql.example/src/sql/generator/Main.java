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

package sql.generator;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.Statement;

import sql.SQLServiceImpl;

import com.objectfabric.ImmutableClass;
import com.objectfabric.SQLAdapter;
import com.objectfabric.generator.FieldDef;
import com.objectfabric.generator.GeneratedClassDef;
import com.objectfabric.generator.Generator;
import com.objectfabric.generator.ObjectModelDef;
import com.objectfabric.generator.PackageDef;

/**
 * Generates transactional classes by reading the structure of tables in a database. This
 * code is meant to be simple enough to be adapted to each application, potentially
 * writing part of the model manually, changing column types etc.
 */
public class Main {

    public static void main(String[] args) throws Exception {
        /*
         * Reads part of the model from an XML file.
         */
        ObjectModelDef model = ObjectModelDef.fromXMLFile("src/sql/generator/ObjectModel.xml");

        PackageDef p = new PackageDef("sql.generated");
        model.Packages.add(p);

        /*
         * Then generates additional classes based on a relational table.
         */
        Connection connection = SQLServiceImpl.createConnection();
        DatabaseMetaData md = connection.getMetaData();
        ResultSet tables = md.getTables(null, null, null, new String[] { "TABLE" });

        while (tables.next()) {
            String table = tables.getString(3);
            GeneratedClassDef class_ = new GeneratedClassDef(getIdentifier(table));

            Statement st = connection.createStatement();
            ResultSet rs = st.executeQuery("SELECT * FROM " + table);
            ResultSetMetaData columns = rs.getMetaData();

            for (int i = 1; i <= columns.getColumnCount(); i++) {
                String column = columns.getColumnName(i);
                ImmutableClass type = SQLAdapter.getImmutableClass(columns.getColumnType(i));
                class_.Fields.add(new FieldDef(type, getIdentifier(column)));
            }

            p.Classes.add(class_);
        }

        Generator generator = new Generator(model);
        generator.run("./src");
    }

    private static String getIdentifier(String name) {
        String[] parts = name.split("[_]");
        String result = firstLetterUpOnly(parts[0]);

        for (int i = 1; i < parts.length; i++)
            result += firstLetterUpOnly(parts[i]);

        return result;
    }

    private static String firstLetterUpOnly(String value) {
        return value.substring(0, 1) + value.substring(1).toLowerCase();
    }
}
