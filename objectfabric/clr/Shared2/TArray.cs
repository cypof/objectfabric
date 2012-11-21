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
using System.Collections;
using System.Collections.Generic;
using System.Collections.Specialized;

namespace ObjectFabric
{
    public class TArray<T> : org.objectfabric.TIndexed, IEnumerable<T>, INotifyCollectionChanged, TObject
    {
        new public static readonly TType TYPE = new TType(DefaultObjectModel.Instance, -1, TType.From(typeof(T)));

        static readonly bool IS_TOBJECT = TType.IsTObject(typeof(T));

        static readonly bool CAN_BE_TOBJECT = TType.CanBeTObject(typeof(T));

        readonly int _length;

        TType[] _genericParameters;

        public TArray(Resource resource, int length)
            : base(resource, new org.objectfabric.TArrayVersion<T>(length))
        {
            _length = length;
            _genericParameters = new global::ObjectFabric.TType[] { global::ObjectFabric.TType.From(typeof(T)) };
        }

        internal override org.objectfabric.TType[] genericParameters()
        {
            return _genericParameters;
        }

        internal override int length()
        {
            return _length;
        }


        public Resource Resource
        {
            get { return (Resource) resource(); }
        }

        public Workspace Workspace
        {
            get { return (Workspace) workspace(); }
        }

        public int Length
        {
            get { return length(); }
        }

        public T this[int index]
        {
            get
            {
                if (index < 0 || index >= length())
                    throw new System.ArgumentOutOfRangeException();

                Transaction outer = current_();
                Transaction inner = startRead_(outer);
                org.objectfabric.TArrayVersion<T> version = (org.objectfabric.TArrayVersion<T>) getVersionN_(inner, index);
                T value = version != null ? (T) version.get(index) : default(T);
                endRead_(outer, inner);
                return value;
            }
            set
            {
                if (index < 0 || index >= length())
                    throw new System.ArgumentOutOfRangeException();

                object asObject = (object) value;

                if (IS_TOBJECT)
                {
                    if (asObject != null && ((org.objectfabric.TObject) asObject).resource() != resource())
                        wrongResource_();
                }
                else if (CAN_BE_TOBJECT)
                {
                    if (asObject is org.objectfabric.TObject && ((org.objectfabric.TObject) asObject).resource() != resource())
                        wrongResource_();
                }

                Transaction outer = current_();
                Transaction inner = startWrite_(outer);
                org.objectfabric.TArrayVersion<T> version = (org.objectfabric.TArrayVersion<T>) getOrCreateVersion_(inner);
                version.setBit(index);
                version.set(index, value);
                endWrite_(outer, inner);
            }
        }

        public IEnumerator<T> GetEnumerator()
        {
            for (int i = 0; i < Length; i++)
                yield return this[i];
        }

        IEnumerator IEnumerable.GetEnumerator()
        {
            for (int i = 0; i < Length; i++)
                yield return this[i];
        }

        protected internal override org.objectfabric.TObject.Version createVersion_()
        {
            org.objectfabric.TArrayVersion<T> version = new org.objectfabric.TArrayVersion<T>(0);
            version.setObject(this);
            return version;
        }

        protected internal override int classId_()
        {
            if (org.objectfabric.Debug.ENABLED)
                org.objectfabric.Debug.assertion(length() >= 0);

            return -length() - 1;
        }

        // Events

        public event Action<int> Set
        {
            add { addListener(new Listener(value)); }
            remove { removeListener(new Listener(value)); }
        }

        class Listener : Listener<Action<int>>, org.objectfabric.IndexListener
        {
            public Listener(Action<int> d)
                : base(d)
            {
            }

            public void onSet(int i)
            {
                _delegate(i);
            }
        }

        // INotifyCollectionChanged

        public event NotifyCollectionChangedEventHandler CollectionChanged
        {
            add { addListener(new CollectionListener(this, value)); }
            remove { removeListener(new CollectionListener(this, value)); }
        }

        class CollectionListener : Listener<NotifyCollectionChangedEventHandler>, org.objectfabric.IndexListener
        {
            readonly TArray<T> _object;

            public CollectionListener(TArray<T> o, NotifyCollectionChangedEventHandler handler)
                : base(handler)
            {
                _object = o;
            }

            public void onSet(int i)
            {
                _delegate(_object, new NotifyCollectionChangedEventArgs(NotifyCollectionChangedAction.Replace, _object[i], null, i));
            }
        }
    }
}
