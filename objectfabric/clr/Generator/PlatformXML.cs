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

using System;
using System.IO;
using Microsoft.Xml.Serialization.GeneratedAssembly;

namespace Generator
{
    public class PlatformXML : org.objectfabric.misc.PlatformXML
    {
        protected override org.objectfabric.generator.ObjectModelDef fromXMLFileDotNet( string file )
        {
            StreamReader reader = null;

            try
            {
                ObjectModelSerializer serializer = new ObjectModelSerializer();
                reader = new StreamReader( file );
                ObjectModel model = (ObjectModel) serializer.Deserialize( reader );
                return model.Value;
            }
            finally
            {
                if( reader != null )
                    reader.Close();
            }
        }

        protected override org.objectfabric.generator.ObjectModelDef fromXMLStringDotNet( string str )
        {
            throw new NotImplementedException();
        }

        protected override string toXMLStringDotNet( org.objectfabric.generator.ObjectModelDef omd, string str )
        {
            throw new NotImplementedException();
        }
    }
}