#!/usr/bin/env groovy


node('linux') {
    List mavenEnv = [
        "JAVA_HOME=${tool 'jdk8'}",
        "PATH+MVN=${tool 'mvn'}/bin",
        'PATH+JAVA=${JAVA_HOME}/bin',
        'MAVEN_OPTS=-Dmaven.repo.local=${PWD}/.m2_repo',
    ]

    checkout scm
    stage('Prepare Indexer') {
        dir('pluginFolder') {
            git changelog: false,
                     poll: false,
                      url:'https://github.com/jenkinsci/backend-extension-indexer.git',
                   branch: 'master'

            withEnv(mavenEnv) {
                sh 'mvn -s ../settings.xml clean install -DskipTests'
            }
        }
    }

    stage('Run Indexer') {
        dir('pluginFolder') {
            withEnv(mavenEnv) {
                sh 'java -verbose:gc -jar ./target/*-bin/extension-indexer*.jar -plugins ./plugins'
                stash includes: '**/*.hpi', name: 'plugins'
                deleteDir()
            }
        }
    }
}


node('linux') {
    List mavenEnv = [
        "JAVA_HOME=${tool 'jdk8'}",
        "PATH+MVN=${tool 'mvn'}/bin",
        'PATH+JAVA=${JAVA_HOME}/bin',
        'MAVEN_OPTS=-Dmaven.repo.local=${PWD}/.m2_repo',
    ]

    stage('Generate Documentation') {
        dir('docFolder') {
            checkout scm

            withEnv(mavenEnv) {
                sh 'mvn -s ../settings.xml clean install -DskipTests'
            }

            dir('plugins') {
                unstash 'plugins'
            }

            sh 'java -verbose:gc -jar ./target/*-bin/pipeline-steps-doc-generator*.jar'
        }
    }

    stage('Clean up') {
        dir('docFolder') {
            zip dir: './allAscii', glob: '', zipFile: 'allAscii.zip'
            archive 'allAscii.zip'
            deleteDir()
        }
    }
}

