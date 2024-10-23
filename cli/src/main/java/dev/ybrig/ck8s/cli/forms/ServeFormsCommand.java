package dev.ybrig.ck8s.cli.forms;

import dev.ybrig.ck8s.cli.Ck8sPathOptionsMixin;
import dev.ybrig.ck8s.cli.cfg.CliConfigurationProvider;
import dev.ybrig.ck8s.cli.common.*;
import dev.ybrig.ck8s.cli.common.verify.Ck8sPayloadVerifier;
import dev.ybrig.ck8s.cli.concord.ConcordProcess;
import dev.ybrig.ck8s.cli.executor.*;
import dev.ybrig.ck8s.cli.model.CliConfiguration;
import dev.ybrig.ck8s.cli.model.ConcordProfile;
import dev.ybrig.ck8s.cli.utils.LogUtils;
import org.eclipse.jetty.client.Request;
import org.eclipse.jetty.http.*;
import org.eclipse.jetty.http.content.*;
import org.eclipse.jetty.proxy.ProxyHandler;
import org.eclipse.jetty.server.Handler;
import org.eclipse.jetty.server.Response;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.handler.*;
import org.eclipse.jetty.util.Callback;
import org.eclipse.jetty.util.resource.Resource;
import org.eclipse.jetty.util.resource.ResourceFactory;
import picocli.CommandLine;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URL;
import java.nio.ByteBuffer;
import java.nio.channels.Channels;
import java.nio.channels.ReadableByteChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;

@CommandLine.Command(name = "serve-forms",
        description = {"Serve forms"}
)
public class ServeFormsCommand implements Callable<Integer> {

    @CommandLine.Mixin
    Ck8sPathOptionsMixin ck8sPathOptions;

    @CommandLine.Option(names = {"--port"}, description = "HTTP server port")
    int port = 8000;

    @CommandLine.Option(names = {"--forms-dir"}, description = "Path to form")
    Path formsDir;

    @CommandLine.Option(required = true, names = {"-p", "--profile"}, description = "concord instance profile name")
    String profile;

    @CommandLine.Option(names = {"-V", "--verbose"}, description = {
            "Specify multiple -v options to increase verbosity. For example, `-V -V -V` or `-VVV`",
            "-V debug logs"})
    boolean verbose = false;

    @CommandLine.Option(names = {"--target-root"}, description = "path to target dir")
    Path targetRootPath = Path.of(System.getProperty("java.io.tmpdir")).resolve("ck8s-cli");

    @CommandLine.Option(names = {"--concord-project"}, description = "Default concord project where to start concord processes")
    String concordProject = "form-tests";

    @Override
    public Integer call() throws Exception {
        CliConfiguration cfg = CliConfigurationProvider.get();
        ConcordProfile instanceProfile = CliConfigurationProvider.getConcordProfile(profile);

        Map<String, Object> defaultDataJs = new HashMap<>();
        defaultDataJs.put("concordHost", instanceProfile.baseUrl());
        defaultDataJs.put("org", "Default");
        defaultDataJs.put("project", concordProject);

        if (formsDir == null) {
            formsDir = ck8sPathOptions.getCk8sPath();

            CliConfiguration.Forms forms = cfg.forms();
            if (forms != null) {
                formsDir = ck8sPathOptions.getCk8sPath().resolve(forms.path());
            }
        }

        if (Files.notExists(formsDir)) {
            LogUtils.error("Forms directory does not exist: " + formsDir);
            return -1;
        }

        if (verbose) {
            LogUtils.info("redirecting api requests to: {}", instanceProfile.baseUrl());
            LogUtils.info("default data.js values: {}", defaultDataJs);
            LogUtils.info("forms dir: {}", formsDir);
        }

        Ck8sPath ck8s = new Ck8sPath(ck8sPathOptions.getCk8sPath(), ck8sPathOptions.getCk8sExtPath());

        Server server = new Server(new InetSocketAddress("localhost", port));

        ContextHandlerCollection contextCollection = new ContextHandlerCollection();

        ResourceHandler handler = new ResourceHandler() {
            @Override
            protected HttpContent.Factory newHttpContentFactory() {
                return new FormResourceHttpContentFactory(getBaseResource(), getMimeTypes(), defaultDataJs);
            }
        };

        handler.setBaseResource(ResourceFactory.of(handler).newResource(formsDir));
        handler.setDirAllowed(true);
        handler.setAcceptRanges(true);

        ContextHandler ch = new ContextHandler(handler, "/");
        ch.setAliasChecks(List.of((pathInContext, resource) -> true));
        contextCollection.addHandler(ch);

        ContextHandler processExecutorHandler = new ContextHandler(new ProcessExecutorHandler(ck8s, instanceProfile, targetRootPath, concordProject), "/api/ck8s/v2/process");
        processExecutorHandler.setAllowNullPathInContext(true);
        contextCollection.addHandler(processExecutorHandler);

        Handler proxyHandler = createProxyHandler(HttpURI.build(instanceProfile.baseUrl()), instanceProfile);
        if (verbose) {
            proxyHandler = new DebugHandler(proxyHandler);
        }
        contextCollection.addHandler(new ContextHandler(proxyHandler, "/api"));

        server.setHandler(contextCollection);

        server.start();

        LogUtils.info("Server started at {}", server.getURI());

        server.join();

        return 0;
    }

