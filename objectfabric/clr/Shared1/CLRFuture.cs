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
    public class ObjectFuture : CLRFuture<object>
    {
    }

    public class CLRFuture<T> : TaskCompletionSource<T>, java.util.concurrent.Future
    {
        public object get( long timeout, java.util.concurrent.TimeUnit unit )
        {
            throw new NotImplementedException();
        }

        public virtual object get()
        {
            try
            {
                return Task.Result;
            }
            catch( Exception ex )
            {
                throw new java.util.concurrent.ExecutionException( ex );
            }
        }

        public bool isCancelled()
        {
            return Task.IsCanceled;
        }

        public bool isDone()
        {
            return Task.IsCompleted;
        }

        //

        public virtual bool cancel( bool mayInterruptIfRunning )
        {
            if( TrySetCanceled() )
            {
                done();
                return true;
            }

            return false;
        }

        public virtual void set( object value )
        {
            if( TrySetResult( (T) value ) )
                done();
        }

        public virtual void setException( java.lang.Exception ex )
        {
            if( TrySetException( ex ) )
                done();
        }

        protected virtual void done()
        {
        }
    }
}
