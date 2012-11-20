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
    /// Argument of a method.
    /// </summary>
    public class ArgumentDef : org.objectfabric.ArgumentDef
    {
        public ArgumentDef()
        {
        }

        public ArgumentDef( Type type, string name )
            : base( type, name )
        {
        }

        public ArgumentDef( ClassDef classDef, string name )
            : base( classDef, name )
        {
        }
    }
}