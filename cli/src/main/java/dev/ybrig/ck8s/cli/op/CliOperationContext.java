package dev.ybrig.ck8s.cli.op;

import com.walmartlabs.concord.cli.Verbosity;
import dev.ybrig.ck8s.cli.CliApp;
import dev.ybrig.ck8s.cli.common.Ck8sPath;
import dev.ybrig.ck8s.cli.utils.LogUtils;

import static java.util.Objects.requireNonNull;

public final class CliOperationContext {
    private final Ck8sPath ck8sPath;
    private final Verbosity verbosity;
    private final CliApp cliApp;

    public CliOperationContext(Ck8sPath ck8sPath, Verbosity verbosity, CliApp cliApp) {
        requireNonNull(ck8sPath, "path is null");
        requireNonNull(verbosity, "verbosity is null");
        requireNonNull(cliApp, "cliApp is null");

        this.ck8sPath = ck8sPath;
        this.verbosity = verbosity;
        this.cliApp = cliApp;
    }

    public Ck8sPath ck8sPath() {
        return ck8sPath;
    }

    public Verbosity verbosity() {
        return verbosity;
    }

    public CliApp cliApp() {
        return cliApp;
    }

    @Override
    public String toString() {
        return "CliOperationContext[" +
                "ck8sPath=" + ck8sPath + ", " +
                "verbosity=" + verbosity + ", " +
                "cliApp=" + cliApp + ']';
    }


    public CliOperationContext(CliApp cliApp) {
        this(new Verbosity(cliApp.getVerbosity()), cliApp);
    }

    public CliOperationContext(Verbosity verbosity, CliApp cliApp) {
        this(getCk8sPath(verbosity, cliApp), verbosity, cliApp);
    }

    private static Ck8sPath getCk8sPath(Verbosity verbosity, CliApp cliApp) {
        Ck8sPath ck8s = new Ck8sPath(cliApp.getCk8sPath(), cliApp.getCk8sExtPath());
        if (verbosity.verbose()) {
            LogUtils.info("Using ck8s path: {}", ck8s.ck8sDir());
            if (ck8s.ck8sExtDir() != null) {
                LogUtils.info("Using ck8s-ext path: {}", ck8s.ck8sExtDir());
            }

            LogUtils.info("Using target path: {}", cliApp.getTargetRootPath());
        }
        return ck8s;
    }
}
