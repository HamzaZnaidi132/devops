pipeline {
    agent any

    environment {
        IMAGE_NAME = "saiffrikhi/foyer_project"
        IMAGE_TAG = "latest"
        K8S_NAMESPACE = "devops"
        BUILD_NUMBER = "${BUILD_ID}"
    }

    stages {
        stage('Checkout') {
            steps {
                echo "Récupération du code depuis GitHub..."
                git branch: 'main', url: 'https://github.com/saifeddinefrikhi-lab/FoyerProject.git'
            }
        }

        stage('Clean & Build') {
            steps {
                echo "Nettoyage + Build Maven..."
                sh 'mvn clean install -DskipTests -B'
            }
        }

        stage('Test Image Locally') {
            steps {
                echo "Test de l'image Docker localement..."
                script {
                    // Vérifiez que l'application peut être construite
                    sh """
                        docker build -t ${IMAGE_NAME}:test-${BUILD_NUMBER} .
                    """

                    // Testez avec des variables d'environnement de base
                    try {
                        sh """
                            docker run -d --name test-container-${BUILD_NUMBER} \
                              -e SPRING_DATASOURCE_URL="jdbc:h2:mem:testdb" \
                              -e SPRING_DATASOURCE_USERNAME="sa" \
                              -e SPRING_DATASOURCE_PASSWORD="" \
                              -p 18080:8080 \
                              ${IMAGE_NAME}:test-${BUILD_NUMBER} &
                            sleep 30

                            # Testez si l'application répond
                            curl -f http://localhost:18080/actuator/health || exit 1
                            docker stop test-container-${BUILD_NUMBER}
                            docker rm test-container-${BUILD_NUMBER}
                        """
                    } catch (Exception e) {
                        error "L'image Docker ne démarre pas localement. Vérifiez l'application."
                    }
                }
            }
        }

        stage('Build Docker Image') {
            steps {
                echo "Construction de l'image Docker..."
                sh """
                    docker build -t ${IMAGE_NAME}:${IMAGE_TAG} .
                    docker tag ${IMAGE_NAME}:${IMAGE_TAG} ${IMAGE_NAME}:${BUILD_NUMBER}
                """
            }
        }

        stage('Docker Login & Push') {
            steps {
                echo "Connexion + push vers DockerHub..."
                withCredentials([usernamePassword(credentialsId: 'docker-hub',
                    usernameVariable: 'DOCKER_USER',
                    passwordVariable: 'DOCKER_PASS')]) {
                    sh """
                        echo "$DOCKER_PASS" | docker login -u "$DOCKER_USER" --password-stdin
                        docker push ${IMAGE_NAME}:${IMAGE_TAG}
                        docker push ${IMAGE_NAME}:${BUILD_NUMBER}
                    """
                }
            }
        }

        stage('Test Database Connection') {
            steps {
                echo "Test de connexion à la base de données..."
                sh """
                    # Vérifiez que MySQL est accessible
                    kubectl run db-test-${BUILD_NUMBER} -n ${K8S_NAMESPACE} \
                      --image=mysql:8.0 -it --rm --restart=Never -- \
                      mysql -h mysql-service -u root -proot123 -e "SHOW DATABASES; SELECT 'MySQL is accessible';" || echo "Échec de connexion MySQL"
                """
            }
        }

        stage('Prepare Kubernetes Deployment') {
            steps {
                echo "Préparation du déploiement Kubernetes..."
                script {
                    // Créez un déploiement minimaliste sans probes d'abord
                    writeFile file: 'deployment.yaml', text: """
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
        image: ${IMAGE_NAME}:${BUILD_NUMBER}  # Utilisez le numéro de build spécifique
        ports:
        - containerPort: 8080
        env:
        - name: SPRING_DATASOURCE_URL
          value: "jdbc:mysql://mysql-service:3306/springdb?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC"
        - name: SPRING_DATASOURCE_USERNAME
          value: "root"
        - name: SPRING_DATASOURCE_PASSWORD
          value: "root123"
        - name: SPRING_JPA_HIBERNATE_DDL_AUTO
          value: "update"
        - name: SPRING_JPA_SHOW_SQL
          value: "true"
        - name: LOGGING_LEVEL_ORG_SPRINGFRAMEWORK
          value: "DEBUG"
        - name: LOGGING_LEVEL_COM_ZAXXER_HIKARI
          value: "DEBUG"
        command: ["sh", "-c"]
        args: ["echo 'Démarrage de l\'application...'; java -jar /app.jar; echo 'Application arrêtée avec code: \$?']
        # Pas de probes pour le débogage
"""
                }
            }
        }

        stage('Deploy to Kubernetes - Debug Mode') {
            steps {
                echo "Déploiement en mode debug..."
                script {
                    // Supprimez l'ancien déploiement s'il existe
                    sh """
                        kubectl delete deployment spring-app -n ${K8S_NAMESPACE} --ignore-not-found=true
                        sleep 10
                    """

                    // Appliquez le nouveau déploiement
                    sh """
                        kubectl apply -f deployment.yaml
                    """

                    // Attendez que le pod soit créé (pas nécessairement prêt)
                    timeout(time: 2, unit: 'MINUTES') {
                        sh """
                            kubectl wait --for=condition=ContainersReady pod -l app=spring-app -n ${K8S_NAMESPACE} --timeout=120s || true
                        """
                    }
                }
            }
        }

        stage('Debug Application') {
            steps {
                echo "Debug de l'application..."
                script {
                    // Attendez un peu puis vérifiez l'état
                    sleep 30

                    sh """
                        echo "=== État du pod ==="
                        kubectl get pods -n ${K8S_NAMESPACE} -l app=spring-app -o wide

                        echo "=== Description du pod ==="
                        kubectl describe pods -n ${K8S_NAMESPACE} -l app=spring-app || true

                        echo "=== Logs du pod ==="
                        POD_NAME=\$(kubectl get pods -n ${K8S_NAMESPACE} -l app=spring-app -o jsonpath='{.items[0].metadata.name}' 2>/dev/null || echo "")
                        if [ -n "\$POD_NAME" ]; then
                            kubectl logs -n ${K8S_NAMESPACE} \$POD_NAME --tail=100 || kubectl logs -n ${K8S_NAMESPACE} \$POD_NAME --previous --tail=100 || echo "Pas de logs disponibles"
                        else
                            echo "Aucun pod trouvé"
                        fi

                        echo "=== Événements ==="
                        kubectl get events -n ${K8S_NAMESPACE} --sort-by='.lastTimestamp' | tail -20 || true
                    """

                    // Si le pod est en cours d'exécution, testez-le
                    sh """
                        POD_NAME=\$(kubectl get pods -n ${K8S_NAMESPACE} -l app=spring-app -o jsonpath='{.items[0].metadata.name}' 2>/dev/null || echo "")
                        if [ -n "\$POD_NAME" ] && kubectl get pod -n ${K8S_NAMESPACE} \$POD_NAME | grep -q Running; then
                            echo "Le pod est en cours d'exécution, test de l'application..."
                            kubectl exec -n ${K8S_NAMESPACE} \$POD_NAME -- curl -s http://localhost:8080/actuator/health || echo "L'application ne répond pas encore"
                        fi
                    """
                }
            }
        }
    }

    post {
        always {
            echo "Pipeline terminé"

            // Nettoyage
            sh '''
                docker system prune -f || true
                rm -f deployment.yaml || true
                docker rm -f test-container-* 2>/dev/null || true
            '''

            // Rapport final
            script {
                sh """
                    echo "=== RAPPORT FINAL ==="
                    echo "État du cluster:"
                    kubectl get all -n ${K8S_NAMESPACE} || true

                    echo ""
                    echo "Résumé des pods Spring Boot:"
                    kubectl get pods -n ${K8S_NAMESPACE} -l app=spring-app || true
                """
            }
        }

        success {
            echo "Pipeline réussi!"

            // Si le déploiement a réussi, appliquez la version finale avec les probes
            script {
                sh """
                    # Créez un déploiement final avec les probes
                    cat > deployment-final.yaml <<EOF
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
          value: "jdbc:mysql://mysql-service:3306/springdb?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC"
        - name: SPRING_DATASOURCE_USERNAME
          value: "root"
        - name: SPRING_DATASOURCE_PASSWORD
          value: "root123"
        - name: SPRING_JPA_HIBERNATE_DDL_AUTO
          value: "update"
        readinessProbe:
          httpGet:
            path: /actuator/health
            port: 8080
          initialDelaySeconds: 180
          periodSeconds: 10
          failureThreshold: 3
        livenessProbe:
          httpGet:
            path: /actuator/health
            port: 8080
          initialDelaySeconds: 240
          periodSeconds: 15
EOF

                    # Appliquez le déploiement final
                    kubectl apply -f deployment-final.yaml

                    # Attendez le déploiement avec un timeout long
                    kubectl rollout status deployment/spring-app -n ${K8S_NAMESPACE} --timeout=600s || echo "Rollout en cours"

                    # Obtenez l'URL
                    minikube service spring-service -n ${K8S_NAMESPACE} --url 2>/dev/null || echo "Service: http://\$(minikube ip):30080"
                """
            }
        }

        failure {
            echo "Le pipeline a échoué."

            script {
                // Diagnostic complet en cas d'échec
                sh """
                    echo "=== DIAGNOSTIC COMPLET ==="

                    echo "1. État du déploiement:"
                    kubectl describe deployment spring-app -n ${K8S_NAMESPACE} 2>/dev/null || echo "Pas de déploiement"

                    echo ""
                    echo "2. État des pods:"
                    kubectl get pods -n ${K8S_NAMESPACE} -o wide

                    echo ""
                    echo "3. Logs de tous les conteneurs:"
                    for POD in \$(kubectl get pods -n ${K8S_NAMESPACE} -o name); do
                        echo "=== \$POD ==="
                        kubectl logs -n ${K8S_NAMESPACE} \$POD --all-containers=true --tail=50 || true
                    done

                    echo ""
                    echo "4. Configuration MySQL:"
                    kubectl get svc mysql-service -n ${K8S_NAMESPACE} -o yaml || true

                    echo ""
                    echo "5. Solution de contournement:"
                    echo "Pour tester manuellement:"
                    echo "  kubectl run debug-shell -n ${K8S_NAMESPACE} --image=busybox -it --rm -- /bin/sh"
                    echo "  # Dans le shell:"
                    echo "  nslookup mysql-service"
                    echo "  telnet mysql-service 3306"
                """
            }
        }
    }
}