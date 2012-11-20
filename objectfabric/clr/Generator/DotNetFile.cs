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

using System.IO;

namespace ObjectFabric
{
    public class DotNetFile
    {
        protected readonly FileStream _file;

        protected DotNetFile( string path )
        {
            _file = File.Open( path, FileMode.OpenOrCreate, FileAccess.ReadWrite );
        }

        public static void DeleteFiles( string folder, string extension )
        {
            DeleteFiles( new DirectoryInfo( folder ), extension );
        }

        static void DeleteFiles( DirectoryInfo folder, string extension )
        {
            foreach( FileSystemInfo entry in folder.EnumerateFileSystemInfos() )
            {
                FileInfo file = entry as FileInfo;

                if( file != null && file.Extension == extension )
                    file.Delete();

                DirectoryInfo directory = entry as DirectoryInfo;

                if( directory != null )
                    DeleteFiles( directory, extension );
            }
        }
    }
}
