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

package of4gwt.misc;

/**
 * Manipulates bits in arrays of integers. Bits are stored in integers because 32 bits
 * JVMs do not guarantee longs are read or written atomically.
 */
public class Bits {

    public static final int BITS_PER_UNIT_SHIFT = 5; // 2 ^ 5 == 32

    public static final int BITS_PER_UNIT = 1 << BITS_PER_UNIT_SHIFT;

    public static final int BIT_INDEX_MASK = BITS_PER_UNIT - 1;

    public static final int SPARSE_BITSET_DEFAULT_CAPACITY = 2;

    /**
     * For sparse arrays. Cannot use two arrays as elements must be written atomically
     * during object version merges.
     */
    public static final class Entry {

        public int IntIndex;

        public int Value;

        public Entry(int intIndex, int value) {
            IntIndex = intIndex;
            Value = value;
        }
    }

    protected Bits() {
    }

    public static int arrayLength(int size) {
        if (Debug.ENABLED)
            Debug.assertion(size > 0);

        return intIndex(size - 1) + 1;
    }

    public static boolean get(int value, int index) {
        if (Debug.ENABLED)
            Debug.assertion(index >= 0 && index < Integer.SIZE);

        return (value & mask32(index)) != 0;
    }

    public static boolean get(Entry[] sparse, int index) {
        if (Debug.ENABLED)
            Debug.assertion(index >= 0);

        if (Debug.ENABLED)
            checkInvariants(sparse);

        if (sparse != null) {
            Entry entry = getEntry(sparse, intIndex(index));

            if (entry != null)
                return (entry.Value & maskArray(index)) != 0;
        }

        return false;
    }

    private static Entry getEntry(Entry[] sparse, int intIndex) {
        int foldedIntIndex = intIndex & (sparse.length - 1);

        for (int i = SparseArrayHelper.attemptsStart(sparse.length); i >= 0; i--) {
            Entry current = sparse[foldedIntIndex];

            if (current != null && current.IntIndex == intIndex)
                return current;

            foldedIntIndex = (foldedIntIndex + 1) & (sparse.length - 1);
        }

        return null;
    }

    public static int getFoldedIntIndexFromIndex(Entry[] sparse, int index) {
        int intIndex = intIndex(index);
        return getFoldedIntIndexFromIntIndex(sparse, intIndex);
    }

    public static int getFoldedIntIndexFromIntIndex(Entry[] sparse, int intIndex) {
        if (Debug.ENABLED)
            checkInvariants(sparse);

        int foldedIntIndex = intIndex & (sparse.length - 1);

        for (int i = SparseArrayHelper.attemptsStart(sparse.length); i >= 0; i--) {
            Entry current = sparse[foldedIntIndex];

            if (current != null && current.IntIndex == intIndex)
                return foldedIntIndex;

            foldedIntIndex = (foldedIntIndex + 1) & (sparse.length - 1);
        }

        return -1;
    }

    //

    public static int set(int set, int index) {
        if (Debug.ENABLED)
            Debug.assertion(index >= 0 && index < Integer.SIZE);

        return set | mask32(index);
    }

    public static int set(int set, int index, boolean value) {
        if (Debug.ENABLED)
            Debug.assertion(index >= 0 && index < Integer.SIZE);

        int mask = mask32(index);
        return value ? set | mask : set & ~mask;
    }

    public static final Entry[] set(Entry[] sparse, int index) {
        if (sparse == null)
            sparse = PlatformAdapter.createBitsArray(Bits.SPARSE_BITSET_DEFAULT_CAPACITY);

        while (!tryToSet(sparse, index))
            sparse = reindex(sparse);

        return sparse;
    }

    public static boolean tryToSet(Entry[] sparse, int index) {
        if (Debug.ENABLED)
            Debug.assertion(index >= 0);

        if (Debug.ENABLED)
            checkInvariants(sparse);

        int intIndex = intIndex(index);
        int foldedIntIndex = intIndex & (sparse.length - 1);

        for (int i = SparseArrayHelper.attemptsStart(sparse.length); i >= 0; i--) {
            Entry current = sparse[foldedIntIndex];

            if (current != null && current.IntIndex == intIndex) {
                current.Value |= maskArray(index);
                return true;
            }

            if (current == null) {
                sparse[foldedIntIndex] = PlatformAdapter.createBitsEntry(intIndex, maskArray(index));
                return true;
            }

            foldedIntIndex = (foldedIntIndex + 1) & (sparse.length - 1);
        }

        return false;
    }

    private static final Entry[] reindex(Entry[] sparse) {
        Entry[] old = sparse;

        for (;;) {
            sparse = PlatformAdapter.createBitsArray(sparse.length << SparseArrayHelper.TIMES_TWO_SHIFT);

            if (reindex(old, sparse))
                break;
        }

        return sparse;
    }

