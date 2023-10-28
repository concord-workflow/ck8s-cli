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

    public String orgName() {
        return MapUtils.assertString(cfg, "organization.name");
    }

    public Optional<ClusterGroup> clusterGroup() {
        Map<String, Object> groupCfg = MapUtils.getMap(cfg, "clusterGroup", null);
        if (groupCfg == null) {
            return Optional.empty();
        }
        return Optional.of(new ClusterGroup(groupCfg));
    }

    public Optional<ConcordConfiguration> concord() {
        Map<String, Object> concordCfg = MapUtils.getMap(cfg, "concord", null);
        if (concordCfg == null) {
            return Optional.empty();
        }
        return Optional.of(new ConcordConfiguration(concordCfg));
    }

    public Map<String, Object> asMap() {
        return cfg;
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

    public static class ConcordConfiguration {

        private final Map<String, Object> cfg;

        public ConcordConfiguration(Map<String, Object> cfg) {
            this.cfg = cfg;
        }

        public boolean useOrgs() {
            return MapUtils.getBoolean(cfg, "useOrgs", false);
        }

        public Optional<Server> server() {
            Map<String, Object> serverCfg = MapUtils.getMap(cfg, "server", null);
            if (serverCfg == null) {
                return Optional.empty();
            }
            return Optional.of(new Server(serverCfg));
        }

        public static class Server {

            public enum Type {
                INTERNAL, EXTERNAL
            }

            private final Map<String, Object> cfg;

            public Server(Map<String, Object> cfg) {
                this.cfg = cfg;
            }

            public Type type() {
                String type = MapUtils.getString(cfg, "type", Type.INTERNAL.name());
                return Type.valueOf(type.toUpperCase());
            }
        }
    }
}
