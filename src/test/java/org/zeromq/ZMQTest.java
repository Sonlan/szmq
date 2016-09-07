package org.zeromq;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.CharacterCodingException;
import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;
import org.zeromq.ZMQ.Context;
import org.zeromq.ZMQ.Event;
import org.zeromq.ZMQ.Poller;
import org.zeromq.ZMQ.Socket;

/**
 * @author Cliff Evans
 */
public class ZMQTest {

    /**
     * Test method for {@link ZMQ#makeVersion(int, int, int)}.
     */
    @Test
    public void testMakeVersion() {
        assertEquals(ZMQ.getFullVersion(),
                ZMQ.makeVersion(ZMQ.getMajorVersion(), ZMQ.getMinorVersion(), ZMQ.getPatchVersion()));
    }

    /**
     * Test method for {@link ZMQ#getVersionString()}.
     */
    @Test
    public void testGetVersion() {
        assertEquals(ZMQ.getMajorVersion() + "." + ZMQ.getMinorVersion() + "." + ZMQ.getPatchVersion(),
                ZMQ.getVersionString());
    }

    /**
     * Test method for {@link Socket#bindToRandomPort(String)}.
     */
    @Test
    public void testBindToRandomPort() {
        Context context = ZMQ.context(1);
        Socket sock = context.socket(ZMQ.DEALER);

        // Check that bindToRandomport generate valid port number
        for (int i = 0; i < 100; i++) {
            sock.bindToRandomPort("tcp://127.0.0.1");
        }

        sock.close();
        sock = context.socket(ZMQ.DEALER);

        // Check that exception different of EADDRINUSE is not catched

        // Invalid protocol
        try {
            sock.bindToRandomPort("noprotocol://127.0.0.1");
        } catch (ZMQException e) {
            assertEquals(e.getErrorCode(), ZMQ.EPROTONOSUPPORT());
        }
    }

    @Test
    public void testReqRep() {
        Context context = ZMQ.context(1);

        Socket in = context.socket(ZMQ.REQ);
        in.bind("inproc://reqrep");

        Socket out = context.socket(ZMQ.REP);
        out.connect("inproc://reqrep");

        for (int i = 0; i < 10; i++) {
            byte[] req = ("request" + i).getBytes();
            byte[] rep = ("reply" + i).getBytes();

            assertTrue(in.send(req, 0));
            byte[] reqTmp = out.recv(0);
            assertArrayEquals(req, reqTmp);

            assertTrue(out.send(rep, 0));
            byte[] repTmp = in.recv(0);
            assertArrayEquals(rep, repTmp);
        }
    }

    @Test
    public void testXPUBSUB() {
        if (ZMQ.getFullVersion() < ZMQ.make_version(3, 0, 0)) {
            // Can only test XPUB on ZMQ >= of 3.0
            return;
        }

        Context context = ZMQ.context(1);

        Socket pub = context.socket(ZMQ.XPUB);
        pub.bind("inproc://xpub");

        Socket sub = context.socket(ZMQ.SUB);
        sub.connect("inproc://xpub");
        Socket xsub = context.socket(ZMQ.XSUB);
        xsub.connect("inproc://xpub");

        sub.subscribe("".getBytes());
        byte[] subcr = pub.recv(0);
        assertArrayEquals(new byte[] { 1 }, subcr);

        sub.unsubscribe("".getBytes());
        subcr = pub.recv(0);
        assertArrayEquals(new byte[] { 0 }, subcr);

        byte[] subscription = "subs".getBytes();

        // Append subscription
        byte[] expected = new byte[subscription.length + 1];
        expected[0] = 1;
        System.arraycopy(subscription, 0, expected, 1, subscription.length);

        sub.subscribe(subscription);
        subcr = pub.recv(0);
        assertArrayEquals(expected, subcr);

        // Verify xsub subscription
        xsub.send(expected, 0);
        subcr = pub.recv(1);
        assertNull(subcr);

        for (int i = 0; i < 10; i++) {
            byte[] data = ("subscrip" + i).getBytes();

            assertTrue(pub.send(data, 0));
            // Verify SUB
            byte[] tmp = sub.recv(0);
            assertArrayEquals(data, tmp);

            // Verify XSUB
            tmp = xsub.recv(0);
            assertArrayEquals(data, tmp);
        }
    }
    
