#!/usr/bin/env groovy


node('puppet') {
    deleteDir()

    List mavenEnv = [
        "JAVA_HOME=${tool 'jdk8'}",
        "PATH+MVN=${tool 'mvn'}/bin",
        'PATH+JDK=${JAVA_HOME}/bin',
        'MAVEN_OPTS=-Dmaven.repo.local=${PWD}/.m2_repo',
    ]

    stage('Checkout') {
        checkout scm
    }

    stage('Prepare Indexer') {
        dir('pluginFolder') {
            timestamps {
                git changelog: false,
                        poll: false,
                        url:'https://github.com/jenkinsci/backend-extension-indexer.git',
                    branch: 'master'

                withEnv(mavenEnv) {
                    sh 'mvn -s ../settings.xml clean install -DskipTests'
                }
            }
        }
    }

    stage('Run Indexer') {
        dir('pluginFolder') {
            timestamps {
                withEnv(mavenEnv) {
                    sh 'java -verbose:gc -jar ./target/*-bin/extension-indexer*.jar -plugins ./plugins && mv plugins ..'
                }
            }
        }
    }

    stage('Generate Documentation') {
        dir('docFolder') {
            checkout scm

            withEnv(mavenEnv) {
                timestamps {
                    sh 'mvn -s ../settings.xml clean install -DskipTests'
                    sh 'mv ../plugins . && java -verbose:gc -javaagent:./contrib/file-leak-detector.jar -jar ./target/*-bin/pipeline-steps-doc-generator*.jar'
                }
            }
        }
    }

    stage('Clean up') {
        timestamps {
            dir('docFolder') {
                zip dir: './allAscii', glob: '', zipFile: 'allAscii.zip'
                archiveArtifacts artifacts: 'allAscii.zip', fingerprint: true
            }
        }
    }

    /* shut. down. everything. */
    deleteDir()
}


