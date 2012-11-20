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
using System.Threading;
using System.Threading.Tasks;

namespace ObjectFabric
{
    public class Workspace : org.objectfabric.Workspace, IDisposable
    {
        static Workspace()
        {
            CLRPlatform.LoadClass();
        }

        public Workspace()
            : base(org.objectfabric.Workspace.Granularity.COALESCE)
        {
        }

        internal Workspace(org.objectfabric.Workspace.Granularity granularity)
            : base(granularity)
        {
        }

        public void Dispose()
        {
            close();
        }

        public void AddURIHandler(org.objectfabric.URIHandler handler)
        {
            addURIHandler(handler);
        }

        public Resource Resolve(string uri)
        {
            return (Resource) resolve(uri);
        }

        //

        public void Flush()
        {
            flush();
        }

        public Task FlushAsync()
        {
            ObjectFuture future = (ObjectFuture) flushAsync(org.objectfabric.FutureWithCallback.NOP_CALLBACK);
            return future.Task;
        }

        //

        public void Atomic(Action code)
        {
            atomic((java.lang.Runnable) (object) (ikvm.runtime.Delegates.RunnableDelegate) delegate { code(); });
        }

        public void AtomicRead(Action code)
        {
            atomicRead((java.lang.Runnable) (object) (ikvm.runtime.Delegates.RunnableDelegate) delegate { code(); });
        }

        public void AtomicWrite(Action code)
        {
            atomicWrite((java.lang.Runnable) (object) (ikvm.runtime.Delegates.RunnableDelegate) delegate { code(); });
        }

        //

        protected internal override java.util.concurrent.Executor createCallbackExecutor()
        {
            if (SynchronizationContext.Current != null)
                return TaskSchedulerWrapper.GetExecutor(TaskScheduler.FromCurrentSynchronizationContext());

            return TPLExecutor.Instance;
        }

        internal override org.objectfabric.Resource newResource(org.objectfabric.URI uri)
        {
            return new Resource(this, uri);
        }

        //

        // TODO public?
        void FlushNotifications()
        {
            flushNotifications();
        }
    }
}
