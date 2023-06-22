package brig.ck8s.actions;

import brig.ck8s.model.ClusterInfo;
import brig.ck8s.model.MandatoryValuesMissing;
import brig.ck8s.utils.Ck8sPath;
import brig.ck8s.utils.Ck8sUtils;
import brig.ck8s.utils.LogUtils;
import brig.ck8s.utils.Mapper;

import java.nio.file.Path;
import java.util.Comparator;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static brig.ck8s.utils.LogUtils.logAsTable;

public class ClusterListAction
{

    private final Ck8sPath ck8sPath;

    public ClusterListAction(Ck8sPath ck8sPath)
    {
        this.ck8sPath = ck8sPath;
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

    public int perform()
    {
        Map<Path, ClusterInfo> clusters = getInfo();

        String[] caption = new String[] {"Alias", "Name", "Region", "Server", "Path"};
        Stream<String[]> rows = clusters.entrySet().stream()
                .sorted(Comparator.comparing(e -> e.getValue().alias(), Comparator.nullsLast(Comparator.naturalOrder())))
                .map(this::toTableRow);

        logAsTable(Stream.concat(Stream.<String[]>of(caption), rows).collect(Collectors.toList()));
        return 0;
    }

    public Map<Path, ClusterInfo> getInfo()
    {
        return Ck8sUtils.streamClusterYaml(ck8sPath)
                .map(p -> new PathForClusterInfo(p, toClusterInfo(p)))
                .filter(r -> r.clusterInfo() != null)
                .collect(Collectors.toMap(PathForClusterInfo::clusterYaml, PathForClusterInfo::clusterInfo));
    }

    private String[] toTableRow(Map.Entry<Path, ClusterInfo> e)
    {
        ClusterInfo c = e.getValue();
        return new String[] {c.alias(), c.name(), c.region(), c.server(), ck8sPath.relativize(e.getKey()).toString()};
    }

    record PathForClusterInfo(Path clusterYaml, ClusterInfo clusterInfo) {}
}
