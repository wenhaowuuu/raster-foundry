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
      
     def build_task_names = [
        "static-asset-bundle",
        "migrations",
        "batch",
        "api",
        "tile",
        "backsplash"
      ]
      
      def build_tasks = [:]

      for (build_task_name in build_task_names) {
        def name = "$build_task_name"
        build_tasks[name] = {
          wrap([$class: 'AnsiColorBuildWrapper']) {
            sh "scripts/cibuild --$name"
          }
        }
      }

      throttle(['cibuild']) {
        parallel build_tasks
      }
 
      wrap([$class: 'AnsiColorBuildWrapper']) {
        sh 'scripts/cibuild --tests'
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
