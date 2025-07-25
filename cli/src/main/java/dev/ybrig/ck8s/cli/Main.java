package dev.ybrig.ck8s.cli;

/*-
 * *****
 * Concord
 * -----
 * Copyright (C) 2017 - 2019 Walmart Inc.
 * -----
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * =====
 */

import picocli.CommandLine;

public class Main {

    public static void main(String[] args) {
//        args = new String[] {"--flow-executor", "concord-cli", "-p", "ci1", "-c", "ci1", "-f", "show", "--secretsProvider=local", "-VVVVV"};

        var cmd = new CommandLine(new CliApp())
                .setCaseInsensitiveEnumValuesAllowed(true)
                .setExitCodeExceptionMapper(Main::mapException);

        // hide generate-completion subcommand from usage help
        var gen = cmd.getSubcommands().get("generate-completion");
        gen.getCommandSpec().usageMessage().hidden(true);

        if (cmd.isVersionHelpRequested()) {
            cmd.printVersionHelp(System.out);
            return;
        }

        var code = cmd.execute(args);
        System.exit(code);
    }

    private static int mapException(Throwable th) {
        if (th instanceof CommandLine.ParameterException) {
            return 2;
        }
        return 1;
    }
}
