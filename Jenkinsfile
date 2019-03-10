#!/usr/bin/env groovy

pipeline {
    agent { label 'maven' }
    triggers {
        cron('H H * * 0')
    }

    options {
        timestamps()
    }

    stages {
        stage('Checkout') {
            steps {
                deleteDir()
                checkout scm
            }
        }

        stage('Prepare Indexer') {
            steps {
                dir('pluginFolder') {
                    git changelog: false,
                            poll: false,
                            url:'https://github.com/jenkinsci/backend-extension-indexer.git',
                        branch: 'master'
                    sh 'mvn -s ../settings.xml clean install -DskipTests'
                }
            }
        }

        stage('Run Indexer') {
            steps {
                dir('pluginFolder') {
                    sh 'java -verbose:gc -jar ./target/*-bin/extension-indexer*.jar -plugins ./plugins && mv plugins ..'
                }
            }
        }

        stage('Generate Documentation') {
            steps {
                dir('docFolder') {
                    checkout scm
                    sh 'mvn -s ../settings.xml clean install -DskipTests'
                    sh 'mv ../plugins . && java -verbose:gc -javaagent:./contrib/file-leak-detector.jar -jar ./target/*-bin/pipeline-steps-doc-generator*.jar'
                }
            }
        }

        stage('Clean up') {
            steps {
                dir('docFolder') {
                    zip dir: './allAscii', glob: '', zipFile: 'allAscii.zip'
                    zip dir: './declarative', glob: '', zipFile: 'declarative.zip'
                    archiveArtifacts artifacts: 'allAscii.zip,declarative.zip', fingerprint: true
                }
            }
        }
    }
}
