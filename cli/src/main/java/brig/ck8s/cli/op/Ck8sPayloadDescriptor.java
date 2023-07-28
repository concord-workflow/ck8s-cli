package brig.ck8s.cli.op;

import brig.ck8s.cli.common.Ck8sRepos;
import com.walmartlabs.concord.cli.Verbosity;

import java.nio.file.Path;

import static java.util.Objects.requireNonNull;

public record Ck8sPayloadDescriptor(Verbosity verbosity, Ck8sRepos ck8sPath, Path targetRootPath, boolean withTests, boolean withInputAssert)
{
    public Ck8sPayloadDescriptor
    {
        requireNonNull(verbosity, "verbosity is null");
        requireNonNull(ck8sPath, "ck8sPath is null");
        requireNonNull(targetRootPath, "targetRootPath is null");
    }

    public Ck8sPayloadDescriptor(Verbosity verbosity, Ck8sRepos ck8sPath, Path targetRootPath)
    {
        this(verbosity, ck8sPath, targetRootPath, false, false);
    }
}
