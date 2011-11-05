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

package users.client;

import of4gwt.transports.http.HTTPClient;

import com.google.gwt.core.client.EntryPoint;

public class Main implements EntryPoint {

    public void onModuleLoad() {

        HTTPClient client = new HTTPClient("http://localhost:8080");

//        client.connectAsync(new AsyncCallback<Object>() {
//
//            @SuppressWarnings("unchecked")
//            public void onSuccess(Object result) {
//                Service share = (Service) result;
//
//                /*
//                 * Make servers's trunk the default so we do not need to pass it as
//                 * argument every time we create and object or start a transaction.
//                 */
//                Transaction.setDefaultTrunk(share.getTrunk());
//            }
//
//            public void onFailure(Throwable caught) {
//            }
//        });
    }
}