    @Test
    public void testSetXpubVerbose() {
        if (ZMQ.getFullVersion() < ZMQ.make_version(3, 2, 2)) {
            // Can only test ZMQ_XPUB_VERBOSE on ZMQ >= of 3.2.2
            return;
        }
        Context context = ZMQ.context(1);
        
        byte[] topic = "topic".getBytes();
        byte[] subscription = new byte[topic.length + 1];
        subscription[0] = 1;
        System.arraycopy(topic, 0, subscription, 1, topic.length);
        
        Socket xpubVerbose = context.socket(ZMQ.XPUB);
        xpubVerbose.setXpubVerbose(true);
        xpubVerbose.bind("inproc://xpub_verbose");
        
        Socket xpubDefault = context.socket(ZMQ.XPUB);
        xpubDefault.bind("inproc://xpub_default");
        
        Socket[] xsubs = new Socket[3];
        for (int i = 0; i < xsubs.length; i++) {
            xsubs[i] = context.socket(ZMQ.XSUB);
            xsubs[i].connect("inproc://xpub_verbose");
            xsubs[i].connect("inproc://xpub_default");
        }
        
        for (int i = 0; i < xsubs.length; i++) {
            xsubs[i].send(subscription, 0);
            assertArrayEquals(subscription, xpubVerbose.recv(0));
            if (i == 0) {
                assertArrayEquals(subscription, xpubDefault.recv(0));
            }
            else {
                assertNull(xpubDefault.recv(ZMQ.DONTWAIT));
            }
        }
        
        for (int i = 0; i < xsubs.length; i++) {
            xsubs[i].close();
        }
        xpubVerbose.close();
        xpubDefault.close();
        context.term();
    }

    /**
     * Test method for various set/get options.
     */
    @Test
    public void testSetOption() {
        Context context = ZMQ.context(1);

        Socket sock = context.socket(ZMQ.REQ);

        if (ZMQ.getFullVersion() >= ZMQ.makeVersion(3, 2, 0)) {
            sock.setIPv4Only(false);
            assertEquals(false, sock.getIPv4Only());

            sock.setIPv4Only(true);
            assertEquals(true, sock.getIPv4Only());
        }
        sock.close();

        context.term();
    }

    static class Client extends Thread {

        private Socket s = null;
        private String name = null;

        public Client(Context ctx, String name_) {
            s = ctx.socket(ZMQ.REQ);
            name = name_;

            s.setIdentity(name.getBytes());
        }

        @Override
        public void run() {
            s.connect("tcp://127.0.0.1:6660");
            s.send("hello", 0);
            String msg = s.recvStr(0);
            s.send("world", 0);
            msg = s.recvStr(0);

            s.close();
        }
    }

    static class Dealer extends Thread {

        private Socket s = null;
        private String name = null;

        public Dealer(Context ctx, String name_) {
            s = ctx.socket(ZMQ.DEALER);
            name = name_;

            s.setIdentity(name.getBytes());
        }

        @Override
        public void run() {

            s.connect("tcp://127.0.0.1:6661");
            int count = 0;
            while (count < 2) {
                String msg = s.recvStr(0);
                if (msg == null) {
                    throw new RuntimeException();
                }
                String identity = msg;
                msg = s.recvStr(0);
                if (msg == null) {
                    throw new RuntimeException();
                }

                msg = s.recvStr(0);
                if (msg == null) {
                    throw new RuntimeException();
                }

                s.send(identity, ZMQ.SNDMORE);
                s.send("", ZMQ.SNDMORE);
                String response = "OK " + msg;

                s.send(response, 0);
                count++;
            }
            s.close();
        }
    }

    static class Main extends Thread {

        Context ctx;

        Main(Context ctx_) {
            ctx = ctx_;
        }

        @Override
        public void run() {
            Socket frontend = ctx.socket(ZMQ.ROUTER);

            assertNotNull(frontend);
            frontend.bind("tcp://127.0.0.1:6660");

            Socket backend = ctx.socket(ZMQ.DEALER);
            assertNotNull(backend);
            backend.bind("tcp://127.0.0.1:6661");

            ZMQ.proxy(frontend, backend, null);

            frontend.close();
            backend.close();
        }

    }

