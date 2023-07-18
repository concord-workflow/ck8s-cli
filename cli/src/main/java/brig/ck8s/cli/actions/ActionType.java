package brig.ck8s.cli.actions;

public enum ActionType
{
    UP,
    DOWN,
    DOCKER_REGISTRY,
    DNSMASQ_SETUP,
    DNSMASQ_RESTART,
    INSTALL_CONCORD,
    REINSTALL_CONCORD_AGENT_POOL,
    CONSOLE,
    AWS_KUBE_CONFIG
}
