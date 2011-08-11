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

package com.objectfabric.examples.indexes;

import java.io.File;

import junit.framework.Assert;

import org.junit.Test;

import com.objectfabric.FileStore;
import com.objectfabric.SQLiteIndex;
import com.objectfabric.SQLiteIndex.TObjectStatement;
import com.objectfabric.Site;
import com.objectfabric.TObject;
import com.objectfabric.Transaction;
import com.objectfabric.examples.indexes.generated.ObjectModel;
import com.objectfabric.examples.indexes.generated.StoredClass;
import com.objectfabric.misc.PlatformAdapter;
import com.objectfabric.misc.PlatformFile;

/**
 * Full text search for stores using project 'objectfabric.indexes.sqlite'. Early
 * implementation for a client, work in progress.
 */
public class Main {

    public static final String STORE = "temp/store.db";

    public static final String INDEX = "temp/index.db";

    public void run() throws Exception {
        ObjectModel.register();

        PlatformFile.mkdir("temp");
        PlatformFile.deleteFileIfExists(STORE);
        PlatformFile.deleteFileIfExists(STORE + FileStore.LOG_EXTENSION);
        File file = new File(INDEX);

        // SQLite close does not always release the file (?)
        if (file.exists())
            file.delete();

        PlatformFile.deleteFileIfExists(INDEX + "-journal");

        /*
         * Create a store backed by a file.
         */
        FileStore store = new FileStore(STORE);

        /*
         * Create a trunk in this store and set it as default so that all object we create
         * will be part of it. C.f. example about trunks in /objectfabric/examples:
         * part5.stm.STM_5_Trunks.
         */
        Transaction trunk = Site.getLocal().createTrunk(store);
        Transaction.setDefaultTrunk(trunk);

        /*
         * Create a full text index, in batch mode (no need to sync the file system for
         * each insert here).
         */
        SQLiteIndex index = new SQLiteIndex(INDEX, true);

        // Store and index some data

        final int COUNT = 200;

        for (int i = 0; i < COUNT; i++) {
            StoredClass stored = new StoredClass();
            stored.setInt(i);
            index.insertAsync(stored, null, null, "keyword" + i, i == 100 ? "blah" : "");
        }

        store.close();
        index.close();

        /*
         * Read back data
         */

        ObjectModel.register();

        store = new FileStore(STORE);

        index = new SQLiteIndex(INDEX, false);
        TObjectStatement st = null;

        try {
            st = index.get("keyword*");
            st.startRead(100, 0);
            TObject[] result = st.get();
            Assert.assertEquals(100, result.length);
        } finally {
            if (st != null)
                st.dispose();
        }

        try {
            st = index.get("keyword12");
            st.startRead(10, 0);
            TObject[] result = st.get();
            Assert.assertEquals(1, result.length);
            StoredClass object = (StoredClass) result[0];
            Assert.assertEquals(12, object.getInt());
        } finally {
            if (st != null)
                st.dispose();
        }

        try {
            st = index.get("blah");
            st.startRead(100, 0);
            TObject[] result = st.get();
            Assert.assertEquals(1, result.length);
            StoredClass object = (StoredClass) result[0];
            Assert.assertEquals(100, object.getInt());
        } finally {
            if (st != null)
                st.dispose();
        }

        try {
            st = index.get("keyword12 AND blah");
            st.startRead(100, 0);
            TObject[] result = st.get();
            Assert.assertEquals(0, result.length);
        } finally {
            if (st != null)
                st.dispose();
        }

        store.close();
        index.clear();
        index.close();
    }

    public static void main(String[] args) throws Exception {
        Main test = new Main();

        for (int i = 0; i < 1; i++) {
            test.run();
            Transaction.setDefaultTrunk(Site.getLocal().getTrunk());
            PlatformAdapter.shutdown();
        }
    }

    @Test
    public void asTest() throws Exception {
        run();
        Transaction.setDefaultTrunk(Site.getLocal().getTrunk());
        PlatformAdapter.reset();
    }
}
