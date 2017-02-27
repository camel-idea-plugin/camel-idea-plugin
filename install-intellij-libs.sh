#!/bin/sh
# This script will install all files in IntelliJ IDEA's lib/ folder to the local maven .m2 repository.
# This way we can use them during the build
#
# Usage:
#   ./install-intellij-libs.sh 2016.3.2 /Users/JOE/Applications/IntelliJ IDEA CE.app/Contents /User/JOE/.m2

IDEA_VERSION=$1
INTELLIJ_HOME=$2
M2_REPO_HOME=$3/repository/

if [ -z "$INTELLIJ_HOME" ]
then
  echo "Please provide the version and path to the IntelliJ home directory. For example: ./install-intellij-libs.sh 2016.3.2 /Users/JOE/Applications/IntelliJ IDEA CE.app/Contents/ /User/JOE/.m2"
  exit 1
fi

if [ ! -d "$INTELLIJ_HOME" ]
then
  echo "Directory does not exist: $INTELLIJ_HOME"
  exit 1
fi

echo 'Installing IntelliJ artifacts to Maven local repository'
echo "Intellij home: ${INTELLIJ_HOME}"
echo "M2 HOME: ${M2_REPO_HOME}"

for i in "${INTELLIJ_HOME}"/lib/*.jar
do
    JAR_FILE=$(basename "$i")
    JAR_NAME="${JAR_FILE%.jar}"
    ARTIFACT_ID="${JAR_FILE%.*}"
    M2_REPO_FILE="${M2_REPO_HOME}/com/intellij/${ARTIFACT_ID}/${IDEA_VERSION}/${JAR_NAME}-${IDEA_VERSION}.jar"

    if [ ! -f "${M2_REPO_FILE}" ]; then
     mvn install:install-file -Dfile="$i" -DgroupId=com.intellij -DartifactId=${ARTIFACT_ID} -Dversion=${IDEA_VERSION} -Dpackaging=jar
    fi
done

 if [ ! -f "${M2_REPO_HOME}/com/intellij/plugins/properties/${IDEA_VERSION}/properties-${IDEA_VERSION}.jar" ]; then
   mvn install:install-file -Dfile="${INTELLIJ_HOME}/plugins/properties/lib/properties.jar" -DgroupId=com.intellij.plugins -DartifactId=properties -Dversion=${IDEA_VERSION} -Dpackaging=jar
 fi

 if [ ! -f "${M2_REPO_HOME}/com/intellij/plugins/resources_en/${IDEA_VERSION}/resources_en-${IDEA_VERSION}.jar" ]; then
    mvn install:install-file -Dfile="${INTELLIJ_HOME}/plugins/properties/lib/resources_en.jar" -DgroupId=com.intellij.plugins -DartifactId=resources_en -Dversion=${IDEA_VERSION} -Dpackaging=jar
 fi

if [ ! -f "${M2_REPO_HOME}/com/intellij/plugins/spring/${IDEA_VERSION}/spring-api-${IDEA_VERSION}.jar" ]; then
    mvn install:install-file -Dfile="${INTELLIJ_HOME}/plugins/Spring/lib/spring-api.jar" -DgroupId=com.intellij.plugins -DartifactId=spring-api -Dversion=${IDEA_VERSION} -Dpackaging=jar
 fi

if [ ! -f "${M2_REPO_HOME}/com/intellij/plugins/spring/${IDEA_VERSION}/spring-${IDEA_VERSION}.jar" ]; then
    mvn install:install-file -Dfile="${INTELLIJ_HOME}/plugins/Spring/lib/spring.jar" -DgroupId=com.intellij.plugins -DartifactId=spring -Dversion=${IDEA_VERSION} -Dpackaging=jar
 fi

if [ ! -f "${M2_REPO_HOME}/com/intellij/plugins/java-i18n/${IDEA_VERSION}/java-i18n-${IDEA_VERSION}.jar" ]; then
    mvn install:install-file -Dfile="${INTELLIJ_HOME}/plugins/java-i18n/lib/java-i18n.jar" -DgroupId=com.intellij -DartifactId=java-i18n -Dversion=${IDEA_VERSION} -Dpackaging=jar
 fi