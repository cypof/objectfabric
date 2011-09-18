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

package com.objectfabric.security.shiro;

import com.objectfabric.TSet;
import com.objectfabric.Transaction;

public class Account extends AccountBase {

    /**
     * Declared abstract in the model just to have protected constructor. Accounts can be
     * created only using the service method to make sure passwords is hashed.
     */
    protected Account(String username) {
        super(username);
    }

    protected Account(Transaction trunk, String username, TSet<Role> roles) {
        super(trunk, username, roles);
    }
}
