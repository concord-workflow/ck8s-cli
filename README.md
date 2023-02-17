# ck8s-cli

Requirements for ck8s-cli with local concord (concord-cli):
- Java 17+
- yq v4.30.6+
- mustache

Running ck8s test:
```bash
alias ck8s-cli='/<path>/ck8s-cli-0.0.1-runner --ck8s-root <ck8s-path> --ck8s-ext-root <ck8s-ext-path> --target-root /tmp/ck8s --flow-executor concord_cli'
```
```bash
ck8s-cli concord install-cli
```
```bash
ck8s-cli test -c <cluster-alias> <component-name> 
```