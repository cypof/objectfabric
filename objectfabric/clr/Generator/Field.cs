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
    public class Field
    {
        public readonly ObjectFabric.Generator.FieldDef Value = new ObjectFabric.Generator.FieldDef();

        [XmlAttribute( "type" )]
        public string Type
        {
            get { return Value.Type; }
            set { Value.Type = value; }
        }

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

        [XmlAttribute( "readonly" )]
        public bool Readonly
        {
            get { return Value.Readonly; }
            set { Value.Readonly = value; }
        }

        [XmlAttribute( "public" )]
        public string Public
        {
            get { return Value.Public; }
            set { Value.Public = value; }
        }

        [XmlAttribute( "transient" )]
        public bool Transient
        {
            get { return Value.Transient; }
            set { Value.Transient = value; }
        }
    }
}