/*
 *  Primitive Collections for Java.
 *  Copyright (C) 2003  Søren Bak
 *
 *  This library is free software; you can redistribute it and/or
 *  modify it under the terms of the GNU Lesser General Public
 *  License as published by the Free Software Foundation; either
 *  version 2.1 of the License, or (at your option) any later version.
 *
 *  This library is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 *  Lesser General Public License for more details.
 *
 *  You should have received a copy of the GNU Lesser General Public
 *  License along with this library; if not, write to the Free Software
 *  Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
 */

package com.objectfabric;

import java.util.NoSuchElementException;

/**
 * This class provides static methods for throwing exceptions. It is only provided as a
 * utility class for the collection implementations and is not a part of the API.
 * 
 * @author S&oslash;ren Bak
 * @version 1.0 21-08-2003 18:44 ********* JDBM Project Note ************* This class was
 *          extracted from the pcj project (with permission) for use in jdbm only.
 *          Modifications to original were performed by Kevin Day to make it work outside
 *          of the pcj class structure.
 */
class Exceptions {

    public static void negativeArgument(String name, Object value) throws IllegalArgumentException {
        throw new IllegalArgumentException(name + " cannot be negative: " + String.valueOf(value));
    }

    public static void endOfIterator() throws NoSuchElementException {
        throw new NoSuchElementException("Attempt to iterate past iterator's last element.");
    }

    public static void noElementToRemove() throws IllegalStateException {
        throw new IllegalStateException("Attempt to remove element from iterator that has no current element.");
    }

    public static void noElementToGet() throws IllegalStateException {
        throw new IllegalStateException("Attempt to get element from iterator that has no current element. Call next() first.");
    }
}