    @Test
    public void testProxy() throws Exception {

        if (ZMQ.getFullVersion() < ZMQ.make_version(3, 2, 2)) {
            // Can only test zmq_proxy on ZMQ >= of 3.2.2
            return;
        }

        Context ctx = ZMQ.context(1);
        assert (ctx != null);

        Main mt = new Main(ctx);
        mt.start();
        new Dealer(ctx, "AA").start();
        new Dealer(ctx, "BB").start();

        Thread.sleep(1000);
        Thread c1 = new Client(ctx, "X");
        c1.start();

        Thread c2 = new Client(ctx, "Y");
        c2.start();

        c1.join();
        c2.join();

        ctx.term();
    }

    /**
     * Test method for Router Mandatory
     */
    @Test
    public void testRouterMandatory() {
        if (ZMQ.getFullVersion() < ZMQ.makeVersion(3, 2, 0))
            return;

        Context context = ZMQ.context(1);

        Socket sock = context.socket(ZMQ.ROUTER);
        boolean ret = sock.sendMore("UNREACHABLE");
        assertEquals(true, ret);
        sock.send("END");

        sock.setRouterMandatory(true);
        try {
            sock.sendMore("UNREACHABLE");
            assertFalse(true);
        } catch (ZMQException e) {
            assertEquals(ZMQ.EHOSTUNREACH(), e.getErrorCode());
        }

        sock.close();
        context.term();
    }

    @Test
    public void testSendMoreRequestReplyOverTcp() {
        Context context = ZMQ.context(1);
        Socket reply = null;
        Socket socket = null;
        try {
            reply = context.socket(ZMQ.REP);
            reply.bind("tcp://*:12345");
            socket = context.socket(ZMQ.REQ);
            socket.connect("tcp://localhost:12345");
            socket.send("test1", ZMQ.SNDMORE);
            socket.send("test2");
            assertEquals("test1", reply.recvStr());
            assertTrue(reply.hasReceiveMore());
            assertEquals("test2", reply.recvStr());
        } finally {
            try {
                socket.close();
            } catch (Exception ignore){}
            try {
                reply.close();
            } catch (Exception ignore){}
            try {
                context.term();    
            } catch (Exception ignore) {}
        }
    }

    @Test
    public void testWritingToClosedSocket() {
        Context context = ZMQ.context(1);
        Socket sock = null;
        try {
            sock = context.socket(ZMQ.REQ);
            sock.connect("ipc:///tmp/hai");
            sock.close();
            sock.send("PING".getBytes(), 0);
        } catch (ZMQException e) {
            assertEquals(ZMQ.ENOTSOCK(), e.getErrorCode());
        } finally {
            try {
                sock.close();
            } catch (Exception ignore) {
            }
            try {
                context.term();
            } catch (Exception ignore) {
            }
        }
    }

    @Test
    public void testZeroCopyRecv() {
        if (ZMQ.version_full() >= ZMQ.make_version(3, 0, 0)) {
            Context context = ZMQ.context(1);

            ByteBuffer response = ByteBuffer.allocateDirect(1024).order(ByteOrder.nativeOrder());
            Socket push = null;
            Socket pull = null;
            try {
                push = context.socket(ZMQ.PUSH);
                pull = context.socket(ZMQ.PULL);
                pull.bind("tcp://*:45324");
                push.connect("tcp://localhost:45324");

                push.send("PING");
                int rc = pull.recvZeroCopy(response, 16, 0);
                response.flip();
                byte[] b = new byte[rc];
                response.get(b);
                assertEquals("PING", new String(b));
            } finally {
                try {
                    push.close();
                } catch (Exception ignore) {
                }
                try {
                    pull.close();
                } catch (Exception ignore) {
                }
                try {
                    context.term();
                } catch (Exception ignore) {
                }
            }
        }
    }

    @Test
    public void testZeroCopySend() throws InterruptedException {
        if (ZMQ.version_full() >= ZMQ.make_version(3, 0, 0)) {
            Context context = ZMQ.context(1);
            ByteBuffer bb = ByteBuffer.allocateDirect(1024).order(ByteOrder.nativeOrder());
            Socket push = null;
            Socket pull = null;
            try {
                push = context.socket(ZMQ.PUSH);
                pull = context.socket(ZMQ.PULL);
                pull.bind("tcp://*:45324");
                push.connect("tcp://localhost:45324");
                bb.put("PING".getBytes());
                push.sendZeroCopy(bb, bb.position(), 0);
                assertEquals("PING", new String(pull.recv()));
            } finally {
                try {
                    push.close();
                } catch (Exception ignore) {
                }
                try {
                    pull.close();
                } catch (Exception ignore) {
                }
                try {
                    context.term();
                } catch (Exception ignore) {
                }
            }
        }
    }

