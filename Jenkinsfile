pipeline {
    agent any

    environment {
        // Docker Hub configuration
        DOCKERHUB_REGISTRY = 'index.docker.io'
        DOCKERHUB_NAMESPACE = 'saiffrikhi'  // Your Docker Hub username
        IMAGE_NAME = "foyer_project"
        IMAGE_FULL_NAME = "${DOCKERHUB_NAMESPACE}/${IMAGE_NAME}"
        IMAGE_TAG = "${BUILD_NUMBER}"
        IMAGE_LATEST = "${DOCKERHUB_NAMESPACE}/${IMAGE_NAME}:latest"

        // Kubernetes configuration
        K8S_NAMESPACE = "devops"
        CONTEXT_PATH = "/tp-foyer"

        // Docker Hub credentials (stored in Jenkins credentials)
        DOCKERHUB_CREDENTIALS = credentials('docker-hub')
    }

    triggers {
        githubPush() // This enables webhook triggers
    }

    stages {
        stage('Checkout') {
            steps {
                echo "📦 Récupération du code depuis GitHub..."
                git branch: 'main', url: 'https://github.com/saifeddinefrikhi-lab/FoyerProject.git'
            }
        }

        stage('Build & Test') {
            steps {
                echo "🔨 Construction de l'application..."
                sh '''
                    echo "=== Build Maven ==="
                    mvn clean package -DskipTests -B

                    echo "=== Vérification du JAR ==="
                    JAR_FILE=$(find target -name "*.jar" -type f | head -1)
                    if [ -f "$JAR_FILE" ]; then
                        echo "✅ JAR trouvé: $JAR_FILE"
                        ls -lh "$JAR_FILE"
                    else
                        echo "❌ Aucun fichier JAR trouvé!"
                        exit 1
                    fi
                '''
            }
        }

        stage('Login to Docker Hub') {
            steps {
                echo "🔐 Connexion à Docker Hub..."
                withCredentials([usernamePassword(
                    credentialsId: 'docker-hub',
                    usernameVariable: 'DOCKERHUB_USERNAME',
                    passwordVariable: 'DOCKERHUB_PASSWORD'
                )]) {
                    sh """
                        echo "=== Connexion à Docker Hub ==="
                        echo "\${DOCKERHUB_PASSWORD}" | docker login ${DOCKERHUB_REGISTRY} -u "\${DOCKERHUB_USERNAME}" --password-stdin
                        echo "✅ Connecté à Docker Hub en tant que \${DOCKERHUB_USERNAME}"
                    """
                }
            }
        }

        stage('Build Docker Image for Docker Hub') {
            steps {
                echo "🐳 Construction de l'image Docker pour Docker Hub..."
                script {
                    // Check if Dockerfile exists
                    def dockerfileExists = fileExists('Dockerfile')
                    if (!dockerfileExists) {
                        echo "⚠️ Dockerfile non trouvé, création d'un Dockerfile par défaut..."
                        sh '''
                            cat > Dockerfile << \'EOF\'
FROM eclipse-temurin:17-jre-alpine
WORKDIR /app
COPY target/*.jar app.jar
EXPOSE 8050
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
EOF
                        '''
                    }
                }

                sh """
                    echo "=== Construction de l'image avec Dockerfile existant ==="
                    echo "Image: ${IMAGE_FULL_NAME}:${IMAGE_TAG}"

                    docker build -t ${IMAGE_FULL_NAME}:${IMAGE_TAG} .
                    docker tag ${IMAGE_FULL_NAME}:${IMAGE_TAG} ${IMAGE_FULL_NAME}:latest

                    echo "=== Liste des images construites ==="
                    docker images | grep ${IMAGE_FULL_NAME}
                """
            }
        }

        stage('Push Docker Image to Docker Hub') {
            steps {
                echo "🚀 Pushing Docker image to Docker Hub..."
                sh """
                    echo "=== Push de l'image versionnée ==="
                    docker push ${IMAGE_FULL_NAME}:${IMAGE_TAG}

                    echo "=== Push de l'image latest ==="
                    docker push ${IMAGE_FULL_NAME}:latest

                    echo "✅ Images poussées vers Docker Hub"
                    echo "Image disponible sur: https://hub.docker.com/r/${DOCKERHUB_NAMESPACE}/${IMAGE_NAME}"
                """
            }
        }

        stage('Logout from Docker Hub') {
            steps {
                echo "👋 Déconnexion de Docker Hub..."
                sh """
                    docker logout ${DOCKERHUB_REGISTRY}
                    echo "✅ Déconnecté de Docker Hub"
                """
            }
        }

        stage('Clean Old Kubernetes Resources') {
            steps {
                echo "🧹 Nettoyage des anciennes ressources..."
                sh """
                    # Supprimez toutes les ressources existantes
                    kubectl delete deployment spring-app -n ${K8S_NAMESPACE} --ignore-not-found=true
                    kubectl delete service spring-service -n ${K8S_NAMESPACE} --ignore-not-found=true
                    kubectl delete deployment mysql -n ${K8S_NAMESPACE} --ignore-not-found=true
                    kubectl delete service mysql-service -n ${K8S_NAMESPACE} --ignore-not-found=true
                    kubectl delete pvc mysql-pvc -n ${K8S_NAMESPACE} --ignore-not-found=true
                    kubectl delete pv mysql-pv --ignore-not-found=true
                    kubectl delete configmap --all -n ${K8S_NAMESPACE} --ignore-not-found=true
                    kubectl delete secret --all -n ${K8S_NAMESPACE} --ignore-not-found=true

                    sleep 10

                    echo "=== État après nettoyage ==="
                    kubectl get pods -n ${K8S_NAMESPACE} || true
                """
            }
        }

        stage('Deploy MySQL') {
            steps {
                echo "🗄️  Déploiement de MySQL..."
                sh """
                    echo "=== Création du PV et PVC MySQL ==="
                    cat > /tmp/mysql-storage.yaml << 'EOF'
apiVersion: v1
kind: PersistentVolume
metadata:
  name: mysql-pv
spec:
  capacity:
    storage: 2Gi
  accessModes:
    - ReadWriteOnce
  persistentVolumeReclaimPolicy: Retain
  hostPath:
    path: "/tmp/mysql-data"
    type: DirectoryOrCreate
---
apiVersion: v1
kind: PersistentVolumeClaim
metadata:
  name: mysql-pvc
  namespace: devops
spec:
  accessModes:
    - ReadWriteOnce
  resources:
    requests:
      storage: 2Gi
EOF
                    kubectl apply -f /tmp/mysql-storage.yaml

                    echo "=== Création du déploiement MySQL ==="
                    cat > /tmp/mysql-deployment.yaml << 'EOF'
apiVersion: v1
kind: ConfigMap
metadata:
  name: mysql-config
  namespace: devops
data:
  my.cnf: |
    [mysqld]
    bind-address = 0.0.0.0
    default_authentication_plugin = mysql_native_password
    skip-name-resolve
---
apiVersion: apps/v1
kind: Deployment
metadata:
  name: mysql
  namespace: devops
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
        command:
          - mysqld
          - --bind-address=0.0.0.0
          - --default-authentication-plugin=mysql_native_password
          - --skip-name-resolve
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
        - name: mysql-config
          mountPath: /etc/mysql/conf.d/my.cnf
          subPath: my.cnf
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
      - name: mysql-config
        configMap:
          name: mysql-config
---
apiVersion: v1
kind: Service
metadata:
  name: mysql-service
  namespace: devops
spec:
  selector:
    app: mysql
  ports:
    - port: 3306
      targetPort: 3306
  type: ClusterIP
EOF
                    kubectl apply -f /tmp/mysql-deployment.yaml

                    echo "=== Attente du démarrage de MySQL ==="
                    sleep 60

                    echo "=== Configuration des permissions MySQL ==="
                    for i in \$(seq 1 30); do
                        POD_NAME=\$(kubectl get pods -n devops -l app=mysql -o jsonpath='{.items[0].metadata.name}' 2>/dev/null || echo "")
                        if [ -n "\$POD_NAME" ]; then
                            if kubectl get pod -n devops \$POD_NAME -o jsonpath='{.status.phase}' 2>/dev/null | grep -q Running; then
                                sleep 10

                                if kubectl exec -n devops \$POD_NAME -- mysqladmin ping -h localhost -u root -proot123 2>/dev/null; then
                                    echo "✅ MySQL est prêt. Configuration des permissions..."

                                    cat > /tmp/setup-mysql.sql << 'EOF2'
CREATE USER IF NOT EXISTS 'root'@'%' IDENTIFIED BY 'root123';
CREATE USER IF NOT EXISTS 'root'@'localhost' IDENTIFIED BY 'root123';
GRANT ALL PRIVILEGES ON *.* TO 'root'@'%' WITH GRANT OPTION;
GRANT ALL PRIVILEGES ON *.* TO 'root'@'localhost' WITH GRANT OPTION;
CREATE DATABASE IF NOT EXISTS springdb CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
FLUSH PRIVILEGES;
SELECT User, Host FROM mysql.user;
EOF2

                                    kubectl cp /tmp/setup-mysql.sql devops/\$POD_NAME:/tmp/setup-mysql.sql
                                    kubectl exec -n devops \$POD_NAME -- mysql -u root -proot123 < /tmp/setup-mysql.sql

                                    echo "✅ Permissions MySQL configurées"
                                    break
                                fi
                            fi
                        fi
                        echo "⏱️  Attente MySQL... (\$i/30)"
                        sleep 10
                    done

                    echo "=== Vérification finale MySQL ==="
                    kubectl get pods,svc -n ${K8S_NAMESPACE}
                """
            }
        }

        stage('Deploy Spring Boot from Docker Hub') {
            steps {
                echo "🚀 Déploiement de l'application Spring Boot depuis Docker Hub..."
                script {
                    String yamlContent = """apiVersion: v1
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
---
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
        image: ${IMAGE_FULL_NAME}:${IMAGE_TAG}  # Image depuis Docker Hub
        # No need for imagePullPolicy: Never anymore
        ports:
        - containerPort: 8080
        env:
        - name: SPRING_DATASOURCE_URL
          value: "jdbc:mysql://mysql-service:3306/springdb?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC&createDatabaseIfNotExist=true"
        - name: SPRING_DATASOURCE_USERNAME
          value: "root"
        - name: SPRING_DATASOURCE_PASSWORD
          value: "root123"
        - name: SPRING_DATASOURCE_DRIVER_CLASS_NAME
          value: "com.mysql.cj.jdbc.Driver"
        - name: SPRING_JPA_HIBERNATE_DDL_AUTO
          value: "update"
        - name: SPRING_JPA_SHOW_SQL
          value: "true"
        - name: LOGGING_LEVEL_ROOT
          value: "INFO"
        - name: SERVER_SERVLET_CONTEXT_PATH
          value: "${CONTEXT_PATH}"
        - name: SPRING_APPLICATION_NAME
          value: "foyer-app"
        - name: SERVER_PORT
          value: "8080"
        resources:
          requests:
            memory: "512Mi"
            cpu: "250m"
          limits:
            memory: "1Gi"
            cpu: "500m"
"""

                    writeFile file: 'spring-deployment.yaml', text: yamlContent
                }

                sh """
                    echo "=== Application du déploiement ==="
                    kubectl apply -f spring-deployment.yaml

                    echo "=== Attente du démarrage (120 secondes) ==="
                    sleep 120

                    echo "=== Vérification de l'état ==="
                    kubectl get pods,svc -n ${K8S_NAMESPACE}
                """
            }
        }

        stage('Verify Deployment') {
            steps {
                echo "✅ Vérification du déploiement..."
                sh """
                    echo "=== Attente supplémentaire ==="
                    sleep 30

                    echo "=== Vérification des pods ==="
                    kubectl get pods -n ${K8S_NAMESPACE} -o wide

                    echo ""
                    echo "=== Logs Spring Boot ==="
                    POD_NAME=\$(kubectl get pods -n ${K8S_NAMESPACE} -l app=spring-app -o jsonpath='{.items[0].metadata.name}' 2>/dev/null || echo "")
                    if [ -n "\$POD_NAME" ]; then
                        echo "Pod: \$POD_NAME"
                        kubectl logs -n ${K8S_NAMESPACE} \$POD_NAME --tail=50
                    fi

                    echo ""
                    echo "=== Test de l'application ==="
                    MINIKUBE_IP=\$(minikube ip)

                    echo "Tentative de connexion..."
                    for i in \$(seq 1 10); do
                        echo "Tentative \$i/10..."
                        if curl -s -f -m 10 "http://\${MINIKUBE_IP}:30080${CONTEXT_PATH}/actuator/health"; then
                            echo "✅ Application accessible!"
                            echo "=== Test de l'API Foyer ==="
                            curl -s "http://\${MINIKUBE_IP}:30080${CONTEXT_PATH}/foyer/getAllFoyers"
                            echo ""
                            break
                        elif curl -s -f -m 10 "http://\${MINIKUBE_IP}:30080/actuator/health"; then
                            echo "✅ Application accessible (sans contexte path)"
                            break
                        else
                            echo "⏱️  En attente... (\$i/10)"
                            sleep 15
                        fi
                    done

                    echo ""
                    echo "=== État final ==="
                    kubectl get all -n ${K8S_NAMESPACE}
                """
            }
        }
    }

    post {
        always {
            echo "🏁 Pipeline terminé"

            // Nettoyage
            sh '''
                echo "=== Nettoyage des fichiers temporaires ==="
                rm -f Dockerfile.jenkins spring-deployment.yaml /tmp/mysql-deployment.yaml /tmp/mysql-storage.yaml /tmp/setup-mysql.sql 2>/dev/null || true
            '''

            // Rapport final
            script {
                sh """
                    echo "=== RAPPORT FINAL ==="
                    echo "Build: ${BUILD_NUMBER}"
                    echo "Docker Hub Image: ${IMAGE_FULL_NAME}:${IMAGE_TAG}"
                    echo "Latest Image: ${IMAGE_FULL_NAME}:latest"
                    echo "Docker Hub URL: https://hub.docker.com/r/${DOCKERHUB_NAMESPACE}/${IMAGE_NAME}"
                    echo "Namespace: ${K8S_NAMESPACE}"
                    echo "Contexte path: ${CONTEXT_PATH}"

                    MINIKUBE_IP=\$(minikube ip)
                    echo ""
                    echo "=== URL d'accès ==="
                    echo "Application: http://\${MINIKUBE_IP}:30080${CONTEXT_PATH}"
                    echo "API Foyer: http://\${MINIKUBE_IP}:30080${CONTEXT_PATH}/foyer/getAllFoyers"
                    echo ""
                    echo "=== Pour tester manuellement ==="
                    echo "1. Tester l'API: curl http://\${MINIKUBE_IP}:30080${CONTEXT_PATH}/foyer/getAllFoyers"
                    echo "2. Vérifier l'image: docker pull ${IMAGE_FULL_NAME}:${IMAGE_TAG}"
                """
            }
        }

        failure {
            echo "💥 Le pipeline a échoué"
            script {
                sh """
                    echo "=== DEBUG INFO ==="
                    echo "1. État des pods:"
                    kubectl get pods -n ${K8S_NAMESPACE} -o wide || true
                    echo ""
                    echo "2. Logs MySQL:"
                    kubectl logs -n ${K8S_NAMESPACE} -l app=mysql --tail=100 || true
                    echo ""
                    echo "3. Logs Spring Boot:"
                    kubectl logs -n ${K8S_NAMESPACE} -l app=spring-app --tail=200 || true
                    echo ""
                    echo "4. Événements:"
                    kubectl get events -n ${K8S_NAMESPACE} --sort-by='.lastTimestamp' | tail -30 || true
                """
            }
        }
    }
}