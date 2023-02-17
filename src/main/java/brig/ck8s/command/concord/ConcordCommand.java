package brig.ck8s.command.concord;

import brig.ck8s.utils.EnumCompletionCandidates;
import brig.ck8s.utils.EnumConverter;
import picocli.CommandLine;

import java.util.Collections;
import java.util.List;

@CommandLine.Command(name = "concord", description = "Run actions for concord")
@SuppressWarnings("unused")
public class ConcordCommand implements Runnable {

    @CommandLine.Spec
    CommandLine.Model.CommandSpec spec;

    @CommandLine.Parameters(arity = "1..*", description = "actions: ${COMPLETION-CANDIDATES}", completionCandidates = ActionTypeCompletionCandidates.class, converter = ActionTypeConverter.class)
    List<ActionType> actions = Collections.emptyList();

    @CommandLine.Option(names = {"--verbose"}, description = "verbose output")
    boolean verbose = false;

    @CommandLine.Option(names = { "-h", "--help" }, usageHelp = true, description = "display a help message")
    private boolean helpRequested = false;

    @Override
    public void run() {
        for (ActionType action : actions) {
            switch (action) {
                case INSTALL_CLI -> new InstallConcordCliAction().perform();
                default -> {
                    throw new IllegalArgumentException("Unknown action: " + action);
                }
            }
        }
    }

    static class ActionTypeCompletionCandidates extends EnumCompletionCandidates<ActionType> {

        public ActionTypeCompletionCandidates() {
            super(ActionType.class);
        }
    }

    static class ActionTypeConverter extends EnumConverter<ActionType> {

        public ActionTypeConverter() {
            super(ActionType.class);
        }
    }
}
