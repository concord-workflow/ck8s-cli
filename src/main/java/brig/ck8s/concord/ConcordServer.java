package brig.ck8s.concord;

import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import jakarta.ws.rs.ext.RuntimeDelegate;

import java.io.IOException;
import java.net.InetSocketAddress;

public class ConcordServer {

    public static HttpServer start() throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress(8001), 0);
        Runtime.getRuntime().addShutdownHook(new Thread(() -> server.stop(0)));

        HttpHandler handler = RuntimeDelegate.getInstance().createEndpoint(new ConcordResourceApp(), HttpHandler.class);
        server.createContext("/", handler);

        server.start();

        return server;
    }
}
