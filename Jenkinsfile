pipeline {
    agent any
    parameters {
        string(name: 'CODE_GUARDIAN_URL', defaultValue: 'http://host.docker.internal:7003', description: 'CodeGuardian 服务地址')
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
                    echo "仓库: ${env.GIT_URL_FULL} | 分支: ${env.GIT_BRANCH} | 提交: ${env.GIT_COMMIT_SHA}"
                }
            }
        }

        stage('Trigger Code Review') {
            steps {
                script {
                    def body = """{"gitUrl":"${env.GIT_URL_FULL}","branch":"${env.GIT_BRANCH}","commitHash":"${env.GIT_COMMIT_SHA}","triggerBy":"JENKINS","blockOn":"${params.BLOCK_ON}"}"""
                    def response = sh(
                        script: """curl -s -X POST "${params.CODE_GUARDIAN_URL}/api/v1/cicd/trigger" \
                            -H "Content-Type: application/json" \
                            -d '${body}'""",
                        returnStdout: true
                    ).trim()
                    echo "触发响应: ${response}"

                    // 解析 taskId（用 grep/sed 提取，不依赖插件）
                    env.REVIEW_TASK_ID = sh(
                        script: """echo '${response}' | grep -o '"taskId":[0-9]*' | grep -o '[0-9]*'""",
                        returnStdout: true
                    ).trim()
                    echo "审查任务已提交，Task ID: ${env.REVIEW_TASK_ID}"

                    if (!env.REVIEW_TASK_ID) {
                        error("未能获取 Task ID，请检查 CodeGuardian 服务是否正常运行")
                    }
                }
            }
        }

        stage('Wait for Review') {
            steps {
                script {
                    def statusUrl = "${params.CODE_GUARDIAN_URL}/api/v1/cicd/status/${env.REVIEW_TASK_ID}?blockOn=${params.BLOCK_ON}"
                    def timeoutMin = env.POLL_TIMEOUT_MIN.toInteger()
                    def intervalSec = env.POLL_INTERVAL_SEC.toInteger()

                    timeout(time: timeoutMin, unit: 'MINUTES') {
                        waitUntil(initialRecurrencePeriod: intervalSec * 1000) {
                            def resp = sh(
                                script: """curl -s "${statusUrl}" """,
                                returnStdout: true
                            ).trim()

                            def status = sh(
                                script: """echo '${resp}' | grep -o '"status":"[^"]*"' | head -1 | grep -o ':[^}]*' | tr -d ':"'""",
                                returnStdout: true
                            ).trim()

                            def message = sh(
                                script: """echo '${resp}' | grep -o '"message":"[^"]*"' | grep -o ':[^}]*' | tr -d ':"'""",
                                returnStdout: true
                            ).trim()

                            echo "当前状态: ${status} | ${message}"
                            return (status == 'COMPLETED' || status == 'FAILED')
                        }
                    }
                }
            }
        }

        stage('Quality Gate') {
            steps {
                script {
                    def statusUrl = "${params.CODE_GUARDIAN_URL}/api/v1/cicd/status/${env.REVIEW_TASK_ID}?blockOn=${params.BLOCK_ON}"
                    def resp = sh(script: """curl -s "${statusUrl}" """, returnStdout: true).trim()
                    echo "最终审查结果: ${resp}"

                    def passed = sh(
                        script: """echo '${resp}' | grep -o '"passed":[a-z]*' | grep -o '[a-z]*\$'""",
                        returnStdout: true
                    ).trim()

                    def critical = sh(script: """echo '${resp}' | grep -o '"critical":[0-9]*' | grep -o '[0-9]*'""", returnStdout: true).trim() ?: '0'
                    def high     = sh(script: """echo '${resp}' | grep -o '"high":[0-9]*' | grep -o '[0-9]*'""",     returnStdout: true).trim() ?: '0'
                    def medium   = sh(script: """echo '${resp}' | grep -o '"medium":[0-9]*' | grep -o '[0-9]*'""",   returnStdout: true).trim() ?: '0'
                    def low      = sh(script: """echo '${resp}' | grep -o '"low":[0-9]*' | grep -o '[0-9]*'""",      returnStdout: true).trim() ?: '0'

                    echo "审查结果: ${passed == 'true' ? '✅ 通过' : '❌ 未通过'} | C:${critical} H:${high} M:${medium} L:${low}"
                    currentBuild.description = "C:${critical} H:${high} M:${medium} L:${low}"

                    if (passed != 'true' && params.FAIL_ON_GATE) {
                        error("质量门禁未通过：存在 ${params.BLOCK_ON} 级别及以上问题")
                    }
                }
            }
        }
    }

    post {
        failure {
            echo "构建失败，请查看 CodeGuardian 审查报告"
        }
        always {
            echo "Pipeline 结束，Task ID: ${env.REVIEW_TASK_ID ?: '未获取'}"
        }
    }
}
