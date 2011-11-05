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

package bench;

import bench.generated.BenchObjectModel;
import bench.generated.MyClass;

import com.objectfabric.Transaction;

public class Data {

    private volatile Thread thread;

    private MyClass myClass;

    protected int writes;

    public Data() {
        BenchObjectModel.register();
    }

    public final MyClass getMyClass() {
        return myClass;
    }

    public final void setMyClass(MyClass value) {
        if (myClass != null && value != myClass)
            throw new RuntimeException("All sites should share the same instance.");

        myClass = value;
    }

    protected void sendData() {
        if (thread == null) {
            thread = new Thread(new Runnable() {

                public void run() {
                    while (thread != null) {
                        Transaction transaction = Transaction.start(myClass.getTrunk());
                        int value = myClass.getMyField();
                        myClass.setMyField(value + 1);
                        transaction.commitAsync(null);

                        writes++;
                    }
                }
            });

            thread.setName("Bench Writer");
            thread.start();
        }
    }

    protected void stopData() {
        thread = null;
    }
}
