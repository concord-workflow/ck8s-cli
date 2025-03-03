package dev.ybrig.ck8s.cli.forms;

import com.walmartlabs.concord.sdk.Constants;
import dev.ybrig.ck8s.cli.Ck8sPathOptionsMixin;
import dev.ybrig.ck8s.cli.cfg.CliConfigurationProvider;
import dev.ybrig.ck8s.cli.common.*;
import dev.ybrig.ck8s.cli.concord.ConcordProcess;
import dev.ybrig.ck8s.cli.executor.RemoteFlowExecutorV2;
import dev.ybrig.ck8s.cli.model.ConcordProfile;
import dev.ybrig.ck8s.cli.utils.Ck8sPayloadArchiver;
import dev.ybrig.ck8s.cli.utils.LogUtils;
import org.eclipse.jetty.client.Request;
import org.eclipse.jetty.http.*;
import org.eclipse.jetty.http.content.HttpContent;
import org.eclipse.jetty.http.content.ResourceHttpContent;
import org.eclipse.jetty.http.content.ResourceHttpContentFactory;
import org.eclipse.jetty.proxy.ProxyHandler;
import org.eclipse.jetty.server.Handler;
import org.eclipse.jetty.server.Response;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.handler.ContextHandler;
import org.eclipse.jetty.server.handler.ContextHandlerCollection;
import org.eclipse.jetty.server.handler.EventsHandler;
import org.eclipse.jetty.server.handler.ResourceHandler;
import org.eclipse.jetty.util.Callback;
import org.eclipse.jetty.util.resource.Resource;
import org.eclipse.jetty.util.resource.ResourceFactory;
import picocli.CommandLine;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.ByteBuffer;
import java.nio.channels.Channels;
import java.nio.channels.ReadableByteChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.Callable;

@CommandLine.Command(name = "serve-forms",
        description = {"Serve forms"}
)
public class ServeFormsCommand implements Callable<Integer> {

    private static final String DATA_FILE_TEMPLATE = "data = %s;";
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
        var cfg = CliConfigurationProvider.get();
        var instanceProfile = CliConfigurationProvider.getConcordProfile(profile);
        var ck8s = new Ck8sPath(ck8sPathOptions.getCk8sPath(), ck8sPathOptions.getCk8sExtPath());

        var orgName = Ck8sUtils.streamClusterYaml(ck8s).map(p -> {
                    try {
                        var clusterRequest = Ck8sUtils.buildClusterRequest(ck8s, p);
                        return MapUtils.assertString(clusterRequest, "organization.name");
                    } catch (Exception e) {
                        throw new RuntimeException("Error parsing cluster definition file " + p + ": " + e.getMessage());
                    }
                })
                .filter(Objects::nonNull)
                .findFirst()
                .orElseThrow(() -> new RuntimeException("Can't find organization.name in cluster definitions"));

        Map<String, Object> defaultDataJs = new HashMap<>();
        defaultDataJs.put("concordHost", instanceProfile.baseUrl());
        defaultDataJs.put("org", orgName);
        defaultDataJs.put("project", concordProject);
        defaultDataJs.put("repo", "ck8s");

        if (formsDir == null) {
            formsDir = ck8sPathOptions.getCk8sPath();

            var forms = cfg.forms();
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

        var server = new Server(new InetSocketAddress("localhost", port));

        var contextCollection = new ContextHandlerCollection();

        var handler = new ResourceHandler() {
            @Override
            protected HttpContent.Factory newHttpContentFactory() {
                return new FormResourceHttpContentFactory(getBaseResource(), getMimeTypes(), defaultDataJs);
            }
        };

        handler.setBaseResource(ResourceFactory.of(handler).newResource(formsDir));
        handler.setDirAllowed(true);
        handler.setAcceptRanges(true);

        var ch = new ContextHandler(handler, "/");
        ch.setAliasChecks(List.of((pathInContext, resource) -> true));
        contextCollection.addHandler(ch);

        var chResources = new ContextHandler(handler, "/api/plugin/repositorybrowser/v1");
        chResources.setAllowNullPathInContext(true);
        chResources.setAliasChecks(List.of((pathInContext, resource) -> true));
        contextCollection.addHandler(chResources);

        var processExecutorHandler = new ContextHandler(new ProcessExecutorHandler(ck8s, instanceProfile), "/api/ck8s/v3/process");
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
                var originalUri = clientToProxyRequest.getHttpURI();

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

    private static Map<String, Object> readDataJs(Path p) {
        try {
            var content = Files.readString(p);
            return Mapper.jsonMapper().readMap(content.substring("data = ".length()));
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid data js format: " + e.getMessage());
        }
    }

    private static String writeDataJs(Map<String, Object> data) {
        return String.format(DATA_FILE_TEMPLATE, Mapper.jsonMapper().writeAsString(data));
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
            } else if (pathInContext.matches(".*common-.*\\.js$")) {
                return getCommonJsContent(pathInContext);
            } else if (pathInContext.endsWith("jetty-dir.css")) {
                return getResourceContent("/webapp/jetty-dir.css", "text/css");
            } else if (pathInContext.endsWith("favicon.ico")) {
                return getResourceContent("/webapp/favicon.ico", "image/x-icon");
            }

            return super.getContent(pathInContext);
        }

