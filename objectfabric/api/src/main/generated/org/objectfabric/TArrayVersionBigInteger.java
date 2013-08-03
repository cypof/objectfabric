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

package org.objectfabric;

//==============================================================================
//                                                                              
//  THIS FILE HAS BEEN GENERATED BY OBJECTFABRIC                                        
//                                                                              
//==============================================================================

class TArrayVersionBigInteger extends TIndexed.VersionN {

    // .NETstatic readonly bool IS_TOBJECT = ObjectFabric.TType.IsTObject(typeof(T));

    private java.math.BigInteger[][] _values;

    TArrayVersionBigInteger(int length) {
        super(length);

        if (length > 0) {
            // Preallocate (C.f. TIndexedNVersion._writes)
            int arrayLength = getBits().length;
            _values = new java.math.BigInteger[arrayLength][];
        }
    }

    final java.math.BigInteger[][] getValues() {
        return _values;
    }

    final void setValues(java.math.BigInteger[][] value) {
        _values = value;
    }

    public final java.math.BigInteger get(int index) {
        if (_values != null) {
            int foldedIndex = org.objectfabric.Bits.getFoldedIntIndexFromIndex(getBits(), index);

            if (foldedIndex >= 0) {
                java.math.BigInteger[] current = _values[foldedIndex];

                if (current != null)
                    return current[index & org.objectfabric.Bits.BIT_INDEX_MASK];
            }
        }

        return null;
    }

    @Override
    public final java.lang.Object getAsObject(int index) {
        return get(index);
    }

    public final void set(int index, java.math.BigInteger value) {
        if (_values == null) {
            if (value != null) {
                int arrayLength = getBits().length;
                _values = new java.math.BigInteger[arrayLength][];
            }
        } else {
            if (org.objectfabric.Debug.ENABLED)
                org.objectfabric.Debug.assertion(_values.length == getBits().length);
        }

        if (_values != null) {
            int folded = org.objectfabric.Bits.getFoldedIntIndexFromIndex(getBits(), index);

            if (_values[folded] == null) {
                if (value != null) {
                    int arrayLength = org.objectfabric.Bits.BITS_PER_UNIT;
                    _values[folded] = new java.math.BigInteger[arrayLength];
                }
            }

            if (_values[folded] != null)
                _values[folded][index & org.objectfabric.Bits.BIT_INDEX_MASK] = value;
        }
    }

    @SuppressWarnings("cast")
    @Override
    public final void setAsObject(int index, java.lang.Object value) {
        set(index, (java.math.BigInteger) value);
    }

    @Override
    final void reindexed(org.objectfabric.Bits.Entry[] old) {
        if (_values != null) {
            java.math.BigInteger[][] oldValues = _values;
            int arrayLength = getBits().length;
            _values = new java.math.BigInteger[arrayLength][];

            for (int i = old.length - 1; i >= 0; i--) {
                if (old[i] != null) {
                    int intIndex = old[i].IntIndex;
                    int folded = org.objectfabric.Bits.getFoldedIntIndexFromIntIndex(getBits(), intIndex);

                    if (org.objectfabric.Debug.ENABLED)
                        org.objectfabric.Debug.assertion(_values[folded] == null);

                    _values[folded] = oldValues[i];
                }
            }
        }
    }

    @Override
    TObject.Version merge(TObject.Version target, TObject.Version next, boolean threadPrivate) {
        TArrayVersionBigInteger source = (TArrayVersionBigInteger) next;
        TArrayVersionBigInteger merged = (TArrayVersionBigInteger) super.merge(target, next, threadPrivate);
        merged.merge(source, false);
        return merged;
    }

    @Override
    void deepCopy(TObject.Version source_) {
        TArrayVersionBigInteger source = (TArrayVersionBigInteger) source_;
        super.deepCopy(source);
        merge(source, true);
    }

    // TODO simplify
    @SuppressWarnings("cast")
    private final void merge(TArrayVersionBigInteger source, boolean deep) {
        boolean skip1 = _values == null;

        if (skip1)
            if (getBits() != null && source.getBits() != null)
                if (getBits().length != source.getBits().length)
                    skip1 = false;

        if (skip1) {
            if (!source.bitsEmpty() && source._values != null) {
                if (deep) {
                    int arrayLength = source._values.length;
                    _values = new java.math.BigInteger[arrayLength][];

                    for (int i = _values.length - 1; i >= 0; i--) {
                        if (source._values[i] != null) {
                            arrayLength = org.objectfabric.Bits.BITS_PER_UNIT;
                            _values[i] = new java.math.BigInteger[arrayLength];
                            Platform.arraycopy(source._values[i], 0, _values[i], 0, _values[i].length);
                        }
                    }
                } else
                    _values = source._values;
            }
        } else {
            org.objectfabric.Bits.Entry[] writes = source.getBits();

            if (writes != null) {
                for (int i = writes.length - 1; i >= 0; i--) {
                    if (writes[i] != null && writes[i].Value != 0) {
                        if (_values == null) {
                            int arrayLength = getBits().length;
                            _values = new java.math.BigInteger[arrayLength][];
                        }

                        int folded = org.objectfabric.Bits.getFoldedIntIndexFromIntIndex(getBits(), writes[i].IntIndex);
                        boolean skip2 = false;

                        if (!deep) {
                            skip2 = _values[folded] == null;

                            if (!skip2) // All overwritten
                                skip2 = writes[i].Value == -1 && source._values != null && source._values[i] != null;
                        } else if (_values[folded] == null) {
                            int arrayLength = org.objectfabric.Bits.BITS_PER_UNIT;
                            _values[folded] = new java.math.BigInteger[arrayLength];
                        }

                        if (skip2)
                            _values[folded] = source._values != null ? source._values[i] : null;
                        else
                            merge(_values[folded], writes[i], source._values != null ? source._values[i] : null);
                    }
                }
            }
        }

        if (org.objectfabric.Debug.ENABLED)
            checkInvariants();
    }

    private static void merge(java.math.BigInteger[] merged, org.objectfabric.Bits.Entry writes, java.math.BigInteger[] source) {
        for (int i = org.objectfabric.Bits.BITS_PER_UNIT - 1; i >= 0; i--)
            if (org.objectfabric.Bits.get(writes.Value, i))
                merged[i] = source != null ? source[i] : null;
    }

    //

    @Override
    public final void writeWrite(Writer writer, int index) {
        if (writer.interrupted())
            writer.resume();

        writer.writeBigInteger(get(index));

        if (writer.interrupted()) {
            writer.interrupt(null);
            return;
        }
    }

    @Override
    public final void readWrite(Reader reader, int index, java.lang.Object[] versions) {
        if (reader.interrupted())
            reader.resume();

        java.math.BigInteger value = reader.readBigInteger();

        if (reader.interrupted()) {
            reader.interrupt(null);
            return;
        }

        for (int i = versions.length - 1; i >= 0; i--)
            ((TArrayVersionBigInteger) versions[i]).set(index, value);
    }

    //

    @SuppressWarnings("cast")
    @Override
    void checkInvariants_() {
        super.checkInvariants_();

        if (getBits() != null && getValues() != null)
            org.objectfabric.Debug.assertion(getValues().length == getBits().length);
    }
}
// .NET}