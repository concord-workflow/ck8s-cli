package brig.ck8s.executor.processors;

import brig.ck8s.concord.Ck8sPayload;

public interface PayloadProcessor {

    Ck8sPayload process(Ck8sPayload payload);
}
