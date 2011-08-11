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

import of4gwt.TObject.Reference;
import of4gwt.TObject.Version;
import of4gwt.misc.Debug;
import of4gwt.misc.SparseArrayHelper;

// TODO: replace with UserTObjectSet when hash stored in Version
final class VersionSet {

    /**
     * Replaces a removed version. Removed slots cannot be set to null due to conflicts
     * offsets.
     */
    public static final TObject.Version REMOVED;

    static {
        TObject.Version null_shared = new TObject.Version(null);
        null_shared.setUnion(new Reference(null, false), true);
        REMOVED = null_shared.createVersion();
    }

    public static boolean contains(Version[] array, Version shared) {
        return indexOf(array, shared) >= 0;
    }

    public static int indexOf(Version[] array, Version shared) {
        if (Debug.ENABLED)
            Debug.assertion(shared.isShared());

        int index = shared.getSharedHashCode_objectfabric() & (array.length - 1);

        for (int i = SparseArrayHelper.attemptsStart(array.length); i >= 0; i--) {
            if (array[index] == null)
                break;

            if (array[index] == shared)
                return index;

            index = (index + 1) & (array.length - 1);
        }

        return -1;
    }

    public static int tryToAdd(Version[] array, Version shared) {
        int hash = shared.getSharedHashCode_objectfabric();
        int result;

        if ((result = tryToAdd(array, shared, hash, false)) == SparseArrayHelper.REHASH)
            result = tryToAdd(array, shared, hash, true);

        return result;
    }

    private static int tryToAdd(Version[] array, Version shared, int hash, boolean overrideRemoved) {
        int index = hash & (array.length - 1);

        for (int i = SparseArrayHelper.attemptsStart(array.length); i >= 0; i--) {
            if (array[index] == null || (overrideRemoved && array[index] == REMOVED)) {
                array[index] = shared;

                if (Debug.ENABLED)
                    checkInvariants(array, true);

                return index;
            }

            if (array[index] == shared)
                return SparseArrayHelper.ALREADY_PRESENT;

            index = (index + 1) & (array.length - 1);
        }

        return SparseArrayHelper.REHASH;
    }

    public static boolean rehash(Version[] previous, Version[] array) {
        for (int i = previous.length - 1; i >= 0; i--) {
            if (previous[i] != null && previous[i] != REMOVED) {
                if (Debug.ENABLED)
                    Debug.assertion(!contains(array, previous[i]));

                int hash = previous[i].getSharedHashCode_objectfabric();
                int result = tryToAdd(array, previous[i], hash, false);

                if (result < 0) {
                    if (Debug.ENABLED)
                        Debug.assertion(result == SparseArrayHelper.REHASH);

                    return false;
                }
            }
        }

        if (Debug.ENABLED)
            checkInvariants(array, false);

        return true;
    }

    public static int remove(Version[] array, Version shared) {
        int hash = shared.getSharedHashCode_objectfabric();
        int index = hash & (array.length - 1);

        for (int i = SparseArrayHelper.attemptsStart(array.length); i >= 0; i--) {
            if (array[index] == shared) {
                array[index] = REMOVED;
                return index;
            }

            index = (index + 1) & (array.length - 1);
        }

        return -1;
    }

    public static void removePartOfClear(Version[] array, Version shared) {
        int hash = shared.getSharedHashCode_objectfabric();
        int index = hash & (array.length - 1);

        for (;;) {
            if (array[index] == shared) {
                array[index] = null;
                break;
            }

            index = (index + 1) & (array.length - 1);
        }
    }

    public static void checkInvariants(Version[] array, boolean allowNulls) {
        if (!Debug.ENABLED)
            throw new IllegalStateException();

        if (Debug.SLOW_CHECKS)
            for (int i = 0; i < array.length; i++)
                for (int j = 0; j < array.length; j++)
                    if (i != j)
                        Debug.assertion(array[i] == null || array[i] == REMOVED || array[i] != array[j]);

        if (!allowNulls)
            for (int i = array.length - 1; i >= 0; i--)
                Debug.assertion(array[i] != REMOVED);
    }
}
