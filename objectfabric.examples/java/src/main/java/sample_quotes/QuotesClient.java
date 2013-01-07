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

package sample_quotes;

import java.math.BigDecimal;
import java.util.Set;

import org.objectfabric.AbstractResourceListener;
import org.objectfabric.JVMWorkspace;
import org.objectfabric.Netty;
import org.objectfabric.Resource;
import org.objectfabric.Workspace;

import sample_quotes.generated.ObjectModel;
import sample_quotes.generated.Order;

/**
 * This sample listens for stock quotes and sends orders.
 */
@SuppressWarnings("unchecked")
public class QuotesClient {

    public static void main(String[] args) {
        ObjectModel.register();
        Workspace workspace = new JVMWorkspace();
        workspace.addURIHandler(new Netty());

        /*
         * Write current prices and listens for future ones.
         */
        final Resource goog = workspace.open("ws://localhost:8888/GOOG");
        final Resource msft = workspace.open("ws://localhost:8888/MSFT");

        System.out.println("GOOG " + goog.get());
        System.out.println("MSFT " + msft.get());

        goog.addListener(new AbstractResourceListener() {

            @Override
            public void onSet() {
                System.out.println("GOOG " + goog.get());
            }
        });

        msft.addListener(new AbstractResourceListener() {

            @Override
            public void onSet() {
                System.out.println("MSFT " + msft.get());
            }
        });

        /*
         * Send an order.
         */
        Resource orders = workspace.open("ws://localhost:8888/orders");

        Order order = new Order(orders);
        order.user("Mini Me");
        order.instrument("GOOG");
        order.price(new BigDecimal(1));
        order.quantity(new BigDecimal(1));

        ((Set<Order>) orders.get()).add(order);

        workspace.close();
    }
}
