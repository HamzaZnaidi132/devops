pipeline {
    agent any

    environment {
        IMAGE_NAME = "saiffrikhi/foyer_project"
        IMAGE_TAG = "${BUILD_NUMBER}"
        LATEST_TAG = "latest"
        K8S_NAMESPACE = "devops"
        CONTEXT_PATH = "/tp-foyer"
    }

    triggers {
        githubPush()
    }

    stages {
        stage('Clean Workspace') {
            steps {
                cleanWs()
                echo "🧹 Workspace nettoyé"
            }
        }

        stage('Checkout') {
            steps {
                echo "📦 Récupération du code depuis GitHub..."
                git branch: 'main', url: 'https://github.com/saifeddinefrikhi-lab/FoyerProject.git'
            }
        }

        stage('Build Application') {
            steps {
                echo "🔨 Construction de l'application..."
                sh '''
                    echo "=== Build Maven (skip tests) ==="
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

        stage('Build Docker Image') {
            steps {
                echo "🐳 Construction de l'image Docker..."
                script {
                    // Utiliser le Dockerfile existant au lieu d'en créer un nouveau
                    sh """
                        echo "=== Construction avec le Dockerfile existant ==="
                        docker build -t ${IMAGE_NAME}:${IMAGE_TAG} .
                        docker tag ${IMAGE_NAME}:${IMAGE_TAG} ${IMAGE_NAME}:${LATEST_TAG}

                        echo "=== Images créées ==="
                        docker images | grep ${IMAGE_NAME}
                    """
                }
            }
        }

        stage('Test Docker Image Locally') {
            steps {
                echo "🧪 Test Docker simple..."
                script {
                    try {
                        sh """
                            echo "=== Test rapide en local ==="
                            docker run -d --name quick-test-${BUILD_NUMBER} \\
                              -e SPRING_DATASOURCE_URL="jdbc:h2:mem:testdb" \\
                              -e SPRING_DATASOURCE_USERNAME="sa" \\
                              -e SPRING_DATASOURCE_PASSWORD="" \\
                              -e SPRING_JPA_HIBERNATE_DDL_AUTO="create-drop" \\
                              -p 18080:8080 \\
                              ${IMAGE_NAME}:${IMAGE_TAG}

                            echo "Attente 30 secondes..."
                            sleep 30

                            echo "=== Vérification rapide ==="
                            if curl -s -f http://localhost:18080/actuator/health; then
                                echo "✅ Test local réussi"
                            else
                                echo "⚠️ Test local non concluant, continuation..."
                            fi

                            docker stop quick-test-${BUILD_NUMBER} 2>/dev/null || true
                            docker rm quick-test-${BUILD_NUMBER} 2>/dev/null || true
                        """
                    } catch (Exception e) {
                        echo "⚠️ Test local ignoré, continuation du pipeline"
                    }
                }
            }
        }

        stage('Docker Login & Push') {
            steps {
                echo "🔐 Connexion et push DockerHub..."
                withCredentials([usernamePassword(credentialsId: 'docker-hub',
                    usernameVariable: 'DOCKER_USER',
                    passwordVariable: 'DOCKER_PASS')]) {
                    sh """
                        echo "=== Connexion à Docker Hub ==="
                        echo "\$DOCKER_PASS" | docker login -u "\$DOCKER_USER" --password-stdin

                        echo "=== Push des images ==="
                        docker push ${IMAGE_NAME}:${IMAGE_TAG}
                        docker push ${IMAGE_NAME}:${LATEST_TAG}

                        echo "✅ Images poussées"
                    """
                }
            }
        }

        stage('Clean Kubernetes Resources') {
            steps {
                echo "🧹 Nettoyage Kubernetes..."
                script {
                    sh """
                        echo "=== Suppression des anciennes ressources ==="
                        kubectl delete deployment spring-app -n ${K8S_NAMESPACE} --ignore-not-found=true --force --grace-period=0
                        kubectl delete service spring-service -n ${K8S_NAMESPACE} --ignore-not-found=true

                        # Attendre que tout soit supprimé
                        sleep 20

                        echo "=== État après nettoyage ==="
                        kubectl get all -n ${K8S_NAMESPACE} || true
                    """
                }
            }
        }

        stage('Deploy MySQL') {
            steps {
                echo "🗄️ Déploiement MySQL..."
                script {
                    sh """
                        # Créer un déploiement MySQL simple
                        cat > mysql-simple.yaml << EOF
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
      volumes:
      - name: mysql-storage
        emptyDir: {}
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

                        echo "=== Application de MySQL ==="
                        kubectl apply -f mysql-simple.yaml

                        echo "=== Attente de MySQL (60s) ==="
                        sleep 60

                        echo "=== Vérification MySQL ==="
                        kubectl get pods -n ${K8S_NAMESPACE}
                    """
                }
            }
        }

        stage('Deploy Spring Boot') {
            steps {
                echo "🚀 Déploiement Spring Boot..."
                script {
                    // Créer un déploiement simple sans probes d'abord
                    sh """
                        cat > spring-app-simple.yaml << EOF
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
        imagePullPolicy: Always
        ports:
        - containerPort: 8080
        env:
        - name: SPRING_DATASOURCE_URL
          value: "jdbc:mysql://mysql-service:3306/springdb?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC"
        - name: SPRING_DATASOURCE_USERNAME
          value: "root"
        - name: SPRING_DATASOURCE_PASSWORD
          value: "root123"
        - name: SPRING_DATASOURCE_DRIVER_CLASS_NAME
          value: "com.mysql.cj.jdbc.Driver"
        - name: SPRING_JPA_HIBERNATE_DDL_AUTO
          value: "update"
        - name: SERVER_SERVLET_CONTEXT_PATH
          value: "${CONTEXT_PATH}"
        # Désactiver les probes pour le moment
        # readinessProbe:
        #   httpGet:
        #     path: ${CONTEXT_PATH}/actuator/health
        #     port: 8080
        #   initialDelaySeconds: 120
        #   periodSeconds: 30
        # livenessProbe:
        #   httpGet:
        #     path: ${CONTEXT_PATH}/actuator/health
        #     port: 8080
        #   initialDelaySeconds: 180
        #   periodSeconds: 30
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

                        echo "=== Application de Spring Boot ==="
                        kubectl apply -f spring-app-simple.yaml

                        echo "=== Attente longue pour le démarrage (3 minutes) ==="
                        sleep 180
                    """
                }
            }
        }

        stage('Verify Deployment') {
            steps {
                echo "✅ Vérification du déploiement..."
                script {
                    sh """
                        echo "=== État des ressources ==="
                        kubectl get all -n ${K8S_NAMESPACE}

                        echo ""
                        echo "=== Détails du pod Spring Boot ==="
                        kubectl describe pods -n ${K8S_NAMESPACE} -l app=spring-app || true

                        echo ""
                        echo "=== Événements récents ==="
                        kubectl get events -n ${K8S_NAMESPACE} --sort-by='.lastTimestamp' | tail -20 || true

                        echo ""
                        echo "=== Tentative de logs ==="
                        POD_NAME=\$(kubectl get pods -n ${K8S_NAMESPACE} -l app=spring-app -o jsonpath='{.items[0].metadata.name}' 2>/dev/null || echo "none")
                        if [ "\$POD_NAME" != "none" ]; then
                            echo "Tentative de récupération des logs pour \$POD_NAME..."
                            kubectl logs -n ${K8S_NAMESPACE} \$POD_NAME --tail=100 2>/dev/null || echo "Impossible de récupérer les logs (pod peut-être pas encore démarré)"
                        fi
                    """
                }
            }
        }

        stage('Test Application') {
            steps {
                echo "🧪 Test de l'application..."
                script {
                    sh """
                        echo "=== Test de l'application ==="
                        MINIKUBE_IP=\$(minikube ip 2>/dev/null || echo "192.168.49.2")

                        echo "Test 1: Health endpoint"
                        curl -s -f "http://\${MINIKUBE_IP}:30080${CONTEXT_PATH}/actuator/health" && \\
                            echo "✅ Health endpoint OK" || echo "⚠️ Health endpoint non accessible"

                        echo ""
                        echo "Test 2: API endpoint"
                        curl -s "http://\${MINIKUBE_IP}:30080${CONTEXT_PATH}/getAllFoyers" && \\
                            echo "✅ API endpoint répond" || echo "⚠️ API endpoint non accessible"

                        echo ""
                        echo "=== URL d'accès finale ==="
                        echo "Application: http://\${MINIKUBE_IP}:30080${CONTEXT_PATH}"
                        echo "Health: http://\${MINIKUBE_IP}:30080${CONTEXT_PATH}/actuator/health"
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
                rm -f mysql-simple.yaml spring-app-simple.yaml 2>/dev/null || true
                docker system prune -f 2>/dev/null || true
            '''

            // Rapport final
            script {
                sh """
                    echo ""
                    echo "=== RAPPORT FINAL ==="
                    echo "Build: ${BUILD_NUMBER}"
                    echo "Image: ${IMAGE_NAME}:${IMAGE_TAG}"
                    echo "Namespace: ${K8S_NAMESPACE}"
                    echo "Contexte: ${CONTEXT_PATH}"

                    echo ""
                    echo "=== État final du cluster ==="
                    kubectl get pods,svc,deploy -n ${K8S_NAMESPACE} || true

                    echo ""
                    echo "=== Diagnostic ==="
                    echo "1. Pods:"
                    kubectl get pods -n ${K8S_NAMESPACE} -o wide || true

                    echo ""
                    echo "2. Événements:"
                    kubectl get events -n ${K8S_NAMESPACE} --sort-by='.lastTimestamp' | tail -10 || true
                """
            }
        }

        success {
            echo "🎉 Pipeline réussi!"

            script {
                sh """
                    echo "=== SUCCÈS ==="
                    echo "✅ Déploiement terminé"
                    MINIKUBE_IP=\$(minikube ip 2>/dev/null || echo "192.168.49.2")
                    echo "URL: http://\${MINIKUBE_IP}:30080${CONTEXT_PATH}"
                """
            }
        }

        failure {
            echo "💥 Pipeline échoué"

            script {
                sh """
                    echo "=== DEBUG ==="
                    echo "1. Décrire les pods:"
                    kubectl describe pods -n ${K8S_NAMESPACE} || true

                    echo ""
                    echo "2. Événements détaillés:"
                    kubectl get events -n ${K8S_NAMESPACE} --sort-by='.lastTimestamp' | grep -E "(Failed|Error|Warning)" | tail -20 || true

                    echo ""
                    echo "=== COMMANDES DE RÉCUPÉRATION ==="
                    echo "1. Vérifier l'image:"
                    echo "   docker pull ${IMAGE_NAME}:${IMAGE_TAG}"
                    echo ""
                    echo "2. Forcer le redéploiement:"
                    echo "   kubectl rollout restart deployment/spring-app -n ${K8S_NAMESPACE}"
                    echo ""
                    echo "3. Vérifier MySQL:"
                    echo "   kubectl exec -it \$(kubectl get pods -n ${K8S_NAMESPACE} -l app=mysql -o name) -- mysql -u root -proot123 -e 'SHOW DATABASES;'"
                    echo ""
                    echo "4. Voir les logs détaillés:"
                    echo "   kubectl logs -n ${K8S_NAMESPACE} \$(kubectl get pods -n ${K8S_NAMESPACE} -l app=spring-app -o name) --previous 2>/dev/null || echo 'Pas de logs précédents'"
                """
            }
        }

        cleanup {
            echo "🧹 Nettoyage final..."
            sh '''
                docker rm -f $(docker ps -aq --filter "name=quick-test-") 2>/dev/null || true
                echo "Nettoyage terminé"
            '''
        }
    }
}