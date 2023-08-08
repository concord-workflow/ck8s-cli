package dev.ybrig.ck8s.cli;

import picocli.CommandLine;

import static picocli.CommandLine.Spec.Target.MIXEE;

public abstract class BaseMixin<T extends BaseMixin<?>>
{

    @CommandLine.Spec(MIXEE) CommandLine.Model.CommandSpec mixee;

    @SuppressWarnings("unchecked")
    protected T rootMixin()
    {
        for (CommandLine.Model.CommandSpec mixinCommand : mixee.root().mixins().values()) {
            Object obj = mixinCommand.userObject();
            if (obj.getClass().isAssignableFrom(this.getClass())) {
                return (T) obj;
            }
        }
        throw new IllegalStateException("Root command does not have a @Mixin of type" + this.getClass());
    }
}
