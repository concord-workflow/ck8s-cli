package dev.ybrig.ck8s.cli.common.processors;

import dev.ybrig.ck8s.cli.common.Ck8sPayload;
import dev.ybrig.ck8s.cli.common.VersionProvider;

public class Ck8sCliVersionProcessor implements PayloadProcessor {

    @Override
    public Ck8sPayload process(Ck8sPayload payload, String flowName) {
        return Ck8sPayload.builder().from(payload)
                .putArgs("ck8sCliVersion", VersionProvider.get())
                .build();
    }
}
