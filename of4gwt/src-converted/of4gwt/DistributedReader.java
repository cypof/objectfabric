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
import of4gwt.Extension.TObjectMapEntry;
import of4gwt.TObject.Descriptor;
import of4gwt.TObject.Reference;
import of4gwt.TObject.UserTObject;
import of4gwt.TObject.Version;
import of4gwt.VersionMap.Source;
import of4gwt.misc.Debug;
import of4gwt.misc.List;
import of4gwt.misc.Log;
import of4gwt.misc.PlatformAdapter;
import of4gwt.misc.PlatformClass;
import of4gwt.misc.SparseArrayHelper;
import of4gwt.misc.ThreadAssert;
import of4gwt.misc.ThreadAssert.SingleThreaded;

@SingleThreaded
abstract class DistributedReader extends MultiplexerReader {

    private final Endpoint _endpoint;

    private final List<Object> _stack = new List<Object>();

    private Version[] _publicVersions, _reads, _writes;

    @SuppressWarnings("unchecked")
    private TObjectMapEntry<Version[]>[] _publicImports = new TObjectMapEntry[SparseArrayHelper.DEFAULT_CAPACITY];

    private final List<Transaction> _publicImportsBranches = new List<Transaction>();

    private final List<Version> _immutables = new List<Version>();

    public DistributedReader(Endpoint endpoint) {
        super(endpoint.getNewTObjects());

        if (endpoint == null)
            throw new IllegalArgumentException();

        _endpoint = endpoint;

        setVisitingGatheredVersions(false);
    }

    public final Endpoint getEndpoint() {
        return _endpoint;
    }

    public final void validateWrites(Transaction mainBranch, Version[] custom) {
        Validator validator = _endpoint.getValidator();

        if (validator != null) {
            ThreadContext context = ThreadContext.getCurrent();
            Transaction transaction = null;

            // Create snapshot

            if (Debug.ENABLED)
                Helper.getInstance().setNoTransaction(false);

            for (int i = 0; i < _publicImportsBranches.size(); i++) {
                Transaction branch = _publicImportsBranches.get(i);
                Version[] branchImports = TObjectMapEntry.get(_publicImports, branch);
                transaction = context.saveCurrentAndStartTransaction(Transaction.getCurrent(), branch, Transaction.FLAG_AUTO);
                transaction.setPrivateSnapshotVersions(branchImports);
            }

            if (transaction == null || transaction.getTrunk() != mainBranch)
                transaction = context.saveCurrentAndStartTransaction(Transaction.getCurrent(), mainBranch, Transaction.FLAG_AUTO);

            if (Debug.ENABLED)
                Helper.getInstance().setNoTransaction(true);

            if (_publicVersions != null)
                transaction.addPrivateSnapshotVersions(_publicVersions);

            if (_writes != null)
                transaction.addPrivateSnapshotVersions(_writes);

            if (custom != null)
                transaction.addPrivateSnapshotVersions(custom);

            // Iterate and validate each write
            Connection connection = getEndpoint().getConnection();

            for (int i = 0; i < _publicImportsBranches.size(); i++) {
                Transaction branch = _publicImportsBranches.get(i);
                Version[] branchImports = TObjectMapEntry.get(_publicImports, branch);
                ExpectedExceptionThrower.validateWrite(connection, validator, branchImports);
            }

            if (_publicVersions != null)
                ExpectedExceptionThrower.validateWrite(connection, validator, _publicVersions);

            if (_writes != null)
                ExpectedExceptionThrower.validateWrite(connection, validator, _writes);

            if (custom != null)
                ExpectedExceptionThrower.validateWrite(connection, validator, custom);

            ExpectedExceptionThrower.validateWrite(connection, validator, _immutables);
        }
    }

    public final void propagateStandalone() {
        for (int i = _publicImportsBranches.size() - 1; i >= 0; i--) {
            Transaction branch = _publicImportsBranches.remove(i);
            TObjectMapEntry<Version[]> entry = TObjectMapEntry.getEntry(_publicImports, branch);

            for (int j = 0; j < entry.getValue().length; j++)
                if (entry.getValue()[j] != null)
                    entry.getValue()[j].mergeReadOnlyFields();

            TransactionManager.propagate(branch, entry.getValue(), VersionMap.IMPORTS_SOURCE);
            entry.setValue(null);
        }

        if (_publicVersions != null) {
            Source source = new Source((Connection.Version) getEndpoint().getConnection().getSharedVersion_objectfabric(), (byte) 0, true);
            TransactionManager.propagate(getBranch(), _publicVersions, source);
            _publicVersions = null;
        }

        for (int i = _immutables.size() - 1; i >= 0; i--) {
            Version version = _immutables.remove(i);
            version.mergeReadOnlyFields();
        }
    }

