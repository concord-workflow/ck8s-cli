package brig.ck8s.executor;

import brig.ck8s.cfg.ConcordConfigurationProvider;
import brig.ck8s.utils.LogUtils;
import brig.ck8s.utils.MapUtils;
import brig.ck8s.utils.Mapper;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static brig.ck8s.utils.ExceptionUtils.throwError;
import static java.nio.file.StandardOpenOption.CREATE;
import static java.nio.file.StandardOpenOption.TRUNCATE_EXISTING;

public class MvnJsonProvider {

    public Path get() {
        Path cfgPath = Path.of(System.getProperty("user.home")).resolve(".ck8s").resolve("mvn.json");
        if (Files.exists(cfgPath)) {
            return cfgPath;
        }

        installFromTemplate(cfgPath);
        populateMvnConfFromSettingsXml(cfgPath);
        return cfgPath;
    }

    private void installFromTemplate(Path cfgPath) {
        if (Files.notExists(cfgPath.getParent())) {
            try {
                Files.createDirectories(cfgPath.getParent());
            } catch (Exception e) {
                throwError("Error creating concord-cli directory: ", e);
            }
        }

        try (InputStream in = ConcordConfigurationProvider.class.getResourceAsStream("/templates/mvn.json")) {
            if (in == null) {
                throw new RuntimeException("Can't find mvn.json template. This is most likely a bug.");
            }

            String content = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            content = content.replace("<USER_HOME>", System.getProperty("user.home"));

            Files.writeString(cfgPath, content, CREATE, TRUNCATE_EXISTING);
        } catch (IOException e) {
            throwError("Can't load default concord config. This is most likely a bug: ", e);
        }
    }

    private void populateMvnConfFromSettingsXml(Path cfgPath) {
        Path settingsXmlPath = Path.of(System.getProperty("user.home")).resolve(".m2").resolve("settings.xml");
        if (Files.notExists(settingsXmlPath)) {
            return;
        }

        Map<String, Object> settingsXml = Mapper.xmlMapper().readMap(settingsXmlPath);
        Map<String, Object> nexusServer = getServer(settingsXml, "nexus");
        String username = MapUtils.getString(nexusServer, "username");
        String password = MapUtils.getString(nexusServer, "password");
        if (username == null || password == null) {
            LogUtils.info("Populating mvn conf from settings.xml -> nexus credentials not found", cfgPath);
            return;
        }

        Map<String, Object> nexusRepo = new HashMap<>();
        nexusRepo.put("id", "nexus");
        nexusRepo.put("url", "https://nexus.eng.aetion.com/repository/aetion-maven");
        nexusRepo.put("username", username);
        nexusRepo.put("password", password);

        Map<String, Object> options = Mapper.jsonMapper().readMap(cfgPath);
        List<Map<String, Object>> repositories = MapUtils.getList(options, "repositories");
        repositories.add(nexusRepo);

        Mapper.jsonMapper().write(cfgPath, options);

        LogUtils.info("Added nexus config to mvn conf from settings.xml");
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> getServer(Map<String, Object> settingsXml, String id) {
        Object servers = MapUtils.get(settingsXml, "servers.server", null, Object.class);
        if (servers instanceof List) {
            for (Map<String, Object> server : (List<Map<String, Object>>)servers) {
                String serverId = MapUtils.getString(server, "id");
                if (id.equals(serverId)) {
                    return server;
                }
            }
        } else if (servers instanceof Map) {
            Map<String, Object> server = (Map<String, Object>)servers;
            String serverId = MapUtils.getString(server, "id");
            if (id.equals(serverId)) {
                return server;
            }
        }
        return Collections.emptyMap();
    }
}
