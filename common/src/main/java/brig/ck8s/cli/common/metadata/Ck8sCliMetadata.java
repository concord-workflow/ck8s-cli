package brig.ck8s.cli.common.metadata;

import static java.util.Objects.requireNonNull;

public record Ck8sCliMetadata(String version)
{
    public Ck8sCliMetadata
    {
        requireNonNull(version, "version is null");
    }
}
