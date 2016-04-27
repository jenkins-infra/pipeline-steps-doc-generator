stage "Setup"

  
stage "Create Plugin Folder"
  //pull in the plugins, save to ./plugins
node {
  def mvntool = tool name: 'Maven 3.3.3', type: 'hudson.tasks.Maven$MavenInstallation' //system dependent
  def jdktool = tool name: 'Oracle JDK 8u40', type: 'hudson.model.JDK' //system dependent
  List customEnv = ["PATH+MVN=${mvntool}/bin", "PATH+JDK=${jdktool}/bin", "JAVA_HOME=${jdktool}/", "MAVEN_HOME=${mvntool}"]
  customEnv.add("MAVEN_OPTS=-Dmaven.repo.local=${pwd()}/.m2_repo")
  
  dir('pluginFolder'){
    def curdir = pwd()
    echo curdir
    git changelog: false, poll: false, url:'https://github.com/jenkinsci/backend-extension-indexer.git', branch: 'master'
    withEnv(customEnv) {
      sh 'mvn clean install -DskipTests'
    }
    sh "java -verbose:gc -jar ./target/*-bin/extension-indexer*.jar -plugins ${curdir}/plugins"
    stash includes: './plugins/*', name: 'plugins'
    deleteDir()
  }
}

node {
  def mvntool = tool name: 'Maven 3.3.3', type: 'hudson.tasks.Maven$MavenInstallation' //system dependent
  def jdktool = tool name: 'Oracle JDK 8u40', type: 'hudson.model.JDK' //systeme dependent
  List customEnv = ["PATH+MVN=${mvntool}/bin", "PATH+JDK=${jdktool}/bin", "JAVA_HOME=${jdktool}/", "MAVEN_HOME=${mvntool}"]
  customEnv.add("MAVEN_OPTS=-Dmaven.repo.local=${pwd()}/.m2_repo")
  
  stage "Generate Documentation"
  dir('docFolder'){
    git changelog: false, poll: false, url: 'https://github.com/kwhetstone/pipeline-steps-doc-generator.git', branch: 'master'
    withEnv(customEnv) {
      sh 'mvn clean install -DskipTests'
    }
    dir('pluings'){
        unstash 'plugins'
    }
    sh 'java -verbose:gc -jar ./target/*-bin/pipeline-steps-doc-generator*.jar'
  }
    
  stage "Archive and Cleanup"
  dir('docFolder'){
    zip dir: './allAscii', glob: '', zipFile: 'allAscii.zip'
    archive 'allAscii.zip'
    deleteDir()
  }
}