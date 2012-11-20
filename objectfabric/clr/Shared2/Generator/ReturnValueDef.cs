/// 
/// Copyright (c) ObjectFabric Inc. All rights reserved.
///
/// This file is part of ObjectFabric (objectfabric.org).
///
/// ObjectFabric is licensed under the Apache License, Version 2.0, the terms
/// which may be found at http://www.apache.org/licenses/LICENSE-2.0.html.
///
/// This file is provided AS IS with NO WARRANTY OF ANY KIND, INCLUDING THE
/// WARRANTY OF DESIGN, MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE.
/// 

using System;

namespace ObjectFabric.Generator
{
    /// <summary>
    /// Return value of a method.
    /// </summary>
    public class ReturnValueDef : org.objectfabric.ReturnValueDef
    {
        public ReturnValueDef()
        {
        }

        public ReturnValueDef( Type type )
            : base( type )
        {
        }

        public ReturnValueDef( ClassDef classDef )
            : base( classDef )
        {
        }
    }
}