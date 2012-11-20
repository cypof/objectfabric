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
    [XmlRoot]
    [XmlType]
    public class ObjectModel
    {
        public readonly ObjectFabric.Generator.ObjectModelDef Value = new ObjectFabric.Generator.ObjectModelDef();

        Package[] _packages;

        [XmlAttribute( "name" )]
        public string Name
        {
            get { return Value.Name; }
            set { Value.Name = value; }
        }

        [XmlElement( ElementName = "Package" )]
        public Package[] Packages
        {
            get { return _packages; }
            set
            {
                _packages = value;

                if( value != null )
                    foreach( Package item in value )
                        Value.Packages.add( item.Value );
            }
        }
    }
}