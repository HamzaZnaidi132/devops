pipeline {
    agent any

    environment {
        IMAGE_NAME = "saiffrikhi/foyer_project"  // Your DockerHub image
        IMAGE_TAG = "${BUILD_NUMBER}"  // Use build number for versioning
        DOCKERHUB_USERNAME = credentials('docker-hub')  // Store DockerHub creds in Jenkins
        DOCKERHUB_PASSWORD = credentials('dockerhub-password')
        K8S_NAMESPACE = "devops"
        CONTEXT_PATH = "/tp-foyer"
        MAVEN_OPTS = "-Dmaven.repo.local=/tmp/.m2/repository"  // Cache Maven dependencies
    }

    triggers {
        githubPush()  // GitHub webhook trigger
    }

    stages {
        stage('Checkout') {
            steps {
                echo "📦 Fetching code from GitHub..."
                git branch: 'main',
                    url: 'https://github.com/saifeddinefrikhi-lab/FoyerProject.git',
                    changelog: true,
                    poll: true
            }
        }

        stage('Build & Test') {
            steps {
                echo "🔨 Building application..."
                sh '''
                    echo "=== Clean Maven build ==="
                    mvn clean package -DskipTests -B -q

                    echo "=== Verify JAR ==="
                    JAR_FILE=$(find target -name "*.jar" -type f | head -1)
                    if [ -f "$JAR_FILE" ]; then
                        echo "✅ JAR found: $(ls -lh "$JAR_FILE")"
                    else
                        echo "❌ No JAR file found!"
                        exit 1
                    fi
                '''
            }
        }

        stage('Build & Push Docker Image') {
            steps {
                echo "🐳 Building and pushing Docker image to DockerHub..."
                script {
                    // Use existing Dockerfile (assuming it's in project root)
                    sh """
                        echo "=== Building Docker image ==="
                        docker build -t ${IMAGE_NAME}:${IMAGE_TAG} .
                        docker tag ${IMAGE_NAME}:${IMAGE_TAG} ${IMAGE_NAME}:latest

                        echo "=== Logging into DockerHub ==="
                        echo ${DOCKERHUB_PASSWORD} | docker login -u ${DOCKERHUB_USERNAME} --password-stdin

                        echo "=== Pushing to DockerHub ==="
                        docker push ${IMAGE_NAME}:${IMAGE_TAG}
                        docker push ${IMAGE_NAME}:latest

                        echo "=== Cleanup local images ==="
                        docker logout
                    """
                }
            }
        }

        stage('Clean Old Resources') {
            steps {
                echo "🧹 Cleaning old resources..."
                sh """
                    # Clean only resources that will be recreated
                    kubectl delete deployment spring-app -n ${K8S_NAMESPACE} --ignore-not-found=true --timeout=30s
                    kubectl delete service spring-service -n ${K8S_NAMESPACE} --ignore-not-found=true --timeout=30s
                    kubectl delete configmap spring-config -n ${K8S_NAMESPACE} --ignore-not-found=true --timeout=30s
                    kubectl delete secret spring-secret -n ${K8S_NAMESPACE} --ignore-not-found=true --timeout=30s

                    # Wait for cleanup (reduced time)
                    sleep 5
                """
            }
        }

        stage('Deploy MySQL (Fast)') {
            steps {
                echo "🗄️ Deploying MySQL..."
                script {
                    // Create MySQL deployment with readiness probe for faster startup detection
                    String mysqlYaml = """
apiVersion: v1
kind: PersistentVolume
metadata:
  name: mysql-pv-fast
spec:
  capacity:
    storage: 2Gi
  accessModes:
    - ReadWriteOnce
  hostPath:
    path: "/tmp/mysql-data"
    type: DirectoryOrCreate
---
apiVersion: v1
kind: PersistentVolumeClaim
metadata:
  name: mysql-pvc-fast
  namespace: ${K8S_NAMESPACE}
spec:
  accessModes:
    - ReadWriteOnce
  resources:
    requests:
      storage: 2Gi
---
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
        readinessProbe:
          exec:
            command:
            - mysqladmin
            - ping
            - -h
            - localhost
          initialDelaySeconds: 5
          periodSeconds: 5
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
          claimName: mysql-pvc-fast
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
    - port: 3306
      targetPort: 3306
  type: ClusterIP
"""

                    writeFile file: 'mysql-fast.yaml', text: mysqlYaml

                    sh """
                        echo "=== Deploying MySQL ==="
                        kubectl apply -f mysql-fast.yaml

                        echo "=== Waiting for MySQL to be ready (max 30s) ==="
                        kubectl wait --for=condition=ready pod -l app=mysql -n ${K8S_NAMESPACE} --timeout=30s

                        # Initialize database immediately
                        MYSQL_POD=\$(kubectl get pods -n ${K8S_NAMESPACE} -l app=mysql -o jsonpath='{.items[0].metadata.name}')
                        kubectl exec -n ${K8S_NAMESPACE} \${MYSQL_POD} -- \\
                            mysql -u root -proot123 -e "
                                CREATE USER IF NOT EXISTS 'spring'@'%' IDENTIFIED BY 'spring123';
                                GRANT ALL PRIVILEGES ON springdb.* TO 'spring'@'%';
                                FLUSH PRIVILEGES;
                            "
                    """
                }
            }
        }

        stage('Deploy Spring Boot Application') {
            steps {
                echo "🚀 Deploying Spring Boot Application..."
                script {
                    // Create ConfigMap and Secret
                    String configYaml = """
apiVersion: v1
kind: ConfigMap
metadata:
  name: spring-config
  namespace: ${K8S_NAMESPACE}
data:
  SPRING_DATASOURCE_URL: jdbc:mysql://mysql-service:3306/springdb?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC
  SPRING_DATASOURCE_DRIVER_CLASS_NAME: com.mysql.cj.jdbc.Driver
  SPRING_JPA_HIBERNATE_DDL_AUTO: update
  SERVER_SERVLET_CONTEXT_PATH: ${CONTEXT_PATH}
  SPRING_APPLICATION_NAME: foyer-app
---
apiVersion: v1
kind: Secret
metadata:
  name: spring-secret
  namespace: ${K8S_NAMESPACE}
type: Opaque
stringData:  # Use stringData to avoid base64 encoding
  SPRING_DATASOURCE_USERNAME: spring
  SPRING_DATASOURCE_PASSWORD: spring123
"""

                    writeFile file: 'spring-config.yaml', text: configYaml

                    // Create Deployment and Service
                    String deploymentYaml = """
apiVersion: apps/v1
kind: Deployment
metadata:
  name: spring-app
  namespace: ${K8S_NAMESPACE}
spec:
  replicas: 2
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
        imagePullPolicy: Always  # Always pull from DockerHub
        ports:
        - containerPort: 8080
        envFrom:
        - configMapRef:
            name: spring-config
        - secretRef:
            name: spring-secret
        readinessProbe:
          httpGet:
            path: ${CONTEXT_PATH}/actuator/health
            port: 8080
          initialDelaySeconds: 30
          periodSeconds: 10
        livenessProbe:
          httpGet:
            path: ${CONTEXT_PATH}/actuator/health
            port: 8080
          initialDelaySeconds: 60
          periodSeconds: 30
        resources:
          requests:
            memory: "512Mi"
            cpu: "250m"
          limits:
            memory: "1Gi"
            cpu: "500m"
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
    - port: 8080
      targetPort: 8080
      nodePort: 30080
  type: NodePort
"""

                    writeFile file: 'spring-deployment.yaml', text: deploymentYaml

                    sh """
                        echo "=== Applying configuration ==="
                        kubectl apply -f spring-config.yaml
                        kubectl apply -f spring-deployment.yaml

                        echo "=== Waiting for pods to be ready (max 60s) ==="
                        kubectl wait --for=condition=ready pod -l app=spring-app -n ${K8S_NAMESPACE} --timeout=60s
                    """
                }
            }
        }

        stage('Verify & Test') {
            steps {
                echo "✅ Verifying deployment..."
                sh """
                    echo "=== Current status ==="
                    kubectl get pods,svc -n ${K8S_NAMESPACE}

                    echo "=== Testing application ==="
                    MINIKUBE_IP=\$(minikube ip)

                    # Quick health check
                    for i in \$(seq 1 10); do
                        if curl -s -f -m 5 "http://\${MINIKUBE_IP}:30080${CONTEXT_PATH}/actuator/health" > /dev/null; then
                            echo "✅ Application is healthy"

                            # Test API endpoint
                            echo "=== Testing API ==="
                            curl -s "http://\${MINIKUBE_IP}:30080${CONTEXT_PATH}/foyer/getAllFoyers" | head -c 200
                            echo ""
                            break
                        else
                            echo "⏱️ Waiting... (\$i/10)"
                            sleep 5
                        fi
                    done
                """
            }
        }
    }

    post {
        always {
            echo "🏁 Pipeline completed"
            sh """
                echo "=== Cleaning temporary files ==="
                rm -f mysql-fast.yaml spring-config.yaml spring-deployment.yaml 2>/dev/null || true

                echo "=== Final report ==="
                MINIKUBE_IP=\$(minikube ip 2>/dev/null || echo "N/A")
                echo "Docker Image: ${IMAGE_NAME}:${IMAGE_TAG}"
                echo "Access URL: http://\${MINIKUBE_IP}:30080${CONTEXT_PATH}"
            """
        }
        failure {
            echo "💥 Pipeline failed"
            sh """
                echo "=== Debug information ==="
                kubectl describe pods -n ${K8S_NAMESPACE} -l app=spring-app | tail -50
                kubectl logs -n ${K8S_NAMESPACE} -l app=spring-app --tail=100
                kubectl get events -n ${K8S_NAMESPACE} --sort-by='.lastTimestamp' | tail -20
            """
        }
    }
}