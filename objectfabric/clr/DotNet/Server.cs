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

namespace ObjectFabric
{
    class Server : IDisposable
    {
        static Server()
        {
          //  CLRPlatform.LoadClass();
        }

        //    readonly IPAddress _host;
        //    readonly int _port;
        //    readonly Socket _listener;
        //    readonly ReadOnlyCollection<Sockettnnection> _sessions;

        //    public Server( TObject share, IPAddress host, int port )
        //    {
        //        _share = share;
        //        _host = host;
        //        _port = port;

        //        _listener = new Socket( AddressFamily.InterNetwork, SocketType.Stream, ProtocolType.Tcp );
        //        _sessions = new ReadOnlyCollection<SocketConnection>( getSessions() );
        //    }

        //    public TObject Share
        //    {
        //        get { return _share; }
        //    }

        //    public Socket Socket
        //    {
        //        get { return _listener; }
        //    }

        //    public ReadOnlyCollection<SocketConnection> Sessions
        //    {
        //        get { return _sessions; }
        //    }

        //    public void Start()
        //    {
        //        _listener.Bind( new IPEndPoint( _host, _port ) );
        //        _listener.Listen( 100 );
        //        _listener.BeginAccept( new System.AsyncCallback( Accept ), null );
        //    }

        public void Dispose()
        {
            //        _listener.Close();

            //        Transaction.Run( delegate
            //        {
            //            foreach( Connection connection in _sessions )
            //                connection.close();
            //        } );

            //        // Sessions clean themselves

            //        while( getSessions().size() > 0 )
            //            Thread.Sleep( 1 );
        }

        //    private void Accept( IAsyncResult result )
        //    {
        //        if( Debug.ENABLED )
        //            Debug.assertion( Transaction.Current == null );

        //        Socket socket = _listener.EndAccept( result );
        //        _listener.BeginAccept( new System.AsyncCallback( Accept ), null );

        //        Session session = new Session( socket, this );
        //        session.Start();
        //    }

        //    private class Session : SocketConnection
        //    {
        //        Server _server;

        //        public Session( Socket socket, Server server )
        //            : base( Site.Local, socket )
        //        {
        //            _server = server;
        //        }

        //        protected internal override org.objectfabric.TObject getShare()
        //        {
        //            return (org.objectfabric.TObject) _server.Share;
        //        }

        //        protected internal override void onConnection( org.objectfabric.Transaction branch, org.objectfabric.TObject share )
        //        {
        //            if( Debug.ENABLED )
        //                Debug.assertion( Transaction.Current == null && share == null );

        //            if( branch == Origin.Trunk )
        //                ((SessionSet) _server.getSessions()).addSession( this );
        //        }

        //        protected internal override void onWriteStopped( Exception t )
        //        {
        //            base.onWriteStopped( t );

        //            ((SessionSet) _server.getSessions()).removeSession( this );
        //        }
        //    }
    }
}
