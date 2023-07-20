package brig.ck8s.cli.common;

import javax.annotation.Nullable;
import java.nio.file.Files;
import java.nio.file.Path;

public class Ck8sPath
{

    private static final Path CK8S_CORE = Path.of("flows");
    private static final Path CK8S_ORGS_DIR = CK8S_CORE.resolve("ck8s-orgs");
    private static final Path CK8S_COMPONENTS = CK8S_CORE.resolve("ck8s-components");
    private static final Path CK8S_COMPONENTS_TESTS = CK8S_CORE.resolve("ck8s-components-tests");
    private static final Path CK8S_EXT_ORGS_DIR = Path.of("ck8s-orgs");
    private static final Path CK8S_EXT_COMPONENTS = Path.of("ck8s-components");
    private static final Path CK8S_EXT_COMPONENTS_TESTS = Path.of("ck8s-components-tests");
    private final Path ck8s;
    @Nullable
    private final Path ck8sExt;

    public Ck8sPath(Path ck8s, Path ck8sExt)
    {
        this.ck8s = normalize(ck8s);
        this.ck8sExt = dirOrNull(normalize(ck8sExt));

        assertDirectory("ck8s:", this.ck8s);
    }

    public static Ck8sPath from(String ck8sDir, String ck8sExtDir)
    {
        return new Ck8sPath(Path.of(ck8sDir), ck8sExtDir != null ? Path.of(ck8sExtDir) : null);
    }

    private static Path normalize(Path p)
    {
        if (p == null) {
            return null;
        }

        return p.toAbsolutePath().normalize();
    }

    private static Path dirOrNull(Path p)
    {
        if (p == null) {
            return null;
        }

        if (!Files.isDirectory(p)) {
            return null;
        }

        return p;
    }

    private static void assertDirectory(String prefix, Path p)
    {
        if (!Files.isDirectory(p)) {
            throw new RuntimeException(prefix + " '" + p + "' is not a directory or does not exists");
        }
    }

    public Path ck8sDir()
    {
        return ck8s;
    }

    @Nullable
    public Path ck8sExtDir()
    {
        return ck8sExt;
    }

    @Nullable
    public Path ck8sExtOrgDir()
    {
        if (ck8sExt == null) {
            return null;
        }

        return ck8sExt.resolve(CK8S_EXT_ORGS_DIR);
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

    @Nullable
    public Path ck8sExtComponents()
    {
        if (ck8sExt == null) {
            return null;
        }

        return ck8sExt.resolve(CK8S_EXT_COMPONENTS);
    }

    @Nullable
    public Path ck8sExtComponentsTests()
    {
        if (ck8sExt == null) {
            return null;
        }

        return ck8sExt.resolve(CK8S_EXT_COMPONENTS_TESTS);
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
        else if (ck8sExt != null && p.startsWith(ck8sExt)) {
            return ck8sExt.relativize(p);
        }

        return p;
    }
}
