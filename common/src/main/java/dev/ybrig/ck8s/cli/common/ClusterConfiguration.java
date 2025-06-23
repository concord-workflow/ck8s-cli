package dev.ybrig.ck8s.cli.common;

import java.util.Map;
import java.util.Optional;

public class ClusterConfiguration {

    private final Map<String, Object> cfg;

    public ClusterConfiguration(Map<String, Object> cfg) {
        this.cfg = cfg;
    }

    public String alias() {
        return MapUtils.assertString(cfg, "alias");
    }

    public Optional<ClusterGroup> clusterGroup() {
        var groupCfg = MapUtils.getMap(cfg, "clusterGroup", null);
        if (groupCfg == null) {
            return Optional.empty();
        }
        return Optional.of(new ClusterGroup(groupCfg));
    }

    public static class ClusterGroup {

        private final Map<String, Object> cfg;

        public ClusterGroup(Map<String, Object> cfg) {
            this.cfg = cfg;
        }

        public boolean isActive() {
            return MapUtils.getBoolean(cfg, "activeCluster", false);
        }

        public String alias() {
            return MapUtils.assertString(cfg, "alias");
        }
    }
}
