package dev.ybrig.ck8s.cli.selfupdate;

import dev.ybrig.ck8s.cli.CliApp;
import dev.ybrig.ck8s.cli.common.VersionProvider;
import dev.ybrig.ck8s.cli.utils.LogUtils;
import picocli.CommandLine;

import java.io.FileOutputStream;
import java.net.URL;
import java.net.URLDecoder;
import java.nio.channels.Channels;
import java.nio.channels.ReadableByteChannel;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.Callable;

@CommandLine.Command(name = "self-update",
        description = {"Update ck8s-cli to the latest version"},
        helpCommand = true
)
public class SelfUpdateCommand
        implements Callable<Integer>
{

    private static final String downloadTemplate = "https://github.com/concord-workflow/ck8s-cli/releases/download/%s/ck8s-cli-%s-executable.jar";

    @Override
    public Integer call()
            throws Exception
    {
        String currentVersion = VersionProvider.get();
        String latestVersion = new GitHubLatestReleaseFinder().find("concord-workflow", "ck8s-cli");

        LogUtils.info("Current version: {}, latest version: {}", currentVersion, latestVersion);
        if (currentVersion.equals(latestVersion)) {
            LogUtils.info("Already up-to-date");
            return 0;
        }

        String cliPath = URLDecoder.decode(CliApp.class.getProtectionDomain().getCodeSource().getLocation().getPath(), StandardCharsets.UTF_8);

        LogUtils.info("Updating '{}' to '{}' version...", cliPath, latestVersion);

        URL url = new URL(String.format(downloadTemplate, latestVersion, latestVersion));

        try (ReadableByteChannel rbc = Channels.newChannel(url.openStream());
                FileOutputStream fos = new FileOutputStream(cliPath)) {
            fos.getChannel().transferFrom(rbc, 0, Long.MAX_VALUE);
        }
        LogUtils.info("Done");
        return 0;
    }
}
