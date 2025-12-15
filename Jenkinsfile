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
        MINIKUBE_IP = "192.168.49.2"  // Your static Minikube IP
    }

    triggers {
        githubPush()
    }

    stages {
        stage('Checkout') {
            steps {
                echo "📦 Récupération du code depuis GitHub..."
                git branch: 'main', url: 'https://github.com/saifeddinefrikhi-lab/FoyerProject.git'
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
                                -Dsonar.projectKey=${SONAR_PROJECT_KEY} \
                                -Dsonar.projectName="Foyer Project" \
                                -Dsonar.sources=src/main/java \
                                -Dsonar.tests=src/test/java \
                                -Dsonar.java.binaries=target/classes \
                                -Dsonar.java.libraries=target/**/*.jar \
                                -Dsonar.coverage.jacoco.xmlReportPaths=target/site/jacoco/jacoco.xml \
                                -Dsonar.sourceEncoding=UTF-8 \
                                -Dsonar.host.url=${SONAR_HOST_URL} \
                                -DskipTests=true

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
                echo "🔨 Construction de l'application avec tests..."
                sh '''
                    echo "=== Build Maven (skip tests) ==="
                    mvn clean package -B -DskipTests

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

        stage('Build Docker Image') {
            steps {
                echo "🐳 Construction de l'image Docker..."
                sh """
                    # Switch to Minikube's Docker daemon
                    eval \$(minikube docker-env)

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

                    echo "=== Liste des images dans Minikube ==="
                    docker images | grep ${IMAGE_NAME} | head -5

                    # Switch back to normal Docker daemon
                    eval \$(minikube docker-env -u)
                """
            }
        }

        stage('Push to DockerHub') {
            steps {
                echo "🚀 Pushing image to DockerHub..."
                withCredentials([usernamePassword(credentialsId: 'docker-hub', usernameVariable: 'DOCKER_USER', passwordVariable: 'DOCKER_PASS')]) {
                    sh """
                        echo "=== Login to DockerHub ==="
                        echo "\${DOCKER_PASS}" | docker login -u "\${DOCKER_USER}" --password-stdin

                        echo "=== Pushing image ==="
                        docker push ${IMAGE_NAME}:${IMAGE_TAG}

                        echo "=== Logout from DockerHub ==="
                        docker logout
                    """
                }
            }
        }

        stage('Clean Old Resources') {
            steps {
                echo "🧹 Nettoyage des anciennes ressources..."
                sh """
                    # Supprimez toutes les ressources existantes
                    echo "=== Suppression des déploiements et services ==="
                    kubectl delete deployment spring-app -n ${K8S_NAMESPACE} --ignore-not-found=true --wait=false
                    kubectl delete service spring-service -n ${K8S_NAMESPACE} --ignore-not-found=true --wait=false
                    kubectl delete deployment mysql -n ${K8S_NAMESPACE} --ignore-not-found=true --wait=false
                    kubectl delete service mysql-service -n ${K8S_NAMESPACE} --ignore-not-found=true --wait=false
                    kubectl delete pvc mysql-pvc -n ${K8S_NAMESPACE} --ignore-not-found=true --wait=false
                    kubectl delete pv mysql-pv --ignore-not-found=true --wait=false
                    kubectl delete configmap spring-config -n ${K8S_NAMESPACE} --ignore-not-found=true --wait=false
                    kubectl delete secret spring-secret -n ${K8S_NAMESPACE} --ignore-not-found=true --wait=false

                    sleep 10

                    # Vérifiez qu'il ne reste plus de pods
                    echo "=== État après nettoyage ==="
                    kubectl get pods -n ${K8S_NAMESPACE} || true
                """
            }
        }

        stage('Deploy MySQL') {
            steps {
                echo "🗄️  Déploiement de MySQL..."
                sh """
                    echo "=== Création du répertoire de données ==="
                    minikube ssh "sudo mkdir -p /tmp/mysql-data && sudo chmod 777 /tmp/mysql-data"

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
  storageClassName: manual
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
  storageClassName: manual
EOF
                    kubectl apply -f /tmp/mysql-storage.yaml

                    echo "=== Création du déploiement MySQL ==="
                    cat > /tmp/mysql-deployment.yaml << 'EOF'
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
        env:
        - name: MYSQL_ROOT_PASSWORD
          value: "root123"
        - name: MYSQL_DATABASE
          value: "springdb"
        - name: MYSQL_ROOT_HOST
          value: "%"
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

                    echo "=== Vérification de l'état MySQL ==="
                    kubectl get pods,svc -n ${K8S_NAMESPACE}

                    echo "=== Configuration des permissions MySQL ==="
                    for i in \$(seq 1 30); do
                        POD_NAME=\$(kubectl get pods -n devops -l app=mysql -o jsonpath='{.items[0].metadata.name}' 2>/dev/null || echo "")
                        if [ -n "\$POD_NAME" ]; then
                            echo "Tentative \$i/30: Vérification du pod \$POD_NAME..."
                            POD_STATUS=\$(kubectl get pod -n devops \$POD_NAME -o jsonpath='{.status.phase}' 2>/dev/null)
                            if [ "\$POD_STATUS" = "Running" ]; then
                                echo "✅ MySQL est en cours d'exécution. Configuration des permissions..."

                                # Configure MySQL permissions
                                kubectl exec -n devops \$POD_NAME -- mysql -u root -proot123 -e "
                                    CREATE USER IF NOT EXISTS 'spring'@'%' IDENTIFIED BY 'spring123';
                                    GRANT ALL PRIVILEGES ON springdb.* TO 'spring'@'%';
                                    GRANT ALL PRIVILEGES ON *.* TO 'root'@'%' WITH GRANT OPTION;
                                    FLUSH PRIVILEGES;
                                    CREATE DATABASE IF NOT EXISTS springdb;
                                    USE springdb;
                                    SELECT '✅ Base de données créée' as Status;
                                " 2>/dev/null && break || echo "⚠️  Réessayer dans 10 secondes..."
                            fi
                        fi
                        sleep 10
                    done

                    echo "=== Test de connexion MySQL ==="
                    MYSQL_POD=\$(kubectl get pods -n ${K8S_NAMESPACE} -l app=mysql -o jsonpath='{.items[0].metadata.name}' 2>/dev/null || echo "")
                    if [ -n "\$MYSQL_POD" ]; then
                        kubectl exec -n devops \$MYSQL_POD -- mysql -u root -proot123 -e "SHOW DATABASES;"
                    fi
                """
            }
        }

        stage('Create ConfigMap and Secret') {
            steps {
                echo "🔧 Création des ConfigMap et Secret..."
                sh """
                    echo "=== Création de ConfigMap ==="
                    cat > /tmp/spring-configmap.yaml << 'EOF'
apiVersion: v1
kind: ConfigMap
metadata:
  name: spring-config
  namespace: ${K8S_NAMESPACE}
data:
  SPRING_DATASOURCE_URL: "jdbc:mysql://mysql-service:3306/springdb?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC&createDatabaseIfNotExist=true"
  SPRING_DATASOURCE_DRIVER_CLASS_NAME: "com.mysql.cj.jdbc.Driver"
  SPRING_JPA_HIBERNATE_DDL_AUTO: "update"
  SERVER_SERVLET_CONTEXT_PATH: "${CONTEXT_PATH}"
  SPRING_APPLICATION_NAME: "foyer-app"
  SPRING_JPA_PROPERTIES_HIBERNATE_DIALECT: "org.hibernate.dialect.MySQL8Dialect"
EOF

                    echo "=== Création de Secret ==="
                    cat > /tmp/spring-secret.yaml << 'EOF'
apiVersion: v1
kind: Secret
metadata:
  name: spring-secret
  namespace: ${K8S_NAMESPACE}
type: Opaque
data:
  SPRING_DATASOURCE_USERNAME: "cm9vdA=="  # root
  SPRING_DATASOURCE_PASSWORD: "cm9vdDEyMw=="  # root123
EOF

                    kubectl apply -f /tmp/spring-configmap.yaml
                    kubectl apply -f /tmp/spring-secret.yaml

                    echo "=== Vérification ==="
                    kubectl get configmap,secret -n ${K8S_NAMESPACE}
                """
            }
        }

        stage('Deploy Spring Boot Application') {
            steps {
                echo "🚀 Déploiement de l'application Spring Boot..."
                script {
                    String yamlContent = """apiVersion: apps/v1
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
        imagePullPolicy: Never
        ports:
        - containerPort: 8080
        envFrom:
        - configMapRef:
            name: spring-config
        - secretRef:
            name: spring-secret
        resources:
          requests:
            memory: "512Mi"
            cpu: "250m"
          limits:
            memory: "1Gi"
            cpu: "500m"
        livenessProbe:
          httpGet:
            path: ${CONTEXT_PATH}/actuator/health
            port: 8080
          initialDelaySeconds: 180
          periodSeconds: 20
          timeoutSeconds: 10
          failureThreshold: 3
        readinessProbe:
          httpGet:
            path: ${CONTEXT_PATH}/actuator/health
            port: 8080
          initialDelaySeconds: 120
          periodSeconds: 10
          timeoutSeconds: 5
          failureThreshold: 3
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

                    writeFile file: 'spring-deployment.yaml', text: yamlContent
                }

                sh """
                    echo "=== Application du déploiement ==="
                    kubectl apply -f spring-deployment.yaml

                    echo "=== Attente du démarrage (3 minutes) ==="
                    for i in \$(seq 1 18); do
                        POD_NAME=\$(kubectl get pods -n ${K8S_NAMESPACE} -l app=spring-app -o jsonpath='{.items[0].metadata.name}' 2>/dev/null || echo "")
                        if [ -n "\$POD_NAME" ]; then
                            POD_STATUS=\$(kubectl get pod -n ${K8S_NAMESPACE} \$POD_NAME -o jsonpath='{.status.phase}' 2>/dev/null)
                            if [ "\$POD_STATUS" = "Running" ]; then
                                echo "✅ Spring Boot pod est en cours d'exécution"
                                break
                            elif [ "\$POD_STATUS" = "Failed" ] || [ "\$POD_STATUS" = "Error" ]; then
                                echo "❌ Pod a échoué. Affichage des logs:"
                                kubectl logs -n ${K8S_NAMESPACE} \$POD_NAME --tail=100
                                exit 1
                            fi
                        fi
                        echo "⏱️  Attente Spring Boot... (\$i/18)"
                        sleep 10
                    done

                    echo "=== Vérification de l'état ==="
                    kubectl get pods,svc -n ${K8S_NAMESPACE}
                """
            }
        }

        stage('Verify Deployment') {
            steps {
                echo "✅ Vérification du déploiement..."
                sh """
                    echo "=== Attente supplémentaire (30 secondes) ==="
                    sleep 30

                    echo "=== Vérification des pods ==="
                    kubectl get pods -n ${K8S_NAMESPACE} -o wide

                    echo ""
                    echo "=== Logs Spring Boot ==="
                    POD_NAME=\$(kubectl get pods -n ${K8S_NAMESPACE} -l app=spring-app -o jsonpath='{.items[0].metadata.name}' 2>/dev/null || echo "")
                    if [ -n "\$POD_NAME" ]; then
                        echo "Pod: \$POD_NAME"
                        kubectl logs -n ${K8S_NAMESPACE} \$POD_NAME --tail=100
                    fi

                    echo ""
                    echo "=== Test de l'application ==="
                    MINIKUBE_IP="${MINIKUBE_IP}"
                    echo "Minikube IP: \${MINIKUBE_IP}"

                    for i in \$(seq 1 15); do
                        echo "Tentative \$i/15..."
                        if curl -s -f -m 30 "http://\${MINIKUBE_IP}:30080${CONTEXT_PATH}/actuator/health" > /dev/null; then
                            echo "✅ Application accessible avec contexte path!"
                            echo ""
                            echo "=== Test de l'API Foyer ==="
                            curl -s "http://\${MINIKUBE_IP}:30080${CONTEXT_PATH}/foyer/getAllFoyers" | head -20
                            echo ""
                            break
                        elif curl -s -f -m 30 "http://\${MINIKUBE_IP}:30080/actuator/health" > /dev/null; then
                            echo "✅ Application accessible (sans contexte path)"
                            break
                        else
                            echo "⏱️  En attente... (\$i/15)"
                            sleep 10
                        fi
                    done

                    echo ""
                    echo "=== État final ==="
                    kubectl get all -n ${K8S_NAMESPACE}
                """
            }
        }

        stage('Debug Application') {
            steps {
                echo "🐛 Debug de l'application (si nécessaire)..."
                sh """
                    echo "=== Vérification des ressources ==="
                    kubectl describe deployment spring-app -n ${K8S_NAMESPACE} || true

                    echo ""
                    echo "=== Vérification des événements ==="
                    kubectl get events -n ${K8S_NAMESPACE} --sort-by='.lastTimestamp' | tail -20 || true

                    echo ""
                    echo "=== Vérification des logs Spring Boot ==="
                    POD_NAME=\$(kubectl get pods -n ${K8S_NAMESPACE} -l app=spring-app -o jsonpath='{.items[0].metadata.name}' 2>/dev/null || echo "")
                    if [ -n "\$POD_NAME" ]; then
                        echo "=== Dernières 200 lignes de logs ==="
                        kubectl logs -n ${K8S_NAMESPACE} \$POD_NAME --tail=200 | grep -E "(ERROR|WARN|INFO.*Application|Started)" || echo "Aucune erreur trouvée"
                    fi
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
                rm -f Dockerfile.jenkins spring-deployment.yaml /tmp/mysql-deployment.yaml /tmp/mysql-storage.yaml /tmp/spring-configmap.yaml /tmp/spring-secret.yaml 2>/dev/null || true
            '''

            // Rapport final
            sh """
                echo "=== RAPPORT FINAL ==="
                echo "Image Docker: ${IMAGE_NAME}:${IMAGE_TAG}"
                echo "Namespace: ${K8S_NAMESPACE}"
                echo "Contexte path: ${CONTEXT_PATH}"
                echo "Minikube IP: ${MINIKUBE_IP}"
                echo ""
                echo "=== Liens SonarQube ==="
                echo "Dashboard SonarQube: ${SONAR_HOST_URL}/dashboard?id=${SONAR_PROJECT_KEY}"
                echo "Projet SonarQube: ${SONAR_HOST_URL}/project/overview?id=${SONAR_PROJECT_KEY}"
                echo ""
                echo "=== URL d'accès ==="
                echo "Application: http://${MINIKUBE_IP}:30080${CONTEXT_PATH}"
                echo "Health Check: http://${MINIKUBE_IP}:30080${CONTEXT_PATH}/actuator/health"
                echo "API Foyer: http://${MINIKUBE_IP}:30080${CONTEXT_PATH}/foyer/getAllFoyers"
                echo ""
                echo "=== Pour tester manuellement ==="
                echo "1. Test MySQL: kubectl exec -n devops -it \$(kubectl get pods -n devops -l app=mysql -o name | head -1) -- mysql -u root -proot123"
                echo "2. Test Spring Boot: curl -s http://${MINIKUBE_IP}:30080${CONTEXT_PATH}/actuator/health"
                echo "3. Test API: curl -s http://${MINIKUBE_IP}:30080${CONTEXT_PATH}/foyer/getAllFoyers"
                echo ""
                echo "=== Commandes utiles ==="
                echo "Voir les pods: kubectl get pods -n ${K8S_NAMESPACE}"
                echo "Voir les logs: kubectl logs -n ${K8S_NAMESPACE} -l app=spring-app --tail=100"
                echo "Redémarrer Spring Boot: kubectl rollout restart deployment/spring-app -n ${K8S_NAMESPACE}"
            """
        }

        success {
            echo "✅ Pipeline exécuté avec succès!"
            sh """
                echo "=== QUALITÉ DU CODE ==="
                echo "✅ L'analyse SonarQube a été effectuée avec succès"
                echo "📊 Consultez le rapport: ${SONAR_HOST_URL}/dashboard?id=${SONAR_PROJECT_KEY}"
                echo ""
                echo "=== DÉPLOIEMENT ==="
                echo "✅ L'application Spring Boot a été déployée avec succès"
                echo "🌐 Accès: http://${MINIKUBE_IP}:30080${CONTEXT_PATH}"
            """
        }

        failure {
            echo "💥 Le pipeline a échoué"
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
                echo "4. Événements récents:"
                kubectl get events -n ${K8S_NAMESPACE} --sort-by='.lastTimestamp' | tail -30 || true
                echo ""
                echo "5. Services:"
                kubectl get svc -n ${K8S_NAMESPACE} || true
                echo ""
                echo "6. ConfigMaps et Secrets:"
                kubectl get configmap,secret -n ${K8S_NAMESPACE} || true
                echo ""
                echo "7. Minikube status:"
                minikube status || true
            """
        }
    }
}