package brig.ck8s.cli.common.processors;

import brig.ck8s.cli.common.*;

import java.nio.file.Path;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class Ck8sInfoProcessor implements PayloadProcessor {

    @Override
    public Ck8sPayload process(String flowName, Ck8sPayload payload) {
        Ck8sPath ck8sPath = payload.ck8sPath();

        Map<String, Object> args = new HashMap<>();
        args.put("processCk8sBranch", getBranch(ck8sPath.ck8sDir()));
        args.put("ck8sRef", getSha(ck8sPath.ck8sDir()));
        args.put("processCk8sExtBranch", getBranch(ck8sPath.ck8sExtDir()));
        args.put("ck8sExtRef", getSha(ck8sPath.ck8sExtDir()));

        return Ck8sPayload.builder().from(payload)
                .putAllArgs(args.entrySet().stream().filter(e -> e.getValue() != null).collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue)))
                .build();
    }

    private static String getBranch(Path repoPath) {
        if (repoPath == null) {
            return null;
        }

        try {
            List<String> args = Arrays.asList("git", "rev-parse", "--abbrev-ref", "HEAD");
            return CliCommand.grabOut(args, repoPath).trim();
        } catch (Exception e) {
            System.out.println("getBranch error: " + e.getMessage());
            return null;
        }
    }

    public static String getSha(Path repoPath) {
        if (repoPath == null) {
            return null;
        }

        try {
            List<String> args = Arrays.asList("git", "rev-parse", "HEAD");
            return CliCommand.grabOut(args, repoPath).trim();
        } catch (Exception e) {
            System.out.println("getSha error: " + e.getMessage());
            return null;
        }
    }
}
