pipeline {
    agent any
    parameters {
        string(name: 'CODE_GUARDIAN_URL', defaultValue: 'http://localhost:7003', description: 'CodeGuardian 服务地址')
        choice(name: 'BLOCK_ON', choices: ['CRITICAL', 'HIGH', 'MEDIUM', 'LOW'], description: '质量门禁级别')
        booleanParam(name: 'FAIL_ON_GATE', defaultValue: true, description: '未通过时构建失败')
    }
    environment {
        POLL_INTERVAL_SEC = '15'
        POLL_TIMEOUT_MIN  = '30'
    }
    stages {
        stage('Checkout') {
            steps {
                checkout scm
                script {
                    env.GIT_URL_FULL   = sh(script: 'git remote get-url origin', returnStdout: true).trim()
                    env.GIT_BRANCH     = env.BRANCH_NAME ?: sh(script: 'git rev-parse --abbrev-ref HEAD', returnStdout: true).trim()
                    env.GIT_COMMIT_SHA = sh(script: 'git rev-parse HEAD', returnStdout: true).trim()
                }
            }
        }
        stage('Trigger Code Review') {
            steps {
                script {
                    def body = groovy.json.JsonOutput.toJson([
                        gitUrl: env.GIT_URL_FULL, branch: env.GIT_BRANCH,
                        commitHash: env.GIT_COMMIT_SHA, triggerBy: 'JENKINS', blockOn: params.BLOCK_ON
                    ])
                    def resp = httpRequest(httpMode: 'POST', url: "${params.CODE_GUARDIAN_URL}/api/v1/cicd/trigger",
                        contentType: 'APPLICATION_JSON', requestBody: body, validResponseCodes: '200')
                    env.REVIEW_TASK_ID = (readJSON text: resp.content).taskId.toString()
                    echo "Task ID: ${env.REVIEW_TASK_ID}"
                }
            }
        }
        stage('Wait for Review') {
            steps {
                script {
                    def url = "${params.CODE_GUARDIAN_URL}/api/v1/cicd/status/${env.REVIEW_TASK_ID}?blockOn=${params.BLOCK_ON}"
                    timeout(time: env.POLL_TIMEOUT_MIN.toInteger(), unit: 'MINUTES') {
                        waitUntil(initialRecurrencePeriod: env.POLL_INTERVAL_SEC.toInteger() * 1000) {
                            def s = readJSON text: httpRequest(httpMode: 'GET', url: url, validResponseCodes: '200').content
                            echo "状态: ${s.status} | ${s.message}"
                            return s.status in ['COMPLETED', 'FAILED']
                        }
                    }
                }
            }
        }
        stage('Quality Gate') {
            steps {
                script {
                    def url    = "${params.CODE_GUARDIAN_URL}/api/v1/cicd/status/${env.REVIEW_TASK_ID}?blockOn=${params.BLOCK_ON}"
                    def result = readJSON text: httpRequest(httpMode: 'GET', url: url, validResponseCodes: '200').content
                    def s = result.summary
                    echo "审查结果: ${result.passed ? '✅ 通过' : '❌ 未通过'} | C:${s?.critical} H:${s?.high} M:${s?.medium} L:${s?.low}"
                    currentBuild.description = "C:${s?.critical} H:${s?.high} M:${s?.medium} L:${s?.low}"
                    if (!result.passed && params.FAIL_ON_GATE) {
                        error("质量门禁未通过：存在 ${params.BLOCK_ON} 级别及以上问题")
                    }
                }
            }
        }
    }
}
