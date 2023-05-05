#!/usr/bin/env groovy

pipeline {
    // This build requires at least 8Gb of memory
    agent { label 'linux-amd64' }
    triggers {
        cron('H H * * 0')
    }

    options {
        timestamps()
        timeout(time: 1, unit: 'HOURS')
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
                    script {
                        infra.runMaven(['clean', 'install', '-DskipTests'], 11)
                    }
                }
            }
        }

        stage('Run Indexer') {
            steps {
                dir('pluginFolder') {
                    sh 'java -verbose:gc -XshowSettings:vm -jar ./target/*-bin/extension-indexer*.jar -plugins ./plugins && mv plugins ..'
                }
            }
        }

        stage('Generate Documentation') {
            steps {
                dir('docFolder') {
                    checkout scm
                    script {
                        infra.runMaven(['clean', 'install', '-DskipTests'], 11)
                    }
                    sh 'mv ../plugins . && java -XshowSettings:vm -jar ./target/*-bin/pipeline-steps-doc-generator*.jar'
                }
            }
        }

        stage('Publish and Clean up') {
            steps {
                dir('docFolder') {
                    // allAscii and declarative must not include directory name in their zip files
                    sh  '''
                        ( cd allAscii    && zip -r -1 -q ../allAscii.zip    . )
                        ( cd declarative && zip -r -1 -q ../declarative.zip . )
                        '''
                    script {
                        if (env.BRANCH_IS_PRIMARY && infra.isInfra()) {
                            infra.publishReports(['allAscii.zip', 'declarative.zip'])
                        } else {
                            // On branches and PR or not infra, archive the files
                            archiveArtifacts artifacts: 'allAscii.zip,declarative.zip', fingerprint: true
                        }
                    }
                    // Fail job if tests do not pass
                    sh './test-the-generated-docs.sh'
                }
            }
        }
    }
}
