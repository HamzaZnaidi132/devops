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

        stage('Cleanup & Setup MySQL Storage') {
            steps {
                echo "🧹 Nettoyage et préparation du stockage MySQL..."
                sh """
                    echo "=== Suppression des anciennes ressources MySQL ==="
                    kubectl delete deployment mysql -n ${K8S_NAMESPACE} --ignore-not-found=true --timeout=60s || true
                    kubectl delete service mysql-service -n ${K8S_NAMESPACE} --ignore-not-found=true --timeout=60s || true
                    kubectl delete pvc mysql-pvc -n ${K8S_NAMESPACE} --ignore-not-found=true --timeout=60s || true
                    kubectl delete pv mysql-pv --ignore-not-found=true --timeout=60s || true

                    echo "=== Attente pour la suppression complète ==="
                    sleep 15

                    echo "=== Création du répertoire de stockage sur minikube ==="
                    minikube ssh "sudo mkdir -p /data/mysql && sudo chmod 777 /data/mysql" || echo "Répertoire déjà créé"

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
  storageClassName: ""
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
  volumeName: mysql-pv
  storageClassName: ""
EOF
                    kubectl apply -f /tmp/mysql-storage.yaml

                    echo "=== Attente que le PVC soit lié ==="
                    sleep 10

                    echo "=== Vérification du PV et PVC ==="
                    kubectl get pv
                    kubectl get pvc -n ${K8S_NAMESPACE}
                """
            }
        }

        stage('Deploy MySQL') {
            steps {
                echo "🗄️  Déploiement de MySQL..."
                script {
                    sh """
                        echo "=== Création du déploiement MySQL ==="
                        cat > /tmp/mysql-deployment.yaml << 'EOF'
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
        readinessProbe:
          tcpSocket:
            port: 3306
          initialDelaySeconds: 20
          periodSeconds: 5
          timeoutSeconds: 3
        livenessProbe:
          tcpSocket:
            port: 3306
          initialDelaySeconds: 30
          periodSeconds: 10
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
    - port: 3306
      targetPort: 3306
  type: ClusterIP
EOF
                        kubectl apply -f /tmp/mysql-deployment.yaml

                        echo "=== Attente du démarrage de MySQL ==="
                        def attempts = 30
                        def mysqlReady = false

                        for (int attempt = 1; attempt <= attempts; attempt++) {
                            echo "Tentative \${attempt}/\${attempts}..."
                            sleep 10

                            def podStatus = sh(script: "kubectl get pods -n ${K8S_NAMESPACE} -l app=mysql -o jsonpath='{.items[0].status.phase}' 2>/dev/null || echo 'Not Found'", returnStdout: true).trim()

                            if (podStatus == "Running") {
                                echo "✅ MySQL est en cours d'exécution."
                                mysqlReady = true
                                sleep 10  // Donner plus de temps pour l'initialisation
                                break
                            }
                        }

                        if (!mysqlReady) {
                            echo "❌ MySQL n'a pas démarré dans le temps imparti"
                            sh """
                                echo "=== Logs MySQL ==="
                                kubectl logs -n ${K8S_NAMESPACE} -l app=mysql --tail=50
                                echo "=== Détails du pod MySQL ==="
                                kubectl describe pod -n ${K8S_NAMESPACE} -l app=mysql
                            """
                            error("MySQL n'a pas démarré")
                        }

                        echo "=== Vérification finale ==="
                        sh "kubectl get pods,svc -n ${K8S_NAMESPACE}"
                    """
                }
            }
        }

        stage('Test MySQL Connection') {
            steps {
                echo "🔍 Test de connexion à MySQL..."
                script {
                    sh """
                        echo "=== Test de connexion à MySQL ==="
                        def timeout = 120
                        def interval = 5
                        def elapsed = 0
                        def mysqlConnected = false

                        while (elapsed < timeout) {
                            def podName = sh(script: "kubectl get pods -n ${K8S_NAMESPACE} -l app=mysql -o jsonpath='{.items[0].metadata.name}' 2>/dev/null || echo ''", returnStdout: true).trim()

                            if (podName) {
                                echo "Test de connexion au pod MySQL: \${podName}"
                                def testResult = sh(script: "kubectl exec -n ${K8S_NAMESPACE} \${podName} -- mysqladmin ping -h localhost -u root -proot123 2>/dev/null && echo 'SUCCESS' || echo 'FAILED'", returnStdout: true).trim()

                                if (testResult == "SUCCESS") {
                                    echo "✅ MySQL est accessible!"

                                    // Vérifier/Créer la base de données
                                    sh """
                                        kubectl exec -n ${K8S_NAMESPACE} \${podName} -- mysql -u root -proot123 -e "
                                            CREATE DATABASE IF NOT EXISTS springdb;
                                            SHOW DATABASES;
                                        " 2>/dev/null || true
                                    """
                                    echo "✅ Base de données vérifiée/créée"
                                    mysqlConnected = true
                                    break
                                }
                            }

                            echo "⏱️  Attente... (\${elapsed}/\${timeout} secondes)"
                            sleep interval
                            elapsed += interval
                        }

                        if (!mysqlConnected) {
                            echo "❌ Timeout en attendant MySQL"
                            echo "=== Logs MySQL ==="
                            sh "kubectl logs -n ${K8S_NAMESPACE} -l app=mysql --tail=50"
                            error("MySQL n'est pas accessible")
                        }
                    """
                }
            }
        }

        stage('Build Docker Image') {
            steps {
                echo "🐳 Construction de l'image Docker..."
                sh """
                    # Créez un Dockerfile simple
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

                    echo "=== Liste des images ==="
                    docker images | grep ${IMAGE_NAME} | head -5
                """
            }
        }

        stage('Docker Login & Push') {
            steps {
                echo "📤 Push vers DockerHub..."
                withCredentials([usernamePassword(
                    credentialsId: 'docker-hub',
                    usernameVariable: 'DOCKER_USER',
                    passwordVariable: 'DOCKER_PASS'
                )]) {
                    sh """
                        echo "\$DOCKER_PASS" | docker login -u "\$DOCKER_USER" --password-stdin
                        docker push ${IMAGE_NAME}:${IMAGE_TAG}
                        docker push ${IMAGE_NAME}:latest
                    """
                }
            }
        }

        stage('Clean Old Spring Boot Resources') {
            steps {
                echo "🧹 Nettoyage des anciennes ressources Spring Boot..."
                sh """
                    kubectl delete deployment spring-app -n ${K8S_NAMESPACE} --ignore-not-found=true --timeout=60s || true
                    kubectl delete service spring-service -n ${K8S_NAMESPACE} --ignore-not-found=true --timeout=60s || true
                    sleep 10

                    # Nettoyer les pods terminés
                    kubectl delete pods -n ${K8S_NAMESPACE} --field-selector=status.phase!=Running --timeout=60s 2>/dev/null || true
                """
            }
        }

        stage('Deploy Spring Boot Application') {
            steps {
                echo "🚀 Déploiement de l'application Spring Boot..."
                script {
                    writeFile file: 'spring-deployment.yaml', text: """
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
          value: "false"
        - name: LOGGING_LEVEL_ROOT
          value: "INFO"
        - name: SERVER_SERVLET_CONTEXT_PATH
          value: "${CONTEXT_PATH}"
        # Probes avec le bon contexte path
        readinessProbe:
          httpGet:
            path: ${CONTEXT_PATH}/actuator/health
            port: 8080
          initialDelaySeconds: 90
          periodSeconds: 15
          timeoutSeconds: 5
          failureThreshold: 5
        livenessProbe:
          httpGet:
            path: ${CONTEXT_PATH}/actuator/health
            port: 8080
          initialDelaySeconds: 120
          periodSeconds: 20
          timeoutSeconds: 5
          failureThreshold: 5
        resources:
          requests:
            memory: "512Mi"
            cpu: "250m"
          limits:
            memory: "1Gi"
            cpu: "500m"
"""

                    sh """
                        echo "=== Application du déploiement ==="
                        kubectl apply -f spring-deployment.yaml

                        echo "=== Attente du démarrage (120 secondes) ==="
                        sleep 120

                        echo "=== État du déploiement ==="
                        kubectl get pods,svc,deploy -n ${K8S_NAMESPACE} -o wide
                    """
                }
            }
        }

        stage('Verify Deployment') {
            steps {
                echo "✅ Vérification du déploiement..."
                script {
                    sh """
                        echo "=== État des pods ==="
                        kubectl get pods -n ${K8S_NAMESPACE} -o wide

                        echo ""
                        echo "=== Logs de l'application Spring Boot ==="
                        POD_NAME=\$(kubectl get pods -n ${K8S_NAMESPACE} -l app=spring-app -o jsonpath='{.items[0].metadata.name}' 2>/dev/null || echo "")
                        if [ -n "\$POD_NAME" ]; then
                            echo "Pod: \$POD_NAME"
                            kubectl logs -n ${K8S_NAMESPACE} \$POD_NAME --tail=50
                        else
                            echo "Aucun pod Spring Boot trouvé"
                        fi

                        echo ""
                        echo "=== Test de l'application ==="
                        MINIKUBE_IP=\$(minikube ip)
                        echo "Minikube IP: \$MINIKUBE_IP"
                        echo "Test 1: http://\${MINIKUBE_IP}:30080${CONTEXT_PATH}/actuator/health"
                        echo "Test 2: http://\${MINIKUBE_IP}:30080/actuator/health"

                        # Essayer plusieurs fois avec différents chemins
                        for attempt in \$(seq 1 10); do
                            echo ""
                            echo "Tentative \$attempt..."

                            # Essayer avec contexte path
                            if curl -s -f -m 10 "http://\${MINIKUBE_IP}:30080${CONTEXT_PATH}/actuator/health"; then
                                echo "✅ SUCCÈS avec contexte path: ${CONTEXT_PATH}"
                                break
                            fi

                            # Essayer sans contexte path
                            if curl -s -f -m 10 "http://\${MINIKUBE_IP}:30080/actuator/health"; then
                                echo "✅ SUCCÈS sans contexte path"
                                break
                            fi

                            echo "❌ Les deux tentatives ont échoué, attente 10 secondes..."
                            sleep 10
                        done

                        # Vérification finale
                        echo ""
                        echo "=== Vérification finale des services ==="
                        kubectl get svc -n ${K8S_NAMESPACE}
                    """
                }
            }
        }
    }

    post {
        always {
            echo "🏁 Pipeline terminé"

            // Nettoyage
            sh '''
                echo "=== Nettoyage des fichiers temporaires ==="
                rm -f Dockerfile.jenkins spring-deployment.yaml /tmp/mysql-*.yaml 2>/dev/null || true
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
                    echo "Spring Boot (avec contexte): http://\${MINIKUBE_IP}:30080${CONTEXT_PATH}"
                    echo "Health Check: http://\${MINIKUBE_IP}:30080${CONTEXT_PATH}/actuator/health"
                """
            }
        }

        success {
            echo "🎉 Pipeline réussi!"

            script {
                sh """
                    echo "=== TEST FINAL ==="
                    MINIKUBE_IP=\$(minikube ip)
                    echo "Test de l'endpoint health:"
                    curl -s -m 30 "http://\${MINIKUBE_IP}:30080${CONTEXT_PATH}/actuator/health" || echo "⚠️  L'application ne répond pas"
                """
            }
        }

        failure {
            echo "💥 Le pipeline a échoué"
            script {
                echo "Le pipeline a échoué au build ${BUILD_NUMBER}"
            }
        }
    }
}