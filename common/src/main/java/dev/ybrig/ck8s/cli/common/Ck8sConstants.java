package dev.ybrig.ck8s.cli.common;

public final class Ck8sConstants {

    public static final String PROCESS_TYPE = "ck8s";

    public static class Request {

        public static final String PROCESS_TYPE_KEY = "processType";

        public static final String CK8S_REF = "ck8sRef";
    }

    public static class Arguments {

        public static final String CLUSTER_REQUEST = "clusterRequest";

        public static final String FLOW = "flow";

        public static final String CLIENT_CLUSTER = "clientCluster";

        public static final String INPUT_ARGS = "inputArgs";
    }
}
