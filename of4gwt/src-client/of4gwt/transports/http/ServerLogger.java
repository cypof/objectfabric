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

package of4gwt.transports.http;

import of4gwt.Privileged;
import of4gwt.transports.http.CometTransport.HTTPRequestBase;

/**
 * Writes to server's log, e.g. error reports.
 */
public class ServerLogger extends Privileged {

    private static final int MAX_LOG_LENGTH = 10000;

    private static String _url;

    private ServerLogger() {
    }

    static void setUrl(String value) {
        _url = value;
    }

    public static void write(String text) {
        if (text.length() > MAX_LOG_LENGTH)
            text = text.substring(0, MAX_LOG_LENGTH);

        HTTPRequestBase request = HTTPClient.createRequestStatic(_url, false);
        writeString(text, request.getBuffer());
        request.connect();
        request.close();
    }
}