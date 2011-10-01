/**
 * Copyright (c) ObjectFabric Inc. All rights reserved.
 *
 * This file is part of ObjectFabric (objectfabric.com).
 *
 * ObjectFabric is licensed under the Apache License, Version 2.0, the terms
 * of which may be found at http://www.apache.org/licenses/LICENSE-2.0.html.
 *
 * This file is provided AS IS with NO WARRANTY OF ANY KIND, INCLUDING THE
 * WARRANTY OF DESIGN, MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE.
 */

package chess.client;

import of4gwt.FieldListener;
import of4gwt.TArrayDouble;
import of4gwt.TMap;
import of4gwt.Transaction;
import of4gwt.misc.Log;
import of4gwt.misc.PlatformAdapter;
import of4gwt.transports.Client.Callback;
import of4gwt.transports.http.HTTPClient;

import com.google.gwt.core.client.EntryPoint;
import com.google.gwt.event.logical.shared.ValueChangeEvent;
import com.google.gwt.event.logical.shared.ValueChangeHandler;
import com.google.gwt.user.client.History;
import com.google.gwt.user.client.ui.HTML;
import com.google.gwt.user.client.ui.RootPanel;

/**
 * This client is designed to connect to the Java version of the Images demo. First launch
 * /objectfabric.examples/src/part09/chess/Chess.java, then this Web application.
 */
public class Main implements EntryPoint, ValueChangeHandler<String> {

    // private static LazyMap<String, TArrayDouble> _games;

    private static TMap<String, TArrayDouble> _games;

    private static TArrayDouble _game;

    private static FieldListener _listener;

    public void onModuleLoad() {
        /*
         * Redirect log to web page.
         */
        Log.add(new Log() {

            @Override
            protected void onWrite(String message) {
                RootPanel.get("log").add(new HTML(message));
            }
        });

        HTTPClient client = new HTTPClient("http://localhost:8080");

        client.setCallback(new Callback() {

            @SuppressWarnings("unchecked")
            public void onReceived(Object object) {
                _games = (TMap<String, TArrayDouble>) object;

                /*
                 * Make servers's trunk the default so we do not need to pass it as
                 * argument every time we create and object or start a transaction.
                 */
                Transaction.setDefaultTrunk(_games.getTrunk());

                String gameId = History.getToken();
                start(gameId);
            }

            public void onDisconnected(Throwable t) {
            }
        });

        client.connectAsync();
        initMethods();
    }

    // History
    public void onValueChange(ValueChangeEvent<String> event) {
        String gameId = event.getValue();
        start(gameId);
    }

    private void start(String gameId) {
        Log.write("start(" + gameId + ")");

        if (_game != null)
            removeListener();

        if (gameId == null || gameId.length() == 0) {
            gameId = Base32.encode(PlatformAdapter.createUID());
            _game = new TArrayDouble(32);
            addListener();
            _games.put(gameId, _game);
            History.newItem(gameId);
        } else {
            _game = _games.get(gameId);
            Log.write("get: " + _game + "");
            addListener();

            // _games.getAsync(gameId, new AsyncCallback<TArrayDouble>() {
            //
            // public void onSuccess(TArrayDouble result) {
            // Log.write("onSuccess(" + result + ")");
            //
            // if (result != null) {
            // _game = result;
            // addListener();
            // }
            // }
            //
            // public void onFailure(Throwable caught) {
            // Log.write("onFailure(" + PlatformAdapter.getStackAsString(caught) + ")");
            // }
            // });
        }
    }

    private void addListener() {
        Log.write("addListener()");

        _listener = new FieldListener() {

            public void onFieldChanged(int fieldIndex) {
                int piece = fieldIndex / 2;
                double x = _game.get(piece * 2);
                double y = _game.get(piece * 2 + 1);

                Log.write("onMove(" + piece + ", " + x + ", " + y + ")");
                onMove(piece, x, y);
            }
        };

        _game.addListener(_listener);
    }

    private void removeListener() {
        Log.write("removeListener()");

        _game.removeListener(_listener);
    }

    public static native void initMethods()/*-{
		$wnd.moveGWT = $entry(@chess.client.Main::moveGWT(IDD));
    }-*/;

    private static void moveGWT(int piece, double x, double y) {
        Log.write("moveGWT(" + piece + ", " + x + ", " + y + ")");

        _game.set(piece * 2, x);
        _game.set(piece * 2 + 1, y);
    }

    public static native void onMove(int piece, double x, double y) /*-{
		$wnd.onMove(piece, x, y);
    }-*/;
}
