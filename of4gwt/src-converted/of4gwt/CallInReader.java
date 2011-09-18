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

package of4gwt;

import of4gwt.misc.Executor;

import of4gwt.Connection.Endpoint;
import of4gwt.MultiplexerWriter.Command;
import of4gwt.TObject.UserTObject;
import of4gwt.TObject.UserTObject.LocalMethodCall;
import of4gwt.TObject.Version;
import of4gwt.misc.Debug;
import of4gwt.misc.PlatformThreadPool;
import of4gwt.misc.Queue;
import of4gwt.misc.ThreadAssert;
import of4gwt.misc.ThreadAssert.SingleThreaded;
import of4gwt.misc.TransparentExecutor;

@SingleThreaded
final class CallInReader extends DistributedReader {

    private final CallInWriter _writer;

    private final Queue<Call> _calls = new Queue<Call>();

    public CallInReader(Endpoint endpoint, CallInWriter writer) {
        super(endpoint);

        if (writer == null)
            throw new IllegalArgumentException();

        _writer = writer;
    }

    private enum Steps {
        CODE, COMMAND
    }

    @Override
    @SuppressWarnings({ "fallthrough" })
    public byte read() {
        for (;;) {
            Steps step = Steps.CODE;
            byte code = 0;

            if (interrupted()) {
                step = (Steps) resume();
                code = resumeByte();
            }

            switch (step) {
                case CODE: {
                    code = readCode();

                    if (interrupted()) {
                        interruptByte(code);
                        interrupt(Steps.CODE);
                        return 0;
                    }

                    if (Debug.ENABLED)
                        Debug.assertion((code & TObjectWriter.FLAG_TOBJECT) == 0);

                    if ((code & TObjectWriter.FLAG_EOF) != 0)
                        return code;

                    if (Debug.ENABLED)
                        Debug.assertion((code & TObjectWriter.FLAG_IMMUTABLE) == 0);
                }
                case COMMAND: {
                    switch (code) {
                        case CallOutWriter.COMMAND_CALL_STORE: {
                            Call call = getCall();
                            _calls.add(call);
                            break;
                        }
                        case CallOutWriter.COMMAND_CALL_EXECUTE: {
                            Call call = _calls.poll();
                            execCall(call);
                            break;
                        }
                        default:
                            throw new IllegalStateException();
                    }
                }
            }
        }
    }

    private final Call getCall() {
        Transaction transaction = (Transaction) ((TObject.Version) poll()).getReference().get();
        UserTObject method = ((TObject.Version) poll()).getReference().get();
        UserTObject target = ((TObject.Version) poll()).getReference().get();
        int index = (Integer) poll();
        boolean inTransaction = index >= 0;

        if (index < 0)
            index = -index - 1;

        if (Debug.ENABLED) {
            /*
             * Local snapshot is not the same as the remote snapshot of the transaction,
             * so would not be relevant anyway to keep track of reads.
             */
            Debug.assertion(takeReads() == null);
        }

        Validator validator = getEndpoint().getValidator();

        if (validator != null)
            ExpectedExceptionThrower.validateCall(getEndpoint().getConnection(), validator, target, method);

        Call call = new Call(getEndpoint(), _writer, target, method, index, inTransaction ? transaction : null, takeWrites());

        if (!inTransaction)
            call.setFakeTransaction(transaction);

        return call;
    }

    private final void execCall(Call call) {
        TObject.Version[] writes = call.getWrites();
        validateWrites(call.getMethod().getTrunk(), writes);
        TObject.Version version = TransactionSets.getVersionFromTObject(writes, call.getMethod());
        call.setMethodVersion(version);

        if (call.getTransaction() != null) {
            Transaction branch = call.getTransaction().getBranch();
            Snapshot snapshot = branch.takeSnapshot();

            if (Debug.ENABLED)
                branch.takeSnapshotDebug(snapshot, call.getTransaction(), "CallInReader.execCall");

            branch.startFromPublic(call.getTransaction(), snapshot);
            call.getTransaction().setFlags(Transaction.FLAG_NO_READS | Transaction.FLAG_REMOTE | Transaction.FLAG_REMOTE_METHOD_CALL);
            call.getTransaction().setPrivateSnapshotVersions(writes);
        }

        Executor executor = call.getTarget().getDefaultMethodExecutor_objectfabric();

        /*
         * Avoid using NIO thread to execute methods.
         */
        if (executor == TransparentExecutor.getInstance())
            executor = PlatformThreadPool.getInstance();

        if (Debug.THREADS)
            ThreadAssert.exchangeGive(call, call.getTransaction());

        try {
            if (Debug.ENABLED)
                Helper.getInstance().setNoTransaction(false);

            executor.execute(call);
        } finally {
            ThreadContext.getCurrent().abortAll();

            if (Debug.ENABLED)
                Helper.getInstance().setNoTransaction(true);
        }
    }

    public static final class Call extends MethodCall implements Runnable {

        // Copy for thread assertions
        private final Endpoint _endpoint;

        private final CallInWriter _writer;

        private final Version[] _writes;

        public Call(Endpoint endpoint, CallInWriter writer, UserTObject target, UserTObject method, int index, Transaction transaction, Version[] writes) {
            super(target, method, index, transaction);

            _endpoint = endpoint;
            _writer = writer;
            _writes = writes;
        }

        public Version[] getWrites() {
            return _writes;
        }

        @Override
        public void run() {
            if (Debug.ENABLED)
                Debug.assertion(Transaction.getCurrent() == null);

            if (Debug.THREADS)
                ThreadAssert.exchangeTake(this);

            if (getTransaction() != null)
                Transaction.setCurrentUnsafe(getTransaction());

            getTarget().invoke_objectfabric(this);

            if (getTransaction() != null)
                Transaction.setCurrentUnsafe(null);

            if (Debug.THREADS)
                ThreadAssert.exchangeGive(this, getTransaction());
        }

        @SuppressWarnings("unchecked")
        @Override
        public void set(Object result, boolean direct) {
            Throwable throwable = null;

            if (!direct)
                throwable = LocalMethodCall.afterRun(this, null);

            TObject.Version version = getMethod().getSharedVersion_objectfabric().createVersion();

            if (throwable == null)
                getTarget().setResult_objectfabric(version, getIndex(), result);
            else
                getTarget().setError_objectfabric(version, getIndex(), of4gwt.misc.PlatformAdapter.getStackAsString(throwable));

            setMethodVersion(version);
            super.set(result, direct);
        }

        @Override
        public void setException(Throwable throwable, boolean direct) {
            if (!direct)
                throwable = LocalMethodCall.afterRun(this, throwable);

            TObject.Version version = getMethod().getSharedVersion_objectfabric().createVersion();
            getTarget().setError_objectfabric(version, getIndex(), of4gwt.misc.PlatformAdapter.getStackAsString(throwable));
            setMethodVersion(version);
            super.setException(throwable, direct);
        }

        @Override
        protected void done() {
            _endpoint.enqueueOnWriterThread(new Command() {

                public MultiplexerWriter getWriter() {
                    return _writer;
                }

                public void run() {
                    _writer.writeCallback(Call.this);
                }
            });
        }
    }

    // Debug

    @Override
    protected String getCommandString(byte command) {
        String value = CallOutWriter.getCommandStringStatic(command);

        if (value != null)
            return value;

        return super.getCommandString(command);
    }
}
