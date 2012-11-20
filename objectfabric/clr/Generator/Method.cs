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
    public class Method
    {
        public readonly ObjectFabric.Generator.MethodDef Value = new ObjectFabric.Generator.MethodDef();

        Argument[] _arguments;

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

        [XmlElement( ElementName = "Argument" )]
        public Argument[] Arguments
        {
            get { return _arguments; }
            set
            {
                _arguments = value;

                if( value != null )
                    foreach( Argument item in value )
                        Value.Arguments.add( item.Value );
            }
        }

        public ReturnValue ReturnValue
        {
            set { Value.ReturnValue = value.Value; }
        }
    }
}