    private static ProxyHandler createProxyHandler(HttpURI newBaseUri, ConcordProfile instanceProfile) {

        ProxyHandler proxyHandler = new ProxyHandler.Forward() {
            @Override
            protected HttpURI rewriteHttpURI(org.eclipse.jetty.server.Request clientToProxyRequest) {
                HttpURI originalUri = clientToProxyRequest.getHttpURI();

                return HttpURI.build(originalUri)
                        .scheme(newBaseUri.getScheme())
                        .host(newBaseUri.getHost())
                        .port(newBaseUri.getPort())
                        .user(newBaseUri.getUser());
            }

            @Override
            protected void addProxyHeaders(org.eclipse.jetty.server.Request clientToProxyRequest, Request proxyToServerRequest) {
                proxyToServerRequest.headers(headers -> headers.add(HttpHeader.AUTHORIZATION, instanceProfile.apiKey()));
            }
        };

        proxyHandler.setProxyToServerHost(newBaseUri.getHost());

        return proxyHandler;
    }

    private static class FormResourceHttpContentFactory extends ResourceHttpContentFactory {

        private final Resource baseResource;
        private final Map<String, Object> defaultDataJs;

        public FormResourceHttpContentFactory(Resource baseResource, MimeTypes mimeTypes, Map<String, Object> defaultDataJs) {
            super(ResourceFactory.of(baseResource), mimeTypes);

            this.baseResource = baseResource;
            this.defaultDataJs = defaultDataJs;
        }

        @Override
        public HttpContent getContent(String pathInContext) throws IOException {
            if (pathInContext.endsWith("data.js")) {
                return getDataJsContent(pathInContext);
            } else if (pathInContext.endsWith("jetty-dir.css")) {
                return getResourceContent("/webapp/jetty-dir.css", "text/css");
            } else if (pathInContext.endsWith("favicon.ico")) {
                return getResourceContent("/webapp/favicon.ico", "image/x-icon");
            }

            return super.getContent(pathInContext);
        }

        private HttpContent getDataJsContent(String pathInContext) {
            Map<String, Object> dataJs = new HashMap<>();
            Resource dataJsResource = baseResource.resolve(pathInContext);
            if (dataJsResource != null && dataJsResource.exists()) {
                dataJs.putAll(readDataJs(dataJsResource.getPath()));
            }
            dataJs.putAll(defaultDataJs);

            String dataJsContent = writeDataJs(dataJs);

            return new ResourceHttpContent(new InMemResource(dataJsContent.getBytes(StandardCharsets.UTF_8)), "text/javascript");
        }

        private HttpContent getResourceContent(String name, String mimeType) {
            URL url = getClass().getResource(name);
            if (url == null) {
                throw new IllegalStateException("Missing server resource: " + name);
            }

            return new ResourceHttpContent(ResourceFactory.root().newMemoryResource(url), mimeType);
        }
    }

    private static class InMemResource extends Resource {

        private final Instant created = Instant.now();

        private final byte[] content;

        public InMemResource(byte[] content) {
            this.content = content;
        }

        @Override
        public Instant lastModified() {
            return created;
        }

        @Override
        public Path getPath() {
            return null;
        }

        @Override
        public boolean isDirectory() {
            return false;
        }

