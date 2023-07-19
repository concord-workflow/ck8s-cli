package brig.ck8s.cli.common;

import com.walmartlabs.concord.common.IOUtils;
import org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream;
import org.immutables.value.Value;

import javax.annotation.Nullable;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

@Value.Immutable
@Value.Style(
        jdkOnly = true)
public interface Ck8sPayload
{
    String CLUSTER_CONCORD_YML_SUFFIX = "-concord.yml";
    String CLUSTER_CONCORD_YAML_FORMAT = "%s" + CLUSTER_CONCORD_YML_SUFFIX;

    static boolean isClusterConcordYaml(Path file)
    {
        return file.toString().endsWith(CLUSTER_CONCORD_YML_SUFFIX);
    }

    static String createClusterConcordYamlFileName(String clusterAlias)
    {
        return CLUSTER_CONCORD_YAML_FORMAT.formatted(clusterAlias);
    }

    static Builder builder()
    {
        return new Builder();
    }

    String[] clusterAliases();

    Path location();

    @Nullable
    Ck8sPath cks8sPath();

    @Value.Default
    default Map<String, Object> concord()
    {
        return Collections.emptyMap();
    }

    default Path flowsPath()
    {
        return location().resolve("concord");
    }

    default List<Path> rootClusterConcordYamlList()
    {
        return Arrays.stream(clusterAliases())
                .map(Ck8sPayload::createClusterConcordYamlFileName)
                .map(location()::resolve)
                .toList();
    }

    @Nullable
    default String flowName()
    {
        return MapUtils.getString(args(), "flow");
    }

    @Value.Default
    default Map<String, Object> args()
    {
        return Collections.emptyMap();
    }

    @Value.Default
    default Map<String, Object> meta()
    {
        return Collections.emptyMap();
    }

    default void createArchive(OutputStream out)
    {
        try {
            try (ZipArchiveOutputStream zip = new ZipArchiveOutputStream(out)) {
                IOUtils.zip(zip, location());
            }
        }
        catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    class Builder
            extends ImmutableCk8sPayload.Builder
    {
        public Builder flow(String name)
        {
            if (name != null) {
                return putArgs("flow", name);
            }
            return this;
        }
    }
}
