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
    public abstract class ClientURIHandler : org.objectfabric.ClientURIHandler
    {
        protected abstract Remote CreateRemote(org.objectfabric.Address address);

        internal override org.objectfabric.Remote createRemote(org.objectfabric.Address address)
        {
            return CreateRemote(address);
        }
    }
}