        @Override
        public boolean isReadable() {
            return true;
        }

        @Override
        public URI getURI() {
            return null;
        }

        @Override
        public String getName() {
            return "";
        }

        @Override
        public String getFileName() {
            return "";
        }

        @Override
        public Resource resolve(String subUriPath) {
            return null;
        }

        @Override
        public InputStream newInputStream() {
            return new ByteArrayInputStream(content);
        }

        @Override
        public ReadableByteChannel newReadableByteChannel() {
            return Channels.newChannel(newInputStream());
        }
    }

    static class ProcessExecutorHandler extends Handler.Abstract.NonBlocking {

        private final Ck8sPath ck8s;
        private final ConcordProfile concordProfile;
        private final Path targetRootPath;
        private final String project;

        ProcessExecutorHandler(Ck8sPath ck8s, ConcordProfile concordProfile, Path targetRootPath, String project) {
            this.ck8s = ck8s;
            this.concordProfile = concordProfile;
            this.targetRootPath = targetRootPath;
            this.project = project;
        }

        @Override
        public boolean handle(org.eclipse.jetty.server.Request request, Response response, Callback callback) {
            String contentType = request.getHeaders().get(HttpHeader.CONTENT_TYPE);
            if (contentType != null && contentType.startsWith(MimeTypes.Type.MULTIPART_FORM_DATA.asString())) {
                // Extract the multipart boundary.
                String boundary = MultiPart.extractBoundary(contentType);

                // Create and configure the multipart parser.
                MultiPartFormData.Parser parser = new MultiPartFormData.Parser(boundary);
                // By default, uploaded files are stored in this directory, to
                // avoid to read the file content (which can be large) in memory.
                parser.setFilesDirectory(Path.of("/tmp"));
                // Convert the request content into parts.
                CompletableFuture<MultiPartFormData.Parts> completableParts = parser.parse(request);

                // When all the request content has arrived, process the parts.
                completableParts.whenComplete((parts, failure) ->
                {
                    if (failure == null) {
                        try {
                            assertMandatoryParts(parts);
                        } catch (Exception e) {
                            Response.writeError(request, response, callback, HttpStatus.BAD_REQUEST_400, "invalid request: " + e.getMessage());
                            return;
                        }

                        UUID instanceId;
                        try {
                            instanceId = startProcess(parts);
                        } catch (Exception e) {
                            Response.writeError(request, response, callback, e);
                            return;
                        }

                        MimeTypes.Type type = MimeTypes.Type.APPLICATION_JSON;

                        response.getHeaders().put(type.getContentTypeField(StandardCharsets.UTF_8));
                        response.setStatus(HttpStatus.OK_200);
                        response.write(true, ByteBuffer.wrap(("{\"ok\": true, \"instanceId\": \"" + instanceId + "\"}").getBytes()), callback);

                        callback.succeeded();
                    } else {
                        // Reading the request content failed.
                        // Send an error response, completing the callback.
                        Response.writeError(request, response, callback, failure);
                    }
                });

                // The callback will be eventually completed in all cases, return true.
                return true;
            } else {
                // Send an error response, completing the callback, and returning true.
                Response.writeError(request, response, callback, HttpStatus.BAD_REQUEST_400, "invalid request");
                return true;
            }
        }

        private UUID startProcess(MultiPartFormData.Parts parts) {
            Map<String, Object> arguments = getArguments(parts);
            String flow = assertString(parts, "flow");
            String clusterAlias = assertString(parts, "clusterAlias");

            Ck8sPayloadVerifier verifier = new Ck8sPayloadVerifier();
            Ck8sFlows ck8sFlows = new Ck8sFlowBuilder(ck8s, targetRootPath, verifier)
                    .includeTests(true)
                    .build();

            Map<String, Object> clusterRequest = Ck8sUtils.buildClusterRequest(ck8s, clusterAlias);

            Ck8sPayload payload = Ck8sPayload.builder()
                    .debug(true)
                    .arguments(MapUtils.merge(Map.of("clusterRequest", clusterRequest), arguments))
                    .ck8sFlows(ck8sFlows)
                    .project(project)
                    .build();

            // TODO: allow local concord cli...
            RemoteFlowExecutor executor = new RemoteFlowExecutor(concordProfile.baseUrl(), concordProfile.apiKey());
            ConcordProcess process = executor.execute(clusterAlias, payload, flow, List.of());
            return process.instanceId();
        }

