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
    public abstract class ObjectModel : org.objectfabric.ObjectModel
    {
        protected ObjectModel()
        {
        }

        protected static void register(ObjectModel model)
        {
            org.objectfabric.ObjectModel.register(model);
        }

        protected internal override Type getClass(int classId, org.objectfabric.TType[] genericParameters)
        {
            return getClass(classId);
        }

        protected virtual Type getClass(int classId)
        {
            throw new NotImplementedException();
        }

        protected internal override org.objectfabric.TObject createInstance(org.objectfabric.Resource resource, int classId, org.objectfabric.TType[] genericParameters)
        {
            return (org.objectfabric.TObject) createInstance((Resource) resource, classId);
        }

        protected virtual TObject createInstance(Resource resource, int classId)
        {
            throw new NotImplementedException();
        }

        protected internal override string objectFabricVersion()
        {
            throw new NotImplementedException();
        }
    }
}