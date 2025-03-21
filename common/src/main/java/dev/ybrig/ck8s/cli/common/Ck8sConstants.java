package dev.ybrig.ck8s.cli.common;

public final class Ck8sConstants {

    public static final String DEFAULT_REPO_NAME = "ck8s";

    public static final String PROCESS_TYPE = "ck8s";

    public static class Meta {
        public static final String PROCESS_TYPE_KEY = "processType";
    }

    public static class Arguments {

        public static final String CONCORD_URL = "concordUrl";

        public static final String LOCAL_CONCORD_CLI = "localConcordCli";

        public static final String CLUSTER_REQUEST = "clusterRequest";

        public static final String FLOW = "flow";

        public static final String CLIENT_CLUSTER = "clientCluster";

        public static final String INPUT_ARGS = "inputArgs";
    }
}
