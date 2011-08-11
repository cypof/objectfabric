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

package com.objectfabric;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.EnumSet;

import org.junit.Ignore;
import org.junit.Test;

import com.objectfabric.ImmutableWriter;
import com.objectfabric.Multiplexer;
import com.objectfabric.Reader;
import com.objectfabric.Site;
import com.objectfabric.TObject;
import com.objectfabric.TestsHelper;
import com.objectfabric.TransactionManager;
import com.objectfabric.Writer;
import com.objectfabric.TObject.Descriptor;
import com.objectfabric.TObject.UserTObject;
import com.objectfabric.generated.SimpleClass;
import com.objectfabric.generated.SimpleObjectModel;
import com.objectfabric.misc.Debug;
import com.objectfabric.misc.List;
import com.objectfabric.misc.SeparateClassLoader;

@Ignore
public class DGCTest extends TestsHelper {

    private static boolean SERVER;

    public static final class Server {

        private static TestTransport _transport;

        private static SimpleClass _simple;

        private static Descriptor _simpleDescriptor;

        private static WeakReference<SimpleClass> _weakRef;

        protected Server() {
        }

        public static void main() {
            DGCTest.SERVER = true;
            MemoryWatcher.getInstance().setThreadName("MemoryWatcher Server");
            Site.getLocal().registerObjectModel(SimpleObjectModel.getInstance());
            Site.getLocal().setUID("Server");

            _transport = new TestTransport();
            _transport.register(TransactionManager.getTrunk(), null);

            _simple = new SimpleClass();
            _transport.connect(_simple);
            _simple.setText("bla");

            _simpleDescriptor = _simple.getDescriptor();
            _weakRef = new WeakReference<SimpleClass>(_simple);
        }

        public static int writeStatic(byte[] buffer, int offset, int length) {
            return _transport.write(buffer, offset, length);
        }

        public static void readStatic(byte[] buffer, int offset, int length) {
            _transport.read(buffer, offset, length);
        }

        public static void sendTransaction() {
            /*
             * Another replicated transaction to change the transactions referenced by
             * transport to allow GC of _simple.
             */
            Site.getLocal().setName("name");
        }

        public static boolean isDone() {
            System.gc();

            if (_weakRef.get() != null)
                return false;

            if (Site.getLocal().getDescriptor(new Id(_simpleDescriptor.getId())) != null)
                return false;

            return true;
        }

        public static void teardown() {
            _transport.unregister();
        }
    }

    public static final class Client {

        private static TestTransport _transport;

        private static SimpleClass _simple;

        private static Descriptor _simpleDescriptor;

        private static WeakReference<SimpleClass> _weakRef;

        protected Client() {
        }

        public static void main() {
            MemoryWatcher.getInstance().setThreadName("MemoryWatcher Client");
            Site.getLocal().registerObjectModel(SimpleObjectModel.getInstance());
            Site.getLocal().setUID("Client");

            _transport = new TestTransport();
            _transport.register(TransactionManager.getTrunk(), null);
        }

        public static void sendTransaction() {
            /*
             * Another replicated transaction to change the transactions referenced by
             * transport to allow GC of _simple.
             */
            Site.getLocal().setName("name");
        }

        public static void gc() {
            _simple = null;
            System.gc();
        }

        public static boolean isDone() {
            System.gc();

            if (_weakRef == null || _weakRef.get() != null)
                return false;

            if (Site.getLocal().getDescriptor(new Id(_simpleDescriptor.getId())) != null)
                return false;

            return true;
        }

        public static int writeStatic(byte[] buffer, int offset, int length) {
            return _transport.write(buffer, offset, length);
        }

        public static void readStatic(byte[] buffer, int offset, int length) {
            _transport.read(buffer, offset, length);
        }

        public static void teardown() {
            _transport.unregister();

            Debug.assertion(_weakRef.get() == null);
        }
    }

    static final class TestWriter extends Writer {

        private boolean _snapshotSent;

        protected TestWriter(Multiplexer transport, ArrayList<Object> continueStack) {
            super(transport, continueStack);
        }

        @Override
        protected boolean forceSnapshot(Descriptor descriptor) {
            if (descriptor.getTObject() == Server._simple) {
                if (!_snapshotSent) {
                    _snapshotSent = true;
                    return true;
                }
            }

            return false;
        }
    }

    static final class TestReader extends Reader {

        protected TestReader(Multiplexer transport) {
            super(new List<UserTObject>());
        }

        @Override
        protected void onReadDescriptor(Descriptor descriptor) {
            TObject object = null;

            if (descriptor != null)
                object = descriptor.getTObject();

            if (Client._simple == null && object instanceof SimpleClass) {
                Client._simple = (SimpleClass) object;
                Client._simpleDescriptor = Client._simple.getDescriptor();
                Client._weakRef = new WeakReference<SimpleClass>(Client._simple);
            }
        }
    }

    private static final class TestTransport extends Multiplexer {

        protected Site _remoteSite;

        public TestTransport() {
            super(DGCTest.SERVER ? EnumSet.noneOf(Flags.class) : EnumSet.of(Flags.PROCESS_ALL_CHANGES, Flags.INTERCEPTS_COMMIT));

            registerThread(Thread.currentThread());
        }

        @Override
        protected Writer createWriter() {
            if (DGCTest.SERVER)
                return new TestWriter(this, getContinueStack());

            return super.createWriter();
        }

        @Override
        protected Reader createReader() {
            if (!DGCTest.SERVER)
                return new TestReader(this);

            return super.createReader();
        }

        @Override
        protected void onRemoteSite(Site site) {
            _remoteSite = site;
        }
    }

    @Test
    public void run() {
        int writeLength = ImmutableWriter.MIN_BUFFER_LENGTH;
        // writeLength += 5;
        // writeLength += GWTAdapter.getRandom().nextInt(30);
        writeLength += 10000;
        byte[] buffer = new byte[writeLength];

        SeparateClassLoader a = new SeparateClassLoader("Server", Server.class.getName(), false);
        SeparateClassLoader b = new SeparateClassLoader("Client", Client.class.getName(), false);

        // Invokes main
        a.run();
        b.run();

        int retries = 0;
        boolean done = false;
        int clearingTransactionCount = 0;

        while (!((Boolean) b.invoke("isDone", new Class[0], new Object[0])) || !((Boolean) a.invoke("isDone", new Class[0], new Object[0]))) {
            Class[] types = new Class[] { byte[].class, int.class, int.class };

            int lengthA = (Integer) a.invoke("writeStatic", types, new Object[] { buffer, 0, buffer.length });
            b.invoke("readStatic", types, new Object[] { buffer, 0, lengthA });

            int lengthB = (Integer) b.invoke("writeStatic", types, new Object[] { buffer, 0, buffer.length });
            a.invoke("readStatic", types, new Object[] { buffer, 0, lengthB });

            if (lengthA == 0 && lengthB == 0)
                done = true;

            if (done && clearingTransactionCount < 2) {
                b.invoke("gc", new Class[0], new Object[0]);
                a.invoke("sendTransaction", new Class[0], new Object[0]);
                b.invoke("sendTransaction", new Class[0], new Object[0]);
                clearingTransactionCount++;
            }

            retries++;
        }

        System.out.println("retries: " + retries);

        a.invoke("teardown", new Class[0], new Object[0]);
        b.invoke("teardown", new Class[0], new Object[0]);
    }

    public static void main(String[] args) throws Exception {
        DGCTest test = new DGCTest();
        test.before();
        test.run();
        test.after();
    }
}
