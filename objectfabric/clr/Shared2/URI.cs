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
    public class URI : org.objectfabric.URI
    {
        org.objectfabric.PlatformRef _ref;

        public URI(org.objectfabric.Origin origin, string path)
            : base(origin, path)
        {
        }

        internal override void onReferenced(org.objectfabric.PlatformRef @ref)
        {
            _ref = @ref;
        }

        ~URI()
        {
            if (!Environment.HasShutdownStarted)
                _ref.collected();
        }

        public Origin Origin
        {
            get { return (Origin) origin(); }
        }
    }
}