    @Test
    public void testByteBufferSend() throws InterruptedException {
        if (ZMQ.version_full() >= ZMQ.make_version(3, 0, 0)) {
            Context context = ZMQ.context(1);
            ByteBuffer bb = ByteBuffer.allocateDirect(4).order(ByteOrder.nativeOrder());
            Socket push = null;
            Socket pull = null;
            try {
                push = context.socket(ZMQ.PUSH);
                pull = context.socket(ZMQ.PULL);
                pull.bind("ipc:///tmp/sendbb");
                push.connect("ipc:///tmp/sendbb");
                bb.put("PING".getBytes());
                bb.flip();
                push.sendByteBuffer(bb, 0);
                String actual = new String(pull.recv());
                System.out.println(actual);
                assertEquals("PING", actual);
            } finally {
                try {
                    push.close();
                } catch (Exception ignore) {
                }
                try {
                    pull.close();
                } catch (Exception ignore) {
                }
                try {
                    context.term();
                } catch (Exception ignore) {
                }
            }
        }
    }

    @Test
    public void testByteBufferRecv() throws InterruptedException, CharacterCodingException {
        if (ZMQ.version_full() >= ZMQ.make_version(3, 0, 0)) {
            Context context = ZMQ.context(1);
            ByteBuffer bb = ByteBuffer.allocateDirect(4).order(ByteOrder.nativeOrder());
            Socket push = null;
            Socket pull = null;
            try {
                push = context.socket(ZMQ.PUSH);
                pull = context.socket(ZMQ.PULL);
                pull.bind("ipc:///tmp/recvbb");
                push.connect("ipc:///tmp/recvbb");
                push.send("PING".getBytes(), 0);
                pull.recvByteBuffer(bb, 0);
                bb.flip();
                byte[] b = new byte[4];
                bb.get(b);
                assertEquals("PING", new String(b));
            } finally {
                try {
                    push.close();
                } catch (Exception ignore) {
                }
                try {
                    pull.close();
                } catch (Exception ignore) {
                }
                try {
                    context.term();
                } catch (Exception ignore) {
                }
            }
        }
    }

    @Test
    public void testByteBufferRecvTooLarge() throws InterruptedException, CharacterCodingException {
        if (ZMQ.version_full() >= ZMQ.make_version(3, 0, 0)) {
            Context context = ZMQ.context(1);
            ByteBuffer bb = ByteBuffer.allocateDirect(5).order(ByteOrder.nativeOrder());
            Socket push = null;
            Socket pull = null;
            try {
                push = context.socket(ZMQ.PUSH);
                pull = context.socket(ZMQ.PULL);
                pull.bind("tcp://*:6787");
                push.connect("tcp://127.0.0.1:6787");
                push.send("helloworld".getBytes(), 0);
                int size = pull.recvByteBuffer(bb, 0);
                bb.flip();
                byte[] b = new byte[size];
                bb.get(b);
                assertEquals("hello", new String(b));
            } finally {
                try {
                    push.close();
                } catch (Exception ignore) {
                }
                try {
                    pull.close();
                } catch (Exception ignore) {
                }
                try {
                    context.term();
                } catch (Exception ignore) {
                }
            }
        }
    }
    @Test
    public void testPollerUnregister() {
        Context context = ZMQ.context(1);
        Socket socketOne = context.socket(ZMQ.SUB);
        Socket socketTwo = context.socket(ZMQ.REP);
        Poller poller = new Poller(2);
        poller.register(socketOne, Poller.POLLIN);
        poller.register(socketTwo, Poller.POLLIN);

        socketOne.setLinger(0);
        socketOne.close();
        socketTwo.setLinger(0);
        socketTwo.close();

        poller.unregister(socketOne);
        poller.unregister(socketTwo);
        
        context.term();
    }
    
