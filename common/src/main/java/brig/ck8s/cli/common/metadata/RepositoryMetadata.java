package brig.ck8s.cli.common.metadata;

import static java.util.Objects.requireNonNull;

public record RepositoryMetadata(String sha, String branch)
{
    public RepositoryMetadata
    {
        requireNonNull(sha, "sha is null");
        requireNonNull(branch, "branch is null");
    }
}
