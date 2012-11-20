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
using System.Collections.Generic;
using System.Threading;
using System.Threading.Tasks;

namespace ObjectFabric
{
    public class TaskSchedulerWrapper : TaskScheduler, java.util.concurrent.Executor
    {
        readonly TaskScheduler _scheduler;

        private TaskSchedulerWrapper( TaskScheduler scheduler )
        {
            _scheduler = scheduler;
        }

        public static java.util.concurrent.Executor GetExecutor( TaskScheduler scheduler )
        {
            if( scheduler is java.util.concurrent.Executor )
                return (java.util.concurrent.Executor) scheduler;

            if( scheduler == TaskScheduler.Default )
                return TPLExecutor.Instance;

            return new TaskSchedulerWrapper( scheduler );
        }

        public void execute( java.lang.Runnable runnable )
        {
            Task.Factory.StartNew( runnable.run, CancellationToken.None, TaskCreationOptions.None, _scheduler );
        }

        protected override IEnumerable<Task> GetScheduledTasks()
        {
            throw new NotImplementedException();
        }

        protected override void QueueTask( Task task )
        {
            throw new NotImplementedException();
        }

        protected override bool TryExecuteTaskInline( Task task, bool taskWasPreviouslyQueued )
        {
            throw new NotImplementedException();
        }
    }

    public class TPLExecutor : TaskScheduler, java.util.concurrent.Executor
    {
        public static readonly TPLExecutor Instance = new TPLExecutor();

        private TPLExecutor()
        {
        }

        public void execute( java.lang.Runnable runnable )
        {
            Task.Factory.StartNew( runnable.run, CancellationToken.None, TaskCreationOptions.None, TaskScheduler.Default );
        }

        protected override IEnumerable<Task> GetScheduledTasks()
        {
            throw new NotImplementedException();
        }

        protected override void QueueTask( Task task )
        {
            throw new NotImplementedException();
        }

        protected override bool TryExecuteTaskInline( Task task, bool taskWasPreviouslyQueued )
        {
            throw new NotImplementedException();
        }
    }
}

