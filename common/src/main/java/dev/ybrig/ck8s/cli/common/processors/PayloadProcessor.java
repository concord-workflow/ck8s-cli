package dev.ybrig.ck8s.cli.common.processors;

import dev.ybrig.ck8s.cli.common.Ck8sPayload;

public interface PayloadProcessor {

    Ck8sPayload process(String flowName, Ck8sPayload payload);
}