    public final Version[] takeReads() {
        Version[] temp = _reads;
        _reads = null;
        return temp;
    }

    public final Version[] takeWrites() {
        Version[] temp = _writes;
        _writes = null;
        return temp;
    }

    //

    public final int getStackSize() {
        return _stack.size();
    }

    public final Object peek() {
        return _stack.get(_stack.size() - 1);
    }

    public final Object poll() {
        Object object = _stack.remove(_stack.size() - 1);
        return object;
    }

    public final void push(Object object) {
        _stack.add(object);
    }

    //

    private enum ReadCodeStep {
        VALUE, NUMBER, AS_FLAG, AS_COMMAND
    }

    @SuppressWarnings({ "fallthrough", "null" })
    public final byte readCode() {
        for (;;) {
            ReadCodeStep step = ReadCodeStep.VALUE;
            byte code = 0;
            int number = 0;
            Version version = null;

            if (interrupted()) {
                step = (ReadCodeStep) resume();
                code = resumeByte();
                version = (Version) resume();
            }

            switch (step) {
                case VALUE: {
                    if (!canReadByte()) {
                        interrupt(version);
                        interruptByte(code);
                        interrupt(ReadCodeStep.VALUE);
                        return 0;
                    }

                    code = readByte(TObjectWriter.DEBUG_TAG_CODE);

                    if (Debug.COMMUNICATIONS_LOG)
                        Log.write(PlatformClass.getSimpleName(getClass()) + ".readCode, code: " + code);

                    if (TObjectWriter.isExitCode(code))
                        return code;
                }
                case NUMBER: {
                    if (Debug.COMMUNICATIONS) {
                        if (!canReadInteger()) {
                            interrupt(version);
                            interruptByte(code);
                            interrupt(ReadCodeStep.NUMBER);
                            return 0;
                        }

                        number = readInteger();
                        Debug.assertion(getDebugCommandCounter() == number);
                        getAndIncrementDebugCommandCounter();
                    }
                }
                case AS_FLAG: {
                    if ((code & TObjectWriter.FLAG_TOBJECT) != 0) {
                        Version shared = readTObjectShared(code);

                        if (interrupted()) {
                            if (Debug.ENABLED)
                                Debug.assertion(shared == null);

                            interrupt(version);
                            interruptByte(code);
                            interrupt(ReadCodeStep.AS_FLAG);
                            return 0;
                        }

                        if (shared == null)
                            throw new IllegalStateException();

                        push(shared);
                        break;
                    }

                    if ((code & TObjectWriter.FLAG_IMMUTABLE) != 0) {
                        Object value = UnknownObjectSerializer.read(this, code);

                        if (interrupted()) {
                            if (Debug.ENABLED)
                                Debug.assertion(value == null);

                            interrupt(version);
                            interruptByte(code);
                            interrupt(ReadCodeStep.AS_FLAG);
                            return 0;
                        }

                        push(value);
                        break;
                    }

                    if (Debug.DGC)
                        if (PlatformAdapter.getRandomInt(100) == 0)
                            System.gc();

                    if (Debug.COMMUNICATIONS_LOG)
                        log("Command: " + getCommandString(code) + " (" + number + ")");
                }
                case AS_COMMAND: {
                    switch (code) {
                        case DistributedWriter.COMMAND_PUBLIC_IMPORT: {
                            if (version == null) {
                                Version shared = readTObjectShared();

                                if (interrupted()) {
                                    interrupt(version);
                                    interruptByte(code);
                                    interrupt(ReadCodeStep.AS_COMMAND);
                                    return 0;
                                }

                                version = shared.createVersion();
                            }

                            version.visit(this);

                            if (!interrupted()) {
                                if (version.allModifiedFieldsAreReadOnly())
                                    _immutables.add(version);
                                else {
                                    if (Debug.ENABLED) {
                                        Version shared = version.getUnionAsVersion();
                                        Debug.assertion(!shared.isImmutable() || shared.getReference().get() instanceof Connection);
                                    }

                                    TObjectMapEntry<Version[]> entry = TObjectMapEntry.getEntry(_publicImports, getBranch());

                                    if (entry == null) {
                                        entry = new TObjectMapEntry<Version[]>(getBranch(), null);
                                        TObjectMapEntry.put(_publicImports, entry);
                                    }

                                    if (entry.getValue() == null) {
                                        entry.setValue(new Version[SparseArrayHelper.DEFAULT_CAPACITY]);
                                        _publicImportsBranches.add(getBranch());
                                    }

                                    Version[] branchImports = TransactionSets.putForShared(entry.getValue(), version, version.getUnion());

                                    if (branchImports != entry.getValue())
                                        entry.setValue(branchImports);
                                }
                            }

                            break;
                        }
                        case DistributedWriter.COMMAND_PUBLIC_VERSION: {
                            if (version == null) {
                                Version shared = readTObjectShared();

                                if (interrupted()) {
                                    interrupt(version);
                                    interruptByte(code);
                                    interrupt(ReadCodeStep.AS_COMMAND);
                                    return 0;
                                }

                                version = shared.createVersion();
                            }

                            version.visit(this);

                            if (!interrupted()) {
                                if (_publicVersions == null)
                                    _publicVersions = new Version[SparseArrayHelper.DEFAULT_CAPACITY];

                                _publicVersions = TransactionSets.putForShared(_publicVersions, version, version.getUnion());
                            }

                            break;
                        }
                        case DistributedWriter.COMMAND_READ: {
                            if (version == null) {
                                Version shared = readTObjectShared();

                                if (interrupted()) {
                                    interrupt(version);
                                    interruptByte(code);
                                    interrupt(ReadCodeStep.AS_COMMAND);
                                    return 0;
                                }

                                version = shared.createRead();
                                setVisitingReads(true);
                            }

                            version.visit(this);

                            if (!interrupted()) {
                                setVisitingReads(false);

                                if (_reads == null)
                                    _reads = new Version[SparseArrayHelper.DEFAULT_CAPACITY];

                                _reads = TransactionSets.putForShared(_reads, version, version.getUnion());
                            }

                            break;
                        }
                        case DistributedWriter.COMMAND_WRITE: {
                            if (version == null) {
                                Version shared = readTObjectShared();

                                if (interrupted()) {
                                    interrupt(version);
                                    interruptByte(code);
                                    interrupt(ReadCodeStep.AS_COMMAND);
                                    return 0;
                                }

                                version = shared.createVersion();
                            }

                            version.visit(this);

                            if (!interrupted()) {
                                if (_writes == null)
                                    _writes = new Version[SparseArrayHelper.DEFAULT_CAPACITY];

                                _writes = TransactionSets.putForShared(_writes, version, version.getUnion());
                            }

                            break;
                        }
                        case DistributedWriter.COMMAND_SET_CURRENT_BRANCH: {
                            setBranch((Transaction) readTObject());

                            if (Debug.THREADS)
                                if (!interrupted())
                                    ThreadAssert.exchangeTake(getBranch());

                            break;
                        }
                        default:
                            return code;
                    }

                    if (interrupted()) {
                        interrupt(version);
                        interruptByte(code);
                        interrupt(ReadCodeStep.AS_COMMAND);
                        return 0;
                    }

                    break;
                }
                default:
                    throw new IllegalStateException();
            }
        }
    }