    @Test
    public void testEventConnected() {
        if (ZMQ.version_full() < ZMQ.make_version(3, 2, 2)) // Monitor added in 3.2.2
            return;
        
        Context context = ZMQ.context(1);
        Event event;
        
        Socket helper = context.socket(ZMQ.REQ);
        int port = helper.bindToRandomPort("tcp://127.0.0.1");
        
        Socket socket = context.socket(ZMQ.REP);
        Socket monitor = context.socket(ZMQ.PAIR);
        monitor.setReceiveTimeOut(100);
        
        assertTrue(socket.monitor("inproc://monitor.socket", ZMQ.EVENT_CONNECTED));
        monitor.connect("inproc://monitor.socket");
        
        socket.connect("tcp://127.0.0.1:" + port);
        event = Event.recv(monitor);
        assertNotNull("No event was received", event);
        assertEquals(ZMQ.EVENT_CONNECTED, event.getEvent());
        
        helper.close();
        socket.close();
        monitor.close();
        context.term();
    }
    
    @Test
    public void testEventConnectDelayed() {
        if (ZMQ.version_full() < ZMQ.make_version(3, 2, 2)) // Monitor added in 3.2.2
            return;
        
        Context context = ZMQ.context(1);
        Event event;
        
        Socket socket = context.socket(ZMQ.REP);
        Socket monitor = context.socket(ZMQ.PAIR);
        monitor.setReceiveTimeOut(100);
        
        assertTrue(socket.monitor("inproc://monitor.socket", ZMQ.EVENT_CONNECT_DELAYED));
        monitor.connect("inproc://monitor.socket");
        
        socket.connect("tcp://127.0.0.1:6751");
        event = Event.recv(monitor);
        assertNotNull("No event was received", event);
        assertEquals(ZMQ.EVENT_CONNECT_DELAYED, event.getEvent());
        
        socket.close();
        monitor.close();
        context.term();
    }
    
    @Test
    public void testEventConnectRetried() {
        if (ZMQ.version_full() < ZMQ.make_version(3, 2, 2)) // Monitor added in 3.2.2
            return;
        
        Context context = ZMQ.context(1);
        Event event;
        
        Socket socket = context.socket(ZMQ.REP);
        Socket monitor = context.socket(ZMQ.PAIR);
        monitor.setReceiveTimeOut(100);
        
        assertTrue(socket.monitor("inproc://monitor.socket", ZMQ.EVENT_CONNECT_RETRIED));
        monitor.connect("inproc://monitor.socket");
        
        socket.connect("tcp://127.0.0.1:6752");
        event = Event.recv(monitor);
        assertNotNull("No event was received", event);
        assertEquals(ZMQ.EVENT_CONNECT_RETRIED, event.getEvent());
        
        socket.close();
        monitor.close();
        context.term();
    }
    
    @Test
    public void testEventListening() {
        if (ZMQ.version_full() < ZMQ.make_version(3, 2, 2)) // Monitor added in 3.2.2
            return;
        
        Context context = ZMQ.context(1);
        Event event;
        
        Socket socket = context.socket(ZMQ.REP);
        Socket monitor = context.socket(ZMQ.PAIR);
        monitor.setReceiveTimeOut(100);
        
        assertTrue(socket.monitor("inproc://monitor.socket", ZMQ.EVENT_LISTENING));
        monitor.connect("inproc://monitor.socket");
        
        socket.bindToRandomPort("tcp://127.0.0.1");
        event = Event.recv(monitor);
        assertNotNull("No event was received", event);
        assertEquals(ZMQ.EVENT_LISTENING, event.getEvent());
        
        socket.close();
        monitor.close();
        context.term();
    }
    
    @Test
    public void testEventBindFailed() {
        if (ZMQ.version_full() < ZMQ.make_version(3, 2, 2)) // Monitor added in 3.2.2
            return;
        
        Context context = ZMQ.context(1);
        Event event;
                
        Socket helper = context.socket(ZMQ.REP);
        int port = helper.bindToRandomPort("tcp://127.0.0.1");
        
        Socket socket = context.socket(ZMQ.REP);
        Socket monitor = context.socket(ZMQ.PAIR);
        monitor.setReceiveTimeOut(100);
        
        assertTrue(socket.monitor("inproc://monitor.socket", ZMQ.EVENT_BIND_FAILED));
        monitor.connect("inproc://monitor.socket");
        
        try {
            socket.bind("tcp://127.0.0.1:" + port);
        } catch (ZMQException ex) {}
        event = Event.recv(monitor);
        assertNotNull("No event was received", event);
        assertEquals(ZMQ.EVENT_BIND_FAILED, event.getEvent());
        
        helper.close();
        socket.close();
        monitor.close();
        context.term();
    }
    
