package dev.ybrig.ck8s.cli.codecoverage;

import com.walmartlabs.concord.cli.runner.CliImportsNormalizer;
import com.walmartlabs.concord.cli.runner.CliRepositoryExporter;
import com.walmartlabs.concord.client2.*;
import com.walmartlabs.concord.common.IOUtils;
import com.walmartlabs.concord.dependencymanager.DependencyManager;
import com.walmartlabs.concord.dependencymanager.DependencyManagerConfiguration;
import com.walmartlabs.concord.imports.DefaultImportManager;
import com.walmartlabs.concord.imports.ImportManagerFactory;
import com.walmartlabs.concord.imports.NoopImportManager;
import com.walmartlabs.concord.runtime.v2.ProjectLoaderV2;
import com.walmartlabs.concord.runtime.v2.model.ProcessDefinition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.Set;
import java.util.UUID;

public class Main {

    private static final Logger log = LoggerFactory.getLogger(Main.class);

    public static void main(String[] args) throws Exception {
        String token = "i18cqx8g0EkBPg7ThLXwGQ";
        UUID processId = UUID.fromString("faf55071-91a4-4eff-88a7-ef9dab1d0b4a");
        String baseUrl = "http://localhost:8001";

        DefaultApiClientFactory apiClientFactory = new DefaultApiClientFactory(baseUrl);
        ApiClient apiClient = apiClientFactory.create(ApiClientConfiguration.builder().apiKey(token).build());

        ProcessEventsApi api = new ProcessEventsApi(apiClient);

        Path basePath = Path.of("/Users/brig/prj/github/concord/examples/hello_world/coverage");
        IOUtils.deleteRecursively(basePath);
        Files.createDirectories(basePath);

        ProcessApi process = new ProcessApi(apiClient);
        try (InputStream is = process.downloadState(processId)) {
            IOUtils.unzip(is, basePath);
        }

        Path depsCacheDir = Path.of("/tmp/deps");
        Path repoCacheDir = Path.of("/tmp/repo");

        DependencyManager dependencyManager = new DependencyManager(DependencyManagerConfiguration.of(depsCacheDir));
        var importManager = new ImportManagerFactory(dependencyManager,
                new CliRepositoryExporter(repoCacheDir), Collections.emptySet())
                .create();

        ProjectLoaderV2 loader = new ProjectLoaderV2(importManager);
        ProcessDefinition processDefinition = loader.load(basePath, new CliImportsNormalizer("https://github.com", true, "master"), null).getProjectDefinition();

        LcovReportProducer report = new LcovReportProducer();
        report.init(processDefinition);

        long processedEvents = 0;
        Long fromId = null;
        EventFetcher fetcher = new EventFetcher(api);
//        var allEvents = new ArrayList<>();
        while (true) {
            var result = fetcher.fetch(processId, fromId);
            if (!result.isEmpty()) {
                fromId = result.get(result.size() - 1).getSeqId();
            } else {
                break;
            }

            report.onEvents(result);

            processedEvents += result.size();
            log.info("processed {} events", processedEvents);
//            log.info("{}", result);
//            allEvents.addAll(result);
        }

//        Mapper.yamlMapper()
//                .getObjectMapper()
//                .registerModule(new JavaTimeModule());
//        Mapper.yamlMapper()
//                .write(Path.of("/tmp/events.yaml"), allEvents);

        report.produce(basePath);
    }

    private void processImports() {

    }
}
