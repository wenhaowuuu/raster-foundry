#!groovy

node {
  try {
    // Checkout the proper revision into the workspace.
    stage('checkout') {
      checkout([
        $class: 'GitSCM',
        branches: scm.branches,
        extensions: scm.extensions + [[$class: 'CloneOption', noTags: false, reference: '', shallow: false]],
        userRemoteConfigs: scm.userRemoteConfigs
      ])
    }

    env.AWS_DEFAULT_REGION = 'us-east-1'
    env.RF_ARTIFACTS_BUCKET = 'rasterfoundry-global-artifacts-us-east-1'

    // Execute `cibuild` wrapped within a plugin that translates
    // ANSI color codes to something that renders inside the Jenkins
    // console.
    stage('cibuild') {
      env.RF_SETTINGS_BUCKET = 'rasterfoundry-testing-config-us-east-1'

      wrap([$class: 'AnsiColorBuildWrapper']) {
        sh 'scripts/cibuild --bootstrap'
      }
      
      def max_concurrent_tasks = 2
      def lock_label = "${env.BUILD_ID}-cibuild"
           
      def task_names = [
        "static-asset-bundle",
        "migrations",
        "batch",
        "api",
        "tile",
        "backsplash"
      ]
      
      def tasks = [:]
      
      for(task in task_names) {
        tasks["$task"] = {
          lock(label: lock_label, quantity: max_concurrent_tasks) {
            wrap([$class: 'AnsiColorBuildWrapper']) {
              sh "scripts/cibuild --$task"
            }
          }
        }
      }
      
      parallel tasks
      
      wrap([$class: 'AnsiColorBuildWrapper']) {
        sh 'scripts/cibuild --tests'
      }
    }

    // Publish container images built and tested during `cibuild`
    // to the private Amazon Container Registry tagged with the
    // first seven characters of the revision SHA.
    stage('cipublish') {        
      parallel publishJars: {
        // Decode the `AWS_ECR_ENDPOINT` credential stored within
        // Jenkins. In includes the Amazon ECR registry endpoint.
        withCredentials([[$class: 'StringBinding',
                          credentialsId: 'AWS_ECR_ENDPOINT',
                          variable: 'AWS_ECR_ENDPOINT'], 
                          [$class: 'StringBinding',
                          credentialsId: 'SONATYPE_USERNAME',
                          variable: 'SONATYPE_USERNAME'],
                          [$class: 'StringBinding',
                          credentialsId: 'SONATYPE_PASSWORD',
                          variable: 'SONATYPE_PASSWORD'],
                          [$class: 'StringBinding',
                          credentialsId: 'PGP_HEX_KEY',
                          variable: 'PGP_HEX_KEY'],
                          [$class: 'StringBinding',
                          credentialsId: 'PGP_PASSPHRASE',
                          variable: 'PGP_PASSPHRASE']]) {
          wrap([$class: 'AnsiColorBuildWrapper']) {
            sh './scripts/cipublish --jars'
          }
        }          
      }, publishContainers: {
        // Decode the `AWS_ECR_ENDPOINT` credential stored within
        // Jenkins. In includes the Amazon ECR registry endpoint.
        withCredentials([[$class: 'StringBinding',
                          credentialsId: 'AWS_ECR_ENDPOINT',
                          variable: 'AWS_ECR_ENDPOINT'], 
                          [$class: 'StringBinding',
                          credentialsId: 'SONATYPE_USERNAME',
                          variable: 'SONATYPE_USERNAME'],
                          [$class: 'StringBinding',
                          credentialsId: 'SONATYPE_PASSWORD',
                          variable: 'SONATYPE_PASSWORD'],
                          [$class: 'StringBinding',
                          credentialsId: 'PGP_HEX_KEY',
                          variable: 'PGP_HEX_KEY'],
                          [$class: 'StringBinding',
                          credentialsId: 'PGP_PASSPHRASE',
                          variable: 'PGP_PASSPHRASE']]) {
          wrap([$class: 'AnsiColorBuildWrapper']) {
            sh './scripts/cipublish --containers'
          }
        }
      }
    }
  } catch (err) {
    // Some exception was raised in the `try` block above. Assemble
    // an appropirate error message for Slack.
    def slackMessage = ":jenkins-angry: *raster-foundry (${env.BRANCH_NAME}) #${env.BUILD_NUMBER}*"
    if (env.CHANGE_TITLE) {
      slackMessage += "\n${env.CHANGE_TITLE} - ${env.CHANGE_AUTHOR}"
    }
    slackMessage += "\n<${env.BUILD_URL}|View Build>"
    // slackSend color: 'danger', message: slackMessage

    // Re-raise the exception so that the failure is propagated to
    // Jenkins.
    throw err
  } finally {
    // Pass or fail, ensure that the services and networks
    // created by Docker Compose are torn down.
    sh 'docker-compose down -v --remove-orphans'
  }
}
