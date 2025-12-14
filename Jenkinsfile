pipeline {
    agent any

    environment {
        IMAGE_NAME = "saiffrikhi/foyer_project"
        IMAGE_TAG = "latest"
        K8S_NAMESPACE = "devops"
    }

    triggers {
        githubPush()
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

        stage('Build Docker Image') {
            steps {
                echo "Construction de l'image Docker..."
                sh "docker build -t ${IMAGE_NAME}:${IMAGE_TAG} ."
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
                    """
                }
            }
        }

        stage('Deploy to Kubernetes') {
            steps {
                echo "Déploiement sur Kubernetes..."

                script {
                    // Créer un fichier de déploiement temporaire
                    writeFile file: 'spring-deployment.yaml', text: """
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
    type: Recreate  # Important pour les applications avec base de données
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
        - name: SPRING_JPA_SHOW_SQL
          value: "true"
        readinessProbe:
          httpGet:
            path: /actuator/health
            port: 8080
          initialDelaySeconds: 90  # Augmenter le délai
          periodSeconds: 10
        livenessProbe:
          httpGet:
            path: /actuator/health
            port: 8080
          initialDelaySeconds: 120  # Augmenter le délai
          periodSeconds: 15
        resources:
          requests:
            memory: "512Mi"
            cpu: "250m"
          limits:
            memory: "1024Mi"
            cpu: "500m"
"""

                    // Appliquer le déploiement
                    sh """
                        kubectl apply -f spring-deployment.yaml
                    """

                    // Attendre avec un timeout plus long
                    timeout(time: 5, unit: 'MINUTES') {
                        sh """
                            kubectl rollout status deployment/spring-app -n ${K8S_NAMESPACE}
                        """
                    }
                }
            }
        }

        stage('Verify Deployment') {
            steps {
                echo "Vérification du déploiement..."

                script {
                    // Vérifier l'état des pods
                    sh """
                        kubectl get pods -n ${K8S_NAMESPACE}
                        kubectl get deployments -n ${K8S_NAMESPACE}
                        kubectl get svc -n ${K8S_NAMESPACE}
                    """

                    // Obtenir les logs en cas de succès partiel
                    sh """
                        echo "=== Logs des pods Spring Boot ==="
                        kubectl logs -n ${K8S_NAMESPACE} -l app=spring-app --tail=100 || echo "Impossible de récupérer les logs"

                        echo "=== Événements ==="
                        kubectl get events -n ${K8S_NAMESPACE} --sort-by='.lastTimestamp' | tail -20 || echo "Impossible de récupérer les événements"
                    """
                }
            }
        }
    }

    post {
        always {
            echo "Pipeline terminé"
            // Nettoyage
            sh 'docker system prune -f'

            // Nettoyer les fichiers temporaires
            sh 'rm -f spring-deployment.yaml || true'
        }
        success {
            echo "Build et déploiement effectués avec succès!"

            // Obtenir l'URL du service
            script {
                def SERVICE_URL = sh(
                    script: "minikube service spring-service -n ${K8S_NAMESPACE} --url 2>/dev/null || echo 'Service non disponible'",
                    returnStdout: true
                ).trim()
                echo "Application disponible à: ${SERVICE_URL}"
            }
        }
        failure {
            echo "Le pipeline a échoué."

            // Diagnostic détaillé en cas d'échec
            script {
                sh """
                    echo "=== DIAGNOSTIC DÉTAILLÉ ==="
                    echo "=== Description du déploiement ==="
                    kubectl describe deployment spring-app -n ${K8S_NAMESPACE} || true

                    echo "=== Description des pods ==="
                    kubectl describe pods -n ${K8S_NAMESPACE} -l app=spring-app || true

                    echo "=== Logs complets (si disponible) ==="
                    kubectl logs -n ${K8S_NAMESPACE} -l app=spring-app --all-containers=true --tail=200 || true
                """
            }
        }
    }
}