    @Test
    public void testEventAccepted() {
        if (ZMQ.version_full() < ZMQ.make_version(3, 2, 2)) // Monitor added in 3.2.2
            return;
        
        Context context = ZMQ.context(1);
        Event event;
        
        Socket socket = context.socket(ZMQ.REP);
        Socket monitor = context.socket(ZMQ.PAIR);
        Socket helper = context.socket(ZMQ.REQ);
        monitor.setReceiveTimeOut(100);
        
        assertTrue(socket.monitor("inproc://monitor.socket", ZMQ.EVENT_ACCEPTED));
        monitor.connect("inproc://monitor.socket");
        
        int port = socket.bindToRandomPort("tcp://127.0.0.1");

        helper.connect("tcp://127.0.0.1:" + port);
        event = Event.recv(monitor);
        assertNotNull("No event was received", event);
        assertEquals(ZMQ.EVENT_ACCEPTED, event.getEvent());
        
        helper.close();
        socket.close();
        monitor.close();
        context.term();
    }
    
    @Test
    public void testEventClosed() {
        if (ZMQ.version_full() < ZMQ.make_version(3, 2, 2)) // Monitor added in 3.2.2
            return;
        
        Context context = ZMQ.context(1);
        Event event;
        
        Socket socket = context.socket(ZMQ.REP);
        Socket monitor = context.socket(ZMQ.PAIR);
        monitor.setReceiveTimeOut(100);
        
        socket.bindToRandomPort("tcp://127.0.0.1");
        
        assertTrue(socket.monitor("inproc://monitor.socket", ZMQ.EVENT_CLOSED));
        monitor.connect("inproc://monitor.socket");
        
        socket.close();
        event = Event.recv(monitor);
        assertNotNull("No event was received", event);
        assertEquals(ZMQ.EVENT_CLOSED, event.getEvent());
        
        monitor.close();
        context.term();
    }
    
    @Test
    public void testEventDisconnected() {
        if (ZMQ.version_full() < ZMQ.make_version(3, 2, 2)) // Monitor added in 3.2.2
            return;
        
        Context context = ZMQ.context(1);
        Event event;
        
        Socket socket = context.socket(ZMQ.REP);
        Socket monitor = context.socket(ZMQ.PAIR);
        Socket helper = context.socket(ZMQ.REQ);
        monitor.setReceiveTimeOut(100);
        
        int port = socket.bindToRandomPort("tcp://127.0.0.1");
        helper.connect("tcp://127.0.0.1:" + port);
        
        assertTrue(socket.monitor("inproc://monitor.socket", ZMQ.EVENT_DISCONNECTED));
        monitor.connect("inproc://monitor.socket");
        
        helper.close();
        event = Event.recv(monitor);
        assertNotNull("No event was received", event);
        assertEquals(ZMQ.EVENT_DISCONNECTED, event.getEvent());
        
        socket.close();
        monitor.close();
        context.term();
    }
    
    @Test
    public void testEventMonitorStopped() {
        if (ZMQ.version_full() < ZMQ.make_version(4, 0, 0)) // EVENT_MONITOR_STOPPED added in 4.0.0
            return;
        
        Context context = ZMQ.context(1);
        Event event;
        
        Socket socket = context.socket(ZMQ.REP);
        Socket monitor = context.socket(ZMQ.PAIR);
        monitor.setReceiveTimeOut(100);
        
        assertTrue(socket.monitor("inproc://monitor.socket", ZMQ.EVENT_MONITOR_STOPPED));
        monitor.connect("inproc://monitor.socket");
        
        socket.monitor(null, 0);
        event = Event.recv(monitor);
        assertNotNull("No event was received", event);
        assertEquals(ZMQ.EVENT_MONITOR_STOPPED, event.getEvent());
        
        socket.close();
        monitor.close();
        context.term();
    }
}