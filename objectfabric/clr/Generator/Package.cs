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
    public class Package
    {
        public readonly ObjectFabric.Generator.PackageDef Value = new ObjectFabric.Generator.PackageDef();

        Package[] _packages;

        GeneratedClass[] _classes;

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

        [XmlElement( ElementName = "Class" )]
        public GeneratedClass[] Classes
        {
            get { return _classes; }
            set
            {
                _classes = value;

                if( value != null )
                    foreach( GeneratedClass item in value )
                        Value.Classes.add( item.Value );
            }
        }
    }
}