node {
  def curdir = pwd() echo dir

  git changelog: false, poll: false, url: 'git@github.com:kwhetstone/pipeline-steps-doc-generator.git', branch: 'master'
  stage "Create Plugin Folder"
  /*dir("plugin-finder"){ //one day, this might actually be a project; or an uberjar.
    sh 'mvn clean install -DskipTests'
    sh 'java -verbose:gc -jar ./target/*-bin/plugin-finder*.jar -pipeline jenkinsio -folderLoc ' + curdir
    sh 'mvn clean'
  }*/
  //TODO: pull in the location of the plugin list configurable
  unzip dir: 'plugin-lister', glob: '', zipFile: './scripts/plugin-lister-1.0-SNAPSHOT-bin.zip'
  sh 'java -verbose:gc -jar ./plugin-lister/plugin-finder*.jar -pipeline jenkinsio -folderLoc ' + curdir
  dir(curdir){
    deleteDir("plugin-lister") //at least try to free up some space
  }
  
  stage "Generate Documentation"
  sh 'mvn clean install -DskipTests'
  sh 'java -verbose:gc -jar ./target/*-bin/pipeline-steps-doc-generator*.jar -homeDir ' + curdir
  sh 'mvn clean'
    
  stage "Archive"
  zip dir: './allAscii', glob: '', zipFile: 'allAscii.zip'
  archive 'allAscii.zip'
}