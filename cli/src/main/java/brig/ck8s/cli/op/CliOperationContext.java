package brig.ck8s.cli.op;

import brig.ck8s.cli.CliApp;
import brig.ck8s.cli.VersionProvider;
import brig.ck8s.cli.common.Ck8sRepos;
import brig.ck8s.cli.utils.LogUtils;
import com.walmartlabs.concord.cli.Verbosity;

import java.util.Optional;

import static java.util.Objects.requireNonNull;

public record CliOperationContext(Ck8sRepos ck8sPath, Verbosity verbosity, CliApp cliApp)
{
    public CliOperationContext
    {
        requireNonNull(ck8sPath, "path is null");
        requireNonNull(verbosity, "verbosity is null");
        requireNonNull(cliApp, "cliApp is null");
    }

    public CliOperationContext(CliApp cliApp)
    {
        this(new Verbosity(cliApp.getVerbosity()), cliApp);
    }

    public CliOperationContext(Verbosity verbosity, CliApp cliApp)
    {
        this(getCk8sPath(verbosity, cliApp), verbosity, cliApp);
    }

    private static Ck8sRepos getCk8sPath(Verbosity verbosity, CliApp cliApp)
    {
        Ck8sRepos ck8s = new Ck8sRepos(
                VersionProvider.getCliVersion(),
                cliApp.getCk8sPath(),
                Optional.ofNullable(cliApp.getCk8sExtPath()));
        if (verbosity.verbose()) {
            LogUtils.info("Using ck8s path: {}", ck8s.ck8sDir());
            if (ck8s.ck8sExtDir() != null) {
                LogUtils.info("Using ck8s-ext path: {}", ck8s.ck8sExtDir());
            }

            LogUtils.info("Using target path: {}", cliApp.getTargetRootPath());
            LogUtils.info("Test mode: {}", cliApp.isTestMode());
        }
        return ck8s;
    }
}
