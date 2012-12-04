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
using System.Threading.Tasks;

namespace ObjectFabric
{
    public class Resource : org.objectfabric.Resource, TObject
    {
        org.objectfabric.PlatformRef _ref;

        internal Resource(org.objectfabric.Workspace workspace, org.objectfabric.URI uri)
            : base(workspace, uri)
        {
            if (uri == null)
            {
                if (org.objectfabric.Debug.ENABLED)
                    org.objectfabric.Debug.assertion(workspace.emptyResource() == null);

                GC.SuppressFinalize(this);
            }
        }

        internal override void onReferenced(org.objectfabric.PlatformRef @ref)
        {
            _ref = @ref;
        }

        ~Resource()
        {
            if (!Environment.HasShutdownStarted)
                _ref.collected();
        }

        public Workspace Workspace
        {
            get { return (Workspace) workspaceImpl(); }
        }

        Resource TObject.Resource
        {
            get { return this; }
        }

        public Location Origin
        {
            get { return (Location) origin(); }
        }

        public object Value
        {
            get { return get(); }
            set { set(value); }
        }

        //

        void Push(Location location)
        {
            push((org.objectfabric.Location) location);
        }

        Task PushAsync(Location location)
        {
            ObjectFuture future = (ObjectFuture) pushAsync((org.objectfabric.Location) location, null);
            return future.Task;
        }

        void Pull(Location location)
        {
            pull((org.objectfabric.Location) location);
        }

        Task PullAsync(Location location)
        {
            ObjectFuture future = (ObjectFuture) pullAsync((org.objectfabric.Location) location, null);
            return future.Task;
        }

        //

        public event EventHandler<EventArgs> Changed
        {
            add { addListener(new Listener(this, value)); }
            remove { removeListener(new Listener(this, value)); }
        }

        class Listener : org.objectfabric.ResourceListener
        {
            internal readonly Resource _resource;
            internal readonly EventHandler<EventArgs> _handler;

            public Listener(Resource resource, EventHandler<EventArgs> handler)
            {
                _resource = resource;
                _handler = handler;
            }

            public void onSet()
            {
                _handler(_resource, EventArgs.Empty);
            }

            public void onDelete()
            {
                _handler(_resource, EventArgs.Empty);
            }

            public override bool Equals(object obj)
            {
                Listener other = obj as Listener;

                if (other != null)
                    return _handler.Equals(other._handler);

                return false;
            }

            public override int GetHashCode()
            {
                return _handler.GetHashCode();
            }
        }
    }
}