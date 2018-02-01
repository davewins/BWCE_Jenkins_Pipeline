node {
   def mvnHome
   stage('Checkout') {
      git 'https://github.com/davewins/restbookstore.git'
      mvnHome = tool 'Maven-3.3.9'
   }
   stage('Build') {
      // Run the maven build
      sh "'${mvnHome}/bin/mvn' -f ./*.parent/pom.xml clean package initialize"
   }
   stage('Build Docker Container') {
       echo "Creating Docker Container"
      sh "docker build -t restbookstore ."
   }
    stage('Stop existing Docker Container') {
    def CONTAINER_ID = sh(script: 'docker ps -a -q -f name=demo', returnStdout: true).trim()
    sh """
        if [ ! -z $CONTAINER_ID ]
        then 
            docker stop $CONTAINER_ID
            docker rm -f $CONTAINER_ID
        fi
    """
   }
   stage('Remove existing Docker Image') {
    def IMAGE_ID = sh(script: 'docker images | grep \"<none>\" | awk \"{print \\$3}\"', returnStdout: true)
    sh """
        if [ "X$IMAGE_ID" != "X" ]
        then
            docker rmi -f $IMAGE_ID
        fi
    """
   }
   stage('Start Docker Image') {
    def PUBLIC_HOSTNAME = sh(script: 'curl http://169.254.169.254/latest/meta-data/public-hostname', returnStdout: true).trim()
    sh "docker run --restart unless-stopped -h $PUBLIC_HOSTNAME -d --name demo -p 8888:8888 -e BW_PROFILE=Docker --link monitor:bwcemonitoringservice -e BW_APP_MONITORING_CONFIG='{\"url\":\"http://bwcemonitoringservice:8080\"}' restbookstore"
   }
   stage('Waiting for Service to become available') {
    def PUBLIC_HOSTNAME = sh(script: 'curl http://169.254.169.254/latest/meta-data/public-hostname', returnStdout: true).trim()
    echo "Hostname = $PUBLIC_HOSTNAME"
   timeout(5) {
    waitUntil {
       script {
         def r = sh script: "wget -q http://$PUBLIC_HOSTNAME:8888/swagger -O /dev/null", returnStatus: true
         return (r == 0);
       }
    }
   }

}
}
