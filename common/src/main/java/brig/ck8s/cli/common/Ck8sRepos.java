package brig.ck8s.cli.common;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

import static java.util.Objects.requireNonNull;

public class Ck8sRepos
{
    private static final Path CK8S_CORE = Path.of("flows");
    private static final Path CK8S_ORGS_DIR = CK8S_CORE.resolve("ck8s-orgs");
    private static final Path CK8S_COMPONENTS = CK8S_CORE.resolve("ck8s-components");
    private static final Path CK8S_COMPONENTS_TESTS = CK8S_CORE.resolve("ck8s-components-tests");
    private static final Path CK8S_EXT_ORGS_DIR = Path.of("ck8s-orgs");
    private static final Path CK8S_EXT_COMPONENTS = Path.of("ck8s-components");
    private static final Path CK8S_EXT_COMPONENTS_TESTS = Path.of("ck8s-components-tests");

    private final Path ck8s;
    private final Optional<Path> ck8sExt;

    public Ck8sRepos(Path ck8s, Optional<Path> ck8sExt)
    {
        assertDirectory("ck8s:", ck8s);

        this.ck8s = normalize(ck8s);
        this.ck8sExt = requireNonNull(ck8sExt, "ck8sExt is null")
                .map(this::normalize)
                .filter(Files::isDirectory);
    }

    private Path normalize(Path p)
    {
        if (p == null) {
            return null;
        }

        return p.toAbsolutePath().normalize();
    }

    public Path ck8sDir()
    {
        return ck8s;
    }

    public Path ck8sOrgDir()
    {
        return ck8s.resolve(CK8S_ORGS_DIR);
    }

    public Path ck8sComponents()
    {
        return ck8s.resolve(CK8S_COMPONENTS);
    }

    public Path ck8sComponentsTests()
    {
        return ck8s.resolve(CK8S_COMPONENTS_TESTS);
    }

    public Optional<Path> ck8sExtDir()
    {
        return ck8sExt;
    }

    public Optional<Path> ck8sExtOrgDir()
    {
        return ck8sExt.map(p -> p.resolve(CK8S_EXT_ORGS_DIR));
    }

    public Optional<Path> ck8sExtComponents()
    {
        return ck8sExt.map(p -> p.resolve(CK8S_EXT_COMPONENTS));
    }

    public Optional<Path> ck8sExtComponentsTests()
    {
        return ck8sExt.map(p -> p.resolve(CK8S_EXT_COMPONENTS_TESTS));
    }

    public Path defaultCfg()
    {
        return ck8s.resolve("flows").resolve("ck8s-configs").resolve("ck8s.yaml");
    }

    public Path orgCfgForCluster(Path clusterYaml)
    {
        return clusterYaml.getParent().getParent().getParent().getParent().resolve("organization.yaml");
    }

    public Path accountCfgForCluster(Path clusterYaml)
    {
        return clusterYaml.getParent().getParent().resolve("account.yaml");
    }

    public Path relativize(Path p)
    {
        if (p.startsWith(ck8s)) {
            return ck8s.relativize(p);
        }
        else if (ck8sExt
                .map(path -> p.startsWith(path))
                .orElse(false)) {
            return ck8sExt.map(path -> path.relativize(p)).get();
        }

        return p;
    }

    private void assertDirectory(String prefix, Path p)
    {
        if (!Files.isDirectory(p)) {
            throw new RuntimeException(prefix + " '" + p + "' is not a directory or does not exists");
        }
    }
}
