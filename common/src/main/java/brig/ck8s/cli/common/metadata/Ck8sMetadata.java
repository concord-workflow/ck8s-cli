package brig.ck8s.cli.common.metadata;

import brig.ck8s.cli.common.Ck8sRepos;

import java.util.Optional;

import static java.util.Objects.requireNonNull;

public record Ck8sMetadata(
        Ck8sCliMetadata ck8SCli,
        Optional<RepositoryMetadata> ck8sRepository,
        Optional<RepositoryMetadata> ck8sExtRepository)
{
    public Ck8sMetadata
    {
        requireNonNull(ck8SCli, "ck8SCli is null");
        requireNonNull(ck8sRepository, "ck8sRepository is null");
        requireNonNull(ck8sExtRepository, "ck8sExtRepository is null");
    }

    public Ck8sMetadata(Ck8sRepos ck8sPath)
    {
        this(
                new Ck8sCliMetadata(ck8sPath.getCk8sCliVersion()),
                ck8sPath.ck8sDirSha()
                        .flatMap(sha -> ck8sPath.ck8sDirBranch()
                                .map(branch -> new RepositoryMetadata(sha, branch))),
                ck8sPath.ck8sExtDirSha()
                        .flatMap(sha -> ck8sPath.ck8sExtDirBranch()
                                .map(branch -> new RepositoryMetadata(sha, branch))));
    }
}
