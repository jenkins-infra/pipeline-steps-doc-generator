node {
  stage "Setup"
  def mvntool = tool name: 'M3'
  def curdir = pwd()
  echo curdir
  
  //use scm rather than git
    git changelog: false, poll: false, url: 'https://github.com/kwhetstone/pipeline-steps-doc-generator.git', branch: 'master'
  
  //use scm rather than git
  dir('pluginFolder'){
    git changelog: false, poll: false, url:'https://github.com/kwhetstone/backend-extension-indexer.git', branch: 'pipeline-steps'
  }
  
  stage "Create Plugin Folder"
  //pull in the plugins, save to ./plugins
  dir('pluginFolder'){
    withEnv(["PATH+MAVEN=${mvntool}/bin"]) {
      sh 'mvn clean install -DskipTests'
    }
    sh 'java -verbose:gc -jar ./target/*-bin/extension-indexer*.jar -pipeline jenkinsio -folderLoc ../plugins'
  }
  
  stage "Generate Documentation"
  withEnv(["PATH+MAVEN=${mvntool}/bin"]) {
    sh 'mvn clean install -DskipTests'
  }
  sh 'java -verbose:gc -jar ./target/*-bin/pipeline-steps-doc-generator*.jar'
  sh 'mvn clean'
    
  stage "Archive"
  zip dir: './allAscii', glob: '', zipFile: 'allAscii.zip'
  archive 'allAscii.zip'
}