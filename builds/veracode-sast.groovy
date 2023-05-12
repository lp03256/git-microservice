pipeline {
    agent {
        kubernetes {
            yamlFile 'builds/docker.yaml'
        }
    }

    environment {
        mvnSettingFileId = '8a289dcd-46b4-43af-986b-d55a979a7806'
        mvnName = 'maven-3'
        APPLICATION_NAME = "SwiftKanban"
        BRANCH_BASE_NAME = "${env.GIT_BRANCH}".tokenize('/').last()
        NOTIFICATION_URL = "${env.Swiftalk_Chat_URL}"
    }

    stages {
        stage('Veracode SAST Scan') {
            steps {
                // Veracode SAST scan using Veracode Jenkins Plugin
                // https://help.veracode.com/r/SR6Zbh48KDeo2rH~Guvpiw/_g4SYeBLZyLguunqgdUP2w
                container('maven') {
                    configFileProvider([configFile(fileId: "${mvnSettingFileId}", variable: 'MAVEN_SETTINGS')]) {
                        script {
                            FAILED_STAGE = env.STAGE_NAME
                            ARTIFACT_ID = sh(script: 'mvn -s ${MAVEN_SETTINGS} help:evaluate -Dexpression=project.artifactId -q -DforceStdout', returnStdout: true).trim()
                        }
                    }
                    withMaven(globalMavenSettingsConfig: "${mvnSettingFileId}", maven: "${mvnName}") {
                        sh 'mvn clean package -DskipTests=true'
                    }
                    withCredentials([usernamePassword(credentialsId: 'global_veracode_login', usernameVariable: 'VERACODE_API_ID', passwordVariable: 'VERACODE_API_KEY')]) {
                        veracode applicationName: "${APPLICATION_NAME}",
                                createProfile: false,
                                canFailJob: true,
                                createSandbox: true,
                                criticality: 'VeryHigh',
                                fileNamePattern: '',
                                replacementPattern: '',
                                sandboxName: "${APPLICATION_NAME}" + "_Fabric_" + "${ARTIFACT_ID}",
                                scanExcludesPattern: '**/target/*sources.jar, **/target/*javadoc.jar, **/target/*.zip, **/target/*.tar.gz',
                                scanIncludesPattern: '',
                                scanName: "${APPLICATION_NAME}" + "_Fabric_" + "${ARTIFACT_ID}" + "_" + "${BRANCH_BASE_NAME}" + "_" + "${BUILD_NUMBER}",
                                teams: '',
                                timeout: 60,
                                uploadExcludesPattern: 'target/*sources.jar, target/*javadoc.jar, target/*.zip, target/*.tar.gz, target/*-stub.jar',
                                uploadIncludesPattern: "git-data-lib/target/${ARTIFACT_ID}-*-SNAPSHOT.jar, git-service-fabric/target/${ARTIFACT_ID}-*-SNAPSHOT.jar, IBM-Git-Microservice/target/${ARTIFACT_ID}-*-SNAPSHOT.jar",
                                vid: "${VERACODE_API_ID}",
                                vkey: "${VERACODE_API_KEY}",
                                waitForScan: true
                    }
                }
            }
        }
    }

    post {
        failure {
            googlechatnotification url: "${NOTIFICATION_URL}",
                    message: "\\uD83C\\uDF0B Veracode SAST SCAN failed for ${JOB_NAME} - ${JOB_URL}${BUILD_NUMBER}/console"
        }
        fixed {
            googlechatnotification url: "${NOTIFICATION_URL}",
                    message: "\\uD83C\\uDF7E Veracode SAST SCAN fixed for ${JOB_NAME} - ${JOB_URL}${BUILD_NUMBER}/console"
        }
    }

}
