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
        // credentials() 会自动将账号密码注入为 shell 环境变量
        // CG_CREDS_USR = 账号, CG_CREDS_PSW = 密码（日志中自动屏蔽）
        CG_CREDS = credentials('codeguardian-credentials')
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

        stage('Login to CodeGuardian') {
            steps {
                script {
                    // 全程使用 single-quote shell('''...''')，让 shell 解析 $变量
                    // 避免 Groovy 插值 credentials，消除安全警告和语法错误
                    env.CG_TOKEN = sh(
                        script: '''
                            BODY=$(printf '{"usernameOrEmail":"%s","password":"%s"}' "$CG_CREDS_USR" "$CG_CREDS_PSW")
                            LOGIN_RESP=$(curl -s -X POST "$CODE_GUARDIAN_URL/api/auth/login" \
                                -H "Content-Type: application/json" \
                                -d "$BODY")
                            echo "登录响应: $LOGIN_RESP" >&2
                            echo "$LOGIN_RESP" | grep -o '"token":"[^"]*"' | grep -o ':"[^"]*"' | tr -d ':"'
                        ''',
                        returnStdout: true
                    ).trim()

                    if (!env.CG_TOKEN) {
                        error("登录失败，未获取到 Token，请检查 codeguardian-credentials 中账号密码是否正确")
                    }
                    echo "登录成功"
                }
            }
        }

        stage('Trigger Code Review') {
            steps {
                script {
                    // GIT_URL_FULL / GIT_BRANCH / GIT_COMMIT_SHA / CG_TOKEN 均为 env 变量
                    // 在 single-quote shell 中直接用 $变量名 引用
                    def response = sh(
                        script: '''
                            BODY=$(printf '{"gitUrl":"%s","branch":"%s","commitHash":"%s","triggerBy":"JENKINS","blockOn":"%s"}' \
                                "$GIT_URL_FULL" "$GIT_BRANCH" "$GIT_COMMIT_SHA" "$BLOCK_ON")
                            curl -s -X POST "$CODE_GUARDIAN_URL/api/v1/cicd/trigger" \
                                -H "Content-Type: application/json" \
                                -H "satoken: $CG_TOKEN" \
                                -d "$BODY"
                        ''',
                        returnStdout: true
                    ).trim()
                    echo "触发响应: ${response}"

                    env.REVIEW_TASK_ID = sh(
                        script: "echo '${response}' | grep -o '\"taskId\":[0-9]*' | grep -o '[0-9]*'",
                        returnStdout: true
                    ).trim()
                    echo "审查任务已提交，Task ID: ${env.REVIEW_TASK_ID}"

                    if (!env.REVIEW_TASK_ID) {
                        error("未能获取 Task ID，请检查 CodeGuardian 服务日志")
                    }
                }
            }
        }

        stage('Wait for Review') {
            steps {
                script {
                    timeout(time: env.POLL_TIMEOUT_MIN.toInteger(), unit: 'MINUTES') {
                        waitUntil(initialRecurrencePeriod: env.POLL_INTERVAL_SEC.toInteger() * 1000) {
                            def resp = sh(
                                script: '''
                                    curl -s "$CODE_GUARDIAN_URL/api/v1/cicd/status/$REVIEW_TASK_ID?blockOn=$BLOCK_ON" \
                                        -H "satoken: $CG_TOKEN"
                                ''',
                                returnStdout: true
                            ).trim()

                            def status = sh(
                                script: "echo '${resp}' | grep -o '\"status\":\"[^\"]*\"' | head -1 | grep -o ':\"[^\"]*\"' | tr -d ':\"'",
                                returnStdout: true
                            ).trim()
                            def message = sh(
                                script: "echo '${resp}' | grep -o '\"message\":\"[^\"]*\"' | grep -o ':\"[^\"]*\"' | tr -d ':\"'",
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
                    def resp = sh(
                        script: '''
                            curl -s "$CODE_GUARDIAN_URL/api/v1/cicd/status/$REVIEW_TASK_ID?blockOn=$BLOCK_ON" \
                                -H "satoken: $CG_TOKEN"
                        ''',
                        returnStdout: true
                    ).trim()
                    echo "最终审查结果: ${resp}"

                    def passed   = sh(script: "echo '${resp}' | grep -o '\"passed\":[a-z]*' | grep -o '[a-z]*\$'", returnStdout: true).trim()
                    def critical = sh(script: "echo '${resp}' | grep -o '\"critical\":[0-9]*' | grep -o '[0-9]*'", returnStdout: true).trim() ?: '0'
                    def high     = sh(script: "echo '${resp}' | grep -o '\"high\":[0-9]*' | grep -o '[0-9]*'",     returnStdout: true).trim() ?: '0'
                    def medium   = sh(script: "echo '${resp}' | grep -o '\"medium\":[0-9]*' | grep -o '[0-9]*'",   returnStdout: true).trim() ?: '0'
                    def low      = sh(script: "echo '${resp}' | grep -o '\"low\":[0-9]*' | grep -o '[0-9]*'",      returnStdout: true).trim() ?: '0'

                    echo "审查结果: ${passed == 'true' ? '✅ 通过' : '❌ 未通过'} | C:${critical} H:${high} M:${medium} L:${low}"
                    currentBuild.description = "${passed == 'true' ? '✅' : '❌'} C:${critical} H:${high} M:${medium} L:${low}"

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
