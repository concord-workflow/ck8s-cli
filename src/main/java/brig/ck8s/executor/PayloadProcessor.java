package brig.ck8s.executor;

import brig.ck8s.concord.Ck8sPayload;

public interface PayloadProcessor {

    Ck8sPayload process(Ck8sPayload payload);
}
