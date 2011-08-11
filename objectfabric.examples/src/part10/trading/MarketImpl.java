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

package part10.trading;

import part10.trading.generated.Instrument;
import part10.trading.generated.Market;
import part10.trading.generated.User;

import com.objectfabric.TList;
import com.objectfabric.TSet;

public class MarketImpl extends Market {

    public MarketImpl() {
        super(new TSet<User>());
    }

    @Override
    protected TList<Instrument> getInstrumentsImplementation(String query) {
        TList<Instrument> list = new TList<Instrument>();

        Instrument google = new Instrument();
        google.setName("Google");
        google.setCUSIP("GOOG");
        list.add(google);

        Instrument microsoft = new Instrument();
        microsoft.setName("Microsoft");
        microsoft.setCUSIP("MSFT");
        list.add(microsoft);

        return list;
    }
}
