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

package finance.client;

import of4gwt.TSet;
import of4gwt.smartgwt.OFDataSource;

import com.smartgwt.client.types.FetchMode;
import com.smartgwt.client.widgets.IButton;
import com.smartgwt.client.widgets.events.ClickEvent;
import com.smartgwt.client.widgets.events.ClickHandler;
import com.smartgwt.client.widgets.grid.ListGrid;
import com.smartgwt.client.widgets.layout.VLayout;

import finance.client.generated.User;

public final class FinanceGrid extends VLayout {

    public FinanceGrid() {
        TSet<User> users = new TSet<User>();
        users.add(new User());
        users.add(new User());

        OFDataSource<User> ds = new OFDataSource<User>(users) {

            @Override
            protected User createObject() {
                return new User();
            }

            @Override
            protected void setField(User object, int index, Object value) {
                if (index != User.ORDERS_INDEX)
                    super.setField(object, index, value);
            }
        };

        final ListGrid userList = new ListGrid();
        userList.setWidth(800);
        userList.setHeight(300);
        userList.setDataSource(ds);
        userList.setCanEdit(true);
        userList.setCanRemoveRecords(true);
        // userList.setLeaveScrollbarGap(false);
        userList.setDataFetchMode(FetchMode.LOCAL);
        userList.setAutoFetchData(true);

        IButton addButton = new IButton("Create User");
        addButton.setWidth(110);
        addButton.addClickHandler(new ClickHandler() {

            public void onClick(ClickEvent event) {
                userList.startEditingNew();
            }
        });

        addMember(userList);
        addMember(addButton);
    }
}
