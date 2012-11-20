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

namespace ObjectFabric
{
    class CustomWorkspace : Workspace
    {
        private readonly org.objectfabric.CustomLocation _store;

        public CustomWorkspace(org.objectfabric.CustomLocation store)
            : base(Granularity.COALESCE)
        {
            _store = store;
        }

        internal override org.objectfabric.Resource newResource(org.objectfabric.URI uri)
        {
            return new org.objectfabric.CustomLocation.CustomResource(this, uri, _store);
        }
    }
}