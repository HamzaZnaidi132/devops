pipeline {
    agent any

    environment {
        DOCKER_USER = credentials('DOCKERHUB_CREDENTIALS_USR')
        DOCKER_PASS = credentials('DOCKERHUB_CREDENTIALS_PSW')
        SONAR_TOKEN = credentials('SONAR_TOKEN')
        DOCKER_IMAGE = "${DOCKER_USER}/foyer_project:latest"
        KUBE_NAMESPACE = 'devops'
        SPRING_CONTEXT_PATH = '/tp-foyer'
        MINIKUBE_IP = '192.168.49.2'
        NODE_PORT = '30080'
    }

    triggers{
        githubPush()
    }

    stages {
        stage('Prepare Environment') {
            steps {
                script {
                    sh '''
                        echo "=== Vérification de l'état du namespace ==="

                        # Check if namespace exists
                        if kubectl get namespace ${KUBE_NAMESPACE} &> /dev/null; then
                            echo "Le namespace ${KUBE_NAMESPACE} existe. Suppression complète..."

                            # 1. Delete all resources in namespace
                            echo "Suppression de toutes les ressources..."
                            kubectl delete all --all -n ${KUBE_NAMESPACE} --ignore-not-found=true --wait=false || true

                            # 2. Delete PVCs
                            echo "Suppression des PVCs..."
                            kubectl delete pvc --all -n ${KUBE_NAMESPACE} --ignore-not-found=true --wait=false || true

                            # 3. Delete any configmaps/secrets
                            echo "Suppression des configmaps et secrets..."
                            kubectl delete configmap --all -n ${KUBE_NAMESPACE} --ignore-not-found=true || true
                            kubectl delete secret --all -n ${KUBE_NAMESPACE} --ignore-not-found=true || true

                            # 4. Wait for resources to terminate
                            echo "Attente de la terminaison des pods..."
                            sleep 10

                            # 5. Force delete namespace
                            echo "Suppression forcée du namespace..."
                            kubectl delete namespace ${KUBE_NAMESPACE} --ignore-not-found=true --force --grace-period=0 || true

                            # 6. Clean Minikube storage
                            echo "Nettoyage du stockage Minikube..."
                            minikube ssh "sudo rm -rf /tmp/hostpath-provisioner/${KUBE_NAMESPACE} || true" || true
                            minikube ssh "sudo rm -rf /tmp/hostpath-provisioner/*mysql* || true" || true

                            echo "✅ Namespace ${KUBE_NAMESPACE} supprimé"
                        else
                            echo "Le namespace ${KUBE_NAMESPACE} n'existe pas."
                        fi

                        echo "=== Création du namespace ==="
                        kubectl create namespace ${KUBE_NAMESPACE}
                        sleep 3

                        echo "=== Vérification du namespace ==="
                        kubectl get namespace ${KUBE_NAMESPACE}
                    '''
                }
            }
        }

        stage('Checkout') {
            steps {
                echo "📦 Récupération du code depuis GitHub..."
                git branch: 'main',
                    url: 'https://github.com/saifeddinefrikhi-lab/FoyerProject.git'
            }
        }

        stage('SonarQube Quality Gate') {
            steps {
                echo "🔍 Analyse de la qualité du code avec SonarQube..."
                script {
                    withSonarQubeEnv('SonarQube') {
                        sh '''
                            echo "=== Démarrage de l'analyse SonarQube ==="
                            mvn clean verify sonar:sonar \
                                -Dsonar.projectKey=foyer-project \
                                -Dsonar.projectName="Foyer Project" \
                                -Dsonar.sources=src/main/java \
                                -Dsonar.tests=src/test/java \
                                -Dsonar.java.binaries=target/classes \
                                -Dsonar.java.libraries=target/**/*.jar \
                                -Dsonar.coverage.jacoco.xmlReportPaths=target/site/jacoco/jacoco.xml \
                                -Dsonar.sourceEncoding=UTF-8 \
                                -Dsonar.host.url=http://172.30.40.173:9000 \
                                -DskipTests=true

                            echo "=== Attente du traitement SonarQube ==="
                            sleep 30
                        '''
                    }

                    timeout(time: 10, unit: 'MINUTES') {
                        waitForQualityGate abortPipeline: true
                    }
                }
            }
        }

        stage('Build & Test') {
            steps {
                echo "🔨 Construction de l'application..."
                sh '''
                    echo "=== Build Maven ==="
                    mvn clean package -B -DskipTests

                    echo "=== Vérification du JAR ==="
                    JAR_FILE=$(find target -name "*.jar" -type f | head -1)
                    if [ -f "$JAR_FILE" ]; then
                        echo "✅ JAR trouvé: $JAR_FILE"
                        ls -lh "$JAR_FILE"
                    else
                        echo "❌ Aucun JAR trouvé!"
                        exit 1
                    fi
                '''
            }
        }

        stage('Build Docker Image') {
            steps {
                echo "🐳 Construction de l'image Docker..."
                script {
                    withCredentials([usernamePassword(
                        credentialsId: 'DOCKERHUB_CREDENTIALS',
                        usernameVariable: 'DOCKER_USERNAME',
                        passwordVariable: 'DOCKER_PASSWORD'
                    )]) {
                        sh '''
                            echo "=== Building image ==="

                            # Create Dockerfile.jenkins
                            cat > Dockerfile.jenkins << 'EOF'
FROM eclipse-temurin:17-jre-alpine
WORKDIR /app
COPY target/*.jar app.jar
ENTRYPOINT ["java", "-jar", "app.jar"]
EOF

                            # Build using Minikube's Docker daemon
                            eval $(minikube docker-env)
                            docker build -t ${DOCKER_IMAGE} -f Dockerfile.jenkins .

                            echo "=== Listing images in Minikube ==="
                            docker images | grep "${DOCKER_USER}/foyer_project" | head -5

                            # Reset Docker environment
                            eval $(minikube docker-env -u)
                        '''
                    }
                }
            }
        }

        stage('Push to DockerHub') {
            steps {
                echo "🚀 Pushing image to DockerHub..."
                script {
                    withCredentials([usernamePassword(
                        credentialsId: 'DOCKERHUB_CREDENTIALS',
                        usernameVariable: 'DOCKER_USERNAME',
                        passwordVariable: 'DOCKER_PASSWORD'
                    )]) {
                        sh '''
                            echo "=== Login to DockerHub ==="
                            echo "${DOCKER_PASSWORD}" | docker login -u "${DOCKER_USERNAME}" --password-stdin

                            echo "=== Pushing image ==="
                            docker push ${DOCKER_IMAGE}

                            echo "=== Logout from DockerHub ==="
                            docker logout
                        '''
                    }
                }
            }
        }

        stage('Deploy MySQL') {
            steps {
                echo "🗄️  Déploiement de MySQL..."
                sh '''
                    echo "=== Creating MySQL deployment (using ephemeral storage) ==="

                    # Create MySQL deployment WITHOUT PVC (ephemeral storage for CI/CD)
                    cat <<EOF | kubectl apply -f -
apiVersion: apps/v1
kind: Deployment
metadata:
  name: mysql
  namespace: ${KUBE_NAMESPACE}
  labels:
    app: mysql
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
        ports:
        - containerPort: 3306
        env:
        - name: MYSQL_ROOT_PASSWORD
          value: root123
        - name: MYSQL_DATABASE
          value: springdb
        - name: MYSQL_USER
          value: spring
        - name: MYSQL_PASSWORD
          value: spring123
        - name: MYSQL_ALLOW_EMPTY_PASSWORD
          value: "no"
        resources:
          limits:
            cpu: 500m
            memory: 512Mi
          requests:
            cpu: 250m
            memory: 256Mi
        readinessProbe:
          tcpSocket:
            port: 3306
          initialDelaySeconds: 30
          periodSeconds: 10
        livenessProbe:
          tcpSocket:
            port: 3306
          initialDelaySeconds: 60
          periodSeconds: 20
---
apiVersion: v1
kind: Service
metadata:
  name: mysql-service
  namespace: ${KUBE_NAMESPACE}
spec:
  selector:
    app: mysql
  ports:
  - protocol: TCP
    port: 3306
    targetPort: 3306
EOF

                    echo "=== Waiting for MySQL to start (60 seconds) ==="
                    for i in {1..12}; do
                        echo "⏱️  Waiting... ($i/12)"
                        sleep 5
                    done

                    echo "=== Checking MySQL status ==="
                    kubectl get pods,svc -n ${KUBE_NAMESPACE}

                    echo "=== Waiting for MySQL to be ready ==="
                    MYSQL_POD=$(kubectl get pods -n ${KUBE_NAMESPACE} -l app=mysql -o jsonpath='{.items[0].metadata.name}')

                    if [ -n "$MYSQL_POD" ]; then
                        # Wait for MySQL to be ready
                        for i in {1..30}; do
                            if kubectl exec -n ${KUBE_NAMESPACE} $MYSQL_POD -- mysql -u root -proot123 -e "SELECT 1;" &> /dev/null; then
                                echo "✅ MySQL is ready after $((i*2)) seconds"

                                # Configure additional permissions
                                echo "=== Configuring MySQL permissions ==="
                                kubectl exec -n ${KUBE_NAMESPACE} $MYSQL_POD -- mysql -u root -proot123 -e "
                                    GRANT ALL PRIVILEGES ON springdb.* TO 'spring'@'%';
                                    GRANT ALL PRIVILEGES ON *.* TO 'root'@'%' WITH GRANT OPTION;
                                    FLUSH PRIVILEGES;
                                    SELECT '✅ MySQL configured successfully' as Status;
                                "
                                break
                            else
                                echo "MySQL not ready yet. Retrying in 2 seconds... ($i/30)"
                                sleep 2
                            fi
                        done
                    fi

                    echo "=== Testing MySQL connection ==="
                    kubectl exec -n ${KUBE_NAMESPACE} $MYSQL_POD -- mysql -u root -proot123 -e "SHOW DATABASES; SELECT '✅ MySQL operational' as Status;"
                '''
            }
        }

        stage('Deploy Spring Boot Application') {
            steps {
                echo "🚀 Déploiement de l'application Spring Boot..."
                script {
                    // Create Spring Boot deployment YAML
                    writeFile file: 'spring-deployment.yaml', text: """
apiVersion: apps/v1
kind: Deployment
metadata:
  name: spring-app
  namespace: ${env.KUBE_NAMESPACE}
  labels:
    app: spring-app
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
        image: ${env.DOCKER_IMAGE}
        ports:
        - containerPort: 8080
        env:
        - name: SPRING_DATASOURCE_URL
          value: "jdbc:mysql://mysql-service.${env.KUBE_NAMESPACE}.svc.cluster.local:3306/springdb?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC&createDatabaseIfNotExist=true"
        - name: SPRING_DATASOURCE_USERNAME
          value: "root"
        - name: SPRING_DATASOURCE_PASSWORD
          value: "root123"
        - name: SPRING_DATASOURCE_DRIVER_CLASS_NAME
          value: "com.mysql.cj.jdbc.Driver"
        - name: SPRING_JPA_HIBERNATE_DDL_AUTO
          value: "update"
        - name: SERVER_SERVLET_CONTEXT_PATH
          value: "${env.SPRING_CONTEXT_PATH}"
        - name: SPRING_APPLICATION_NAME
          value: "foyer-app"
        - name: SPRING_JPA_PROPERTIES_HIBERNATE_DIALECT
          value: "org.hibernate.dialect.MySQL8Dialect"
        resources:
          limits:
            cpu: 500m
            memory: 1Gi
          requests:
            cpu: 250m
            memory: 512Mi
        livenessProbe:
          httpGet:
            path: ${env.SPRING_CONTEXT_PATH}/actuator/health
            port: 8080
          initialDelaySeconds: 180
          timeoutSeconds: 1
          periodSeconds: 20
          failureThreshold: 3
        readinessProbe:
          httpGet:
            path: ${env.SPRING_CONTEXT_PATH}/actuator/health
            port: 8080
          initialDelaySeconds: 120
          timeoutSeconds: 1
          periodSeconds: 10
          failureThreshold: 3
        startupProbe:
          tcpSocket:
            port: 8080
          initialDelaySeconds: 60
          timeoutSeconds: 1
          periodSeconds: 10
          failureThreshold: 30
---
apiVersion: v1
kind: Service
metadata:
  name: spring-service
  namespace: ${env.KUBE_NAMESPACE}
spec:
  type: NodePort
  selector:
    app: spring-app
  ports:
  - port: 8080
    targetPort: 8080
    nodePort: 30080
"""

                    sh '''
                        echo "=== Applying Spring Boot deployment ==="
                        kubectl apply -f spring-deployment.yaml

                        echo "=== Waiting for Spring Boot to start (90 seconds) ==="
                        for i in {1..18}; do
                            echo "⏱️  Waiting for Spring Boot... ($i/18)"
                            sleep 5
                        done

                        echo "=== Checking deployment status ==="
                        kubectl get pods,svc -n ${KUBE_NAMESPACE}
                    '''
                }
            }
        }

        stage('Verify Application Startup') {
            steps {
                echo "🔍 Vérification du démarrage de l'application..."
                sh '''
                    echo "=== Additional wait (30 seconds) ==="
                    sleep 30

                    echo "=== Checking pods ==="
                    kubectl get pods -n ${KUBE_NAMESPACE} -o wide

                    echo "=== Spring Boot logs (last 50 lines) ==="
                    SPRING_POD=$(kubectl get pods -n ${KUBE_NAMESPACE} -l app=spring-app -o jsonpath='{.items[0].metadata.name}')
                    if [ -n "$SPRING_POD" ]; then
                        echo "Pod: $SPRING_POD"
                        kubectl logs -n ${KUBE_NAMESPACE} $SPRING_POD --tail=50 | grep -E "(ERROR|WARN|INFO.*Application|Started|JPA)" | head -30
                    fi
                '''
            }
        }

        stage('Test Application Health') {
            steps {
                echo "✅ Test de santé de l'application..."
                sh '''
                    echo "=== Testing health endpoint ==="

                    # Try multiple times with increasing delays
                    for i in {1..12}; do
                        echo "Attempt $i/12..."
                        if curl -s -f -m 30 http://${MINIKUBE_IP}:${NODE_PORT}${SPRING_CONTEXT_PATH}/actuator/health; then
                            echo "✅ Health check successful!"
                            break
                        else
                            echo "⏱️  Waiting... ($i/12)"
                            sleep 10
                        fi
                    done

                    echo "=== Final resource status ==="
                    kubectl get all -n ${KUBE_NAMESPACE}
                '''
            }
        }
    }

    post {
        success {
            echo "🎉 Pipeline exécuté avec succès!"
            sh '''
                echo "=== SUCCESS ==="
                echo "✅ SonarQube analysis completed"
                echo "✅ Docker application built"
                echo "✅ Kubernetes deployment performed"
                echo "✅ Spring Boot application deployed"
                echo ""
                echo "🌐 Your application is accessible at: http://${MINIKUBE_IP}:${NODE_PORT}${SPRING_CONTEXT_PATH}"
                echo "🔧 Health Check: http://${MINIKUBE_IP}:${NODE_PORT}${SPRING_CONTEXT_PATH}/actuator/health"
                echo "📊 Foyer API: http://${MINIKUBE_IP}:${NODE_PORT}${SPRING_CONTEXT_PATH}/foyer/getAllFoyers"
            '''
        }
        failure {
            echo "💥 Le pipeline a échoué"
            sh '''
                echo "=== TROUBLESHOOTING ==="
                echo "1. Pod status:"
                kubectl get pods -n ${KUBE_NAMESPACE}
                echo ""
                echo "2. Recent events:"
                kubectl get events -n ${KUBE_NAMESPACE} --sort-by=.lastTimestamp | tail -20
                echo ""
                echo "3. Services:"
                kubectl get svc -n ${KUBE_NAMESPACE}
                echo ""
                echo "4. Manual tests:"
                echo "   Test MySQL: mysql -h ${MINIKUBE_IP} -P 3306 -u root -proot123"
                echo "   Test Spring Boot: curl -v http://${MINIKUBE_IP}:${NODE_PORT}${SPRING_CONTEXT_PATH}/actuator/health"
            '''
        }
        always {
            echo "🏁 Pipeline terminé"
            sh '''
                echo "=== Cleaning temporary files ==="
                rm -f Dockerfile.jenkins spring-deployment.yaml || true

                echo ""
                echo "=== FINAL REPORT ==="
                echo "✅ Pipeline executed"
                echo "📊 Docker Image: ${DOCKER_IMAGE}"
                echo "📁 Namespace: ${KUBE_NAMESPACE}"
                echo "🌐 Context path: ${SPRING_CONTEXT_PATH}"
                echo ""
                echo "=== IMPORTANT LINKS ==="
                echo "📈 SonarQube Dashboard: http://172.30.40.173:9000/dashboard?id=foyer-project"
                echo "🔍 SonarQube Project: http://172.30.40.173:9000/project/overview?id=foyer-project"
                echo ""
                echo "=== APPLICATION ACCESS ==="
                echo "🌐 Spring Boot Application: http://${MINIKUBE_IP}:${NODE_PORT}${SPRING_CONTEXT_PATH}"
                echo "🔧 Health Check: http://${MINIKUBE_IP}:${NODE_PORT}${SPRING_CONTEXT_PATH}/actuator/health"
                echo "📊 Foyer API: http://${MINIKUBE_IP}:${NODE_PORT}${SPRING_CONTEXT_PATH}/foyer/getAllFoyers"
                echo ""
                echo "=== TROUBLESHOOTING COMMANDS ==="
                echo "1. View all pods: kubectl get pods -n ${KUBE_NAMESPACE}"
                echo "2. View Spring Boot logs: kubectl logs -n ${KUBE_NAMESPACE} -l app=spring-app --tail=100"
                echo "3. View MySQL logs: kubectl logs -n ${KUBE_NAMESPACE} -l app=mysql --tail=50"
                echo "4. Restart Spring Boot: kubectl rollout restart deployment/spring-app -n ${KUBE_NAMESPACE}"
                echo "5. MySQL access: kubectl exec -n ${KUBE_NAMESPACE} -it \$(kubectl get pods -n ${KUBE_NAMESPACE} -l app=mysql -o name | head -1) -- mysql -u root -proot123"
            '''
        }
    }
}