    public static boolean reindex(Entry[] oldSparse, Entry[] newSparse) {
        if (Debug.ENABLED) {
            checkInvariants(oldSparse);
            checkInvariants(newSparse);
        }

        for (int i = oldSparse.length - 1; i >= 0; i--)
            if (oldSparse[i] != null)
                if (add(newSparse, oldSparse[i]) < 0)
                    return false;

        return true;
    }

    //

    public static int add(Entry[] sparse, Entry entry) {
        int foldedIntIndex = entry.IntIndex & (sparse.length - 1);

        for (int i = SparseArrayHelper.attemptsStart(sparse.length); i >= 0; i--) {
            Entry current = sparse[foldedIntIndex];

            if (Debug.ENABLED)
                if (current != null)
                    Debug.assertion(current.IntIndex != entry.IntIndex);

            if (current == null) {
                sparse[foldedIntIndex] = entry;
                return foldedIntIndex;
            }

            foldedIntIndex = (foldedIntIndex + 1) & (sparse.length - 1);
        }

        return -1;
    }

    //

    public static int unset(int set, int index) {
        if (Debug.ENABLED)
            Debug.assertion(index >= 0 && index < Integer.SIZE);

        return set & ~mask32(index);
    }

    public static void unset(Entry[] sparse, int index) {
        if (Debug.ENABLED)
            Debug.assertion(index >= 0);

        if (Debug.ENABLED)
            checkInvariants(sparse);

        int intIndex = intIndex(index);
        int foldedIntIndex = intIndex & (sparse.length - 1);

        for (int i = SparseArrayHelper.attemptsStart(sparse.length); i >= 0; i--) {
            Entry current = sparse[foldedIntIndex];

            if (current != null && current.IntIndex == intIndex) {
                current.Value &= ~maskArray(index);
                break;
            }

            if (current == null)
                break;

            foldedIntIndex = (foldedIntIndex + 1) & (sparse.length - 1);
        }
    }

    //

    public static boolean intersects(int a, int b) {
        return (a & b) != 0;
    }

    public static boolean intersects(Entry[] a, Entry[] b) {
        for (int i = a.length - 1; i >= 0; i--) {
            if (a[i] != null) {
                Entry current = getEntry(b, a[i].IntIndex);

                if (current != null)
                    if ((a[i].Value & current.Value) != 0)
                        return true;
            }
        }

        return false;
    }

    //

    public static int merge(int value, int source) {
        return value | source;
    }

    public static boolean mergeInPlace(Entry[] sparse, Entry[] source) {
        if (Debug.ENABLED) {
            checkInvariants(sparse);
            checkInvariants(source);
        }

        for (int i = source.length - 1; i >= 0; i--)
            if (source[i] != null && source[i].Value != 0)
                if (!mergeInPlace(sparse, source[i]))
                    return false;

        return true;
    }

    private static boolean mergeInPlace(Entry[] sparse, Entry entry) {
        int foldedIntIndex = entry.IntIndex & (sparse.length - 1);

        for (int i = SparseArrayHelper.attemptsStart(sparse.length); i >= 0; i--) {
            Entry current = sparse[foldedIntIndex];

            if (current != null && current.IntIndex == entry.IntIndex) {
                current.Value |= entry.Value;
                return true;
            }

            if (current == null) {
                sparse[foldedIntIndex] = entry;
                return true;
            }

            foldedIntIndex = (foldedIntIndex + 1) & (sparse.length - 1);
        }

        return false;
    }

    public static boolean mergeByCopy(Entry[] sparse, Entry[] source) {
        if (Debug.ENABLED) {
            checkInvariants(sparse);
            checkInvariants(source);
        }

        for (int i = source.length - 1; i >= 0; i--)
            if (source[i] != null && source[i].Value != 0)
                if (!mergeByCopy(sparse, source[i]))
                    return false;

        return true;
    }

    private static boolean mergeByCopy(Entry[] sparse, Entry entry) {
        int foldedIntIndex = entry.IntIndex & (sparse.length - 1);

        for (int i = SparseArrayHelper.attemptsStart(sparse.length); i >= 0; i--) {
            Entry current = sparse[foldedIntIndex];

            if (current != null && current.IntIndex == entry.IntIndex) {
                sparse[foldedIntIndex] = PlatformAdapter.createBitsEntry(current.IntIndex, current.Value | entry.Value);
                return true;
            }

            if (current == null) {
                sparse[foldedIntIndex] = entry;
                return true;
            }

            foldedIntIndex = (foldedIntIndex + 1) & (sparse.length - 1);
        }

        return false;
    }

    //

    public static int and(int a, int b) {
        return a & b;
    }

