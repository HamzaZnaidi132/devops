pipeline {
    agent any

    environment {
        IMAGE_NAME = "saiffrikhi/foyer_project"
        IMAGE_TAG = "${BUILD_NUMBER}"
        K8S_NAMESPACE = "devops"
        CONTEXT_PATH = "/tp-foyer"
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

        stage('Cleanup All Resources') {
            steps {
                echo "🧹 Nettoyage complet de toutes les ressources..."
                sh """
                    echo "=== Suppression de toutes les ressources ==="
                    kubectl delete deployment --all -n ${K8S_NAMESPACE} --ignore-not-found=true --timeout=60s || true
                    kubectl delete service --all -n ${K8S_NAMESPACE} --ignore-not-found=true --timeout=60s || true
                    kubectl delete pvc --all -n ${K8S_NAMESPACE} --ignore-not-found=true --timeout=60s || true
                    kubectl delete pv --all --ignore-not-found=true --timeout=60s || true
                    kubectl delete configmap --all -n ${K8S_NAMESPACE} --ignore-not-found=true --timeout=60s || true
                    kubectl delete secret --all -n ${K8S_NAMESPACE} --ignore-not-found=true --timeout=60s || true

                    echo "=== Attente pour la suppression complète ==="
                    sleep 30

                    echo "=== Nettoyage du stockage local ==="
                    sudo rm -rf /data/mysql/*
                    sudo mkdir -p /data/mysql
                    sudo chmod 777 /data/mysql
                """
            }
        }

        stage('Deploy MySQL with Init Container') {
            steps {
                echo "🗄️  Déploiement de MySQL avec configuration correcte..."
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
    path: "/data/mysql"
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

                    echo "=== Création du déploiement MySQL avec Init Container ==="
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
      initContainers:
      - name: init-mysql
        image: busybox:1.34
        command: ['sh', '-c', 'echo "Waiting for MySQL to be ready..." && sleep 30']
      containers:
      - name: mysql
        image: mysql:8.0
        args:
          - "--bind-address=0.0.0.0"
          - "--skip-name-resolve"
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
          mountPath: /etc/mysql/conf.d/
        resources:
          requests:
            memory: "256Mi"
            cpu: "100m"
          limits:
            memory: "512Mi"
            cpu: "250m"
        readinessProbe:
          exec:
            command:
            - mysqladmin
            - ping
            - -h
            - localhost
            - -uroot
            - -proot123
          initialDelaySeconds: 30
          periodSeconds: 10
          timeoutSeconds: 5
        livenessProbe:
          tcpSocket:
            port: 3306
          initialDelaySeconds: 60
          periodSeconds: 20
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

                    echo "=== Attente du démarrage de MySQL (2 minutes) ==="
                    sleep 120

                    echo "=== Vérification de l'état ==="
                    kubectl get pods,svc -n ${K8S_NAMESPACE}
                """
            }
        }

        stage('Configure MySQL Permissions') {
            steps {
                echo "🔧 Configuration des permissions MySQL..."
                sh """
                    echo "=== Attente que MySQL soit complètement démarré ==="
                    for i in {1..30}; do
                        POD_NAME=\$(kubectl get pods -n ${K8S_NAMESPACE} -l app=mysql -o jsonpath='{.items[0].metadata.name}' 2>/dev/null || echo "")
                        if [ -n "\$POD_NAME" ]; then
                            if kubectl exec -n ${K8S_NAMESPACE} \$POD_NAME -- mysqladmin ping -h localhost -u root -proot123 2>/dev/null; then
                                echo "✅ MySQL est accessible. Configuration des permissions..."

                                # Create a script to fix MySQL permissions
                                cat > /tmp/fix-mysql-permissions.sql << 'EOF'
-- Drop existing root users to avoid conflicts
DROP USER IF EXISTS 'root'@'%';
DROP USER IF EXISTS 'root'@'localhost';

-- Create root user for all hosts
CREATE USER 'root'@'%' IDENTIFIED BY 'root123';
CREATE USER 'root'@'localhost' IDENTIFIED BY 'root123';

-- Grant all privileges
GRANT ALL PRIVILEGES ON *.* TO 'root'@'%' WITH GRANT OPTION;
GRANT ALL PRIVILEGES ON *.* TO 'root'@'localhost' WITH GRANT OPTION;

-- Create database
CREATE DATABASE IF NOT EXISTS springdb;

-- Flush privileges
FLUSH PRIVILEGES;

-- Verify
SELECT User, Host FROM mysql.user;
EOF

                                # Copy script to pod and execute
                                kubectl cp /tmp/fix-mysql-permissions.sql ${K8S_NAMESPACE}/\$POD_NAME:/tmp/fix-mysql-permissions.sql
                                kubectl exec -n ${K8S_NAMESPACE} \$POD_NAME -- mysql -u root -proot123 < /tmp/fix-mysql-permissions.sql

                                echo "✅ Permissions MySQL configurées"
                                break
                            fi
                        fi
                        echo "⏱️  Attente... (\$i/30)"
                        sleep 10
                    done

                    echo "=== Test de connexion MySQL ==="
                    POD_NAME=\$(kubectl get pods -n ${K8S_NAMESPACE} -l app=mysql -o jsonpath='{.items[0].metadata.name}' 2>/dev/null || echo "")
                    if [ -n "\$POD_NAME" ]; then
                        kubectl exec -n ${K8S_NAMESPACE} \$POD_NAME -- mysql -u root -proot123 -e "SELECT 1;" && echo "✅ Test de connexion réussi"
                    fi
                """
            }
        }

        stage('Build Docker Image in Minikube') {
            steps {
                echo "🐳 Construction de l'image Docker dans Minikube..."
                sh """
                    # Switch to Minikube's Docker daemon
                    eval \$(minikube docker-env)

                    # Créez un Dockerfile
                    cat > Dockerfile.jenkins << 'EOF'
FROM eclipse-temurin:17-jre-alpine
WORKDIR /app
COPY target/*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
EOF

                    echo "=== Construction de l'image ==="
                    docker build -t ${IMAGE_NAME}:${IMAGE_TAG} -f Dockerfile.jenkins .

                    echo "=== Tag latest ==="
                    docker tag ${IMAGE_NAME}:${IMAGE_TAG} ${IMAGE_NAME}:latest

                    echo "=== Liste des images dans Minikube ==="
                    docker images | grep ${IMAGE_NAME} | head -5

                    # Switch back to normal Docker daemon
                    eval \$(minikube docker-env -u)
                """
            }
        }

        stage('Deploy Spring Boot Application') {
            steps {
                echo "🚀 Déploiement de l'application Spring Boot..."
                script {
                    // Create YAML content with local image
                    String yamlContent = """apiVersion: v1
kind: Service
metadata:
  name: spring-service
  namespace: ${K8S_NAMESPACE}
spec:
  selector:
    app: spring-app
  ports:
    - port: 8050
      targetPort: 8050
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
  strategy:
    type: Recreate
  template:
    metadata:
      labels:
        app: spring-app
    spec:
      containers:
      - name: spring-app
        image: ${IMAGE_NAME}:${IMAGE_TAG}
        imagePullPolicy: Never  # Use local image, don't pull from registry
        ports:
        - containerPort: 8050
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
          value: "false"
        - name: LOGGING_LEVEL_ROOT
          value: "INFO"
        - name: SERVER_SERVLET_CONTEXT_PATH
          value: "${CONTEXT_PATH}"
        - name: SPRING_APPLICATION_NAME
          value: "foyer-app"
        - name: MANAGEMENT_ENDPOINTS_WEB_EXPOSURE_INCLUDE
          value: "health,info"
        resources:
          requests:
            memory: "512Mi"
            cpu: "250m"
          limits:
            memory: "1Gi"
            cpu: "500m"
        # Remove probes initially to allow app to start
"""

                    // Write the YAML file
                    writeFile file: 'spring-deployment.yaml', text: yamlContent
                }

                sh """
                    echo "=== Application du déploiement ==="
                    kubectl apply -f spring-deployment.yaml

                    echo "=== Attente du démarrage (3 minutes) ==="
                    sleep 180

                    echo "=== Vérification de l'état ==="
                    kubectl get pods,svc -n ${K8S_NAMESPACE}

                    echo "=== Logs du pod Spring Boot ==="
                    POD_NAME=\$(kubectl get pods -n ${K8S_NAMESPACE} -l app=spring-app -o jsonpath='{.items[0].metadata.name}' 2>/dev/null || echo "")
                    if [ -n "\$POD_NAME" ]; then
                        echo "Pod: \$POD_NAME"
                        kubectl logs \$POD_NAME -n ${K8S_NAMESPACE} --tail=100 || echo "Pas encore de logs"
                    fi
                """
            }
        }

        stage('Verify Deployment') {
            steps {
                echo "✅ Vérification finale du déploiement..."
                sh """
                    echo "=== Attente supplémentaire ==="
                    sleep 60

                    echo "=== État des pods ==="
                    kubectl get pods -n ${K8S_NAMESPACE} -o wide

                    echo ""
                    echo "=== Vérification des services ==="
                    kubectl get svc -n ${K8S_NAMESPACE}

                    echo ""
                    echo "=== Test de l'application ==="
                    MINIKUBE_IP=\$(minikube ip 2>/dev/null || echo "192.168.49.2")
                    echo "Minikube IP: \$MINIKUBE_IP"

                    echo ""
                    echo "=== Tentative d'accès à l'application ==="
                    # Try multiple times
                    for i in {1..10}; do
                        echo "Tentative \$i/10..."
                        if curl -s -f -m 10 "http://\${MINIKUBE_IP}:30080${CONTEXT_PATH}/actuator/health"; then
                            echo "✅ SUCCÈS! Application accessible"
                            echo ""
                            echo "=== Test des endpoints ==="
                            echo "1. Health check:"
                            curl -s "http://\${MINIKUBE_IP}:30080${CONTEXT_PATH}/actuator/health"
                            echo ""
                            echo "2. Test de l'API Foyer:"
                            curl -s "http://\${MINIKUBE_IP}:30080${CONTEXT_PATH}/foyer/getAllFoyers"
                            echo ""
                            break
                        else
                            echo "⏱️  En attente... (\$i/10)"
                            sleep 15
                        fi
                    done
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
                rm -f Dockerfile.jenkins spring-deployment.yaml /tmp/mysql-deployment.yaml /tmp/mysql-storage.yaml /tmp/fix-mysql-permissions.sql 2>/dev/null || true
            '''

            // Rapport final
            script {
                sh """
                    echo "=== RAPPORT FINAL ==="
                    echo "Build: ${BUILD_NUMBER}"
                    echo "Image: ${IMAGE_NAME}:${IMAGE_TAG}"
                    echo "Namespace: ${K8S_NAMESPACE}"
                    echo "Contexte: ${CONTEXT_PATH}"

                    echo ""
                    echo "=== État Kubernetes ==="
                    kubectl get all -n ${K8S_NAMESPACE} || true

                    MINIKUBE_IP=\$(minikube ip 2>/dev/null || echo "N/A")
                    echo ""
                    echo "=== URL d'accès ==="
                    echo "Spring Boot: http://\${MINIKUBE_IP}:30080${CONTEXT_PATH}"
                    echo "API Foyer: http://\${MINIKUBE_IP}:30080${CONTEXT_PATH}/foyer/getAllFoyers"
                """
            }
        }

        success {
            echo "🎉 Pipeline réussi!"
        }

        failure {
            echo "💥 Le pipeline a échoué"
            script {
                echo "Le pipeline a échoué au build ${BUILD_NUMBER}"

                // Debug information on failure
                sh """
                    echo "=== DEBUG INFO ==="
                    echo "1. État des pods:"
                    kubectl get pods -n ${K8S_NAMESPACE} -o wide || true
                    echo ""
                    echo "2. Logs MySQL:"
                    kubectl logs -n ${K8S_NAMESPACE} -l app=mysql --tail=50 || true
                    echo ""
                    echo "3. Logs Spring Boot:"
                    kubectl logs -n ${K8S_NAMESPACE} -l app=spring-app --tail=100 || true
                    echo ""
                    echo "4. Événements:"
                    kubectl get events -n ${K8S_NAMESPACE} --sort-by='.lastTimestamp' | tail -20 || true
                """
            }
        }
    }
}