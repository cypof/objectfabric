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

package part05.indexes;

import java.io.File;

import junit.framework.Assert;

import org.junit.Test;

import part05.indexes.generated.ObjectModel;
import part05.indexes.generated.StoredClass;

import com.objectfabric.FileStore;
import com.objectfabric.SQLiteFullTextIndex;
import com.objectfabric.Site;
import com.objectfabric.Transaction;
import com.objectfabric.misc.PlatformAdapter;
import com.objectfabric.misc.PlatformFile;

/**
 * This is an example of how to query objects. C.f. comment on class Index. This
 * particular index uses SQLite to perform full text search over some objects.
 * <nl>
 * Warning: Early implementation for a client, work in progress.
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
        SQLiteFullTextIndex index = new SQLiteFullTextIndex(INDEX, store);

        // Store and index some data

        final int COUNT = 200;

        for (int i = 0; i < COUNT; i++) {
            StoredClass stored = new StoredClass();
            stored.setInt(i);
            index.insertAsync(stored, null, null, "keyword" + i, i == 100 ? "blah" : "");
        }

        /*
         * Read back data
         */

        Object[] result = index.get("keyword*", 0, 100);
        Assert.assertEquals(100, result.length);

        result = index.get("keyword12", 0, 10);
        Assert.assertEquals(1, result.length);
        StoredClass object = (StoredClass) result[0];
        Assert.assertEquals(12, object.getInt());

        result = index.get("blah", 0, 100);
        Assert.assertEquals(1, result.length);
        object = (StoredClass) result[0];
        Assert.assertEquals(100, object.getInt());

        result = index.get("keyword12 AND blah", 0, 100);
        Assert.assertEquals(0, result.length);

        store.close();
        index.clear();
        index.close();
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
}
