pipeline {
    agent any

    environment {
        // === 后端配置（用 Commit SHA 做标签，避免覆盖产生悬空） ===
        GIT_COMMIT_SHORT = sh(script: 'git rev-parse --short HEAD', returnStdout: true).trim()
        BACKEND_IMAGE = "myapp-backend:${env.GIT_COMMIT?.take(7) ?: 'unknown'}"
        JAR_NAME = 'Automatic_test_script-1.0-SNAPSHOT.jar'

        // === 部署配置 ===
        DEPLOY_DIR = '/opt/traffic-data-system-YQ5287476'
        COMPOSE_FILE = 'docker-compose.cicd.yml'

        // === 缓存目录 ===
        MAVEN_CACHE = '/opt/jenkins-cache/.m2/repository'
        NPM_CACHE = '/opt/jenkins-cache/node_modules'

        // === 启用 BuildKit ===
        DOCKER_BUILDKIT = '1'
    }

    parameters {
        choice(name: 'DEPLOY_SCOPE', choices: ['all', 'backend', 'frontend'], description: '选择部署范围')
    }

    stages {
        stage('1. 拉取代码') {
    steps {
        script {
            env.GIT_COMMIT_SHORT = sh(
                script: 'git rev-parse --short HEAD',
                returnStdout: true
            ).trim()

            env.BACKEND_IMAGE = "myapp-backend:${env.GIT_COMMIT_SHORT}"
        }

        echo '>>> 重置工作区...'
        sh 'git reset --hard HEAD'
        sh 'git clean -fd'
        checkout scm
        echo '>>> 当前提交：'
        sh 'git log -1 --format=fuller'
    }
}

        stage('2. 并行构建前后端') {
            parallel {
                stage('后端构建') {
                    steps {
                        dir('backend') {
                            echo '>>> 构建后端 JAR 包...'
                            sh """
                                mkdir -p ${MAVEN_CACHE}
                                mvn clean package -DskipTests \\
                                    -Dmaven.repo.local=${MAVEN_CACHE}
                            """

                            echo '>>> 构建 Docker 镜像（标签: ${GIT_COMMIT_SHORT}）...'
                            sh """
                                docker build --force-rm \
                                    -t ${BACKEND_IMAGE} \
                                    -t myapp-backend:latest .
                            """
                            // 同时打 latest 标签方便 Compose 引用，但保留 Commit 标签用于追溯
                        }
                    }
                }

                stage('前端构建') {
                    steps {
                        dir('frontend') {
                            echo '>>> 安装前端依赖...'
                            sh """
                                if [ -d "${NPM_CACHE}" ] && [ -s "${NPM_CACHE}/.package-lock.json" ]; then
                                    echo "缓存命中"
                                    cp -r ${NPM_CACHE}/* ./node_modules/ 2>/dev/null || true
                                fi
                                npm install
                                mkdir -p ${NPM_CACHE}
                                cp -rf node_modules/* ${NPM_CACHE}/
                            """

                            echo '>>> 构建前端...'
                            sh 'npm run build'
                            sh '''
                                mkdir -p ../nginx/html/dist
                                cp -rf dist/* ../nginx/html/dist/
                            '''
                        }
                    }
                }
            }
        }

        stage('3. 部署') {
            steps {
                echo '>>> 准备部署目录...'
                sh """
    mkdir -p ${DEPLOY_DIR}/persistent-data/{mysql,redis,rabbitmq,export,nginx-logs}
    mkdir -p ${DEPLOY_DIR}/mysql/initsql
    cp -f docker-compose.cicd.yml ${DEPLOY_DIR}/
    cp -rf nginx ${DEPLOY_DIR}/    # ← 直接整个目录覆盖，简单粗暴
    cp -rf mysql ${DEPLOY_DIR}/ 2>/dev/null || true
    cp -rf redis ${DEPLOY_DIR}/ 2>/dev/null || true
"""
                echo '>>> 生成 .env...'
withCredentials([
    string(credentialsId: 'tds-mysql-root-pwd',  variable: 'MYSQL_ROOT_PWD'),
    string(credentialsId: 'tds-mysql-user-pwd',  variable: 'MYSQL_USER_PWD'),
    string(credentialsId: 'tds-rabbitmq-user',   variable: 'RABBITMQ_USER_VAL'),
    string(credentialsId: 'tds-rabbitmq-pwd',    variable: 'RABBITMQ_PWD')
]) {
    sh """
        cat > ${DEPLOY_DIR}/.env << ENVEOF
MYSQL_ROOT_PASSWORD=${MYSQL_ROOT_PWD}
MYSQL_USER=remote_user
MYSQL_PASSWORD=${MYSQL_USER_PWD}
RABBITMQ_USER=${RABBITMQ_USER_VAL}
RABBITMQ_PASSWORD=${RABBITMQ_PWD}
ENVEOF
        chmod 600 ${DEPLOY_DIR}/.env
    """
}

                echo ">>> 部署范围: [${params.DEPLOY_SCOPE}]..."
dir("${DEPLOY_DIR}") {
    sh """
        rm -rf persistent-data/nginx-logs/*

        if [ "${params.DEPLOY_SCOPE}" = "frontend" ] || [ "${params.DEPLOY_SCOPE}" = "all" ]; then
            # 方案一（推荐）：一键全量重建所有容器，最省心，完全避开服务名匹配问题
            docker compose -f ${COMPOSE_FILE} up -d --build
            
            # 方案二（如果你想精准控制）：只重建 nginx 和 backend，注意这里用的是 service name: nginx 和 backend
            # docker compose -f ${COMPOSE_FILE} up -d --build --no-deps nginx backend
        fi
        
        # 如果你只想部署前端，并且不想影响后端，可以用这个：
        # if [ "${params.DEPLOY_SCOPE}" = "frontend" ]; then
        #     docker compose -f ${COMPOSE_FILE} up -d --build --no-deps nginx
        # fi
        # if [ "${params.DEPLOY_SCOPE}" = "backend" ]; then
        #     docker compose -f ${COMPOSE_FILE} up -d --build --no-deps backend
        # fi
    """
}

echo '>>> 部署完成！'
            }
        }
    }

    always {
        echo '>>> 智能清理 Docker 资源...'
        sh '''
            # 1. 清理构建缓存和停止的容器
            docker builder prune -f --filter "until=24h" || true
            docker container prune -f || true

            # 2. 智能清理镜像：保留最新的 3 个 myapp-backend 镜像，删除其余的
            echo ">>> 清理旧的 myapp-backend 镜像，仅保留最新 3 个..."
            docker images myapp-backend --format "{{.Tag}} {{.ID}}" | sort | head -n -3 | awk '{print $2}' | xargs -r docker rmi -f || true
            
            # 3. 清理其他悬空镜像
            docker image prune -f || true
        '''
        cleanWs()
    }
    failure {
        echo '❌ 流水线执行失败！'
    }
    success {
        echo "✅ 部署成功！镜像标签: ${env.GIT_COMMIT_SHORT}"
    }
}

