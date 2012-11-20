///
/// This file is part of ObjectFabric (http://objectfabric.org).
///
/// ObjectFabric is licensed under the Apache License, Version 2.0, the terms
/// of which may be found at http://www.apache.org/licenses/LICENSE-2.0.html.
///
/// Copyright ObjectFabric Inc.
///
/// This file is provided AS IS with NO WARRANTY OF ANY KIND, INCLUDING THE
/// WARRANTY OF DESIGN, MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE.
///

using System;
using System.Collections.Generic;

namespace ObjectFabric
{
    sealed class TestPlatform : CLRPlatform
    {
        static TestPlatform()
        {
        }

        internal override void assertCurrentStack(Object previous)
        {
            if (!Debug.ENABLED)
                throw new Exception();

            List<System.Diagnostics.StackFrame> a = new List<System.Diagnostics.StackFrame>(((System.Diagnostics.StackTrace) previous).GetFrames());
            List<System.Diagnostics.StackFrame> b = new List<System.Diagnostics.StackFrame>(new System.Diagnostics.StackTrace().GetFrames());

            // Remove socket related frames, that can change between two calls

            int aCut = -1, bCut = -1;

            for (int i = 0; i < a.Count; i++)
                if (a[i].GetMethod().DeclaringType == typeof(org.objectfabric.Connection))
                    if (a[i].GetMethod().Name == "write" || a[i].GetMethod().Name == "read")
                        aCut = i;

            for (int i = 0; i < b.Count; i++)
                if (b[i].GetMethod().DeclaringType == typeof(org.objectfabric.Connection))
                    if (b[i].GetMethod().Name == "write" || b[i].GetMethod().Name == "read")
                        bCut = i;

            if (aCut >= 0)
                a.RemoveRange(aCut, a.Count - aCut);

            if (bCut >= 0)
                b.RemoveRange(bCut, b.Count - bCut);

            // Remove classes that can change between two calls

            for (int i = a.Count - 1; i >= 0; i--)
                if (a[i].GetMethod().DeclaringType.Name.Contains(".reflect."))
                    a.RemoveAt(i);

            for (int i = b.Count - 1; i >= 0; i--)
                if (b[i].GetMethod().DeclaringType.Name.Contains(".reflect."))
                    b.RemoveAt(i);

            // Remove methods added by IKVM

            for (int i = a.Count - 1; i >= 0; i--)
                if (a[i].GetMethod().GetCustomAttributes(typeof(HideFromReflectionAttribute), false).Length > 0)
                    a.RemoveAt(i);

            for (int i = b.Count - 1; i >= 0; i--)
                if (b[i].GetMethod().GetCustomAttributes(typeof(HideFromReflectionAttribute), false).Length > 0)
                    b.RemoveAt(i);

            Debug.assertion(a.Count == b.Count);

            for (int i = 0; i < a.Count; i++)
            {
                Debug.assertion(a[i].GetMethod().DeclaringType == b[i].GetMethod().DeclaringType);

                if (i < 3)
                {
                    Debug.assertion(a[i].GetMethod().Name != b[i].GetMethod().Name);
                }
                else if (i == 3)
                {
                    Debug.assertion(a[i].GetMethod().Name == b[i].GetMethod().Name);
                    Debug.assertion(a[i].GetILOffset() != b[i].GetILOffset());
                }
                else if (i > 3)
                {
                    Debug.assertion(a[i].GetMethod().Name == b[i].GetMethod().Name);
                    Debug.assertion(a[i].GetILOffset() == b[i].GetILOffset());
                }
            }
        }
    }
}
