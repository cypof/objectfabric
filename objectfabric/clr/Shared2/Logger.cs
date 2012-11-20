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

namespace ObjectFabric
{
    public class Logger : org.objectfabric.Logger, IDisposable
    {
        public Logger( Workspace workspace )
            : base( workspace )
        {
        }

        public void Dispose()
        {
            close();
        }

        public void Flush()
        {
            flush();
        }
    }
}
