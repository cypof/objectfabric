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
using System.ComponentModel;
using org.objectfabric;

namespace ObjectFabric
{
    public abstract class TGenerated : org.objectfabric.TGenerated, IEnumerable<object>, INotifyPropertyChanged, TObject
    {
        protected TGenerated(Resource resource, Version shared, int length)
            : base(resource, shared, length)
        {
        }

        public Resource Resource
        {
            get { return (Resource) resource(); }
        }

        public Workspace Workspace
        {
            get { return (Workspace) workspace(); }
        }

        public int FieldCount
        {
            get { return getFieldCount(); }
        }

        public object this[int index]
        {
            get { return getField(index); }
            set { setField(index, value); }
        }

        //

        public IEnumerator<object> GetEnumerator()
        {
            for (int i = 0; i < FieldCount; i++)
                yield return this[i];
        }

        IEnumerator IEnumerable.GetEnumerator()
        {
            for (int i = 0; i < FieldCount; i++)
                yield return this[i];
        }

        //

        //protected Transaction startRead_(Transaction outer)
        //{
        //    return (Transaction) base.startRead_(outer);
        //}

        //protected void endRead_(Transaction outer, Transaction inner)
        //{
        //    base.endRead_(outer, inner);
        //}

        //protected Transaction startWrite_(Transaction inner)
        //{
        //    return (Transaction) base.startWrite_(inner);
        //}

        //protected void endWrite_(Transaction outer, Transaction inner)
        //{
        //    base.endWrite_(outer, inner);
        //}

        //protected Version getOrCreateVersion_(Transaction transaction)
        //{
        //    return (Version) base.getOrCreateVersion_(transaction);
        //}

        //protected Version getTIndexed32Version_(Transaction current, int index)
        //{
        //    return (Version) base.getVersion32_(current, index);
        //}

        //protected static TType getTType_(ObjectModel model, int classId, params TType[] genericParameters)
        //{
        //    return new TType(model, classId, genericParameters);
        //}

        //

        public event PropertyChangedEventHandler PropertyChanged
        {
            add { addListener(new Listener(this, value)); }
            remove { removeListener(new Listener(this, value)); }
        }

        protected void OnPropertyChanged(string name)
        {
            raiseListener(name);
        }

        class Listener : IndexListener
        {
            readonly TGenerated _object;
            readonly PropertyChangedEventHandler _handler;

            public Listener(TGenerated o, PropertyChangedEventHandler handler)
            {
                _object = o;
                _handler = handler;
            }

            public void onSet(int i)
            {
                _handler(_object, new PropertyChangedEventArgs(_object.getFieldName(i)));
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

        //

        //new protected abstract class Version32 : org.objectfabric.TIndexed.Version32
        //{
        //    public Version32(int length)
        //        : base(length)
        //    {
        //    }

        //    new protected bool hasBits()
        //    {
        //        return base.hasBits();
        //    }

        //    new protected bool getBit(int index)
        //    {
        //        return base.getBit(index);
        //    }

        //    new protected void setBit(int index)
        //    {
        //        base.setBit(index);
        //    }

        //    new protected static int setBit(int bits, int index)
        //    {
        //        return TIndexed32Read.setBit(bits, index);
        //    }

        //    public override string getFieldName(int index)
        //    {
        //        throw new NotImplementedException();
        //    }

        //    public override org.objectfabric.TType getFieldType(int index)
        //    {
        //        throw new NotImplementedException();
        //    }

        //    //

        //    protected bool interrupted(object visitor)
        //    {
        //        return ((org.objectfabric.Visitor) visitor).interrupted();
        //    }

        //    protected void interrupt(object visitor, object state)
        //    {
        //        ((org.objectfabric.Visitor) visitor).interrupt(state);
        //    }

        //    protected object resume(object visitor)
        //    {
        //        return ((org.objectfabric.Visitor) visitor).resume();
        //    }
        //}

        //new protected abstract class VersionN : org.objectfabric.TIndexed.VersionN
        //{
        //    public VersionN(int length)
        //        : base(length)
        //    {
        //    }

        //    new protected bool hasBits()
        //    {
        //        return base.hasBits();
        //    }

        //    new protected bool getBit(int index)
        //    {
        //        return base.getBit(index);
        //    }

        //    new protected void setBit(int index)
        //    {
        //        base.setBit(index);
        //    }

        //    new protected static int setBit(int bits, int index)
        //    {
        //        return TIndexed32Read.setBit(bits, index);
        //    }
        //}

        //protected internal class Bits : org.objectfabric.Bits
        //{
        //    new public class Entry : org.objectfabric.Bits.Entry
        //    {
        //        public Entry(int intIndex, int value)
        //            : base(intIndex, value)
        //        {
        //        }
        //    }
        //}
    }
}