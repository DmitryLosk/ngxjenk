pipeline {
    agent {node('agent')}

    environment {
        IMAGE = 'nginx:latest'
        C_NAME = 'nginxjenk'
        PORT_MP = '9889:80'
        EMAIL_RECIPIENT = 'my_email@domain.ru'
    }

    stages {
        stage('Clone Repository') {
            steps {
                cleanWs()
                checkout scm
            }
        }

        stage('Run Nginx Container') {
            steps {
                script {
                    sh "docker run -d --name ${C_NAME} -p ${PORT_MP} -v \$(pwd)/index.html:/usr/share/nginx/html/index.html ${IMAGE}"
                }
            }
        }

        stage('Check HTTP Response') {
            steps {
                script {
                    def response = sh(script: "curl -o /dev/null -s -w '%{http_code}' http://51.250.11.245:9889", returnStdout: true).trim()
                    echo "Ответ код $response"
                    if (response != '200') {
                        error "HTTP response code is not 200, but ${response}"
                    }
                }
            }
        }

        stage('Check MD5 Sum') {
            steps {
                script {
                    def local_md5 = sh(script: "md5sum index.html | awk '{ print \$1 }'", returnStdout: true).trim()
                    def container_md5 = sh(script: "curl -s http://51.250.11.245:9889/index.html | md5sum | awk '{ print \$1 }'", returnStdout: true).trim()
                    echo "$local_md5"
                    echo "$container_md5"
                    if (local_md5 != container_md5) {
                        error "MD5 sums do not match! Local: ${local_md5}, Container: ${container_md5}"
                    }
                }
            }
        }
    }

    post {
        always {
            script {
                sh "sudo apt-get -y install sendmail"
                sh "docker stop ${C_NAME} || true"
                sh "docker rm -f ${C_NAME} || true"
            }
        }
        success {
            echo 'Pipeline completed successfully.'
            script {
                sh """
echo "Subject: Build Successful: ${currentBuild.fullDisplayName}\n\nSuccess! Build ${currentBuild.fullDisplayName} completed successfully.\n\nDetails at: ${env.BUILD_URL}" | sendmail ${EMAIL_RECIPIENT}
"""
            }
        }
        failure {
            echo 'Pipeline failed.'
            script {
                sh """
echo "Subject: Build Failed: ${currentBuild.fullDisplayName}\n\nUnfortunately, Build ${currentBuild.fullDisplayName} failed.\n\nDetails at: ${env.BUILD_URL}" | sendmail ${EMAIL_RECIPIENT}
"""
            }
        }
    }
}