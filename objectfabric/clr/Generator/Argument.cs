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
    /// <summary>
    /// Argument of a method.
    /// </summary>
    [XmlType]
    public class Argument
    {
        public readonly ObjectFabric.Generator.ArgumentDef Value = new ObjectFabric.Generator.ArgumentDef();

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
    }
}