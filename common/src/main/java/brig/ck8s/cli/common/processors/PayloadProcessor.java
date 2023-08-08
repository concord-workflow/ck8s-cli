package brig.ck8s.cli.common.processors;

import brig.ck8s.cli.common.Ck8sPayload;

public interface PayloadProcessor {

    Ck8sPayload process(String flowName, Ck8sPayload payload);
}