        private HttpContent getDataJsContent(String pathInContext) {
            Map<String, Object> dataJs = new HashMap<>();
            var dataJsResource = baseResource.resolve(pathInContext);
            if (dataJsResource != null && dataJsResource.exists()) {
                dataJs.putAll(readDataJs(dataJsResource.getPath()));
            }
            dataJs.putAll(defaultDataJs);

            var dataJsContent = writeDataJs(dataJs);

            return new ResourceHttpContent(new InMemResource(dataJsContent.getBytes(StandardCharsets.UTF_8)), "text/javascript");
        }

        private  HttpContent getCommonJsContent(String pathInContext) throws IOException {
            var resource = baseResource.resolve("assets/js/" + Path.of(pathInContext).getFileName());
            if (resource == null) {
                throw new IOException("Could not find common.js file");
            }
            
            var p = resource.getPath();
            if (Files.notExists(p)) {
                LogUtils.warn("Can't find common-js file: {}", p);
                return null;
            }
            return new ResourceHttpContent(new InMemResource(Files.readString(p).getBytes(StandardCharsets.UTF_8)), "text/javascript");
        }

        private HttpContent getResourceContent(String name, String mimeType) {
            var url = getClass().getResource(name);
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

        ProcessExecutorHandler(Ck8sPath ck8s, ConcordProfile concordProfile) {
            this.ck8s = ck8s;
            this.concordProfile = concordProfile;
        }

        @Override
        public boolean handle(org.eclipse.jetty.server.Request request, Response response, Callback callback) {
            var contentType = request.getHeaders().get(HttpHeader.CONTENT_TYPE);
            if (contentType != null && contentType.startsWith(MimeTypes.Type.MULTIPART_FORM_DATA.asString())) {
                // Extract the multipart boundary.
                var boundary = MultiPart.extractBoundary(contentType);

                // Create and configure the multipart parser.
                var parser = new MultiPartFormData.Parser(boundary);
                // By default, uploaded files are stored in this directory, to
                // avoid to read the file content (which can be large) in memory.
                parser.setFilesDirectory(Path.of("/tmp/ck8s-cli/forms"));
                // Convert the request content into parts.
                var completableParts = parser.parse(request);

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
                        } catch (IllegalArgumentException e) {
                            Response.writeError(request, response, callback, HttpStatus.BAD_REQUEST_400, "invalid request: " + e.getMessage());
                            return;
                        } catch (Exception e) {
                            Response.writeError(request, response, callback, HttpStatus.INTERNAL_SERVER_ERROR_500, e.getMessage());
                            return;
                        }

                        var type = MimeTypes.Type.APPLICATION_JSON;

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

        private static String assertString(MultiPartFormData.Parts parts, String key) {
            var result = getString(parts, key);
            if (result == null) {
                throw new IllegalArgumentException("Missing mandatory request part: " + key);
            }
            return result;
        }

        private static String getString(MultiPartFormData.Parts parts, String key) {
            var part = parts.getFirst(key);
            if (part == null) {
                return null;
            }
            return part.getContentAsString(StandardCharsets.UTF_8);
        }

        private static void assertMandatoryParts(MultiPartFormData.Parts parts) {
            var mandatoryAttrs = List.of("org", "project");
            for (var mandatoryAttr : mandatoryAttrs) {
                var part = parts.getFirst(mandatoryAttr);
                if (part == null) {
                    throw new IllegalArgumentException("Missing mandatory request part: " + mandatoryAttr);
                }
                if (part.getLength() == 0) {
                    throw new IllegalArgumentException(part.getName() + " with empty value");
                }
            }
        }

        private static Map<String, Object> getMap(MultiPartFormData.Parts parts, String key) {
            var p = parts.getFirst(key);
            if (p == null) {
                return Map.of();
            }
            return Mapper.jsonMapper().readMap(p.getContentAsString(StandardCharsets.UTF_8));
        }

        private UUID startProcess(MultiPartFormData.Parts parts) {
            var request = prepareRequest(parts);

            // TODO: allow local concord cli...
            var executor = new RemoteFlowExecutorV2(concordProfile.baseUrl(), concordProfile.apiKey(), 30, 30);

            var args = MapUtils.assertMap(request, "request.arguments");

            ConcordProcess process;
            try (var archive = prepareArchive(ck8s, request, MapUtils.assertString(args, Ck8sConstants.Arguments.CLIENT_CLUSTER))) {
                process = executor.execute(request);
            }

            return process.instanceId();
        }

        private static Map<String, Object> prepareRequest(MultiPartFormData.Parts parts) {
            var request = new HashMap<String, Object>();

            var keys = new String[] {
                    Constants.Multipart.ORG_NAME,
                    Constants.Multipart.PROJECT_NAME,
                    Constants.Request.DEBUG_KEY
            };

            for (var key : keys) {
                putString(key, parts, request);
            }

            var ck8sRef = getString(parts, Constants.Request.REPO_BRANCH_OR_TAG);
            if (ck8sRef != null) {
                request.put(Constants.Request.REPO_BRANCH_OR_TAG, ck8sRef);
                putString(Constants.Multipart.REPO_NAME, parts, request);
            }

            var requestParams = getMap(parts, "request");
            assertMandatoryRequestParams(requestParams);

            request.put("request", requestParams);

            return request;
        }

        private static void assertMandatoryRequestParams(Map<String, Object> requestParams) {
            var args = MapUtils.getMap(requestParams, Constants.Request.ARGUMENTS_KEY, Map.of());

            var mandatoryArgs = List.of(Ck8sConstants.Arguments.CLIENT_CLUSTER, Ck8sConstants.Arguments.FLOW);
            for (var key : mandatoryArgs) {
                var arg = args.get(key);
                if (arg == null) {
                    throw new IllegalArgumentException("Missing mandatory request part: request.arguments." + key);
                }
            }
        }

        private static void putString(String name, MultiPartFormData.Parts parts, HashMap<String, Object> request) {
            var result = getString(parts, name);
            if (result != null) {
                request.put(name, result);
            }
        }

        private static Ck8sPayloadArchiver.Archive prepareArchive(Ck8sPath ck8s, Map<String, Object> request, String clusterAlias) {
            var archive = Ck8sPayloadArchiver.archive(ck8s, clusterAlias);
            request.put("archive", archive.path());
            return archive;
        }
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

        private static void print(String what) {
            System.out.println(what);
        }

        private static void dumpRequest(org.eclipse.jetty.server.Request request) {
            var sb = new StringBuilder();

            sb.append(">>> REQUEST")
                    .append(" (thread: ").append(Thread.currentThread().getName()).append(") ")
                    .append(">>>\n")
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
            var sb = new StringBuilder();

            sb.append("<<< RESPONSE")
                    .append(" (thread: ").append(Thread.currentThread().getName()).append(") ")
                    .append("<<<\n")
                    .append(status)
                    .append("\n");

            sb.append(headers);

            print(sb.toString());
        }

        private static void dumpResponseEnd(boolean last, ByteBuffer content) {
            var sb = new StringBuilder();

            sb.append(byteBufferToString(content));

            if (last) {
                sb.append("\n");
                sb.append("<<<<<<<<<<<<<");
            }

            print(sb.toString());
        }

        private static String byteBufferToString(ByteBuffer buffer) {
            var duplicate = buffer.duplicate();

            var bytes = new byte[duplicate.remaining()];

            duplicate.get(bytes);

            return new String(bytes, StandardCharsets.UTF_8);
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
    }
}
