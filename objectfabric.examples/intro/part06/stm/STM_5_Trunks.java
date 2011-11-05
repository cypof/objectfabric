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

package part06.stm;

import java.io.IOException;

import org.junit.Assert;
import org.junit.Test;

import part06.stm.generated.SimpleClass;
import part06.stm.generated.SimpleObjectModel;

import com.objectfabric.FileStore;
import com.objectfabric.Site;
import com.objectfabric.Store;
import com.objectfabric.Strings;
import com.objectfabric.Transaction;
import com.objectfabric.Transaction.ConflictDetection;
import com.objectfabric.misc.Debug;
import com.objectfabric.misc.PlatformAdapter;
import com.objectfabric.misc.PlatformFile;
import com.objectfabric.misc.SeparateClassLoader;

/**
 * All transactions are created as child of a root transaction called a Trunk, in the same
 * model as source-control solutions. Objects also belong to a trunk. All sites have a
 * default trunk transactions start from and objects are added to. You can specify another
 * trunk when starting a transaction or creating an object.
 * <nl>
 * Trunks are a way to partition memory to get better scalability. Transactions on
 * different trunks are processed separately, which reduces contention between threads and
 * improves application performance. In a distributed setting, those transactions can be
 * processed by different servers.
 * <nl>
 * As a consequence, a transaction is not allowed to modify objects belonging to another
 * trunk. An object belonging to a trunk can reference an object from another trunk, but
 * the two objects cannot be modified by the same transaction.
 * <nl>
 * Trunks are also the way to specify properties like how conflicts should be detected
 * (read/write, write/write or last write wins), memory consistency (full or eventual),
 * and granularity (whether subsequent updates on the same data can be coalesced or must
 * be kept intact for logging or notification purposes) and if objects can be persisted.
 */
public class STM_5_Trunks {

    public Store run() throws IOException {
        /*
         * By default transactions are started from local site's trunk.
         */
        Transaction transaction = Transaction.start();
        Assert.assertTrue(Site.getLocal().getTrunk() == transaction.getParent());
        transaction.abort();

        /*
         * Objects are also part of a trunk. By default it is the local site.
         */
        SimpleClass object = new SimpleClass();
        Assert.assertTrue(Site.getLocal().getTrunk() == object.getTrunk());

        /*
         * Create a separate trunk, and start a transaction from it.
         */
        Transaction trunk = Site.getLocal().createTrunk();
        transaction = Transaction.start(trunk);

        try {
            /*
             * Object belongs to local site's trunk, it cannot be modified by the current
             * transaction.
             */
            Debug.expectException();
            object.setInt(1);
            Assert.fail();
        } catch (RuntimeException ex) {
            Assert.assertEquals(Strings.WRONG_TRUNK, ex.getMessage());
        }

        /*
         * Create an object on the new trunk. It can be modified by current transaction.
         */
        final SimpleClass object2 = new SimpleClass(trunk);
        object2.setInt(2);

        transaction.abort();

        /*
         * Create a trunk with specific properties. Transactions created from this trunk
         * will not check for conflicts, which can give higher throughput but leaves it to
         * the developer to make sure threads do not override each others' updates.
         */
        trunk = Site.getLocal().createTrunk(ConflictDetection.LAST_WRITE_WINS);

        /*
         * By default, a trunk cannot have persistent objects.
         */
        Assert.assertNull(Site.getLocal().getTrunk().getStore());
        Assert.assertNull(trunk.getStore());

        /*
         * Create a store to test persistence.
         */
        PlatformFile.mkdir(part04.stores.Main.TEMP_FOLDER);
        PlatformFile.deleteFileIfExists(part04.stores.Main.TEMP_FILE);
        PlatformFile.deleteFileIfExists(part04.stores.Main.TEMP_FILE + FileStore.LOG_EXTENSION);
        final FileStore store = new FileStore(part04.stores.Main.TEMP_FILE);

        /*
         * Object can be made persistent if their trunk has a store. The store must be
         * given at trunk creation.
         */
        trunk = Site.getLocal().createTrunk(store);

        /*
         * Objects created on this trunk can be persisted.
         */
        SimpleClass object3 = new SimpleClass(trunk);
        store.setRoot(object3);

        try {
            /*
             * Objects belonging to a trunk without a store or a trunk with another store
             * cannot be persisted.
             */
            Debug.expectException();
            store.setRoot(object2);
            Assert.fail();
        } catch (Exception ex) {
            Assert.assertTrue(ex.getMessage().endsWith(Strings.WRONG_STORE));
        }

        /*
         * Stores can contain multiple trunks. An object from one trunk can reference an
         * object from another. In this case, object4 will be persisted as a reference
         * from object3 which was already in the store.
         */
        trunk = Site.getLocal().createTrunk(store);
        SimpleClass object4 = new SimpleClass(trunk);
        object3.setObject(object4);
        object4.setInt(3);

        /*
         * For convenience, you can assign a trunk as default for the current process, so
         * that you do not have to pass it to each transaction or object constructor.
         */
        Transaction.setDefaultTrunk(trunk);
        return store;
    }

    public static void main(String[] args) throws Exception {
        STM_5_Trunks test = new STM_5_Trunks();
        test.run();
    }

    @Test
    public void asTest() throws IOException {
        Store store = run();

        store.close();
        SeparateClassLoader test = new SeparateClassLoader(ReadBackTest.class.getName());
        test.run();

        PlatformAdapter.reset();
    }

    public static final class ReadBackTest {

        public static void main(String[] args) throws IOException {
            SimpleObjectModel.register();

            FileStore store = new FileStore(part04.stores.Main.TEMP_FILE);
            SimpleClass object3 = (SimpleClass) store.getRoot();
            SimpleClass object4 = object3.getObject();
            Assert.assertEquals(3, object4.getInt());

            store.close();

            if (Debug.TESTING)
                PlatformAdapter.shutdown();
        }
    }
}
