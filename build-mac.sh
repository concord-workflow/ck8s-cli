export GRAALVM_HOME=/Users/brig/java/graalvm-ce-java17-22.3.1/Contents/Home/
export JAVA_HOME=${GRAALVM_HOME}
export PATH=${GRAALVM_HOME}/bin:$PATH

./mvnw install -Dnative