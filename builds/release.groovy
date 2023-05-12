pipeline {
    agent {
        kubernetes {
            yamlFile 'builds/docker.yaml'
        }
    }
    environment {
        mvnSettingFileId = '8a289dcd-46b4-43af-986b-d55a979a7806'
        maven = "maven-3"
        DOCKER_REPOSITORY = "digite"
        NOTIFICATION_URL = "${env.Swiftalk_Chat_URL}"
    }
    stages {

        stage('Maven Release Prepare') {
            steps {
                container('maven') {
                    configFileProvider([configFile(fileId: "${mvnSettingFileId}", variable: 'MAVEN_SETTINGS')]) {
                        script {
                            FAILED_STAGE = env.STAGE_NAME
                            DOCKER_REPOSITORY=sh (script: 'mvn  -s ${MAVEN_SETTINGS} help:evaluate -Dexpression=project.distributionManagement.snapshotRepository.id -q -DforceStdout', returnStdout: true).trim().tokenize('-').first()
                        }
                    }
                    checkout changelog: false, poll: false, scm: [$class: 'GitSCM', branches: [[name: 'refs/heads/main']], extensions: [[$class: 'LocalBranch', localBranch: 'main']], userRemoteConfigs: [[credentialsId: "${params.GIT_CREDENTIALS}", name: 'origin', refspec: '+refs/heads/main:refs/remotes/origin/main +refs/heads/dev:refs/remotes/origin/dev', url: "${env.GIT_URL}"]]]
                    script {
                        withMaven(mavenSettingsConfig: "${mvnSettingFileId}", maven: "${maven}"){
                            sh '''
                                mvn --batch-mode clean install
                                mvn --batch-mode -Dtag=${RELEASE_VERSION} -Dproject.rel.com.digite.cloud:git-service-fabric=${RELEASE_VERSION} -Dproject.rel.com.digite.cloud:ibm-git-microservice=${RELEASE_VERSION} -Dproject.rel.com.digite.cloud:git-data-lib=${RELEASE_VERSION} \
                                  -Dproject.dev.com.digite.cloud:git-service-fabric=${DEVELOPMENT_VERSION} -Dproject.dev.com.digite.cloud:ibm-git-microservice=${DEVELOPMENT_VERSION} -Dproject.dev.com.digite.cloud:git-service=${DEVELOPMENT_VERSION} -Dproject.dev.com.digite.cloud:git-data-lib=${DEVELOPMENT_VERSION} release:prepare
                            '''
                        }
                    }
                }
            }
        }

        stage('Maven Release Perform') {
            steps {
                script { FAILED_STAGE = env.STAGE_NAME }

                container('maven') {
                    script {
                        withMaven(mavenSettingsConfig: "${mvnSettingFileId}", maven: "${maven}"){
                            sh '''
                                mvn --batch-mode release:perform -Darguments=-DskipTests
                            '''
                        }
                    }
                }
            }
        }

        stage('Merge Next Dev Version') {
            steps{
                script { FAILED_STAGE = env.STAGE_NAME }

                container('maven') {
                    checkout changelog: false, poll: false, scm: [$class: 'GitSCM', branches: [[name: 'refs/heads/dev']], extensions: [[$class: 'LocalBranch', localBranch: 'dev']], userRemoteConfigs: [[credentialsId: "${params.GIT_CREDENTIALS}", name: 'origin', refspec: '+refs/heads/main:refs/remotes/origin/main +refs/heads/dev:refs/remotes/origin/dev', url: "${env.GIT_URL}"]]]
                    script{
                        withCredentials([usernamePassword(credentialsId: "${params.GIT_CREDENTIALS}", passwordVariable: 'GIT_PASSWORD', usernameVariable: 'GIT_USERNAME')]) {
                            sh'''
                                git rebase main
                                git config --local credential.helper "!f() { echo username=\\$GIT_USERNAME; echo password=\\$GIT_PASSWORD; }; f"
                                git push origin dev
                            '''
                        }
                    }
                }
            }
        }

        stage('Publish Docker Image') {
            when { expression { return fileExists('docker/Dockerfile') } }
            steps {
                script {
                    FAILED_STAGE = env.STAGE_NAME
                }
                container('kaniko') {
                    script {
                        withCredentials([string(credentialsId: 'kaniko-digite-docker-creds', variable: 'DOCKER_LOGIN')]) {
                            sh 'mkdir -p /kaniko/.docker'
                            sh 'echo {\\"auths\\":{\\"${Nexus_Docker_URL}\\":{\\"auth\\":\\"${DOCKER_LOGIN}\\"}}} > /kaniko/.docker/config.json'
                            sh "/kaniko/executor -f docker/Dockerfile --build-arg VERSION=${RELEASE_VERSION} --build-arg APP=git-service-fabric --context git-service-fabric/target --cache=true \
                            --destination docker.cloud.digite.com/${DOCKER_REPOSITORY}/git-service-fabric:${RELEASE_VERSION} "
                        }
                    }
                }
            }
        }
    }

    post {
        failure {
            googlechatnotification url: "${NOTIFICATION_URL}",
                    message: "\\uD83C\\uDF0B Release Build failed for ${ARTIFACT_ID} failed for ${JOB_NAME} - ${JOB_URL}${BUILD_NUMBER}/console at stage `${FAILED_STAGE}`"
        }
        success {
            googlechatnotification url: "${NOTIFICATION_URL}",
                    message: "\\uD83C\\uDF7E New Version ${RELEASE_VERSION} Released for ${ARTIFACT_ID} - ${JOB_URL}${BUILD_NUMBER}/console"
        }
    }
}
