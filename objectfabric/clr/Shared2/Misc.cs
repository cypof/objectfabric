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

    class DictionaryCountListener : KeyListener
    {
        readonly object _object;
        readonly PropertyChangedEventHandler _handler;

        public DictionaryCountListener(object o, PropertyChangedEventHandler handler)
        {
            _object = o;
            _handler = handler;
        }

        //

        public void onPut(object obj)
        {
            _handler(_object, new PropertyChangedEventArgs("Count"));
        }

        public void onRemove(object obj)
        {
            _handler(_object, new PropertyChangedEventArgs("Count"));
        }

        public void onClear()
        {
            _handler(_object, new PropertyChangedEventArgs("Count"));
        }

        //

        public override bool Equals(object obj)
        {
            DictionaryCountListener other = obj as DictionaryCountListener;

            if (other != null)
                return _handler.Equals(other._handler);

            return false;
        }

        public override int GetHashCode()
        {
            return _handler.GetHashCode();
        }
    }

    class DictionaryListener : KeyListener
    {
        readonly object _object;
        readonly NotifyCollectionChangedEventHandler _handler;

        public DictionaryListener(object parent, NotifyCollectionChangedEventHandler handler)
        {
            _object = parent;
            _handler = handler;
        }

        public void onPut(object obj)
        {
            _handler(_object, new NotifyCollectionChangedEventArgs(NotifyCollectionChangedAction.Replace, obj, null));
        }

        public void onRemove(object obj)
        {
            _handler(_object, new NotifyCollectionChangedEventArgs(NotifyCollectionChangedAction.Remove, obj));
        }

        public void onClear()
        {
            _handler(_object, new NotifyCollectionChangedEventArgs(NotifyCollectionChangedAction.Reset));
        }

        //

        public override bool Equals(object obj)
        {
            DictionaryListener other = obj as DictionaryListener;

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
