#!groovy

node {
  try {
    // Checkout the proper revision into the workspace.
    stage('checkout') {
      checkout scm
    }

    env.AWS_DEFAULT_REGION = 'us-east-1'
    env.RF_ARTIFACTS_BUCKET = 'rasterfoundry-global-artifacts-us-east-1'

    // Execute `cibuild` wrapped within a plugin that translates
    // ANSI color codes to something that renders inside the Jenkins
    // console.

    env.RF_SETTINGS_BUCKET = 'rasterfoundry-staging-config-us-east-1'

    if (env.BRANCH_NAME == 'develop' || env.BRANCH_NAME =~ /test\//) {
        env.RF_DOCS_BUCKET = 'rasterfoundry-staging-docs-site-us-east-1'
        env.RF_DEPLOYMENT_BRANCH = 'develop'
        env.RF_DEPLOYMENT_ENVIRONMENT = "Staging"

      // Publish container images built and tested during `cibuild`
      // to the private Amazon Container Registry tagged with the
      // first seven characters of the revision SHA.
      stage('cipublish') {
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
            sh './scripts/cipublish'
          }
        }
      }
    }
  } catch (err) {
    // Re-raise the exception so that the failure is propagated to
    // Jenkins.
    throw err
  } finally {
    // Pass or fail, ensure that the services and networks
    // created by Docker Compose are torn down.
    sh 'docker-compose down -v --remove-orphans'
  }
}