    // TODO: remove
    @Override
    protected UserTObject createInstance(ObjectModel model, Transaction trunk, int classId, TType[] genericParameters) {
        UserTObject object = super.createInstance(model, trunk, classId, genericParameters);

        if (object instanceof LazyMap)
            ((LazyMapSharedVersion) object.getSharedVersion_objectfabric()).disableCache();

        return object;
    }

    // Debug

    @Override
    protected void assertIdle() {
        super.assertIdle();

        Debug.assertion(_stack.size() == 0);

        for (int i = 0; i < _publicImports.length; i++)
            if (_publicImports[i] != null)
                Debug.assertion(_publicImports[i].getValue() == null);

        Debug.assertion(_publicImportsBranches.size() == 0);
        Debug.assertion(_publicVersions == null);
        Debug.assertion(_reads == null);
        Debug.assertion(_writes == null);
        Debug.assertion(_immutables.size() == 0);
    }

    /**
     * Sends expected reader state for debug purposes.
     */
    @Override
    protected final int getCustomDebugInfo1() {
        if (!Debug.COMMUNICATIONS)
            throw new IllegalStateException();

        Descriptor descriptor = null;

        if (getBranch() != null) {
            Reference ref = getBranch().getSharedVersion_objectfabric().getReference();

            if (ref instanceof Descriptor)
                descriptor = (Descriptor) ref;
        }

        return descriptor != null ? descriptor.getId() : -1;
    }

    @Override
    protected final int getCustomDebugInfo2() {
        if (!Debug.COMMUNICATIONS)
            throw new IllegalStateException();

        return _stack.size();
    }

    protected String getCommandString(byte command) {
        return DistributedWriter.getCommandStringStatic(command);
    }
}
