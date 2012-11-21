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
using System.Collections.Specialized;
using System.ComponentModel;
using org.objectfabric;

namespace ObjectFabric
{
    public class EventArgs<T> : EventArgs
    {
        public readonly T Value;

        public EventArgs(T value)
        {
            Value = value;
        }
    }

    class Listener<T>
    {
        protected readonly T _delegate;

        public Listener(T d)
        {
            _delegate = d;
        }

        public override bool Equals(object obj)
        {
            Listener<T> other = obj as Listener<T>;

            if (other != null)
                return _delegate.Equals(other._delegate);

            return false;
        }

        public override int GetHashCode()
        {
            return _delegate.GetHashCode();
        }
    }

    class DictionaryCountListener : Listener<PropertyChangedEventHandler>, KeyListener
    {
        readonly object _object;

        public DictionaryCountListener(object o, PropertyChangedEventHandler handler)
            : base(handler)
        {
            _object = o;
        }

        public void onPut(object obj)
        {
            _delegate(_object, new PropertyChangedEventArgs("Count"));
        }

        public void onRemove(object obj)
        {
            _delegate(_object, new PropertyChangedEventArgs("Count"));
        }

        public void onClear()
        {
            _delegate(_object, new PropertyChangedEventArgs("Count"));
        }
    }

    class DictionaryListener : Listener<NotifyCollectionChangedEventHandler>, KeyListener
    {
        readonly object _object;

        public DictionaryListener(object parent, NotifyCollectionChangedEventHandler handler)
            : base(handler)
        {
            _object = parent;
        }

        public void onPut(object obj)
        {
            _delegate(_object, new NotifyCollectionChangedEventArgs(NotifyCollectionChangedAction.Replace, obj, null));
        }

        public void onRemove(object obj)
        {
            _delegate(_object, new NotifyCollectionChangedEventArgs(NotifyCollectionChangedAction.Remove, obj));
        }

        public void onClear()
        {
            _delegate(_object, new NotifyCollectionChangedEventArgs(NotifyCollectionChangedAction.Reset));
        }
    }
}
