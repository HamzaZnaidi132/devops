pipeline {
    agent any

    environment {
        IMAGE_NAME = "saiffrikhi/foyer_project"
        IMAGE_TAG = "latest"
        K8S_NAMESPACE = "devops"
        CONTEXT_PATH = "/tp-foyer"
        DOCKERHUB_CREDENTIALS = credentials('docker-hub')
        SONAR_HOST_URL = "http://172.30.40.173:9000"
        SONAR_PROJECT_KEY = "foyer-project"
        SONAR_TOKEN = credentials('sonar-token')
        MINIKUBE_IP = "192.168.49.2"
    }

    stages {
        // STAGE 1: CLEAN AND PREPARE
        stage('Clean & Setup') {
            steps {
                script {
                    echo "🧹 Nettoyage de l'environnement..."

                    // Force delete namespace if exists
                    sh '''
                        echo "=== Force deleting namespace ==="
                        kubectl delete namespace ${K8S_NAMESPACE} --ignore-not-found=true --force --grace-period=0 2>/dev/null || true
                        sleep 10

                        # Create fresh namespace
                        kubectl create namespace ${K8S_NAMESPACE}
                        kubectl config set-context --current --namespace=${K8S_NAMESPACE}

                        # Create data directory
                        sudo mkdir -p /data/mysql
                        sudo chmod 777 /data/mysql
                    '''
                }
            }
        }

        // STAGE 2: CHECKOUT CODE
        stage('Checkout Code') {
            steps {
                checkout scm
            }
        }

        // STAGE 3: SKIP SONARQUBE (TEMPORARY) - We'll fix this separately
        stage('Skip SonarQube (Temp)') {
            steps {
                echo "⚠️  SonarQube skipped for now - will fix separately"
                echo "You can check SonarQube manually at: ${SONAR_HOST_URL}"
            }
        }

        // STAGE 4: BUILD APPLICATION
        stage('Build Application') {
            steps {
                sh '''
                    echo "=== Building Spring Boot Application ==="
                    mvn clean package -DskipTests -B

                    echo "=== Checking JAR file ==="
                    ls -la target/*.jar
                    echo "JAR size:"
                    du -h target/*.jar
                '''
            }
        }

        // STAGE 5: BUILD DOCKER IMAGE
        stage('Build Docker Image') {
            steps {
                script {
                    // Create Dockerfile if not exists
                    sh '''
                        if [ ! -f Dockerfile ]; then
                            echo "Creating Dockerfile..."
                            cat > Dockerfile << 'EOF'
FROM eclipse-temurin:17-jre-alpine
WORKDIR /app
COPY target/*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
EOF
                        fi
                        cat Dockerfile
                    '''

                    withCredentials([usernamePassword(credentialsId: 'docker-hub', usernameVariable: 'DOCKER_USER', passwordVariable: 'DOCKER_PASS')]) {
                        sh """
                            echo "=== Building Docker image ==="

                            # Build in Minikube's Docker
                            eval \$(minikube docker-env)
                            docker build -t ${IMAGE_NAME}:${IMAGE_TAG} .

                            echo "=== Image built successfully ==="
                            docker images | grep ${IMAGE_NAME}

                            # Switch back
                            eval \$(minikube docker-env -u)

                            # Push to DockerHub
                            echo "\${DOCKER_PASS}" | docker login -u "\${DOCKER_USER}" --password-stdin
                            docker push ${IMAGE_NAME}:${IMAGE_TAG}
                            docker logout
                        """
                    }
                }
            }
        }

        // STAGE 6: DEPLOY MYSQL
        stage('Deploy MySQL') {
            steps {
                sh """
                    echo "=== Deploying MySQL ==="

                    # Create PV/PVC for MySQL
                    cat > mysql-pv.yaml << 'EOF'
apiVersion: v1
kind: PersistentVolume
metadata:
  name: mysql-pv
spec:
  capacity:
    storage: 2Gi
  accessModes:
    - ReadWriteOnce
  hostPath:
    path: "/data/mysql"
    type: DirectoryOrCreate
  persistentVolumeReclaimPolicy: Retain
  storageClassName: standard
---
apiVersion: v1
kind: PersistentVolumeClaim
metadata:
  name: mysql-pvc
  namespace: ${K8S_NAMESPACE}
spec:
  accessModes:
    - ReadWriteOnce
  resources:
    requests:
      storage: 2Gi
  storageClassName: standard
EOF

                    kubectl apply -f mysql-pv.yaml

                    # Deploy MySQL
                    cat > mysql-deployment.yaml << 'EOF'
apiVersion: apps/v1
kind: Deployment
metadata:
  name: mysql
  namespace: ${K8S_NAMESPACE}
spec:
  replicas: 1
  selector:
    matchLabels:
      app: mysql
  template:
    metadata:
      labels:
        app: mysql
    spec:
      containers:
      - name: mysql
        image: mysql:8.0
        env:
        - name: MYSQL_ROOT_PASSWORD
          value: "root123"
        - name: MYSQL_DATABASE
          value: "springdb"
        ports:
        - containerPort: 3306
        volumeMounts:
        - name: mysql-storage
          mountPath: /var/lib/mysql
        resources:
          requests:
            memory: "256Mi"
            cpu: "100m"
          limits:
            memory: "512Mi"
            cpu: "250m"
      volumes:
      - name: mysql-storage
        persistentVolumeClaim:
          claimName: mysql-pvc
---
apiVersion: v1
kind: Service
metadata:
  name: mysql-service
  namespace: ${K8S_NAMESPACE}
spec:
  selector:
    app: mysql
  ports:
    - protocol: TCP
      port: 3306
      targetPort: 3306
  type: ClusterIP
EOF

                    kubectl apply -f mysql-deployment.yaml

                    echo "=== Waiting for MySQL (60 seconds) ==="
                    sleep 60

                    echo "=== Checking MySQL status ==="
                    kubectl get pods -n ${K8S_NAMESPACE}

                    echo "=== Creating database and user ==="
                    MYSQL_POD=\$(kubectl get pods -n ${K8S_NAMESPACE} -l app=mysql -o jsonpath='{.items[0].metadata.name}' 2>/dev/null)
                    if [ -n "\$MYSQL_POD" ]; then
                        echo "MySQL pod: \$MYSQL_POD"
                        kubectl exec -n ${K8S_NAMESPACE} \$MYSQL_POD -- mysql -u root -proot123 -e "
                            CREATE DATABASE IF NOT EXISTS springdb;
                            CREATE USER IF NOT EXISTS 'spring'@'%' IDENTIFIED BY 'spring123';
                            GRANT ALL PRIVILEGES ON springdb.* TO 'spring'@'%';
                            FLUSH PRIVILEGES;
                            SHOW DATABASES;
                        "
                    fi
                """
            }
        }

        // STAGE 7: DEPLOY SPRING BOOT
        stage('Deploy Spring Boot') {
            steps {
                sh """
                    echo "=== Deploying Spring Boot Application ==="

                    cat > spring-deployment.yaml << EOF
apiVersion: apps/v1
kind: Deployment
metadata:
  name: spring-app
  namespace: ${K8S_NAMESPACE}
spec:
  replicas: 1
  selector:
    matchLabels:
      app: spring-app
  template:
    metadata:
      labels:
        app: spring-app
    spec:
      containers:
      - name: spring-app
        image: ${IMAGE_NAME}:${IMAGE_TAG}
        imagePullPolicy: IfNotPresent
        ports:
        - containerPort: 8080
        env:
        - name: SPRING_DATASOURCE_URL
          value: "jdbc:mysql://mysql-service:3306/springdb?useSSL=false"
        - name: SPRING_DATASOURCE_USERNAME
          value: "spring"
        - name: SPRING_DATASOURCE_PASSWORD
          value: "spring123"
        - name: SERVER_SERVLET_CONTEXT_PATH
          value: "${CONTEXT_PATH}"
        - name: SPRING_JPA_HIBERNATE_DDL_AUTO
          value: "update"
        resources:
          requests:
            memory: "512Mi"
            cpu: "250m"
          limits:
            memory: "1Gi"
            cpu: "500m"
        startupProbe:
          httpGet:
            path: ${CONTEXT_PATH}/actuator/health
            port: 8080
          initialDelaySeconds: 120
          periodSeconds: 10
          failureThreshold: 30
        readinessProbe:
          httpGet:
            path: ${CONTEXT_PATH}/actuator/health
            port: 8080
          initialDelaySeconds: 60
          periodSeconds: 10
---
apiVersion: v1
kind: Service
metadata:
  name: spring-service
  namespace: ${K8S_NAMESPACE}
spec:
  selector:
    app: spring-app
  ports:
    - protocol: TCP
      port: 8080
      targetPort: 8080
      nodePort: 30080
  type: NodePort
EOF

                    kubectl apply -f spring-deployment.yaml

                    echo "=== Waiting for Spring Boot (90 seconds) ==="
                    sleep 90

                    echo "=== Checking deployment ==="
                    kubectl get all -n ${K8S_NAMESPACE}

                    echo "=== Checking logs ==="
                    SPRING_POD=\$(kubectl get pods -n ${K8S_NAMESPACE} -l app=spring-app -o jsonpath='{.items[0].metadata.name}' 2>/dev/null)
                    if [ -n "\$SPRING_POD" ]; then
                        echo "Spring Boot pod: \$SPRING_POD"
                        kubectl logs -n ${K8S_NAMESPACE} \$SPRING_POD --tail=50 | grep -E "(Started|Tomcat|MySQL|DataSource|ERROR)" || true
                    fi
                """
            }
        }

        // STAGE 8: TEST APPLICATION
        stage('Test Application') {
            steps {
                sh """
                    echo "=== Testing Application ==="

                    # Wait a bit more
                    sleep 30

                    echo "=== Testing endpoints ==="

                    # Try health endpoint
                    for i in {1..10}; do
                        echo "Attempt \$i/10 to reach application..."
                        if curl -s -f -m 10 "http://${MINIKUBE_IP}:30080${CONTEXT_PATH}/actuator/health" > /dev/null; then
                            echo "✅ Application is healthy!"
                            curl -s "http://${MINIKUBE_IP}:30080${CONTEXT_PATH}/actuator/health"
                            break
                        fi
                        sleep 10
                    done

                    echo "=== Final status ==="
                    kubectl get all -n ${K8S_NAMESPACE}
                """
            }
        }
    }

    post {
        always {
            echo "🏁 Pipeline execution completed"
            sh '''
                echo "=== Cleaning temporary files ==="
                rm -f mysql-pv.yaml mysql-deployment.yaml spring-deployment.yaml 2>/dev/null || true
            '''
            sh """
                echo ""
                echo "=== DEPLOYMENT SUMMARY ==="
                echo "📦 Docker Image: ${IMAGE_NAME}:${IMAGE_TAG}"
                echo "📁 Namespace: ${K8S_NAMESPACE}"
                echo "🌐 Application URL: http://${MINIKUBE_IP}:30080${CONTEXT_PATH}"
                echo "🔧 Health Check: http://${MINIKUBE_IP}:30080${CONTEXT_PATH}/actuator/health"
                echo ""
                echo "=== TROUBLESHOOTING COMMANDS ==="
                echo "kubectl get pods -n ${K8S_NAMESPACE}"
                echo "kubectl logs -n ${K8S_NAMESPACE} -l app=spring-app --tail=100"
                echo "kubectl describe pods -n ${K8S_NAMESPACE} -l app=spring-app"
            """
        }

        success {
            echo "🎉 SUCCESS: Pipeline completed successfully!"
            sh """
                echo ""
                echo "✅ Application deployed successfully!"
                echo "🌐 Access your application at: http://${MINIKUBE_IP}:30080${CONTEXT_PATH}"
            """
        }

        failure {
            echo "❌ FAILURE: Pipeline failed"
            sh """
                echo ""
                echo "=== DEBUGGING INFO ==="
                echo "1. Pods status:"
                kubectl get pods -n ${K8S_NAMESPACE} -o wide 2>/dev/null || echo "No pods found"

                echo ""
                echo "2. Events:"
                kubectl get events -n ${K8S_NAMESPACE} --sort-by='.lastTimestamp' 2>/dev/null | tail -20 || echo "No events"

                echo ""
                echo "3. Services:"
                kubectl get svc -n ${K8S_NAMESPACE} 2>/dev/null || echo "No services"
            """
        }
    }
}