package brig.ck8s.actions;

import brig.ck8s.executor.ConcordConfigurationProvider;
import brig.ck8s.utils.LogUtils;
import brig.ck8s.utils.MapUtils;
import brig.ck8s.utils.Mapper;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.channels.Channels;
import java.nio.channels.FileChannel;
import java.nio.channels.ReadableByteChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.util.*;

import static brig.ck8s.utils.ExceptionUtils.*;
import static java.nio.file.StandardOpenOption.*;

public class InstallConcordCliAction {

    private static final String VERSION = "1.100.0";

    private static final String CONCORD_CLI_URL = String.format("https://repo.maven.apache.org/maven2/com/walmartlabs/concord/concord-cli/%s/concord-cli-%s-executable.jar", VERSION, VERSION);

    public static Path getCliPath() {
        return Path.of(System.getProperty("user.home")).resolve("bin").resolve("concord-cli");
    }

    public int perform() {
        installCli();

        Path cfgPath = installMvnConf();

        populateMvnConfFromSettingsXml(cfgPath);

        LogUtils.info("done");

        return 0;
    }

    private static void installCli() {
        Path dest = getCliPath();

        if (Files.notExists(dest.getParent())) {
            try {
                Files.createDirectories(dest.getParent());
            } catch (Exception e) {
                throwError("Error creating concord-cli directory: ", e);
            }
        }

        LogUtils.info("Installing concord-cli to {}", dest);

        try (InputStream is = new URL(CONCORD_CLI_URL).openStream();
             ReadableByteChannel sourceChannel = Channels.newChannel(is);
             FileChannel destChannel = FileChannel.open(dest, WRITE, CREATE, TRUNCATE_EXISTING)) {

            destChannel.transferFrom(sourceChannel, 0, Long.MAX_VALUE);
        } catch (Exception e) {
            throwError("Error downloading concord-cli: ", e);
        }

        Set<PosixFilePermission> perms = new HashSet<>();
        perms.add(PosixFilePermission.OWNER_READ);
        perms.add(PosixFilePermission.OWNER_WRITE);
        perms.add(PosixFilePermission.OWNER_EXECUTE);
        try {
            Files.setPosixFilePermissions(dest, perms);
        } catch (Exception e) {
            LogUtils.error("Error setting permissions to concord-cli: " + e.getMessage());
        }
    }

    private Path installMvnConf() {
        Path cfgPath = Path.of(System.getProperty("user.home")).resolve(".concord").resolve("mvn.json");
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

            LogUtils.info("Installing concord mvn config to {}", cfgPath);

            Files.writeString(cfgPath, content, CREATE, TRUNCATE_EXISTING);
        } catch (IOException e) {
            throwError("Can't load default concord config. This is most likely a bug: ", e);
        }

        return cfgPath;
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
        nexusRepo.put("username", "username");
        nexusRepo.put("password", "password");

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
