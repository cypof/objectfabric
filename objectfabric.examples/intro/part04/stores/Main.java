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

package part04.stores;

import java.io.IOException;
import java.util.concurrent.Future;

import junit.framework.Assert;

import org.junit.Test;

import part04.stores.generated.ObjectModel;
import part04.stores.generated.StoredClass;

import com.objectfabric.FileStore;
import com.objectfabric.LazyMap;
import com.objectfabric.Site;
import com.objectfabric.TSet;
import com.objectfabric.Transaction;
import com.objectfabric.Transaction.CommitStatus;
import com.objectfabric.misc.AsyncCallback;
import com.objectfabric.misc.PlatformAdapter;
import com.objectfabric.misc.PlatformFile;
import com.objectfabric.misc.SeparateClassLoader;

/**
 * This examples shows how to make objects persistent.
 */
public class Main {

    public static final String TEMP_FOLDER = "temp";

    public static final String TEMP_FILE = TEMP_FOLDER + "/store.db";

    private FileStore store;

    public void run() throws Exception {
        ObjectModel.register();

        PlatformFile.mkdir(TEMP_FOLDER);
        PlatformFile.deleteFileIfExists(TEMP_FILE);
        PlatformFile.deleteFileIfExists(TEMP_FILE + FileStore.LOG_EXTENSION);

        /*
         * Create a store backed by a file.
         */
        store = new FileStore(TEMP_FILE);

        /*
         * Create a trunk that can be persisted to this store, and make it the default so
         * that we do not need to pass it to constructors of every object we create. C.f.
         * example about trunks in part04.stm.STM_5_Trunks.
         */
        Transaction trunk = Site.getLocal().createTrunk(store);
        Transaction.setDefaultTrunk(trunk);

        /*
         * A store can persist any immutable class (Check supported immutable types in
         * com.objectfabric.ImmutableClass), or types that derive from TObject. Generated
         * classes and ObjectFabric collections derive from TObject. A store persists a
         * root object and its reference graph.
         */
        StoredClass root = new StoredClass();
        StoredClass object = new StoredClass();
        root.setReference(object);
        object.setSet(new TSet<String>());
        object.getSet().add("blah");
        store.setRoot(root);

        /*
         * Any update made to an object contained in a store is persisted.
         */
        object.setText("Update");

        /*
         * Lazy classes are not loaded fully when read from a store. A LazyMap for example
         * only reads the value corresponding to a key when requested.
         */
        final LazyMap<String, Object> map = new LazyMap<String, Object>();
        map.put("key1", new Long(12));
        map.put("key2", new StoredClass());
        object.setReference(map);

        /*
         * ObjectFabric default setting is to wait for the store to acknowledge each
         * object and each change to already persisted objects. This can result in low
         * performance, e.g. the file store always does a full flush to disk
         * (FileDescriptor.sync()) before acknowledging a change. To make several changes
         * in a row, you can batch them using a transaction.
         */
        Transaction.run(new Runnable() {

            public void run() {
                /*
                 * These updates will be batched together, and the store will only call
                 * FileDescriptor.sync() once.
                 */
                map.put("key3", "value3");
                map.put("key4", "value4");
            }
        });

        /*
         * A transaction can be committed asynchronously to avoid blocking a thread until
         * a store or remote machine acknowledges it. ObjectFabric auto commit behavior
         * can also be changed so that it does not wait on each commit (C.f.
         * OF.Config.AutoCommitPolicy). Several asynchronous transactions can be written
         * in a row. ObjectFabric will invoke all callbacks in order when acknowledged by
         * the store or remote machine. Using such asynchronous transactions or changing
         * the AutoCommitPolicy allows performance similar to non-persisted objects.
         */
        Future<CommitStatus> future = Transaction.runAsync(new Runnable() {

            public void run() {
                /*
                 * These updates will be batched together, and the store will only call
                 * FileDescriptor.sync() once.
                 */
                map.put("key5", "value5");
                map.put("key6", "value6");
            }
        }, new AsyncCallback<Transaction.CommitStatus>() {

            public void onSuccess(CommitStatus result) {
                // Entries are safe in the store!
            }

            public void onFailure(Exception e) {
                // Entries could not be persisted
            }
        });

        /*
         * If application exits before an asynchronous update is acknowledged, data might
         * be lost if it has not been stored yet.
         */
        future.get();

        /*
         * Read back data from the store as if we were running a new process.
         */
        executeInSimulatedProcess(ReadBack.class);
    }

    public static final class ReadBack {

        @SuppressWarnings("unchecked")
        public static void main(String[] args) throws IOException {
            ObjectModel.register();

            FileStore store = new FileStore(part04.stores.Main.TEMP_FILE);

            StoredClass root = (StoredClass) store.getRoot();
            StoredClass object = (StoredClass) root.getReference();
            TSet<String> set = object.getSet();
            Assert.assertTrue(set.contains("blah"));
            Assert.assertEquals("Update", object.getText());

            LazyMap<String, Object> map = (LazyMap<String, Object>) object.getReference();
            Assert.assertEquals(new Long(12), map.get("key1"));
            Assert.assertTrue(map.get("key2") instanceof StoredClass);
            Assert.assertEquals("value3", map.get("key3"));
            Assert.assertEquals("value4", map.get("key4"));
            Assert.assertEquals("value5", map.get("key5"));
            Assert.assertEquals("value6", map.get("key6"));

            store.close();
            PlatformAdapter.shutdown();
        }
    }

    public static void main(String[] args) throws Exception {
        Main test = new Main();
        test.run();
    }

    @Test
    public void asTest() throws Exception {
        for (int i = 0; i < 10; i++) {
            run();
            PlatformAdapter.reset();
        }
    }

    /**
     * Creates a separate ClassLoader to read back data from the store. The class loader
     * separation ensures data cannot be read from memory and does reflect the file
     * content.
     */
    private void executeInSimulatedProcess(Class class_) {
        store.close();

        // Run read
        SeparateClassLoader test = new SeparateClassLoader(class_.getName());
        test.run();
    }
}
