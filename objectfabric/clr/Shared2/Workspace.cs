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

        public Resource Open(string uri)
        {
            return OpenAsync(uri).Result;
        }

        public Task<Resource> OpenAsync(string uri)
        {
            TaskCompletionSource<Resource> future = new TaskCompletionSource<Resource>();
            openAsync(uri, new ResourceCallback(future));
            return future.Task;
        }

        private class ResourceCallback : org.objectfabric.AsyncCallback
        {
            readonly TaskCompletionSource<Resource> _source;

            public ResourceCallback(TaskCompletionSource<Resource> source)
            {
                _source = source;
            }

            public void onSuccess(object obj)
            {
                _source.SetResult((Resource) obj);
            }

            public void onFailure(java.lang.Exception e)
            {
                _source.SetException(e);
            }
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
