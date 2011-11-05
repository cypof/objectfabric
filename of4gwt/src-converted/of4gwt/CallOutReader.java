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

import of4gwt.Connection.Endpoint;
import of4gwt.misc.Debug;
import of4gwt.misc.Queue;
import of4gwt.misc.ThreadAssert;
import of4gwt.misc.ThreadAssert.SingleThreaded;

@SingleThreaded
final class CallOutReader extends DistributedReader {

    private final CallOutWriter _writer;

    private final Queue<MethodCall> _callbacks = new Queue<MethodCall>();

    public CallOutReader(Endpoint endpoint, CallOutWriter writer) {
        super(endpoint);

        _writer = writer;
    }

    @Override
    protected void onStopped(Exception e) {
        super.onStopped(e);

        MethodCall[] calls;

        synchronized (_writer.getPendingCallsLock()) {
            _writer.setClosingException(e);
            calls = new MethodCall[_writer.getPendingCalls().size()];
            _writer.getPendingCalls().values().toArray(calls);
            _writer.getPendingCalls().clear();
        }

        if (Debug.ENABLED)
            Helper.getInstance().setNoTransaction(false);

        for (MethodCall call : calls)
            call.setException(e, true);

        for (int i = 0; i < _callbacks.size(); i++)
            _callbacks.get(i).setException(e, true);

        if (Debug.ENABLED)
            Helper.getInstance().setNoTransaction(true);
    }

    private enum Steps {
        CODE, COMMAND
    }

    @SuppressWarnings("fallthrough")
    @Override
    protected byte read() {
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
                        case CallInWriter.COMMAND_CALLBACK_STORE: {
                            MethodCall callback = getCallback();
                            _callbacks.add(callback);
                            break;
                        }
                        case CallInWriter.COMMAND_CALLBACK_EXECUTE: {
                            MethodCall callback = _callbacks.poll();
                            invokeCallback(callback);
                            break;
                        }
                        default:
                            throw new IllegalStateException();
                    }
                }
            }
        }
    }

    private final MethodCall getCallback() {
        Transaction transaction = (Transaction) ((TObject.Version) poll()).getReference().get();

        if (Debug.ENABLED)
            Helper.getInstance().disableEqualsOrHashCheck();

        MethodCall call;

        synchronized (_writer.getPendingCallsLock()) {
            call = _writer.getPendingCalls().remove(transaction);
        }

        if (Debug.ENABLED) {
            Helper.getInstance().enableEqualsOrHashCheck();
            Debug.assertion(transaction == call.getTransaction() || transaction == call.getFakeTransaction());

            if (Debug.THREADS)
                ThreadAssert.exchangeTake(call);

            /*
             * C.f. CallInReader.getCall.
             */
            Debug.assertion(takeReads() == null);
        }

        Transaction recycle = call.getFakeTransaction();

        if (recycle != null)
            recycle.getParent().recycleToPublic(recycle);

        TObject.Version[] writes = takeWrites();
        call.setCallbackWrites(writes);
        return call;
    }

    private final void invokeCallback(MethodCall callback) {
        TObject.Version[] writes = callback.getCallbackWrites();
        validateWrites(callback.getMethod().getTrunk(), writes);

        if (writes != null) {
            TObject.Version version = TransactionSets.getVersionFromTObject(writes, callback.getMethod());
            callback.setMethodVersion(version);

            if (callback.getTransaction() != null)
                for (int i = writes.length - 1; i >= 0; i--)
                    if (writes[i] != null && writes[i] != version)
                        callback.getTransaction().mergeVersion(writes[i].getShared(), writes[i]);
        }

        if (Debug.ENABLED)
            Helper.getInstance().setNoTransaction(false);

        callback.getTarget().getResultOrError_objectfabric(callback);

        if (Debug.ENABLED)
            Helper.getInstance().setNoTransaction(true);
    }

    // Debug

    @Override
    protected String getCommandString(byte command) {
        String value = CallInWriter.getCommandStringStatic(command);

        if (value != null)
            return value;

        return super.getCommandString(command);
    }
}
