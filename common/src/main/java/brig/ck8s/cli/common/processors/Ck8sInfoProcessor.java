package brig.ck8s.cli.common.processors;

import brig.ck8s.cli.common.Ck8sPayload;
import brig.ck8s.cli.common.Ck8sRepos;

import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

public class Ck8sInfoProcessor implements PayloadProcessor {

    @Override
    public Ck8sPayload process(Ck8sPayload payload) {
        Ck8sRepos ck8sPath = payload.cks8sPath();
        if (ck8sPath == null) {
            return payload;
        }

        Map<String, Object> args = new HashMap<>();
        args.put("processCk8sBranch", ck8sPath.ck8sDirBranch());
        args.put("processCk8sCommit", ck8sPath.ck8sDirSha());
        args.put("processCk8sExtBranch", ck8sPath.ck8sExtDirBranch().orElse(""));
        args.put("processCk8sExtCommit", ck8sPath.ck8sExtDirSha().orElse(""));

        return Ck8sPayload.builder().from(payload)
                .putAllArgs(args.entrySet().stream().filter(e -> e.getValue() != null).collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue)))
                .build();
    }
}