        private static Map<String, Object> getArguments(MultiPartFormData.Parts parts) {
            MultiPart.Part argsPart = parts.getFirst("arguments");
            if (argsPart == null) {
                return Map.of();
            }

            return Mapper.jsonMapper().readMap(argsPart.getContentAsString(StandardCharsets.UTF_8));
        }

        private static String assertString(MultiPartFormData.Parts parts, String key) {
            MultiPart.Part part = parts.getFirst(key);
            if (part == null) {
                throw new IllegalStateException("Missing required part " + key + " in request");
            }
            if (part.getLength() == 0) {
                throw new IllegalStateException("Missing value for part " + key + " in request");
            }
            return part.getContentAsString(StandardCharsets.UTF_8);
        }

        private static void assertMandatoryParts(MultiPartFormData.Parts parts) {
            List<String> mandatoryAttrs = List.of("org", "project", "flow", "clusterAlias");
            for (String mandatoryAttr : mandatoryAttrs) {
                MultiPart.Part part = parts.getFirst(mandatoryAttr);
                if (part == null) {
                    throw new IllegalArgumentException("Missing mandatory request part: " + mandatoryAttr);
                }
                if (part.getLength() == 0) {
                    throw new IllegalArgumentException(part.getName() + " with empty value");
                }
            }
        }
    }

    private static Map<String, Object> readDataJs(Path p) {
        try {
            String content = Files.readString(p);
            return Mapper.jsonMapper().readMap(content.substring("data = ".length()));
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid data js format: " + e.getMessage());
        }
    }

    private static final String DATA_FILE_TEMPLATE = "data = %s;";

    private static String writeDataJs(Map<String, Object> data) {
        return String.format(DATA_FILE_TEMPLATE, Mapper.jsonMapper().writeAsString(data));
    }


    private static class DebugHandler extends EventsHandler {

        public DebugHandler(Handler handler) {
            super(handler);
        }

        @Override
        public boolean handle(org.eclipse.jetty.server.Request request, Response response, Callback callback) throws Exception {
            dumpRequest(request);

            return super.handle(request, response, callback);
        }

        @Override
        protected void onResponseBegin(org.eclipse.jetty.server.Request request, int status, HttpFields headers) {
            dumpResponseBegin(status, headers);

            super.onResponseBegin(request, status, headers);
        }

        @Override
        protected void onResponseWrite(org.eclipse.jetty.server.Request request, boolean last, ByteBuffer content) {
            dumpResponseEnd(last, content);

            super.onResponseWrite(request, last, content);
        }

        private static void print(String what) {
            System.out.println(what);
        }

        private static void dumpRequest(org.eclipse.jetty.server.Request request) throws IOException {
            StringBuilder sb = new StringBuilder();

            sb.append(">>> REQUEST")
                    .append(" (thread: ").append(Thread.currentThread().getName()).append(") ")
                    .append( ">>>\n")
                    .append(request.getMethod())
                    .append(" ")
                    .append(request.getHttpURI())
                    .append("\n");

            sb.append(request.getHeaders());

            sb.append("\n");
            sb.append(">>>>>>>>>>>>>");

            print(sb.toString());
        }

        private static void dumpResponseBegin(int status, HttpFields headers) {
            StringBuilder sb = new StringBuilder();

            sb.append("<<< RESPONSE")
                    .append(" (thread: ").append(Thread.currentThread().getName()).append(") ")
                    .append( "<<<\n")
                    .append(status)
                    .append("\n");

            sb.append(headers);

            print(sb.toString());
        }

        private static void dumpResponseEnd(boolean last, ByteBuffer content) {
            StringBuilder sb = new StringBuilder();

            sb.append(byteBufferToString(content));

            if (last) {
                sb.append("\n");
                sb.append("<<<<<<<<<<<<<");
            }

            print(sb.toString());
        }

        private static String byteBufferToString(ByteBuffer buffer) {
            ByteBuffer duplicate = buffer.duplicate();

            byte[] bytes = new byte[duplicate.remaining()];

            duplicate.get(bytes);

            return new String(bytes, StandardCharsets.UTF_8);
        }
    }
}
