/**
 * This file is part of ObjectFabric (http://objectfabric.org).
 *
 * ObjectFabric is licensed under the Apache License, Version 2.0, the terms
 * of which may be found at http://www.apache.org/licenses/LICENSE-2.0.html.
 * 
 * Copyright ObjectFabric Inc.
 * 
 * This file is provided AS IS with NO WARRANTY OF ANY KIND, INCLUDING THE
 * WARRANTY OF DESIGN, MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE.
 */

package java.util.concurrent;

public abstract class FutureTask<V> implements Runnable, Future<V> {

    private V _value;

    private Throwable _throwable;

    private boolean _done;

    public FutureTask(Callable<V> callable) {
        if (callable == null)
            throw new NullPointerException();
    }

    public V get() throws InterruptedException, ExecutionException {
        if (_done) {
            if (_throwable != null)
                throw new ExecutionException(_throwable);

            return _value;
        }

        throw new IllegalStateException("GWT applications do not allow blocking, make sure to call asynchronous methods.");
    }

    @SuppressWarnings("unused")
    public V get(long timeout, TimeUnit unit) throws ExecutionException {
        throw new UnsupportedOperationException();
    }

    public boolean isDone() {
        return _done;
    }

    protected void done() {
    }

    protected void set(V value) {
        if (!_done) {
            _value = value;
            _done = true;
            done();
        }
    }

    protected void setException(Throwable t) {
        if (t == null)
            throw new IllegalArgumentException();

        if (!_done) {
            _throwable = t;
            _done = true;
            done();
        }
    }

    public boolean isCancelled() {
        throw new UnsupportedOperationException();
    }

    public boolean cancel(boolean mayInterruptIfRunning) {
        return false;
    }

    public void run() {
        throw new UnsupportedOperationException();
    }
}
