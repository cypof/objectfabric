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

/**
 * Called when a field changes on a transactional object or array.
 */
public interface FieldListener extends TObjectListener {

    /**
     * @param fieldIndex
     *            Index of the field which has changed. For an indexed collection like
     *            TArray, it is the index of the element. For a generated object, you can
     *            used the generated constants to find out which field changed. E.g.
     *            <code>if (fieldIndex == MyClass.MY_FIELD_INDEX) { ... }</code>
     */
    void onFieldChanged(int fieldIndex);
}
