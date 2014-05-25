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

final class TObjectSet {

    public static boolean contains(TObject[] array, TObject object) {
        return indexOf(array, object) >= 0;
    }

    public static int indexOf(TObject[] array, TObject object) {
        int index = object.hash() & (array.length - 1);

        for (int i = OpenMap.attemptsStart(array.length); i >= 0; i--) {
            if (array[index] == null)
                break;

            if (array[index] == object)
                return index;

            index = (index + 1) & (array.length - 1);
        }

        return -1;
    }

    public static int tryToAdd(TObject[] array, TObject object) {
        int index = object.hash() & (array.length - 1);

        for (int i = OpenMap.attemptsStart(array.length); i >= 0; i--) {
            if (array[index] == null) {
                array[index] = object;

                if (Debug.ENABLED)
                    checkInvariants(array);

                return index;
            }

            if (array[index] == object)
                return -index - 1;

            index = (index + 1) & (array.length - 1);
        }

        return OpenMap.REHASH;
    }

    public static boolean rehash(TObject[] previous, TObject[] array) {
        for (int i = previous.length - 1; i >= 0; i--) {
            if (previous[i] != null) {
                if (Debug.ENABLED)
                    Debug.assertion(!contains(array, previous[i]));

                int result = tryToAdd(array, previous[i]);

                if (result == OpenMap.REHASH)
                    return false;
            }
        }

        if (Debug.ENABLED)
            checkInvariants(array);

        return true;
    }

    public static void removePartOfClear(TObject[] array, TObject object) {
        int index = object.hash() & (array.length - 1);

        for (;;) {
            if (array[index] == object) {
                array[index] = null;
                break;
            }

            index = (index + 1) & (array.length - 1);
        }

        if (Debug.ENABLED)
            checkInvariants(array);
    }

    public static void checkInvariants(TObject[] array) {
        if (!Debug.ENABLED)
            throw new IllegalStateException();

        if (Debug.SLOW_CHECKS)
            for (int i = 0; i < array.length; i++)
                for (int j = 0; j < array.length; j++)
                    Debug.assertion(i == j || array[i] == null || array[i] != array[j]);
    }
}
