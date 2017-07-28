VERSION=`./gradlew version | grep Version | cut -f 2 -d " "`
PASSPHRASE=`cat ~/.gnupg/passphrase.txt`
GPGPARAMS="--passphrase $PASSPHRASE --batch --yes --no-tty"
./gradlew assemble generatePom

#mv build/libs/antlr4-c3-kotlin.jar build/libs/antlr4-c3-kotlin-${VERSION}.jar
#mv build/libs/antlr4-c3-kotlin-javadoc.jar build/libs/antlr4-c3-kotlin-${VERSION}-javadoc.jar
#mv build/libs/antlr4-c3-kotlin-sources.jar build/libs/antlr4-c3-kotlin-${VERSION}-sources.jar
sed s/unspecified/$VERSION/g build/pom.xml > build/pom_corrected.xml
mv build/pom_corrected.xml build/pom.xml
gpg $GPGPARAMS -ab build/pom.xml
gpg $GPGPARAMS -ab build/libs/antlr4-c3-kotlin-${VERSION}.jar
gpg $GPGPARAMS -ab build/libs/antlr4-c3-kotlin-${VERSION}-javadoc.jar
gpg $GPGPARAMS -ab build/libs/antlr4-c3-kotlin-${VERSION}-sources.jar
cd build/libs
jar -cvf bundle-antlr4-c3-kotlin.jar ../pom.xml ../pom.xml.asc antlr4-c3-kotlin-${VERSION}.jar antlr4-c3-kotlin-${VERSION}.jar.asc antlr4-c3-kotlin-${VERSION}-javadoc.jar antlr4-c3-kotlin-${VERSION}-javadoc.jar.asc antlr4-c3-kotlin-${VERSION}-sources.jar antlr4-c3-kotlin-${VERSION}-sources.jar.asc
cd ../../..
