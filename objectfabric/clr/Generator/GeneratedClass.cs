/// 
/// Copyright (c) ObjectFabric Inc. All rights reserved.
///
/// This file is part of ObjectFabric (objectfabric.org).
///
/// ObjectFabric is licensed under the Apache License, Version 2.0, the terms
/// which may be found at http://www.apache.org/licenses/LICENSE-2.0.html.
///
/// This file is provided AS IS with NO WARRANTY OF ANY KIND, INCLUDING THE
/// WARRANTY OF DESIGN, MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE.
/// 

using System.Xml.Serialization;

namespace Generator
{
    [XmlType]
    public class GeneratedClass
    {
        public readonly ObjectFabric.Generator.GeneratedClassDef Value = new ObjectFabric.Generator.GeneratedClassDef();

        Field[] _fields;

        Method[] _methods;

        [XmlAttribute( "name" )]
        public string Name
        {
            get { return Value.Name; }
            set { Value.Name = value; }
        }

        [XmlAttribute( "comment" )]
        public string Comment
        {
            get { return Value.Comment; }
            set { Value.Comment = value; }
        }

        [XmlAttribute( "parent" )]
        public string Parent
        {
            get { return Value.Parent; }
            set { Value.Parent = value; }
        }

        [XmlElement( ElementName = "Field" )]
        public Field[] Fields
        {
            get { return _fields; }
            set
            {
                _fields = value;

                if( value != null )
                    foreach( Field item in value )
                        Value.Fields.add( item.Value );
            }
        }

        [XmlElement( ElementName = "Method" )]
        public Method[] Methods
        {
            get { return _methods; }
            set
            {
                _methods = value;

                if( value != null )
                    foreach( Method item in value )
                        Value.Methods.add( item.Value );
            }
        }
    }
}