package brig.ck8s.cli.common.processors;

import brig.ck8s.cli.common.Ck8sPayloadForRemote;

public interface PayloadProcessor {

    Ck8sPayloadForRemote process(Ck8sPayloadForRemote payload);
}
