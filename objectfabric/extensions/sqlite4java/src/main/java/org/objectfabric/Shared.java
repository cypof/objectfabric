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

/**
 * Shared by all SQLite implementations.
 */
final class Shared {

    static final String BLOCKS = "blocks", CLOCKS = "clocks";

    static final String INIT = "" + //
            "CREATE TABLE IF NOT EXISTS " + BLOCKS + " (sha1 BLOB NOT NULL, time INTEGER NOT NULL, peer BLOB NOT NULL, block BLOB NOT NULL);" + //
            "CREATE UNIQUE INDEX IF NOT EXISTS " + BLOCKS + "_index ON " + BLOCKS + " (sha1, time, peer);" + //
            "CREATE TABLE IF NOT EXISTS " + CLOCKS + " (peer BLOB NOT NULL PRIMARY KEY, time INTEGER NOT NULL, object INTEGER NOT NULL)";

    static final String LIST_BLOCKS = "SELECT time, peer FROM " + BLOCKS + " WHERE sha1=?";

    static final String SELECT_BLOCK = "SELECT block FROM " + BLOCKS + " WHERE sha1=? AND time=? AND peer=?";

    static final String REPLACE_BLOCK = "REPLACE INTO " + BLOCKS + " VALUES (?, ?, ?, ?)";

    static final String DELETE_BLOCK = "DELETE FROM " + BLOCKS + " WHERE sha1=? AND time=? AND peer=?";

    //

    static final String SELECT_CLOCKS = "SELECT * FROM " + CLOCKS;

    static final String REPLACE_CLOCK = "REPLACE INTO " + CLOCKS + " VALUES (?, ?, ?)";
}
