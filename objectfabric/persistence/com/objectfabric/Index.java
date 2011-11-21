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

/**
 * ObjectFabric attempts to answer the problem of querying objects by separating storage
 * and indexing/querying. A store can be used by itself, with no querying capability, and
 * keep objects e.g. in a highly efficient binary format. As an application is developed,
 * indexes can then be created to retrieve objects using various algorithms, e.g. full
 * text search, SQL on ad-hoc tables, graph databases, hash maps or any other method.
 * <nl>
 * Indexes can be built, destroyed and rebuilt independently of stores, so they do not
 * need to be durable, and can contain only data involved in queries. E.g. if startup time
 * is not an issue, an SQL-based index could be based on an in-memory database rebuilt at
 * each startup.
 * <nl>
 * Warning: This is a work in progress.
 */
public class Index extends Schedulable {
    // TODO put shared stuff between full text and SQL indexes
}
