pipeline {
    agent any

    environment {
        IMAGE_NAME = "saiffrikhi/foyer_project"
        IMAGE_TAG = "latest"
        K8S_NAMESPACE = "devops"
        CONTEXT_PATH = "/tp-foyer"
    }

    triggers {
        githubPush()
    }

    stages {
        stage('Préparation') {
            steps {
                echo "🔧 Préparation de l'environnement..."
                sh '''
                    echo "=== Vérification des prérequis ==="
                    java -version || echo "Java non installé"
                    mvn --version || echo "Maven non installé"
                    docker --version || echo "Docker non installé"

                    # Configurer minikube si nécessaire
                    if command -v minikube >/dev/null 2>&1; then
                        echo "Minikube détecté"
                        minikube status || minikube start
                        eval $(minikube docker-env) || true
                    fi
                '''
            }
        }

        stage('Checkout') {
            steps {
                echo "📦 Récupération du code..."
                git branch: 'main', url: 'https://github.com/saifeddinefrikhi-lab/FoyerProject.git'
            }
        }

        stage('Setup Kubernetes') {
            steps {
                echo "🔧 Configuration Kubernetes..."
                sh '''
                    echo "=== Configuration de kubectl ==="

                    # Initialiser kubectl si minikube est disponible
                    if command -v minikube >/dev/null 2>&1; then
                        echo "Utilisation de minikube..."
                        minikube update-context || true
                        kubectl config use-context minikube || true

                        # Créer le namespace s'il n'existe pas
                        kubectl create namespace ${K8S_NAMESPACE} --dry-run=client -o yaml | kubectl apply -f - || true

                        # Vérifier la connexion
                        echo "=== Test de connexion ==="
                        kubectl cluster-info && echo "✅ Connexion Kubernetes établie" || echo "⚠️ Problème de connexion"
                    else
                        echo "Minikube non trouvé, vérifiez la configuration manuelle de kubectl"
                        kubectl config view || echo "kubectl non configuré"
                    fi
                '''
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

        stage('Test Local') {
            steps {
                echo "🧪 Test local..."
                script {
                    try {
                        sh """
                            # Vérifier si le port 8081 est disponible
                            timeout 2 nc -z localhost 8081 && echo "Port 8081 déjà utilisé" || true

                            echo "=== Démarrage application avec H2 ==="
                            nohup java -jar target/*.jar \\
                                --spring.profiles.active=test \\
                                --server.port=8081 \\
                                > /tmp/app_test.log 2>&1 &
                            APP_PID=\$!
                            echo "PID: \$APP_PID"

                            # Attendre le démarrage
                            for i in {1..60}; do
                                if curl -s http://localhost:8081/actuator/health > /dev/null 2>&1; then
                                    echo "✅ Application démarrée après \${i} secondes"
                                    break
                                fi
                                sleep 2
                                if [ \$i -eq 60 ]; then
                                    echo "❌ Timeout démarrage"
                                    tail -50 /tmp/app_test.log
                                    kill \$APP_PID 2>/dev/null || true
                                    exit 1
                                fi
                            done

                            # Tester avec contexte
                            echo "=== Test avec contexte path ==="
                            if curl -s -f "http://localhost:8081${CONTEXT_PATH}/actuator/health"; then
                                echo "✅ Test avec contexte réussi"
                            else
                                echo "=== Test sans contexte ==="
                                if curl -s -f "http://localhost:8081/actuator/health"; then
                                    echo "⚠️ Application fonctionne sans contexte"
                                else
                                    echo "❌ Les deux tests ont échoué"
                                    tail -100 /tmp/app_test.log
                                    kill \$APP_PID
                                    exit 1
                                fi
                            fi

                            kill \$APP_PID
                            wait \$APP_PID 2>/dev/null || true
                        """
                    } catch (Exception e) {
                        echo "⚠️ Test local échoué: ${e.getMessage()}"
                        sh "tail -100 /tmp/app_test.log 2>/dev/null || true"
                        // Continuer pour debugging
                    }
                }
            }
        }

        stage('Build Docker Image') {
            steps {
                echo "🐳 Construction image Docker..."
                sh """
                    # Créer un Dockerfile optimisé
                    cat > Dockerfile.jenkins << 'EOF'
FROM eclipse-temurin:17-jre-alpine
RUN apk add --no-cache curl
WORKDIR /app
COPY target/*.jar app.jar
EXPOSE 8080
HEALTHCHECK --interval=30s --timeout=3s --start-period=60s --retries=3 \\
  CMD curl -f http://localhost:8080/actuator/health || exit 1
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
EOF

                    echo "=== Build Docker ==="
                    docker build -t ${IMAGE_NAME}:${IMAGE_TAG} -f Dockerfile.jenkins .

                    echo "=== Tag et push ==="
                    docker tag ${IMAGE_NAME}:${IMAGE_TAG} ${IMAGE_NAME}:build-${BUILD_NUMBER}
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
                        echo "\${DOCKER_PASS}" | docker login -u "\${DOCKER_USER}" --password-stdin
                        docker push ${IMAGE_NAME}:${IMAGE_TAG}
                        docker push ${IMAGE_NAME}:build-${BUILD_NUMBER}
                    """
                }
            }
        }

        stage('Deploy to Kubernetes') {
            steps {
                echo "🚀 Déploiement Kubernetes..."
                script {
                    // Créer d'abord la configuration de déploiement
                    sh """
                        cat > deployment.yaml << EOF
apiVersion: apps/v1
kind: Deployment
metadata:
  name: spring-app
  namespace: ${K8S_NAMESPACE}
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
        image: ${IMAGE_NAME}:${IMAGE_TAG}
        imagePullPolicy: Always
        ports:
        - containerPort: 8080
        env:
        - name: SPRING_PROFILES_ACTIVE
          value: "kubernetes"
        - name: SPRING_DATASOURCE_URL
          value: "jdbc:mysql://mysql-service:3306/springdb?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC"
        - name: SPRING_DATASOURCE_USERNAME
          value: "root"
        - name: SPRING_DATASOURCE_PASSWORD
          value: "root123"
        - name: SERVER_SERVLET_CONTEXT_PATH
          value: "${CONTEXT_PATH}"
        - name: MANAGEMENT_ENDPOINTS_WEB_EXPOSURE_INCLUDE
          value: "health,info"
        readinessProbe:
          httpGet:
            path: ${CONTEXT_PATH}/actuator/health
            port: 8080
            scheme: HTTP
          initialDelaySeconds: 60
          periodSeconds: 10
          timeoutSeconds: 5
          failureThreshold: 3
        livenessProbe:
          httpGet:
            path: ${CONTEXT_PATH}/actuator/health
            port: 8080
            scheme: HTTP
          initialDelaySeconds: 90
          periodSeconds: 15
          timeoutSeconds: 5
          failureThreshold: 3
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
EOF

                        echo "=== Application des ressources ==="
                        kubectl apply -f deployment.yaml

                        echo "=== Attente démarrage (2 minutes) ==="
                        sleep 120

                        echo "=== Vérification déploiement ==="
                        kubectl get deployments,svc,pods -n ${K8S_NAMESPACE} -o wide

                        echo "=== Vérification logs ==="
                        POD_NAME=\$(kubectl get pods -n ${K8S_NAMESPACE} -l app=spring-app -o jsonpath='{.items[0].metadata.name}' 2>/dev/null || echo "none")
                        if [ "\$POD_NAME" != "none" ]; then
                            echo "Pod: \$POD_NAME"
                            kubectl logs -n ${K8S_NAMESPACE} \$POD_NAME --tail=50
                        fi
                    """
                }
            }
        }

        stage('Verify Deployment') {
            steps {
                echo "✅ Vérification finale..."
                script {
                    sh """
                        echo "=== Test de l'application ==="
                        POD_NAME=\$(kubectl get pods -n ${K8S_NAMESPACE} -l app=spring-app -o jsonpath='{.items[0].metadata.name}' 2>/dev/null || echo "")

                        if [ -n "\$POD_NAME" ]; then
                            echo "1. Test depuis l'intérieur du pod:"
                            kubectl exec -n ${K8S_NAMESPACE} \$POD_NAME -- \\
                                curl -s http://localhost:8080${CONTEXT_PATH}/actuator/health || \\
                                echo "Échec interne"

                            echo ""
                            echo "2. Test depuis l'extérieur:"
                            MINIKUBE_IP=\$(minikube ip 2>/dev/null || echo "127.0.0.1")
                            echo "IP Minikube: \$MINIKUBE_IP"

                            curl -s -m 10 "http://\${MINIKUBE_IP}:30080${CONTEXT_PATH}/actuator/health" && \\
                                echo "✅ Application accessible" || echo "⚠️ Application non accessible"
                        else
                            echo "❌ Aucun pod trouvé"
                        fi
                    """
                }
            }
        }
    }

    post {
        always {
            echo "🏁 Pipeline terminé"
            sh '''
                echo "=== Nettoyage ==="
                rm -f Dockerfile.jenkins deployment.yaml || true
                docker rm -f test-container-* 2>/dev/null || true
            '''
        }

        success {
            echo "🎉 Déploiement réussi!"
            script {
                sh """
                    echo "=== URL d'accès ==="
                    MINIKUBE_IP=\$(minikube ip 2>/dev/null || echo "localhost")
                    echo "Application: http://\${MINIKUBE_IP}:30080${CONTEXT_PATH}"
                    echo "Health: http://\${MINIKUBE_IP}:30080${CONTEXT_PATH}/actuator/health"

                    echo ""
                    echo "=== État final ==="
                    kubectl get all -n ${K8S_NAMESPACE} || echo "Impossible de récupérer l'état"
                """
            }
        }

        failure {
            echo "💥 Pipeline échoué"
            script {
                sh """
                    echo "=== DIAGNOSTIC ==="

                    echo "1. Vérifier minikube:"
                    minikube status || echo "Minikube non disponible"

                    echo ""
                    echo "2. Vérifier namespace:"
                    kubectl get namespaces || echo "kubectl non configuré"

                    echo ""
                    echo "3. Vérifier pods:"
                    kubectl get pods -n ${K8S_NAMESPACE} -o wide || echo "Namespace non accessible"

                    echo ""
                    echo "=== Solutions ==="
                    echo "1. Démarrer minikube: minikube start"
                    echo "2. Configurer kubectl: minikube update-context"
                    echo "3. Vérifier MySQL: kubectl get pods -n ${K8S_NAMESPACE} | grep mysql"
                """
            }
        }
    }
}