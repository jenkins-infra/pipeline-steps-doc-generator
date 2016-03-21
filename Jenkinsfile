node {

  stage "Setup"
  def mvntool = tool name: 'M3'
  def curdir = pwd()
  echo curdir
  git changelog: false, poll: false, url: 'https://github.com/kwhetstone/pipeline-steps-doc-generator.git', branch: 'master'
  
  stage "Create Plugin Folder"
  //TODO: pull in the location of the plugin list configurable
  unzip dir: 'plugin-lister', glob: '', zipFile: './scripts/plugin-lister-1.0-SNAPSHOT-bin.zip'
  sh 'java -verbose:gc -jar ./plugin-lister/plugin-lister*.jar -pipeline jenkinsio -folderLoc ' + curdir
  dir("plugin-lister"){
    deleteDir() //at least try to free up some space
  }
  
  stage "Generate Documentation"
  withEnv(["PATH+MAVEN=${mvntool}/bin"]) {
    sh 'mvn clean install -DskipTests'
  }
  sh 'mvn clean install -DskipTests'
  sh 'java -verbose:gc -jar ./target/*-bin/pipeline-steps-doc-generator*.jar'
  sh 'mvn clean'
    
  stage "Archive"
  zip dir: './allAscii', glob: '', zipFile: 'allAscii.zip'
  archive 'allAscii.zip'
}