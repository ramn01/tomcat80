/*
 *  Licensed to the Apache Software Foundation (ASF) under one or more
 *  contributor license agreements.  See the NOTICE file distributed with
 *  this work for additional information regarding copyright ownership.
 *  The ASF licenses this file to You under the Apache License, Version 2.0
 *  (the "License"); you may not use this file except in compliance with
 *  the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package org.apache.coyote.ajp;

import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;

import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.junit.Assert;
import org.junit.Test;

import org.apache.catalina.Context;
import org.apache.catalina.startup.Tomcat;
import org.apache.catalina.startup.TomcatBaseTest;

public class TestAbstractAjpProcessor extends TomcatBaseTest {

    @Override
    protected String getProtocol() {
        /*
         * The tests are all setup for HTTP so need to convert the protocol
         * values to AJP.
         */
        // Has a protocol been specified
        String protocol = System.getProperty("tomcat.test.protocol");

        // Use BIO by default
        if (protocol == null) {
            protocol = "org.apache.coyote.ajp.AjpProtocol";
        } else if (protocol.contains("Nio2")) {
            protocol = "org.apache.coyote.ajp.AjpNio2Protocol";
        } else if (protocol.contains("Nio")) {
            protocol = "org.apache.coyote.ajp.AjpNioProtocol";
        } else if (protocol.contains("Apr")) {
            protocol = "org.apache.coyote.ajp.AjpAprProtocol";
        } else {
            protocol = "org.apache.coyote.ajp.AjpProtocol";
        }

        return protocol;
    }

    private void doSnoopTest(RequestDescriptor desc) throws Exception {

        HashMap<String, String> requestInfo = desc.getRequestInfo();
        HashMap<String, String> contextInitParameters = desc.getContextInitParameters();
        HashMap<String, String> contextAttributes = desc.getContextAttributes();
        HashMap<String, String> headers = desc.getHeaders();
        HashMap<String, String> attributes = desc.getAttributes();
        HashMap<String, String> params = desc.getParams();

        Tomcat tomcat = getTomcatInstance();

        // No file system docBase required
        Context ctx = tomcat.addContext("", null);

        Tomcat.addServlet(ctx, "snoop", new SnoopServlet());
        ctx.addServletMapping("/", "snoop");

        SimpleAjpClient ajpClient = new SimpleAjpClient();

        if (requestInfo.get("REQUEST-QUERY-STRING") != null &&
            params.size() > 0) {
            throw(new IllegalArgumentException("Request setting " +
                "'REQUEST-QUERY-STRING' and explicit params not allowed " +
                "together"));
        }

        String value;
        HashMap<String, String> savedRequestInfo = new HashMap<String, String>();
        for (String name: requestInfo.keySet()) {
            value = requestInfo.get(name);
            switch (name) {
                case "REQUEST-METHOD":
                    ajpClient.setMethod(value);
                    break;
                case "REQUEST-PROTOCOL":
                    ajpClient.setProtocol(value);
                    break;
                case "REQUEST-URI":
                    ajpClient.setUri(value);
                    break;
                case "REQUEST-REMOTE-HOST":
                    /* request.getRemoteHost() will default to
                     * request.getRemoteAddr() unless enableLookups is set. */
                    tomcat.getConnector().setEnableLookups(true);
                    ajpClient.setRemoteHost(value);
                    break;
                case "REQUEST-REMOTE-ADDR":
                    ajpClient.setRemoteAddr(value);
                    break;
                case "REQUEST-SERVER-NAME":
                    ajpClient.setServerName(value);
                    break;
                case "REQUEST-SERVER-PORT":
                    ajpClient.setServerPort(Integer.valueOf(value));
                    break;
                case "REQUEST-IS-SECURE":
                    ajpClient.setSsl(Boolean.parseBoolean(value));
                    break;
                case "REQUEST-LOCAL-ADDR":
                    savedRequestInfo.put(name, value);
                    break;
                case "REQUEST-REMOTE-PORT":
                    savedRequestInfo.put(name, value);
                    break;
                case "REQUEST-REMOTE-USER":
                case "REQUEST-ROUTE":
                case "REQUEST-SECRET":
                case "REQUEST-AUTH-TYPE":
                case "REQUEST-QUERY-STRING":
                    savedRequestInfo.put(name, value);
                    break;
                case "REQUEST-CONTENT-LENGTH":
                    headers.put("CONTENT-LENGTH", value);
                    break;
                case "REQUEST-CONTENT-TYPE":
                    headers.put("CONTENT-TYPE", value);
                    break;
                /* Not yet implemented or not (easily) possible to implement */
                case "REQUEST-LOCAL-NAME":          //request.getLocalName()
                case "REQUEST-LOCAL-PORT":          //request.getLocalPort()
                case "REQUEST-SCHEME":              //request.getScheme()
                case "REQUEST-URL":                 //request.getRequestURL()
                case "REQUEST-CONTEXT-PATH":        //request.getContextPath()
                case "REQUEST-SERVLET-PATH":        //request.getServletPath()
                case "REQUEST-PATH-INFO":           //request.getPathInfo()
                case "REQUEST-PATH-TRANSLATED":     //request.getPathTranslated()
                case "REQUEST-USER-PRINCIPAL":      //request.getUserPrincipal()
                case "REQUEST-CHARACTER-ENCODING":  //request.getCharacterEncoding()
                case "REQUEST-LOCALE":              //request.getLocale()
                case "SESSION-REQUESTED-ID":        //request.getRequestedSessionId()
                case "SESSION-REQUESTED-ID-COOKIE": //request.isRequestedSessionIdFromCookie()
                case "SESSION-REQUESTED-ID-URL":    //request.isRequestedSessionIdFromUrl()
                case "SESSION-REQUESTED-ID-VALID":  //request.isRequestedSessionIdValid()
                default:
                    throw(new IllegalArgumentException("Request setting '" + name + "' not supported"));
            }
        }

        ServletContext sc = ctx.getServletContext();
        for (String name: contextInitParameters.keySet()) {
            sc.setInitParameter(name, contextInitParameters.get(name));
        }
        for (String name: contextAttributes.keySet()) {
            sc.setAttribute(name, contextAttributes.get(name));
        }

        /* Basic request properties must be set before this call */
        TesterAjpMessage forwardMessage = ajpClient.createForwardMessage();

        for (String name: savedRequestInfo.keySet()) {
            value = savedRequestInfo.get(name);
            switch (name) {
                case "REQUEST-LOCAL-ADDR":
                    forwardMessage.addAttribute("AJP_LOCAL_ADDR", value);
                    break;
                case "REQUEST-REMOTE-PORT":
                    forwardMessage.addAttribute("AJP_REMOTE_PORT", value);
                    break;
                case "REQUEST-REMOTE-USER":
                    /* request.getRemoteUser() will not trust the AJP
                     * info if tomcatAuthentication is set. */
                    tomcat.getConnector().setProperty("tomcatAuthentication", "false");
                    forwardMessage.addAttribute(0x03, value);
                    break;
                case "REQUEST-AUTH-TYPE":
                    /* request.getAuthType() will not trust the AJP
                     * info if tomcatAuthentication is set. */
                    tomcat.getConnector().setProperty("tomcatAuthentication", "false");
                    forwardMessage.addAttribute(0x04, value);
                    break;
                case "REQUEST-QUERY-STRING":
                    forwardMessage.addAttribute(0x05, value);
                    break;
                case "REQUEST-ROUTE":
                    forwardMessage.addAttribute(0x06, value);
                    break;
                case "REQUEST-SECRET":
                    forwardMessage.addAttribute(0x0C, value);
                    break;
                default:
                    throw(new IllegalArgumentException("Request setting '" + name + "' not supported"));
            }
        }

        if (params.size() > 0) {
            StringBuilder query = new StringBuilder();
            boolean sep = false;
            for (String name: params.keySet()) {
                if (sep) {
                    query.append("&");
                } else {
                    sep = true;
                }
                query.append(name);
                query.append("=");
                query.append(params.get(name));
            }
            forwardMessage.addAttribute(0x05, query.toString());
        }

        for (String name: headers.keySet()) {
            value = headers.get(name);
            name = name.toUpperCase();
            switch (name) {
                case "ACCEPT":
                    forwardMessage.addHeader(0xA001, value);
                    break;
                case "ACCEPT-CHARSET":
                    forwardMessage.addHeader(0xA002, value);
                    break;
                case "ACCEPT-ENCODING":
                    forwardMessage.addHeader(0xA003, value);
                    break;
                case "ACCEPT-LANGUAGE":
                    forwardMessage.addHeader(0xA004, value);
                    break;
                case "AUTHORIZATION":
                    forwardMessage.addHeader(0xA005, value);
                    break;
                case "CONNECTION":
                    forwardMessage.addHeader(0xA006, value);
                    break;
                case "CONTENT-TYPE":
                    forwardMessage.addHeader(0xA007, value);
                    break;
                case "CONTENT-LENGTH":
                    forwardMessage.addHeader(0xA008, value);
                    break;
                case "COOKIE":
                    forwardMessage.addHeader(0xA009, value);
                    break;
                case "COOKIE2":
                    forwardMessage.addHeader(0xA00A, value);
                    break;
                case "HOST":
                    forwardMessage.addHeader(0xA00B, value);
                    break;
                case "PRAGMA":
                    forwardMessage.addHeader(0xA00C, value);
                    break;
                case "REFERER":
                    forwardMessage.addHeader(0xA00D, value);
                    break;
                case "USER-AGENT":
                    forwardMessage.addHeader(0xA00E, value);
                    break;
                default:
                    forwardMessage.addHeader(name, value);
                    break;
            }
        }
        for (String name: attributes.keySet()) {
            value = attributes.get(name);
            forwardMessage.addAttribute(name, value);
        }
        // Complete the message
        forwardMessage.end();

        tomcat.start();
        ajpClient.setPort(getPort());
        ajpClient.connect();

        // Expect 3 packets: headers, body, end
        TesterAjpMessage responseHeaders = ajpClient.sendMessage(forwardMessage);
        validateResponseHeaders(responseHeaders, 200, "OK");

        String body = extractResponseBody(ajpClient.readMessage());
        RequestDescriptor result = SnoopResult.parse(body);

        /* AJP attributes result in Coyote Request attributes, which are
         * not listed by request.getAttributeNames(), so SnoopServlet
         * does not see them. Delete attributes before result comparison. */
        desc.getAttributes().clear();

        result.compare(desc);

        validateResponseEnd(ajpClient.readMessage(), true);
    }

    @Test
    public void testServerName() throws Exception {
        RequestDescriptor desc = new RequestDescriptor();
        desc.putRequestInfo("REQUEST-SERVER-NAME", "MYSERVER");
        desc.putRequestInfo("REQUEST-URI", "/testServerName");
        doSnoopTest(desc);
    }

    @Test
    public void testServerPort() throws Exception {
        RequestDescriptor desc = new RequestDescriptor();
        desc.putRequestInfo("REQUEST-SERVER-PORT", "8888");
        desc.putRequestInfo("REQUEST-URI", "/testServerPort");
        doSnoopTest(desc);
    }

    @Test
    public void testLocalAddr() throws Exception {
        RequestDescriptor desc = new RequestDescriptor();
        desc.putRequestInfo("REQUEST-LOCAL-ADDR", "10.3.2.1");
        desc.putRequestInfo("REQUEST-URI", "/testLocalAddr");
        doSnoopTest(desc);
    }

    @Test
    public void testRemoteHost() throws Exception {
        RequestDescriptor desc = new RequestDescriptor();
        desc.putRequestInfo("REQUEST-REMOTE-HOST", "MYCLIENT");
        desc.putRequestInfo("REQUEST-URI", "/testRemoteHost");
        doSnoopTest(desc);
    }

    @Test
    public void testRemoteAddr() throws Exception {
        RequestDescriptor desc = new RequestDescriptor();
        desc.putRequestInfo("REQUEST-REMOTE-ADDR", "10.1.2.3");
        desc.putRequestInfo("REQUEST-URI", "/testRemoteAddr");
        doSnoopTest(desc);
    }

    @Test
    public void testRemotePort() throws Exception {
        RequestDescriptor desc = new RequestDescriptor();
        desc.putRequestInfo("REQUEST-REMOTE-PORT", "34567");
        desc.putRequestInfo("REQUEST-URI", "/testRemotePort");
        doSnoopTest(desc);
    }

    @Test
    public void testMethod() throws Exception {
        RequestDescriptor desc = new RequestDescriptor();
        desc.putRequestInfo("REQUEST-METHOD", "LOCK");
        desc.putRequestInfo("REQUEST-URI", "/testMethod");
        doSnoopTest(desc);
    }

    @Test
    public void testUri() throws Exception {
        RequestDescriptor desc = new RequestDescriptor();
        desc.putRequestInfo("REQUEST-URI", "/a/b/c");
        doSnoopTest(desc);
    }

    @Test
    public void testProtocol() throws Exception {
        RequestDescriptor desc = new RequestDescriptor();
        desc.putRequestInfo("REQUEST-PROTOCOL", "HTTP/1.x");
        desc.putRequestInfo("REQUEST-URI", "/testProtocol");
        doSnoopTest(desc);
    }

    @Test
    public void testSecure() throws Exception {
        RequestDescriptor desc = new RequestDescriptor();
        desc.putRequestInfo("REQUEST-IS-SECURE", "true");
        desc.putRequestInfo("REQUEST-URI", "/testSecure");
        doSnoopTest(desc);
    }

    @Test
    public void testQueryString() throws Exception {
        RequestDescriptor desc = new RequestDescriptor();
        desc.putRequestInfo("REQUEST-QUERY-STRING", "p1=1&p2=12&p3=123");
        desc.putRequestInfo("REQUEST-URI", "/testQueryString");
        doSnoopTest(desc);
    }

    @Test
    public void testRemoteUser() throws Exception {
        RequestDescriptor desc = new RequestDescriptor();
        desc.putRequestInfo("REQUEST-REMOTE-USER", "MYUSER");
        desc.putRequestInfo("REQUEST-URI", "/testRemoteUser");
        doSnoopTest(desc);
    }

    @Test
    public void testAuthType() throws Exception {
        RequestDescriptor desc = new RequestDescriptor();
        desc.putRequestInfo("REQUEST-AUTH-TYPE", "MyAuth");
        desc.putRequestInfo("REQUEST-URI", "/testAuthType");
        doSnoopTest(desc);
    }

    @Test
    public void testOneHeader() throws Exception {
        RequestDescriptor desc = new RequestDescriptor();
        desc.putHeader("MYHEADER", "MYHEADER-VALUE");
        desc.putRequestInfo("REQUEST-URI", "/testOneHeader");
        doSnoopTest(desc);
    }

    @Test
    public void testOneAttribute() throws Exception {
        RequestDescriptor desc = new RequestDescriptor();
        desc.putAttribute("MYATTRIBUTE", "MYATTRIBUTE-VALUE");
        desc.putRequestInfo("REQUEST-URI", "/testOneAttribute");
        doSnoopTest(desc);
    }

    @Test
    public void testMulti() throws Exception {
        RequestDescriptor desc = new RequestDescriptor();
        desc.putRequestInfo("REQUEST-SERVER-NAME", "MYSERVER");
        desc.putRequestInfo("REQUEST-SERVER-PORT", "8888");
        desc.putRequestInfo("REQUEST-LOCAL-ADDR", "10.3.2.1");
        desc.putRequestInfo("REQUEST-REMOTE-HOST", "MYCLIENT");
        desc.putRequestInfo("REQUEST-REMOTE-ADDR", "10.1.2.3");
        desc.putRequestInfo("REQUEST-REMOTE-PORT", "34567");
        desc.putRequestInfo("REQUEST-METHOD", "LOCK");
        desc.putRequestInfo("REQUEST-URI", "/a/b/c");
        desc.putRequestInfo("REQUEST-PROTOCOL", "HTTP/1.x");
        desc.putRequestInfo("REQUEST-IS-SECURE", "true");
        desc.putRequestInfo("REQUEST-QUERY-STRING", "p1=1&p2=12&p3=123");
        desc.putRequestInfo("REQUEST-REMOTE-USER", "MYUSER");
        desc.putRequestInfo("REQUEST-AUTH-TYPE", "MyAuth");
        desc.putHeader("MYHEADER1", "MYHEADER1-VALUE");
        desc.putHeader("MYHEADER2", "MYHEADER2-VALUE");
        desc.putAttribute("MYATTRIBUTE1", "MYATTRIBUTE-VALUE1");
        desc.putAttribute("MYATTRIBUTE2", "MYATTRIBUTE-VALUE2");
        doSnoopTest(desc);
    }

    @Test
    public void testKeepAlive() throws Exception {
        Tomcat tomcat = getTomcatInstance();
        tomcat.getConnector().setProperty("connectionTimeout", "-1");
        tomcat.start();

        // No file system docBase required
        Context ctx = tomcat.addContext("", null);

        Tomcat.addServlet(ctx, "helloWorld", new HelloWorldServlet());
        ctx.addServletMapping("/", "helloWorld");

        SimpleAjpClient ajpClient = new SimpleAjpClient();

        ajpClient.setPort(getPort());

        ajpClient.connect();

        validateCpong(ajpClient.cping());

        TesterAjpMessage forwardMessage = ajpClient.createForwardMessage();
        forwardMessage.addHeader("X-DUMMY-HEADER", "IGNORE");
        // Complete the message - no extra headers required.
        forwardMessage.end();

        // Two requests
        for (int i = 0; i < 2; i++) {
            TesterAjpMessage responseHeaders = ajpClient.sendMessage(forwardMessage);
            // Expect 3 packets: headers, body, end
            validateResponseHeaders(responseHeaders, 200, "OK");
            TesterAjpMessage responseBody = ajpClient.readMessage();
            validateResponseBody(responseBody, HelloWorldServlet.RESPONSE_TEXT);
            validateResponseEnd(ajpClient.readMessage(), true);

            // Give connections plenty of time to time out
            Thread.sleep(2000);

            // Double check the connection is still open
            validateCpong(ajpClient.cping());
        }

        ajpClient.disconnect();
    }

    @Test
    public void testPost() throws Exception {
        doTestPost(false, HttpServletResponse.SC_OK, "OK");
    }


    @Test
    public void testPostMultipleContentLength() throws Exception {
        // Multiple content lengths
        doTestPost(true, HttpServletResponse.SC_BAD_REQUEST, "Bad Request");
    }


    public void doTestPost(boolean multipleCL, int expectedStatus,
                           String expectedMessage) throws Exception {

        getTomcatInstanceTestWebapp(false, true);

        SimpleAjpClient ajpClient = new SimpleAjpClient();
        ajpClient.setPort(getPort());
        ajpClient.connect();

        validateCpong(ajpClient.cping());

        ajpClient.setUri("/test/echo-params.jsp");
        ajpClient.setMethod("POST");
        TesterAjpMessage forwardMessage = ajpClient.createForwardMessage();
        forwardMessage.addHeader(0xA008, "9");
        if (multipleCL) {
            forwardMessage.addHeader(0xA008, "99");
        }
        forwardMessage.addHeader(0xA007, "application/x-www-form-urlencoded");
        forwardMessage.end();

        TesterAjpMessage bodyMessage =
                ajpClient.createBodyMessage("test=data".getBytes());

        TesterAjpMessage responseHeaders =
                ajpClient.sendMessage(forwardMessage, bodyMessage);

        validateResponseHeaders(responseHeaders, expectedStatus, expectedMessage);
        if (expectedStatus == HttpServletResponse.SC_OK) {
            // Expect 3 messages: headers, body, end for a valid request
            TesterAjpMessage responseBody = ajpClient.readMessage();
            validateResponseBody(responseBody, "test - data");
            validateResponseEnd(ajpClient.readMessage(), true);

            // Double check the connection is still open
            validateCpong(ajpClient.cping());
        } else {
            // Expect 2 messages: headers, end for an invalid request
            validateResponseEnd(ajpClient.readMessage(), false);
        }


        ajpClient.disconnect();
    }


    /*
     * Bug 55453
     */
    @Test
    public void test304WithBody() throws Exception {

        Tomcat tomcat = getTomcatInstance();

        // No file system docBase required
        Context ctx = tomcat.addContext("", null);

        Tomcat.addServlet(ctx, "bug55453", new Tester304WithBodyServlet());
        ctx.addServletMapping("/", "bug55453");

        tomcat.start();

        SimpleAjpClient ajpClient = new SimpleAjpClient();
        ajpClient.setPort(getPort());
        ajpClient.connect();

        validateCpong(ajpClient.cping());

        TesterAjpMessage forwardMessage = ajpClient.createForwardMessage();
        forwardMessage.end();

        TesterAjpMessage responseHeaders =
                ajpClient.sendMessage(forwardMessage, null);

        // Expect 2 messages: headers, end
        validateResponseHeaders(responseHeaders, 304, "Not Modified");
        validateResponseEnd(ajpClient.readMessage(), true);

        // Double check the connection is still open
        validateCpong(ajpClient.cping());

        ajpClient.disconnect();
    }


    @Test
    public void testZeroLengthRequestBodyGetA() throws Exception {
        doTestZeroLengthRequestBody("GET", true);
    }

    @Test
    public void testZeroLengthRequestBodyGetB() throws Exception {
        doTestZeroLengthRequestBody("GET", false);
    }

    @Test
    public void testZeroLengthRequestBodyPostA() throws Exception {
        doTestZeroLengthRequestBody("POST", true);
    }

    @Test
    public void testZeroLengthRequestBodyPostB() throws Exception {
        doTestZeroLengthRequestBody("POST", false);
    }

    private void doTestZeroLengthRequestBody(String method, boolean callAvailable)
            throws Exception {

        Tomcat tomcat = getTomcatInstance();

        // No file system docBase required
        Context ctx = tomcat.addContext("", null);

        ReadBodyServlet servlet = new ReadBodyServlet(callAvailable);
        Tomcat.addServlet(ctx, "ReadBody", servlet);
        ctx.addServletMapping("/", "ReadBody");

        tomcat.start();

        SimpleAjpClient ajpClient = new SimpleAjpClient();
        ajpClient.setPort(getPort());
        ajpClient.connect();

        validateCpong(ajpClient.cping());

        ajpClient.setMethod(method);
        TesterAjpMessage forwardMessage = ajpClient.createForwardMessage();
        forwardMessage.addHeader(0xA008, "0");
        forwardMessage.end();

        TesterAjpMessage responseHeaders =
                ajpClient.sendMessage(forwardMessage, null);

        // Expect 3 messages: headers, body, end
        validateResponseHeaders(responseHeaders, 200, "OK");
        validateResponseBody(ajpClient.readMessage(),
                "Request Body length in bytes: 0");
        validateResponseEnd(ajpClient.readMessage(), true);

        // Double check the connection is still open
        validateCpong(ajpClient.cping());

        ajpClient.disconnect();

        if (callAvailable) {
            boolean success = true;
            Iterator<Integer> itAvailable = servlet.availableList.iterator();
            Iterator<Integer> itRead = servlet.readList.iterator();
            while (success && itAvailable.hasNext()) {
                success = ((itRead.next().intValue() > 0) == (itAvailable.next().intValue() > 0));
            }
            if (!success) {
                Assert.fail("available() and read() results do not match.\nAvailable: "
                        + servlet.availableList + "\nRead: " + servlet.readList);
            }
        }
    }


    /**
     * Process response header packet and checks the status. Any other data is
     * ignored.
     */
    private void validateResponseHeaders(TesterAjpMessage message,
            int expectedStatus, String expectedMessage) throws Exception {
        // First two bytes should always be AB
        Assert.assertEquals((byte) 'A', message.buf[0]);
        Assert.assertEquals((byte) 'B', message.buf[1]);

        // Set the start position and read the length
        message.processHeader(false);

        // Check the length
        Assert.assertTrue(message.len > 0);

        // Should be a header message
        Assert.assertEquals(0x04, message.readByte());

        // Check status
        Assert.assertEquals(expectedStatus, message.readInt());

        // Check the reason phrase
        Assert.assertEquals(expectedMessage, message.readString());

        // Get the number of headers
        int headerCount = message.readInt();

        for (int i = 0; i < headerCount; i++) {
            // Read the header name
            message.readHeaderName();
            // Read the header value
            message.readString();
        }
    }

    /**
     * Extract the content from a response message.
     */
    private String extractResponseBody(TesterAjpMessage message)
            throws Exception {

        Assert.assertEquals((byte) 'A', message.buf[0]);
        Assert.assertEquals((byte) 'B', message.buf[1]);

        // Set the start position and read the length
        message.processHeader(false);

        // Should be a body chunk message
        Assert.assertEquals(0x03, message.readByte());

        int len = message.readInt();
        Assert.assertTrue(len > 0);
        return message.readString(len);
    }

    /**
     * Validates that the response message is valid and contains the expected
     * content.
     */
    private void validateResponseBody(TesterAjpMessage message,
            String expectedBody) throws Exception {

        String body = extractResponseBody(message);
        Assert.assertTrue(body.contains(expectedBody));
    }

    private void validateResponseEnd(TesterAjpMessage message,
            boolean expectedReuse) {
        Assert.assertEquals((byte) 'A', message.buf[0]);
        Assert.assertEquals((byte) 'B', message.buf[1]);

        message.processHeader(false);

        // Should be an end body message
        Assert.assertEquals(0x05, message.readByte());

        // Check the length
        Assert.assertEquals(2, message.getLen());

        boolean reuse = false;
        if (message.readByte() > 0) {
            reuse = true;
        }

        Assert.assertEquals(Boolean.valueOf(expectedReuse), Boolean.valueOf(reuse));
    }

    private void validateCpong(TesterAjpMessage message) throws Exception {
        // First two bytes should always be AB
        Assert.assertEquals((byte) 'A', message.buf[0]);
        Assert.assertEquals((byte) 'B', message.buf[1]);
        // CPONG should have a message length of 1
        // This effectively checks the next two bytes
        Assert.assertEquals(1, message.getLen());
        // Data should be the value 9
        Assert.assertEquals(9, message.buf[4]);
    }


    private static class Tester304WithBodyServlet extends HttpServlet {

        private static final long serialVersionUID = 1L;

        @Override
        protected void doGet(HttpServletRequest req, HttpServletResponse resp)
                throws ServletException, IOException {

            resp.setStatus(304);
            resp.getWriter().print("Body not permitted for 304 response");
        }
    }


    private static class ReadBodyServlet extends HttpServlet {

        private static final long serialVersionUID = 1L;

        private final boolean callAvailable;
        final List<Integer> availableList;
        final List<Integer> readList;

        public ReadBodyServlet(boolean callAvailable) {
            this.callAvailable = callAvailable;
            this.availableList = callAvailable ? new ArrayList<Integer>() : null;
            this.readList = callAvailable ? new ArrayList<Integer>() : null;
        }

        @Override
        protected void doGet(HttpServletRequest req, HttpServletResponse resp)
                throws ServletException, IOException {
            doRequest(req, resp, false);
        }

        @Override
        protected void doPost(HttpServletRequest req, HttpServletResponse resp)
                throws ServletException, IOException {
            doRequest(req, resp, true);
        }

        private void doRequest(HttpServletRequest request, HttpServletResponse response,
                boolean isPost) throws IOException {

            long readCount = 0;

            try (InputStream s = request.getInputStream()) {
                byte[] buf = new byte[4096];
                int read;
                do {
                    if (callAvailable) {
                        int available = s.available();
                        read = s.read(buf);
                        availableList.add(Integer.valueOf(available));
                        readList.add(Integer.valueOf(read));
                    } else {
                        read = s.read(buf);
                    }
                    if (read > 0) {
                        readCount += read;
                    }
                } while (read > 0);
            }

            response.setContentType("text/plain");
            response.setCharacterEncoding("UTF-8");

            try (PrintWriter w = response.getWriter()) {
                w.println("Method: " + (isPost ? "POST" : "GET") + ". Reading request body...");
                w.println("Request Body length in bytes: " + readCount);
            }
        }
    }
}
