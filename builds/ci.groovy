pipeline {
    agent {
        kubernetes {
            yamlFile 'builds/docker.yaml'
        }
    }

    environment {
        mvnSettingFileId = '8a289dcd-46b4-43af-986b-d55a979a7806'
        mvnName = 'maven-3'
        VERSION = ""
        GROUP_ID = ""
        ARTIFACT_ID = ""
        BRANCH_BASE_NAME = "${env.GIT_BRANCH}".tokenize('/').last()
        SNAPSHOTS_REPOSITORY = "digite-snapshots"
        DOCKER_REPOSITORY = "digite"
        NOTIFICATION_URL = "${env.Swiftalk_Chat_URL}"
    }

    stages {
        stage('Maven Build & SonarQube') {
            when { expression { return fileExists('pom.xml') } }
            steps {
                container('maven') {
                    configFileProvider([configFile(fileId: "${mvnSettingFileId}", variable: 'MAVEN_SETTINGS')]) {
                        script {
                            FAILED_STAGE = env.STAGE_NAME
                            ARTIFACT_ID = sh(script: 'mvn -s ${MAVEN_SETTINGS} help:evaluate -Dexpression=project.artifactId -q -DforceStdout', returnStdout: true).trim()
                            GROUP_ID = sh(script: 'mvn -s ${MAVEN_SETTINGS} help:evaluate -Dexpression=project.groupId -q -DforceStdout', returnStdout: true).trim()
                            VERSION = sh(script: 'mvn -s ${MAVEN_SETTINGS} help:evaluate -Dexpression=project.version -q -DforceStdout', returnStdout: true).trim()
                            SNAPSHOTS_REPOSITORY = sh(script: 'mvn -s ${MAVEN_SETTINGS} help:evaluate -Dexpression=project.distributionManagement.snapshotRepository.id -q -DforceStdout', returnStdout: true).trim()
                            DOCKER_REPOSITORY = sh(script: 'mvn -s ${MAVEN_SETTINGS} help:evaluate -Dexpression=project.distributionManagement.snapshotRepository.id -q -DforceStdout', returnStdout: true).trim().tokenize('-').first()
                        }
                    }
                    withSonarQubeEnv('SonarQube Cloud Server') {
                        withMaven(globalMavenSettingsConfig: "${mvnSettingFileId}", maven: "${mvnName}") {
                            sh "mvn install sonar:sonar -Dsonar.projectKey=${ARTIFACT_ID}-${BRANCH_BASE_NAME} -Dsonar.projectName=${ARTIFACT_ID}-${BRANCH_BASE_NAME} -Dsonar.projectVersion=${VERSION}  -Dsonar.junit.reportPaths=IBM-Git-Microservice/target/surefire-reports"
                        }
                    }
                }
            }
        }

        /*stage("Quality Gate") {
            when { expression { return fileExists('pom.xml') } }
            steps {
                script {
                    FAILED_STAGE = env.STAGE_NAME
                }
                container('maven') {
                    script {
                        timeout(time: 5, unit: 'MINUTES') {
                            waitForQualityGate abortPipeline: true
                        }
                    }
                }
            }
        }*/


        stage('Maven Nexus Publish') {
            when { expression { return fileExists('pom.xml') } }
            steps {
                container('maven') {
                    script {
                        FAILED_STAGE = env.STAGE_NAME
                    }
                    nexusArtifactUploader artifacts: [
                            [
                                    artifactId: "IBM-Git-Microservice",
                                    classifier: '',
                                    file      : 'IBM-Git-Microservice/pom.xml',
                                    type      : 'pom'
                            ],
                            [
                                    artifactId: "IBM-Git-Microservice",
                                    classifier: '',
                                    file      : "IBM-Git-Microservice/target/ibm-git-microservice-${VERSION}.jar",
                                    type      : 'jar'
                            ]
                    ],
                            credentialsId: 'digite-maven-deployer',
                            groupId: "${GROUP_ID}",
                            nexusUrl: 'nexusrm.cloud.digite.com',
                            nexusVersion: 'nexus3',
                            protocol: 'https',
                            repository: "${SNAPSHOTS_REPOSITORY}",
                            version: "${VERSION}"

                    nexusArtifactUploader artifacts: [
                            [
                                    artifactId: "git-service-fabric",
                                    classifier: '',
                                    file      : 'git-service-fabric/pom.xml',
                                    type      : 'pom'
                            ],
                            [
                                    artifactId: "git-service-fabric",
                                    classifier: '',
                                    file      : "git-service-fabric/target/git-service-fabric-${VERSION}.jar",
                                    type      : 'jar'
                            ]
                    ],
                            credentialsId: 'digite-maven-deployer',
                            groupId: "${GROUP_ID}",
                            nexusUrl: 'nexusrm.cloud.digite.com',
                            nexusVersion: 'nexus3',
                            protocol: 'https',
                            repository: "${SNAPSHOTS_REPOSITORY}",
                            version: "${VERSION}"

                    nexusArtifactUploader artifacts: [
                            [
                                    artifactId: "git-data-lib",
                                    classifier: '',
                                    file      : 'git-data-lib/pom.xml',
                                    type      : 'pom'
                            ],
                            [
                                    artifactId: "git-data-lib",
                                    classifier: '',
                                    file      : "git-data-lib/target/git-data-lib-${VERSION}.jar",
                                    type      : 'jar'
                            ]
                    ],
                            credentialsId: 'digite-maven-deployer',
                            groupId: "${GROUP_ID}",
                            nexusUrl: 'nexusrm.cloud.digite.com',
                            nexusVersion: 'nexus3',
                            protocol: 'https',
                            repository: "${SNAPSHOTS_REPOSITORY}",
                            version: "${VERSION}"

                }
            }
        }

        stage('Publish Docker Image') {
            when { expression { return fileExists('docker/Dockerfile') } }
            steps {
                container('kaniko') {
                    script {
                        FAILED_STAGE = env.STAGE_NAME
                    }
                    script {
                        withCredentials([string(credentialsId: 'kaniko-digite-docker-creds', variable: 'DOCKER_LOGIN')]) {
                            sh 'mkdir -p /kaniko/.docker'
                            sh 'echo {\\"auths\\":{\\"${Nexus_Docker_URL}\\":{\\"auth\\":\\"${DOCKER_LOGIN}\\"}}} > /kaniko/.docker/config.json'
                            sh "/kaniko/executor -f docker/Dockerfile --build-arg VERSION=${VERSION} --build-arg APP=git-service-fabric --context git-service-fabric/target --cache=true \
                                --destination docker.cloud.digite.com/${DOCKER_REPOSITORY}/git-service-fabric:${BRANCH_BASE_NAME}-${VERSION} \
                                --destination docker.cloud.digite.com/${DOCKER_REPOSITORY}/git-service-fabric:${BRANCH_BASE_NAME}-latest"
                        }
                    }
                }
            }
        }
    }

    post {
        failure {
            googlechatnotification url: "${NOTIFICATION_URL}",
                    message: "\\uD83C\\uDF0B Build failed for Job - ${JOB_NAME} - ${JOB_URL}${BUILD_NUMBER}/console at stage `${FAILED_STAGE}`"
        }
        fixed {
            googlechatnotification url: "${NOTIFICATION_URL}",
                    message: "\\ud83e\\udd73 Build fixed for Job - ${JOB_NAME} - ${JOB_URL}${BUILD_NUMBER}/console"
        }
    }
}