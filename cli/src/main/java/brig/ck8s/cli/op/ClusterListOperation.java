package brig.ck8s.cli.op;

import brig.ck8s.cli.common.Ck8sRepos;
import brig.ck8s.cli.common.Ck8sUtils;
import brig.ck8s.cli.common.MandatoryValuesMissing;
import brig.ck8s.cli.common.Mapper;
import brig.ck8s.cli.model.ClusterInfo;
import brig.ck8s.cli.utils.LogUtils;

import java.nio.file.Path;
import java.util.Comparator;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static brig.ck8s.cli.utils.LogUtils.logAsTable;

public class ClusterListOperation
        implements CliOperation
{
    public static Map<Path, ClusterInfo> getClusterList(Ck8sRepos ck8sPath)
    {
        return Ck8sUtils.streamClusterYaml(ck8sPath)
                .map(p -> new PathForClusterInfo(p, toClusterInfo(p)))
                .filter(r -> r.clusterInfo() != null)
                .collect(Collectors.toMap(PathForClusterInfo::clusterYaml, PathForClusterInfo::clusterInfo));
    }

    private static ClusterInfo toClusterInfo(Path clusterYaml)
    {
        try {
            return Mapper.yamlMapper().read(clusterYaml, ClusterInfo.class);
        }
        catch (MandatoryValuesMissing e) {
            LogUtils.warn("cluster definition '{}' ignored: {}", clusterYaml, e.getMessage());
        }
        return null;
    }

    public Integer execute(CliOperationContext cliOperationContext)
    {
        Ck8sRepos ck8sPath = cliOperationContext.ck8sPath();
        Map<Path, ClusterInfo> clusters = getClusterList(ck8sPath);
        String[] caption = new String[] {"Alias", "Name", "Region", "Server", "Path"};
        Stream<String[]> rows = clusters.entrySet().stream()
                .sorted(Comparator.comparing(e -> e.getValue().alias(), Comparator.nullsLast(Comparator.naturalOrder())))
                .map(e -> toTableRow(ck8sPath, e));
        logAsTable(Stream.concat(Stream.<String[]>of(caption), rows).collect(Collectors.toList()));
        return 0;
    }

    private String[] toTableRow(Ck8sRepos ck8sPath, Map.Entry<Path, ClusterInfo> e)
    {
        ClusterInfo c = e.getValue();
        return new String[] {c.alias(), c.name(), c.region(), c.server(), ck8sPath.relativize(e.getKey()).toString()};
    }

    record PathForClusterInfo(Path clusterYaml, ClusterInfo clusterInfo) {}
}
