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

import of4gwt.TObject.UserTObject;
import of4gwt.misc.Debug;
import of4gwt.misc.SparseArrayHelper;

/**
 * Simple user object set implementation, not related to transactions.
 */
final class UserTObjectSet {

    /**
     * Replaces a removed version. Removed slots cannot be set to null due to conflicts
     * offsets.
     */
    public static final UserTObject REMOVED;

    static {
        REMOVED = new UserTObject();
    }

    public static boolean contains(UserTObject[] array, UserTObject object) {
        return indexOf(array, object) >= 0;
    }

    public static int indexOf(UserTObject[] array, UserTObject object) {
        int index = object.getSharedHashCode_objectfabric() & (array.length - 1);

        for (int i = SparseArrayHelper.attemptsStart(array.length); i >= 0; i--) {
            if (array[index] == null)
                break;

            if (array[index] == object)
                return index;

            index = (index + 1) & (array.length - 1);
        }

        return -1;
    }

    public static int tryToAdd(UserTObject[] array, UserTObject object) {
        int result;

        if ((result = tryToAdd(array, object, false)) == SparseArrayHelper.REHASH)
            result = tryToAdd(array, object, true);

        return result;
    }

    private static int tryToAdd(UserTObject[] array, UserTObject object, boolean overrideRemoved) {
        int index = object.getSharedHashCode_objectfabric() & (array.length - 1);

        for (int i = SparseArrayHelper.attemptsStart(array.length); i >= 0; i--) {
            if (array[index] == null || (overrideRemoved && array[index] == REMOVED)) {
                array[index] = object;

                if (Debug.ENABLED)
                    checkInvariants(array, true);

                return index;
            }

            if (array[index] == object)
                return SparseArrayHelper.ALREADY_PRESENT;

            index = (index + 1) & (array.length - 1);
        }

        return SparseArrayHelper.REHASH;
    }

    public static boolean rehash(UserTObject[] previous, UserTObject[] array) {
        for (int i = previous.length - 1; i >= 0; i--) {
            if (previous[i] != null && previous[i] != REMOVED) {
                if (Debug.ENABLED)
                    Debug.assertion(!contains(array, previous[i]));

                int result = tryToAdd(array, previous[i], false);

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

    public static boolean remove(UserTObject[] array, UserTObject object) {
        int hash = object.getSharedHashCode_objectfabric();
        int index = hash & (array.length - 1);

        for (int i = SparseArrayHelper.attemptsStart(array.length); i >= 0; i--) {
            if (array[index] == object) {
                array[index] = REMOVED;
                return true;
            }

            index = (index + 1) & (array.length - 1);
        }

        return false;
    }

    public static int removePartOfClear(UserTObject[] array, UserTObject object) {
        int hash = object.getSharedHashCode_objectfabric();
        int index = hash & (array.length - 1);

        for (;;) {
            if (array[index] == object) {
                array[index] = null;
                return index;
            }

            index = (index + 1) & (array.length - 1);
        }
    }

    public static void checkInvariants(UserTObject[] array, boolean allowNulls) {
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
