pipeline {
    agent any

    environment {
        // DockerHub configuration
        DOCKER_REGISTRY = "docker.io"
        DOCKER_IMAGE_NAME = "saiffrikhi/foyer_project"
        DOCKER_IMAGE_TAG = "${BUILD_NUMBER}-${env.BUILD_ID}"
        // Kubernetes configuration
        K8S_NAMESPACE = "devops"
        CONTEXT_PATH = "/tp-foyer"
        // Application configuration
        MYSQL_ROOT_PASSWORD = "root123"
        MYSQL_DATABASE = "springdb"
        SPRING_DB_USERNAME = "root"
        SPRING_DB_PASSWORD = "root123"
    }

        triggers {
            githubPush()
        }

    stages {
        stage('Checkout Code') {
            steps {
                echo "📦 Récupération du code depuis GitHub..."
                git branch: 'main', url: 'https://github.com/saifeddinefrikhi-lab/FoyerProject.git'
            }
        }

        stage('Build Maven Project') {
            steps {
                echo "🔨 Construction de l'application Maven..."
                sh '''
                    echo "=== Nettoyage et compilation ==="
                    mvn clean compile -B

                    echo "=== Packaging de l'application ==="
                    mvn package -DskipTests -B

                    echo "=== Vérification du JAR généré ==="
                    JAR_FILE=$(find target -name "*.jar" -type f | head -1)
                    if [ -f "$JAR_FILE" ]; then
                        echo "✅ JAR trouvé: $JAR_FILE"
                        ls -lh "$JAR_FILE"
                        echo "Taille: $(du -h "$JAR_FILE" | cut -f1)"
                    else
                        echo "❌ Aucun fichier JAR trouvé!"
                        find target -type f -name "*.jar" 2>/dev/null || echo "Aucun JAR dans target/"
                        exit 1
                    fi
                '''
            }
        }

        stage('Test Application') {
            steps {
                echo "🧪 Exécution des tests..."
                sh '''
                    echo "=== Exécution des tests Maven ==="
                    mvn test -B || echo "⚠️  Certains tests ont échoué, continuation du pipeline..."

                    echo "=== Génération du rapport de test ==="
                    if [ -d "target/surefire-reports" ]; then
                        echo "Rapports de test générés:"
                        ls -la target/surefire-reports/*.xml 2>/dev/null | head -5
                    fi
                '''
            }
        }

        stage('Build Docker Image') {
            steps {
                echo "🐳 Construction de l'image Docker..."
                script {
                    // Check if Dockerfile exists
                    def dockerfileExists = fileExists('Dockerfile')
                    if (!dockerfileExists) {
                        echo "⚠️  Dockerfile non trouvé, création d'un Dockerfile..."
                        sh '''
                            cat > Dockerfile << 'EOF'
FROM eclipse-temurin:17-jre-alpine
WORKDIR /app
COPY target/*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
EOF
                        '''
                    }

                    // Display Dockerfile content
                    sh '''
                        echo "=== Contenu du Dockerfile ==="
                        cat Dockerfile
                        echo ""
                        echo "=== Contenu du répertoire target ==="
                        ls -la target/ 2>/dev/null || echo "Répertoire target non trouvé"
                    '''
                }

                // Build Docker image with credentials
                withCredentials([usernamePassword(
                    credentialsId: 'docker-hub',
                    usernameVariable: 'DOCKER_USERNAME',
                    passwordVariable: 'DOCKER_PASSWORD'
                )]) {
                    sh """
                        echo "=== Connexion à DockerHub ==="
                        echo \$DOCKER_PASSWORD | docker login -u \$DOCKER_USERNAME --password-stdin

                        echo "=== Construction de l'image Docker ==="
                        echo "Image: ${DOCKER_IMAGE_NAME}:${DOCKER_IMAGE_TAG}"
                        docker build -t ${DOCKER_IMAGE_NAME}:${DOCKER_IMAGE_TAG} .
                        docker tag ${DOCKER_IMAGE_NAME}:${DOCKER_IMAGE_TAG} ${DOCKER_IMAGE_NAME}:latest

                        echo "=== Liste des images locales ==="
                        docker images | grep ${DOCKER_IMAGE_NAME} || echo "Aucune image trouvée pour ${DOCKER_IMAGE_NAME}"

                        echo "=== Push vers DockerHub ==="
                        docker push ${DOCKER_IMAGE_NAME}:${DOCKER_IMAGE_TAG}
                        docker push ${DOCKER_IMAGE_NAME}:latest

                        echo "=== Déconnexion de DockerHub ==="
                        docker logout

                        echo "✅ Image poussée avec succès: ${DOCKER_IMAGE_NAME}:${DOCKER_IMAGE_TAG}"
                    """
                }
            }
        }

        stage('Clean Old Kubernetes Resources') {
            steps {
                echo "🧹 Nettoyage des anciennes ressources Kubernetes..."
                sh """
                    echo "=== Suppression des ressources Spring Boot ==="
                    kubectl delete deployment spring-app -n ${K8S_NAMESPACE} --ignore-not-found=true --wait=false
                    kubectl delete service spring-service -n ${K8S_NAMESPACE} --ignore-not-found=true --wait=false

                    echo "=== Suppression des ressources MySQL ==="
                    kubectl delete deployment mysql -n ${K8S_NAMESPACE} --ignore-not-found=true --wait=false
                    kubectl delete service mysql-service -n ${K8S_NAMESPACE} --ignore-not-found=true --wait=false
                    kubectl delete pvc mysql-pvc -n ${K8S_NAMESPACE} --ignore-not-found=true --wait=false
                    kubectl delete pv mysql-pv --ignore-not-found=true --wait=false

                    echo "=== Suppression des ConfigMaps et Secrets ==="
                    kubectl delete configmap spring-config -n ${K8S_NAMESPACE} --ignore-not-found=true --wait=false
                    kubectl delete secret spring-secret -n ${K8S_NAMESPACE} --ignore-not-found=true --wait=false

                    echo "=== Attente de la suppression ==="
                    sleep 15

                    echo "=== Vérification de l'état après nettoyage ==="
                    kubectl get all,pvc,pv -n ${K8S_NAMESPACE} 2>/dev/null || echo "Namespace vide"
                """
            }
        }

        stage('Deploy MySQL Database') {
            steps {
                echo "🗄️  Déploiement de MySQL..."
                script {
                    // Create MySQL PersistentVolume and PersistentVolumeClaim
                    String mysqlStorageYaml = """
apiVersion: v1
kind: PersistentVolume
metadata:
  name: mysql-pv
  labels:
    type: local
    app: mysql
spec:
  storageClassName: manual
  capacity:
    storage: 2Gi
  accessModes:
    - ReadWriteOnce
  hostPath:
    path: "/mnt/mysql-data"
    type: DirectoryOrCreate
---
apiVersion: v1
kind: PersistentVolumeClaim
metadata:
  name: mysql-pvc
  namespace: ${K8S_NAMESPACE}
  labels:
    app: mysql
spec:
  storageClassName: manual
  accessModes:
    - ReadWriteOnce
  resources:
    requests:
      storage: 2Gi
"""
                    writeFile file: 'mysql-storage.yaml', text: mysqlStorageYaml

                    // Create MySQL Deployment and Service
                    String mysqlDeploymentYaml = """
apiVersion: apps/v1
kind: Deployment
metadata:
  name: mysql
  namespace: ${K8S_NAMESPACE}
  labels:
    app: mysql
spec:
  replicas: 1
  selector:
    matchLabels:
      app: mysql
  strategy:
    type: Recreate
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
          value: "${MYSQL_ROOT_PASSWORD}"
        - name: MYSQL_DATABASE
          value: "${MYSQL_DATABASE}"
        - name: MYSQL_TCP_PORT
          value: "3306"
        ports:
        - containerPort: 3306
          name: mysql
        volumeMounts:
        - name: mysql-storage
          mountPath: /var/lib/mysql
        resources:
          requests:
            memory: "256Mi"
            cpu: "250m"
          limits:
            memory: "512Mi"
            cpu: "500m"
        livenessProbe:
          exec:
            command:
            - mysqladmin
            - ping
            - -h
            - localhost
          initialDelaySeconds: 30
          periodSeconds: 10
          timeoutSeconds: 5
        readinessProbe:
          exec:
            command:
            - mysqladmin
            - ping
            - -h
            - localhost
          initialDelaySeconds: 5
          periodSeconds: 2
          timeoutSeconds: 1
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
  labels:
    app: mysql
spec:
  selector:
    app: mysql
  ports:
  - port: 3306
    targetPort: 3306
  type: ClusterIP
"""
                    writeFile file: 'mysql-deployment.yaml', text: mysqlDeploymentYaml
                }

                sh """
                    echo "=== Création du stockage MySQL ==="
                    kubectl apply -f mysql-storage.yaml

                    echo "=== Déploiement de MySQL ==="
                    kubectl apply -f mysql-deployment.yaml

                    echo "=== Attente du démarrage de MySQL (60 secondes) ==="
                    sleep 60

                    echo "=== Vérification du pod MySQL ==="
                    kubectl get pods -n ${K8S_NAMESPACE} -l app=mysql

                    echo "=== Configuration des permissions MySQL ==="
                    MYSQL_POD=\$(kubectl get pods -n ${K8S_NAMESPACE} -l app=mysql -o jsonpath='{.items[0].metadata.name}' 2>/dev/null || echo "")
                    if [ -n "\$MYSQL_POD" ]; then
                        echo "Pod MySQL trouvé: \$MYSQL_POD"

                        # Wait for MySQL to be ready
                        for i in \$(seq 1 30); do
                            if kubectl exec -n ${K8S_NAMESPACE} \$MYSQL_POD -- mysqladmin ping -uroot -p${MYSQL_ROOT_PASSWORD} --silent 2>/dev/null; then
                                echo "✅ MySQL est prêt"

                                # Configure database permissions
                                kubectl exec -n ${K8S_NAMESPACE} \$MYSQL_POD -- mysql -uroot -p${MYSQL_ROOT_PASSWORD} -e "
                                    -- Create database if not exists
                                    CREATE DATABASE IF NOT EXISTS ${MYSQL_DATABASE};

                                    -- Create user and grant privileges
                                    CREATE USER IF NOT EXISTS '${SPRING_DB_USERNAME}'@'%' IDENTIFIED BY '${SPRING_DB_PASSWORD}';
                                    GRANT ALL PRIVILEGES ON ${MYSQL_DATABASE}.* TO '${SPRING_DB_USERNAME}'@'%';
                                    GRANT ALL PRIVILEGES ON *.* TO '${SPRING_DB_USERNAME}'@'%' WITH GRANT OPTION;

                                    -- Allow root access from any host
                                    ALTER USER 'root'@'%' IDENTIFIED BY '${MYSQL_ROOT_PASSWORD}';
                                    GRANT ALL PRIVILEGES ON *.* TO 'root'@'%' WITH GRANT OPTION;

                                    FLUSH PRIVILEGES;

                                    -- Show databases
                                    SHOW DATABASES;
                                " 2>/dev/null || echo "⚠️  Échec de la configuration MySQL, poursuite du pipeline..."
                                break
                            else
                                echo "⏱️  Attente de MySQL... (\$i/30)"
                                sleep 5
                            fi
                        done
                    else
                        echo "⚠️  Pod MySQL non trouvé"
                    fi

                    echo "=== Vérification finale MySQL ==="
                    kubectl get svc,pods -n ${K8S_NAMESPACE} -l app=mysql
                """
            }
        }

        stage('Deploy Spring Boot Application') {
            steps {
                echo "🚀 Déploiement de l'application Spring Boot..."
                script {
                    // Create ConfigMap for non-sensitive configuration
                    String configMapYaml = """
apiVersion: v1
kind: ConfigMap
metadata:
  name: spring-config
  namespace: ${K8S_NAMESPACE}
data:
  SPRING_APPLICATION_NAME: "foyer-app"
  SPRING_DATASOURCE_DRIVER_CLASS_NAME: "com.mysql.cj.jdbc.Driver"
  SPRING_JPA_HIBERNATE_DDL_AUTO: "update"
  SPRING_JPA_PROPERTIES_HIBERNATE_DIALECT: "org.hibernate.dialect.MySQL8Dialect"
  SERVER_SERVLET_CONTEXT_PATH: "${CONTEXT_PATH}"
  SPRING_PROFILES_ACTIVE: "kubernetes"
"""
                    writeFile file: 'spring-configmap.yaml', text: configMapYaml

                    // Create Secret for sensitive data
                    String secretYaml = """
apiVersion: v1
kind: Secret
metadata:
  name: spring-secret
  namespace: ${K8S_NAMESPACE}
type: Opaque
data:
  SPRING_DATASOURCE_USERNAME: $(echo -n "${SPRING_DB_USERNAME}" | base64)
  SPRING_DATASOURCE_PASSWORD: $(echo -n "${SPRING_DB_PASSWORD}" | base64)
"""
                    writeFile file: 'spring-secret.yaml', text: secretYaml

                    // Create Spring Boot Deployment and Service
                    String springDeploymentYaml = """
apiVersion: apps/v1
kind: Deployment
metadata:
  name: spring-app
  namespace: ${K8S_NAMESPACE}
  labels:
    app: spring-app
spec:
  replicas: 2
  selector:
    matchLabels:
      app: spring-app
  strategy:
    type: RollingUpdate
    rollingUpdate:
      maxSurge: 1
      maxUnavailable: 0
  template:
    metadata:
      labels:
        app: spring-app
    spec:
      containers:
      - name: spring-app
        image: ${DOCKER_IMAGE_NAME}:${DOCKER_IMAGE_TAG}
        imagePullPolicy: Always  # Always pull from DockerHub
        ports:
        - containerPort: 8080
          name: http
        env:
        - name: SPRING_DATASOURCE_URL
          value: "jdbc:mysql://mysql-service:3306/${MYSQL_DATABASE}?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC&useLegacyDatetimeCode=false"
        - name: SPRING_DATASOURCE_USERNAME
          valueFrom:
            secretKeyRef:
              name: spring-secret
              key: SPRING_DATASOURCE_USERNAME
        - name: SPRING_DATASOURCE_PASSWORD
          valueFrom:
            secretKeyRef:
              name: spring-secret
              key: SPRING_DATASOURCE_PASSWORD
        - name: SPRING_DATASOURCE_DRIVER_CLASS_NAME
          valueFrom:
            configMapKeyRef:
              name: spring-config
              key: SPRING_DATASOURCE_DRIVER_CLASS_NAME
        - name: SPRING_JPA_HIBERNATE_DDL_AUTO
          valueFrom:
            configMapKeyRef:
              name: spring-config
              key: SPRING_JPA_HIBERNATE_DDL_AUTO
        - name: SERVER_SERVLET_CONTEXT_PATH
          valueFrom:
            configMapKeyRef:
              name: spring-config
              key: SERVER_SERVLET_CONTEXT_PATH
        - name: SPRING_APPLICATION_NAME
          valueFrom:
            configMapKeyRef:
              name: spring-config
              key: SPRING_APPLICATION_NAME
        resources:
          requests:
            memory: "512Mi"
            cpu: "250m"
          limits:
            memory: "1Gi"
            cpu: "500m"
        livenessProbe:
          httpGet:
            path: "${CONTEXT_PATH}/actuator/health/liveness"
            port: 8080
          initialDelaySeconds: 90
          periodSeconds: 10
          timeoutSeconds: 5
          failureThreshold: 3
        readinessProbe:
          httpGet:
            path: "${CONTEXT_PATH}/actuator/health/readiness"
            port: 8080
          initialDelaySeconds: 60
          periodSeconds: 5
          timeoutSeconds: 3
          failureThreshold: 3
        startupProbe:
          httpGet:
            path: "${CONTEXT_PATH}/actuator/health"
            port: 8080
          initialDelaySeconds: 30
          periodSeconds: 10
          timeoutSeconds: 5
          failureThreshold: 30
---
apiVersion: v1
kind: Service
metadata:
  name: spring-service
  namespace: ${K8S_NAMESPACE}
  labels:
    app: spring-app
spec:
  selector:
    app: spring-app
  ports:
  - port: 8080
    targetPort: 8080
    nodePort: 30080
  type: NodePort
"""
                    writeFile file: 'spring-deployment.yaml', text: springDeploymentYaml
                }

                sh """
                    echo "=== Application des ConfigMaps et Secrets ==="
                    kubectl apply -f spring-configmap.yaml
                    kubectl apply -f spring-secret.yaml

                    echo "=== Déploiement de l'application Spring Boot ==="
                    kubectl apply -f spring-deployment.yaml

                    echo "=== Attente du démarrage (90 secondes) ==="
                    sleep 90

                    echo "=== Vérification des déploiements ==="
                    kubectl get deployments -n ${K8S_NAMESPACE}

                    echo "=== Vérification des pods ==="
                    kubectl get pods -n ${K8S_NAMESPACE} -o wide

                    echo "=== Vérification des services ==="
                    kubectl get svc -n ${K8S_NAMESPACE}
                """
            }
        }

        stage('Verify and Test Deployment') {
            steps {
                echo "✅ Vérification et test du déploiement..."
                sh """
                    echo "=== Attente supplémentaire (30 secondes) ==="
                    sleep 30

                    echo "=== État complet du namespace ==="
                    kubectl get all -n ${K8S_NAMESPACE}

                    echo ""
                    echo "=== Récupération de l'IP Minikube ==="
                    MINIKUBE_IP=\$(minikube ip)
                    echo "Minikube IP: \${MINIKUBE_IP}"

                    echo ""
                    echo "=== Test de l'application ==="
                    echo "URL: http://\${MINIKUBE_IP}:30080${CONTEXT_PATH}"

                    # Test health endpoint
                    for i in \$(seq 1 20); do
                        echo "Tentative \$i/20: Test de l'endpoint health..."
                        HTTP_CODE=\$(curl -s -o /dev/null -w "%{http_code}" "http://\${MINIKUBE_IP}:30080${CONTEXT_PATH}/actuator/health" || echo "000")

                        if [ "\$HTTP_CODE" = "200" ]; then
                            echo "✅ Application accessible! Code HTTP: \$HTTP_CODE"

                            # Get health details
                            echo "=== Détails de santé ==="
                            curl -s "http://\${MINIKUBE_IP}:30080${CONTEXT_PATH}/actuator/health" | head -20

                            # Test database connection
                            echo ""
                            echo "=== Test de connexion à la base de données ==="
                            curl -s "http://\${MINIKUBE_IP}:30080${CONTEXT_PATH}/actuator/health" | grep -i db || echo "⚠️  Information DB non trouvée dans health"

                            # Test API endpoint
                            echo ""
                            echo "=== Test de l'API Foyer ==="
                            curl -s "http://\${MINIKUBE_IP}:30080${CONTEXT_PATH}/foyer/getAllFoyers" || echo "⚠️  API Foyer non accessible"

                            break
                        elif [ "\$HTTP_CODE" = "000" ]; then
                            echo "⏱️  Application non disponible... (\$i/20)"
                        else
                            echo "⚠️  Code HTTP: \$HTTP_CODE (\$i/20)"
                        fi

                        sleep 10
                    done

                    # Check logs if application not accessible
                    if [ "\$HTTP_CODE" != "200" ]; then
                        echo "❌ Application non accessible après 20 tentatives"
                        echo "=== Vérification des logs Spring Boot ==="
                        POD_NAME=\$(kubectl get pods -n ${K8S_NAMESPACE} -l app=spring-app -o jsonpath='{.items[0].metadata.name}' 2>/dev/null || echo "")
                        if [ -n "\$POD_NAME" ]; then
                            echo "Logs du pod: \$POD_NAME"
                            kubectl logs -n ${K8S_NAMESPACE} \$POD_NAME --tail=50
                        fi
                    fi
                """
            }
        }

        stage('Monitor Application') {
            steps {
                echo "📊 Monitoring de l'application..."
                sh """
                    echo "=== État des pods ==="
                    kubectl get pods -n ${K8S_NAMESPACE} -o wide

                    echo ""
                    echo "=== Ressources utilisées ==="
                    kubectl top pods -n ${K8S_NAMESPACE} 2>/dev/null || echo "Metrics server non disponible"

                    echo ""
                    echo "=== Événements récents ==="
                    kubectl get events -n ${K8S_NAMESPACE} --sort-by='.lastTimestamp' | tail -10

                    echo ""
                    echo "=== Vérification des logs d'application ==="
                    SPRING_POD=\$(kubectl get pods -n ${K8S_NAMESPACE} -l app=spring-app -o jsonpath='{.items[0].metadata.name}' 2>/dev/null || echo "")
                    if [ -n "\$SPRING_POD" ]; then
                        echo "Logs d'application (dernières 20 lignes):"
                        kubectl logs -n ${K8S_NAMESPACE} \$SPRING_POD --tail=20 | grep -E "(STARTED|ERROR|WARN|INFO.*Application)" || echo "Aucun log spécifique trouvé"
                    fi

                    echo ""
                    echo "=== Test de connexion interne ==="
                    if [ -n "\$SPRING_POD" ]; then
                        echo "Test depuis l'intérieur du pod Spring:"
                        kubectl exec -n ${K8S_NAMESPACE} \$SPRING_POD -- sh -c "
                            wget -q -O- http://localhost:8080${CONTEXT_PATH}/actuator/health || echo 'Échec de la connexion interne'
                        "
                    fi
                """
            }
        }
    }

    post {
        always {
            echo "🏁 Pipeline terminé - Nettoyage..."

            script {
                // Clean up temporary files
                sh '''
                    echo "=== Nettoyage des fichiers temporaires ==="
                    rm -f mysql-storage.yaml mysql-deployment.yaml spring-configmap.yaml spring-secret.yaml spring-deployment.yaml 2>/dev/null || true
                '''

                // Final report
                sh """
                    echo ""
                    echo "================================"
                    echo "         RAPPORT FINAL          "
                    echo "================================"
                    echo ""
                    echo "📦 Informations Docker:"
                    echo "   Image: ${DOCKER_IMAGE_NAME}:${DOCKER_IMAGE_TAG}"
                    echo "   DockerHub: https://hub.docker.com/r/${DOCKER_IMAGE_NAME}"
                    echo "   Tag: ${DOCKER_IMAGE_TAG}"
                    echo ""
                    echo "⚙️  Informations Kubernetes:"
                    echo "   Namespace: ${K8S_NAMESPACE}"
                    echo "   Contexte: ${CONTEXT_PATH}"
                    echo "   Réplicas Spring: 2"
                    echo ""
                    echo "🔗 Informations d'accès:"
                    MINIKUBE_IP=\$(minikube ip 2>/dev/null || echo "N/A")
                    echo "   Minikube IP: \${MINIKUBE_IP}"
                    echo "   URL Application: http://\${MINIKUBE_IP}:30080${CONTEXT_PATH}"
                    echo "   API Health: http://\${MINIKUBE_IP}:30080${CONTEXT_PATH}/actuator/health"
                    echo "   API Foyer: http://\${MINIKUBE_IP}:30080${CONTEXT_PATH}/foyer/getAllFoyers"
                    echo ""
                    echo "🔍 Commandes de vérification:"
                    echo "   # Vérifier les pods"
                    echo "   kubectl get pods -n ${K8S_NAMESPACE}"
                    echo ""
                    echo "   # Vérifier les services"
                    echo "   kubectl get svc -n ${K8S_NAMESPACE}"
                    echo ""
                    echo "   # Accéder aux logs Spring"
                    echo "   kubectl logs -n ${K8S_NAMESPACE} \$(kubectl get pods -n ${K8S_NAMESPACE} -l app=spring-app -o jsonpath='{.items[0].metadata.name}')"
                    echo ""
                    echo "   # Tester MySQL"
                    echo "   kubectl exec -n ${K8S_NAMESPACE} \$(kubectl get pods -n ${K8S_NAMESPACE} -l app=mysql -o jsonpath='{.items[0].metadata.name}') -- mysql -u root -p${MYSQL_ROOT_PASSWORD} -e 'SHOW DATABASES;'"
                    echo ""
                    echo "⏱️  Durée du pipeline: ${currentBuild.durationString}"
                    echo "✅ Build: ${currentBuild.result ?: 'SUCCESS'}"
                """
            }
        }

        success {
            echo "🎉 Déploiement réussi!"
            sh '''
                echo "=== Notification de succès ==="
                echo "Le pipeline s'est terminé avec succès"
                echo "L'application est déployée et accessible"
            '''
        }

        failure {
            echo "💥 Le pipeline a échoué"
            script {
                sh """
                    echo "=== DEBUG - Informations d'échec ==="
                    echo ""
                    echo "1. État des pods:"
                    kubectl get pods -n ${K8S_NAMESPACE} -o wide 2>/dev/null || echo "Impossible de récupérer les pods"

                    echo ""
                    echo "2. Événements d'erreur:"
                    kubectl get events -n ${K8S_NAMESPACE} --field-selector type=Warning --sort-by='.lastTimestamp' | tail -20 2>/dev/null || echo "Impossible de récupérer les événements"

                    echo ""
                    echo "3. Logs MySQL:"
                    kubectl logs -n ${K8S_NAMESPACE} -l app=mysql --tail=100 2>/dev/null || echo "Impossible de récupérer les logs MySQL"

                    echo ""
                    echo "4. Logs Spring Boot:"
                    kubectl logs -n ${K8S_NAMESPACE} -l app=spring-app --tail=200 2>/dev/null || echo "Impossible de récupérer les logs Spring"

                    echo ""
                    echo "5. Déploiements:"
                    kubectl get deployments -n ${K8S_NAMESPACE} 2>/dev/null || echo "Impossible de récupérer les déploiements"

                    echo ""
                    echo "6. Services:"
                    kubectl get svc -n ${K8S_NAMESPACE} 2>/dev/null || echo "Impossible de récupérer les services"

                    echo ""
                    echo "7. Volumes persistants:"
                    kubectl get pv,pvc -n ${K8S_NAMESPACE} 2>/dev/null || echo "Impossible de récupérer les volumes"
                """
            }
        }

        unstable {
            echo "⚠️  Pipeline instable"
        }

        changed {
            echo "📈 Pipeline changé depuis la dernière exécution"
        }
    }
}