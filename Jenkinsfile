#!/usr/bin/env groovy

pipeline {
    // This build requires at least 8Gb of memory
    agent {
        label 'linux-amd64'
    }
    triggers {
        cron('H H * * 0')
    }

    options {
        timestamps()
        timeout(time: 1, unit: 'HOURS')
        skipDefaultCheckout true
    }

    stages {
        stage('Checkout') {
            steps {
                deleteDir()
                checkout scm
            }
        }

        stage('Indexer') {
            steps {
                dir('pluginFolder') {
                    git changelog: false,
                            poll: false,
                            url:'https://github.com/jenkinsci/backend-extension-indexer.git',
                        branch: 'master'
                    script {
                        infra.runMaven(['clean', 'install', '-DskipTests'], 11)
                    }
                    sh 'java -XshowSettings:vm -jar ./target/*-bin/extension-indexer*.jar -plugins ./plugins && mv plugins ..'
                }
            }
        }

        stage('Generator') {
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

        stage('Publisher') {
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
                        try {
                            sh './test-the-generated-docs.sh'
                        } catch (err) {
                            // Mark job unstable if tests do not pass
                            unstable 'Test failed, see preceding lines of output'
                        }
                    }
                }
            }
        }
    }
}
