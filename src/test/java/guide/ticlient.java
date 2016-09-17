/*
    Copyright (c) 2007-2014 Contributors as noted in the AUTHORS file

    This file is part of 0MQ.

    0MQ is free software; you can redistribute it and/or modify it under
    the terms of the GNU Lesser General Public License as published by
    the Free Software Foundation; either version 3 of the License, or
    (at your option) any later version.

    0MQ is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU Lesser General Public License for more details.

    You should have received a copy of the GNU Lesser General Public License
    along with this program.  If not, see <http://www.gnu.org/licenses/>.
*/

package guide;

//  Titanic client example
//  Implements client side of http://rfc.zeromq.org/spec:9

//  Calls a TSP service
//  Returns response if successful (status code 200 OK), else NULL
//

import org.zeromq.ZFrame;
import org.zeromq.ZMsg;

public class ticlient
{
    static ZMsg
    serviceCall (mdcliapi session, String service, ZMsg request)
    {
        ZMsg reply = session.send(service, request);
        if (reply != null) {
            ZFrame status = reply.pop();
            if (status.streq("200")) {
                status.destroy();
                return reply;
            }
            else if (status.streq("400")) {
                System.out.println("E: client fatal error, aborting");
            }
            else
            if (status.streq("500")) {
                System.out.println("E: server fatal error, aborting");
            }
            reply.destroy();
        }
        return null;        //  Didn't succeed; don't care why not
    }
    public static void main (String[] args) throws Exception
    {
        boolean verbose = (args.length > 0 && args[0].equals("-v"));
        mdcliapi session = new mdcliapi("tcp://localhost:5555", verbose);

        //  1. Send 'echo' request to Titanic
        ZMsg request = new ZMsg();
        request.add("echo");
        request.add("Hello world");
        ZMsg reply = serviceCall(
                session, "titanic.request", request);

        ZFrame uuid = null;
        if (reply != null) {
            uuid = reply.pop();
            reply.destroy();
            uuid.print("I: request UUID ");
        }
        //  2. Wait until we get a reply
        while (!Thread.currentThread().isInterrupted()) {
            Thread.sleep(100);
            request = new ZMsg();
            request.add(uuid.duplicate());
            reply = serviceCall(
                    session, "titanic.reply", request);

            if (reply != null) {
                String replyString = reply.getLast().toString();
                System.out.printf ("Reply: %s\n", replyString);
                reply.destroy();

                //  3. Close request
                request = new ZMsg();
                request.add(uuid.duplicate());
                reply = serviceCall(session, "titanic.close", request);
                reply.destroy();
                break;
            }
            else {
                System.out.println("I: no reply yet, trying again...");
                Thread.sleep (5000);     //  Try again in 5 seconds
            }
        }
        uuid.destroy();
        session.destroy();
    }
}


