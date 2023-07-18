package brig.ck8s.cli.concord;

import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import jakarta.ws.rs.ext.RuntimeDelegate;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;

public class ConcordServer
{

    public static HttpServer start()
            throws IOException
    {
        System.setProperty("java.net.preferIPv4Stack", "true");
        System.setProperty("jersey.config.json.jackson.disabled.modules", "JaxbAnnotationIntrospector");

        assertPort(8001);

        HttpServer server = HttpServer.create(new InetSocketAddress("0.0.0.0", 8001), 0);
        Runtime.getRuntime().addShutdownHook(new Thread(() -> server.stop(0)));

        HttpHandler handler = RuntimeDelegate.getInstance().createEndpoint(new ConcordResourceApp(), HttpHandler.class);
        server.createContext("/", handler);

        server.start();

        return server;
    }

    private static void assertPort(int port)
    {
        try (ServerSocket unused = new ServerSocket(port, 50, InetAddress.getByName("0.0.0.0"))) {
            // do nothing
        }
        catch (IOException ex) {
            throw new RuntimeException("Can't start concord server: " + ex.getMessage());
        }
    }
}