    public static Entry[] and(Entry[] a, Entry[] b) {
        if (Debug.ENABLED) {
            checkInvariants(a);
            checkInvariants(b);
        }

        if (Debug.ENABLED) { // Should not call for nothing
            boolean empty = true;

            for (int i = b.length - 1; i >= 0; i--) {
                if (b[i] != null) {
                    Debug.assertion(b[i].Value != 0);
                    empty = false;
                }
            }

            Debug.assertion(!empty);
        }

        Entry[] result = PlatformAdapter.createBitsArray(a.length);

        for (int i = a.length - 1; i >= 0; i--)
            if (a[i] != null)
                result[i] = and(a[i], b);

        return result;
    }

    private static Entry and(Entry entry, Entry[] mask) {
        int foldedIntIndex = entry.IntIndex & (mask.length - 1);

        for (int i = SparseArrayHelper.attemptsStart(mask.length); i >= 0; i--) {
            Entry current = mask[foldedIntIndex];

            if (current != null && current.IntIndex == entry.IntIndex)
                return PlatformAdapter.createBitsEntry(entry.IntIndex, entry.Value & current.Value);

            if (current == null)
                return entry;

            foldedIntIndex = (foldedIntIndex + 1) & (mask.length - 1);
        }

        return entry;
    }

    //

    public static int andNot(int a, int b) {
        return a & ~b;
    }

    public static Entry[] andNot(Entry[] a, Entry[] b) {
        if (Debug.ENABLED) {
            checkInvariants(a);
            checkInvariants(b);
        }

        if (Debug.ENABLED) { // Should not call for nothing
            boolean empty = true;

            for (int i = b.length - 1; i >= 0; i--) {
                if (b[i] != null) {
                    Debug.assertion(b[i].Value != 0);
                    empty = false;
                }
            }

            Debug.assertion(!empty);
        }

        Entry[] result = PlatformAdapter.createBitsArray(a.length);

        for (int i = a.length - 1; i >= 0; i--)
            if (a[i] != null)
                result[i] = andNot(a[i], b);

        return result;
    }

    private static Entry andNot(Entry entry, Entry[] mask) {
        int foldedIntIndex = entry.IntIndex & (mask.length - 1);

        for (int i = SparseArrayHelper.attemptsStart(mask.length); i >= 0; i--) {
            Entry current = mask[foldedIntIndex];

            if (current != null && current.IntIndex == entry.IntIndex)
                return PlatformAdapter.createBitsEntry(entry.IntIndex, entry.Value & ~current.Value);

            if (current == null)
                return entry;

            foldedIntIndex = (foldedIntIndex + 1) & (mask.length - 1);
        }

        return entry;
    }

    //

    public static boolean isEmpty(int value) {
        return value == 0;
    }

    public static boolean isEmpty(Entry[] sparse) {
        /*
         * TODO make sure everywhere if array is created, it is not empty, switch test to
         * != null.
         */
        if (sparse != null)
            for (int i = sparse.length - 1; i >= 0; i--)
                if (sparse[i] != null && sparse[i].Value != 0)
                    return false;

        return true;
    }

    //

    public static int remove(int set, int index) {
        int mod = index & BIT_INDEX_MASK;

        if (mod == 0) // Cannot shift by 32
            return set >>> 1;

        return (set & (-1 >>> (32 - mod))) | ((set >>> 1) & (-1 << mod));
    }

    //

    /**
     * Given a bit index return unit index containing it.
     */
    private static int intIndex(int index) {
        return index >> BITS_PER_UNIT_SHIFT;
    }

    /**
     * Given a bit index, return a integer that masks that bit in its integer.
     */
    private static int mask32(int index) {
        if (Debug.ENABLED)
            Debug.assertion(index == (index & BIT_INDEX_MASK));

        return 1 << index;
    }

    private static int maskArray(int index) {
        return 1 << (index & BIT_INDEX_MASK);
    }

    // Debug

    private static final void checkInvariants(Entry[] sparse) {
        if (!Debug.ENABLED)
            Debug.assertAlways(false);

        if (sparse != null) {
            Debug.assertion(Utils.nextPowerOf2(sparse.length) == sparse.length);

            for (int j = sparse.length - 1; j >= 0; j--) {
                if (sparse[j] != null) {
                    if (j != (sparse[j].IntIndex & (sparse.length - 1)))
                        Debug.assertion(sparse[(j - 1) & (sparse.length - 1)] != null);

                    if (Debug.SLOW_CHECKS)
                        for (int i = sparse.length - 1; i > j; i--)
                            Debug.assertion(sparse[j] == null || sparse[i] == null || sparse[j].IntIndex != sparse[i].IntIndex);
                }
            }
        }
    }
}
