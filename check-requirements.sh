#!/bin/bash
#!/bin/bash

echo "============================================"
echo " VERIFICACIÓN DE REQUISITOS DEL PROYECTO"
echo "============================================"
echo ""

# Colores para output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

check_command() {
    local cmd=$1
    local name=$2
    local install_hint=$3

    if command -v $cmd &> /dev/null; then
        local version=$($cmd --version 2>&1 | head -n 1)
        echo -e "${GREEN}✅ $name está instalado${NC}"
        echo "   Versión: $version"
        return 0
    else
        echo -e "${RED}❌ $name NO está instalado${NC}"
        echo -e "   ${YELLOW}Instalación: $install_hint${NC}"
        return 1
    fi
    echo ""
}

check_docker_running() {
    if docker info &> /dev/null; then
        echo -e "${GREEN}✅ Docker daemon está corriendo${NC}"
        return 0
    else
        echo -e "${RED}❌ Docker daemon NO está corriendo${NC}"
        echo -e "   ${YELLOW}Inicia Docker Desktop o ejecuta: sudo systemctl start docker${NC}"
        return 1
    fi
    echo ""
}

check_docker_compose_version() {
    if docker compose version &> /dev/null 2>&1; then
        local version=$(docker compose version)
        echo -e "${GREEN}✅ Docker Compose (plugin) está disponible${NC}"
        echo "   Versión: $version"
        return 0
    elif command -v docker-compose &> /dev/null; then
        local version=$(docker-compose --version)
        echo -e "${GREEN}✅ Docker Compose (standalone) está disponible${NC}"
        echo "   Versión: $version"
        return 0
    else
        echo -e "${RED}❌ Docker Compose NO está disponible${NC}"
        return 1
    fi
    echo ""
}

check_port() {
    local port=$1
    local service=$2

    if lsof -Pi :$port -sTCP:LISTEN -t >/dev/null 2>&1 || netstat -an 2>/dev/null | grep -q ":$port.*LISTEN"; then
        echo -e "${YELLOW}⚠️  Puerto $port ($service) está en uso${NC}"
        echo "   Necesitas liberar este puerto o detener el servicio que lo usa"
        return 1
    else
        echo -e "${GREEN}✅ Puerto $port ($service) está disponible${NC}"
        return 0
    fi
}

check_disk_space() {
    local required_gb=10
    local available_gb=$(df -BG . | awk 'NR==2 {print $4}' | sed 's/G//')

    if [ "$available_gb" -ge "$required_gb" ]; then
        echo -e "${GREEN}✅ Espacio en disco suficiente: ${available_gb}GB disponibles${NC}"
        return 0
    else
        echo -e "${YELLOW}⚠️  Espacio en disco limitado: ${available_gb}GB disponibles${NC}"
        echo "   Se recomienda al menos ${required_gb}GB"
        return 1
    fi
    echo ""
}

check_memory() {
    if [[ "$OSTYPE" == "darwin"* ]]; then
        # macOS
        local total_mb=$(sysctl -n hw.memsize | awk '{print int($1/1024/1024)}')
    else
        # Linux
        local total_mb=$(free -m | awk 'NR==2 {print $2}')
    fi

    local total_gb=$((total_mb / 1024))
    local required_gb=4

    if [ "$total_gb" -ge "$required_gb" ]; then
        echo -e "${GREEN}✅ Memoria RAM suficiente: ${total_gb}GB${NC}"
        return 0
    else
        echo -e "${YELLOW}⚠️  Memoria RAM limitada: ${total_gb}GB${NC}"
        echo "   Se recomienda al menos ${required_gb}GB para todos los servicios"
        return 1
    fi
    echo ""
}

# Iniciar verificaciones
echo " VERIFICANDO HERRAMIENTAS PRINCIPALES:"
echo "----------------------------------------"

all_ok=true

check_command "docker" "Docker" "https://docs.docker.com/get-docker/" || all_ok=false
echo ""

check_docker_compose_version || all_ok=false
echo ""

if command -v docker &> /dev/null; then
    check_docker_running || all_ok=false
    echo ""
fi

check_command "git" "Git" "https://git-scm.com/downloads" || all_ok=false
echo ""

echo " VERIFICANDO HERRAMIENTAS OPCIONALES:"
echo "----------------------------------------"
echo "(No son necesarias si usas Docker, pero útiles para desarrollo local)"
echo ""

check_command "java" "Java JDK" "https://adoptium.net/"
echo ""

check_command "mvn" "Maven" "https://maven.apache.org/download.cgi"
echo ""

echo " VERIFICANDO PUERTOS DISPONIBLES:"
echo "----------------------------------------"

check_port 8080 "API REST/GraphQL"
check_port 5432 "PostgreSQL"
check_port 9092 "Kafka"
check_port 2181 "Zookeeper"
check_port 9200 "Elasticsearch"
check_port 5601 "Kibana"
check_port 8123 "ClickHouse HTTP"
check_port 9000 "ClickHouse Native"
check_port 5002 "Logstash"
echo ""

echo " VERIFICANDO RECURSOS DEL SISTEMA:"
echo "----------------------------------------"

check_disk_space
echo ""

check_memory
echo ""

echo "============================================"
if [ "$all_ok" = true ]; then
    echo -e "${GREEN}✅ TODOS LOS REQUISITOS ESENCIALES ESTÁN CUMPLIDOS${NC}"
    echo ""
    echo "Puedes ejecutar el proyecto con:"
    echo "  docker-compose up --build"
    echo ""
    echo "O si tienes Docker Compose como plugin:"
    echo "  docker compose up --build"
else
    echo -e "${RED}❌ FALTAN ALGUNOS REQUISITOS ESENCIALES${NC}"
    echo ""
    echo "Por favor, instala las herramientas faltantes antes de continuar."
fi
echo "============================================"
echo "============================================"
echo "🔍 VERIFICACIÓN DE REQUISITOS DEL PROYECTO"
echo "============================================"
echo ""

# Colores para output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

check_command() {
    local cmd=$1
    local name=$2
    local install_hint=$3

    if command -v $cmd &> /dev/null; then
        local version=$($cmd --version 2>&1 | head -n 1)
        echo -e "${GREEN}✅ $name está instalado${NC}"
        echo "   Versión: $version"
        return 0
    else
        echo -e "${RED}❌ $name NO está instalado${NC}"
        echo -e "   ${YELLOW}Instalación: $install_hint${NC}"
        return 1
    fi
    echo ""
}

check_docker_running() {
    if docker info &> /dev/null; then
        echo -e "${GREEN}✅ Docker daemon está corriendo${NC}"
        return 0
    else
        echo -e "${RED}❌ Docker daemon NO está corriendo${NC}"
        echo -e "   ${YELLOW}Inicia Docker Desktop o ejecuta: sudo systemctl start docker${NC}"
        return 1
    fi
    echo ""
}

check_docker_compose_version() {
    if docker compose version &> /dev/null 2>&1; then
        local version=$(docker compose version)
        echo -e "${GREEN}✅ Docker Compose (plugin) está disponible${NC}"
        echo "   Versión: $version"
        return 0
    elif command -v docker-compose &> /dev/null; then
        local version=$(docker-compose --version)
        echo -e "${GREEN}✅ Docker Compose (standalone) está disponible${NC}"
        echo "   Versión: $version"
        return 0
    else
        echo -e "${RED}❌ Docker Compose NO está disponible${NC}"
        return 1
    fi
    echo ""
}

check_port() {
    local port=$1
    local service=$2
#!/bin/bash

echo "============================================"
echo " VERIFICACIÓN DE REQUISITOS DEL PROYECTO"
echo "============================================"
echo ""

# Colores para output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

check_command() {
    local cmd=$1
    local name=$2
    local install_hint=$3

    if command -v $cmd &> /dev/null; then
        local version=$($cmd --version 2>&1 | head -n 1)
        echo -e "${GREEN}✅ $name está instalado${NC}"
        echo "   Versión: $version"
        return 0
    else
        echo -e "${RED}❌ $name NO está instalado${NC}"
        echo -e "   ${YELLOW}Instalación: $install_hint${NC}"
        return 1
    fi
    echo ""
}

check_docker_running() {
    if docker info &> /dev/null; then
        echo -e "${GREEN}✅ Docker daemon está corriendo${NC}"
        return 0
    else
        echo -e "${RED}❌ Docker daemon NO está corriendo${NC}"
        echo -e "   ${YELLOW}Inicia Docker Desktop o ejecuta: sudo systemctl start docker${NC}"
        return 1
    fi
    echo ""
}

check_docker_compose_version() {
    if docker compose version &> /dev/null 2>&1; then
        local version=$(docker compose version)
        echo -e "${GREEN}✅ Docker Compose (plugin) está disponible${NC}"
        echo "   Versión: $version"
        return 0
    elif command -v docker-compose &> /dev/null; then
        local version=$(docker-compose --version)
        echo -e "${GREEN}✅ Docker Compose (standalone) está disponible${NC}"
        echo "   Versión: $version"
        return 0
    else
        echo -e "${RED}❌ Docker Compose NO está disponible${NC}"
        return 1
    fi
    echo ""
}

check_port() {
    local port=$1
    local service=$2
#!/bin/bash

echo "============================================"
echo " VERIFICACIÓN DE REQUISITOS DEL PROYECTO"
echo "============================================"
echo ""

# Colores para output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

check_command() {
    local cmd=$1
    local name=$2
    local install_hint=$3

    if command -v $cmd &> /dev/null; then
        local version=$($cmd --version 2>&1 | head -n 1)
        echo -e "${GREEN}✅ $name está instalado${NC}"
        echo "   Versión: $version"
        return 0
    else
        echo -e "${RED}❌ $name NO está instalado${NC}"
        echo -e "   ${YELLOW}Instalación: $install_hint${NC}"
        return 1
    fi
    echo ""
}

check_docker_running() {
    if docker info &> /dev/null; then
        echo -e "${GREEN}✅ Docker daemon está corriendo${NC}"
        return 0
    else
        echo -e "${RED}❌ Docker daemon NO está corriendo${NC}"
        echo -e "   ${YELLOW}Inicia Docker Desktop o ejecuta: sudo systemctl start docker${NC}"
        return 1
    fi
    echo ""
}

check_docker_compose_version() {
    if docker compose version &> /dev/null 2>&1; then
        local version=$(docker compose version)
        echo -e "${GREEN}✅ Docker Compose (plugin) está disponible${NC}"
        echo "   Versión: $version"
        return 0
    elif command -v docker-compose &> /dev/null; then
        local version=$(docker-compose --version)
        echo -e "${GREEN}✅ Docker Compose (standalone) está disponible${NC}"
        echo "   Versión: $version"
        return 0
    else
        echo -e "${RED}❌ Docker Compose NO está disponible${NC}"
        return 1
    fi
    echo ""
}

check_port() {
    local port=$1
    local service=$2

    if lsof -Pi :$port -sTCP:LISTEN -t >/dev/null 2>&1 || netstat -an 2>/dev/null | grep -q ":$port.*LISTEN"; then
        echo -e "${YELLOW}⚠️  Puerto $port ($service) está en uso${NC}"
        echo "   Necesitas liberar este puerto o detener el servicio que lo usa"
        return 1
    else
        echo -e "${GREEN}✅ Puerto $port ($service) está disponible${NC}"
        return 0
    fi
}

check_disk_space() {
    local required_gb=10
    local available_gb=$(df -BG . | awk 'NR==2 {print $4}' | sed 's/G//')

    if [ "$available_gb" -ge "$required_gb" ]; then
        echo -e "${GREEN}✅ Espacio en disco suficiente: ${available_gb}GB disponibles${NC}"
        return 0
    else
        echo -e "${YELLOW}⚠️  Espacio en disco limitado: ${available_gb}GB disponibles${NC}"
        echo "   Se recomienda al menos ${required_gb}GB"
        return 1
    fi
    echo ""
}

check_memory() {
    if [[ "$OSTYPE" == "darwin"* ]]; then
        # macOS
        local total_mb=$(sysctl -n hw.memsize | awk '{print int($1/1024/1024)}')
    else
        # Linux
        local total_mb=$(free -m | awk 'NR==2 {print $2}')
    fi

    local total_gb=$((total_mb / 1024))
    local required_gb=4

    if [ "$total_gb" -ge "$required_gb" ]; then
        echo -e "${GREEN}✅ Memoria RAM suficiente: ${total_gb}GB${NC}"
        return 0
    else
        echo -e "${YELLOW}⚠️  Memoria RAM limitada: ${total_gb}GB${NC}"
        echo "   Se recomienda al menos ${required_gb}GB para todos los servicios"
        return 1
    fi
    echo ""
}

# Iniciar verificaciones
echo " VERIFICANDO HERRAMIENTAS PRINCIPALES:"
echo "----------------------------------------"

all_ok=true

check_command "docker" "Docker" "https://docs.docker.com/get-docker/" || all_ok=false
echo ""

check_docker_compose_version || all_ok=false
echo ""

if command -v docker &> /dev/null; then
    check_docker_running || all_ok=false
    echo ""
fi

check_command "git" "Git" "https://git-scm.com/downloads" || all_ok=false
echo ""

echo " VERIFICANDO HERRAMIENTAS OPCIONALES:"
echo "----------------------------------------"
echo "(No son necesarias si usas Docker, pero útiles para desarrollo local)"
echo ""

check_command "java" "Java JDK" "https://adoptium.net/"
echo ""

check_command "mvn" "Maven" "https://maven.apache.org/download.cgi"
echo ""

echo " VERIFICANDO PUERTOS DISPONIBLES:"
echo "----------------------------------------"

check_port 8080 "API REST/GraphQL"
check_port 5432 "PostgreSQL"
check_port 9092 "Kafka"
check_port 2181 "Zookeeper"
check_port 9200 "Elasticsearch"
check_port 5601 "Kibana"
check_port 8123 "ClickHouse HTTP"
check_port 9000 "ClickHouse Native"
check_port 5002 "Logstash"
echo ""

echo " VERIFICANDO RECURSOS DEL SISTEMA:"
echo "----------------------------------------"

check_disk_space
echo ""

check_memory
echo ""

echo "============================================"
if [ "$all_ok" = true ]; then
    echo -e "${GREEN}✅ TODOS LOS REQUISITOS ESENCIALES ESTÁN CUMPLIDOS${NC}"
    echo ""
    echo "Puedes ejecutar el proyecto con:"
    echo "  docker-compose up --build"
    echo ""
    echo "O si tienes Docker Compose como plugin:"
    echo "  docker compose up --build"
else
    echo -e "${RED}❌ FALTAN ALGUNOS REQUISITOS ESENCIALES${NC}"
    echo ""
    echo "Por favor, instala las herramientas faltantes antes de continuar."
fi
echo "============================================"
    if lsof -Pi :$port -sTCP:LISTEN -t >/dev/null 2>&1 || netstat -an 2>/dev/null | grep -q ":$port.*LISTEN"; then
        echo -e "${YELLOW}⚠️  Puerto $port ($service) está en uso${NC}"
        echo "   Necesitas liberar este puerto o detener el servicio que lo usa"
        return 1
    else
        echo -e "${GREEN}✅ Puerto $port ($service) está disponible${NC}"
        return 0
    fi
}

check_disk_space() {
    local required_gb=10
    local available_gb=$(df -BG . | awk 'NR==2 {print $4}' | sed 's/G//')

    if [ "$available_gb" -ge "$required_gb" ]; then
        echo -e "${GREEN}✅ Espacio en disco suficiente: ${available_gb}GB disponibles${NC}"
        return 0
    else
        echo -e "${YELLOW}⚠️  Espacio en disco limitado: ${available_gb}GB disponibles${NC}"
        echo "   Se recomienda al menos ${required_gb}GB"
        return 1
    fi
    echo ""
}

check_memory() {
    if [[ "$OSTYPE" == "darwin"* ]]; then
        # macOS
        local total_mb=$(sysctl -n hw.memsize | awk '{print int($1/1024/1024)}')
    else
        # Linux
        local total_mb=$(free -m | awk 'NR==2 {print $2}')
    fi

    local total_gb=$((total_mb / 1024))
    local required_gb=4

    if [ "$total_gb" -ge "$required_gb" ]; then
        echo -e "${GREEN}✅ Memoria RAM suficiente: ${total_gb}GB${NC}"
        return 0
    else
        echo -e "${YELLOW}⚠️  Memoria RAM limitada: ${total_gb}GB${NC}"
        echo "   Se recomienda al menos ${required_gb}GB para todos los servicios"
        return 1
    fi
    echo ""
}

# Iniciar verificaciones
echo " VERIFICANDO HERRAMIENTAS PRINCIPALES:"
echo "----------------------------------------"

all_ok=true

check_command "docker" "Docker" "https://docs.docker.com/get-docker/" || all_ok=false
echo ""

check_docker_compose_version || all_ok=false
echo ""

if command -v docker &> /dev/null; then
    check_docker_running || all_ok=false
    echo ""
fi

check_command "git" "Git" "https://git-scm.com/downloads" || all_ok=false
echo ""

echo " VERIFICANDO HERRAMIENTAS OPCIONALES:"
echo "----------------------------------------"
echo "(No son necesarias si usas Docker, pero útiles para desarrollo local)"
echo ""

check_command "java" "Java JDK" "https://adoptium.net/"
echo ""

check_command "mvn" "Maven" "https://maven.apache.org/download.cgi"
echo ""

echo " VERIFICANDO PUERTOS DISPONIBLES:"
echo "----------------------------------------"

check_port 8080 "API REST/GraphQL"
check_port 5432 "PostgreSQL"
check_port 9092 "Kafka"
check_port 2181 "Zookeeper"
check_port 9200 "Elasticsearch"
check_port 5601 "Kibana"
check_port 8123 "ClickHouse HTTP"
check_port 9000 "ClickHouse Native"
check_port 5002 "Logstash"
echo ""

echo " VERIFICANDO RECURSOS DEL SISTEMA:"
echo "----------------------------------------"

check_disk_space
echo ""

check_memory
echo ""

echo "============================================"
if [ "$all_ok" = true ]; then
    echo -e "${GREEN}✅ TODOS LOS REQUISITOS ESENCIALES ESTÁN CUMPLIDOS${NC}"
    echo ""
    echo "Puedes ejecutar el proyecto con:"
    echo "  docker-compose up --build"
    echo ""
    echo "O si tienes Docker Compose como plugin:"
    echo "  docker compose up --build"
else
    echo -e "${RED}❌ FALTAN ALGUNOS REQUISITOS ESENCIALES${NC}"
    echo ""
    echo "Por favor, instala las herramientas faltantes antes de continuar."
fi
echo "============================================"
    if lsof -Pi :$port -sTCP:LISTEN -t >/dev/null 2>&1 || netstat -an 2>/dev/null | grep -q ":$port.*LISTEN"; then
        echo -e "${YELLOW}⚠️  Puerto $port ($service) está en uso${NC}"
        echo "   Necesitas liberar este puerto o detener el servicio que lo usa"
        return 1
    else
        echo -e "${GREEN}✅ Puerto $port ($service) está disponible${NC}"
        return 0
    fi
}

check_disk_space() {
    local required_gb=10
    local available_gb=$(df -BG . | awk 'NR==2 {print $4}' | sed 's/G//')

    if [ "$available_gb" -ge "$required_gb" ]; then
        echo -e "${GREEN}✅ Espacio en disco suficiente: ${available_gb}GB disponibles${NC}"
        return 0
    else
        echo -e "${YELLOW}⚠️  Espacio en disco limitado: ${available_gb}GB disponibles${NC}"
        echo "   Se recomienda al menos ${required_gb}GB"
        return 1
    fi
    echo ""
}

check_memory() {
    if [[ "$OSTYPE" == "darwin"* ]]; then
        # macOS
        local total_mb=$(sysctl -n hw.memsize | awk '{print int($1/1024/1024)}')
    else
        # Linux
        local total_mb=$(free -m | awk 'NR==2 {print $2}')
    fi

    local total_gb=$((total_mb / 1024))
    local required_gb=4

    if [ "$total_gb" -ge "$required_gb" ]; then
        echo -e "${GREEN}✅ Memoria RAM suficiente: ${total_gb}GB${NC}"
        return 0
    else
        echo -e "${YELLOW}⚠️  Memoria RAM limitada: ${total_gb}GB${NC}"
        echo "   Se recomienda al menos ${required_gb}GB para todos los servicios"
        return 1
    fi
    echo ""
}

# Iniciar verificaciones
echo "📦 VERIFICANDO HERRAMIENTAS PRINCIPALES:"
echo "----------------------------------------"

all_ok=true

check_command "docker" "Docker" "https://docs.docker.com/get-docker/" || all_ok=false
echo ""

check_docker_compose_version || all_ok=false
echo ""

if command -v docker &> /dev/null; then
    check_docker_running || all_ok=false
    echo ""
fi

check_command "git" "Git" "https://git-scm.com/downloads" || all_ok=false
echo ""

echo "📦 VERIFICANDO HERRAMIENTAS OPCIONALES:"
echo "----------------------------------------"
echo "(No son necesarias si usas Docker, pero útiles para desarrollo local)"
echo ""

check_command "java" "Java JDK" "https://adoptium.net/"
echo ""

check_command "mvn" "Maven" "https://maven.apache.org/download.cgi"
echo ""

echo "🔌 VERIFICANDO PUERTOS DISPONIBLES:"
echo "----------------------------------------"

check_port 8080 "API REST/GraphQL"
check_port 5432 "PostgreSQL"
check_port 9092 "Kafka"
check_port 2181 "Zookeeper"
check_port 9200 "Elasticsearch"
check_port 5601 "Kibana"
check_port 8123 "ClickHouse HTTP"
check_port 9000 "ClickHouse Native"
check_port 5002 "Logstash"
echo ""

echo "💾 VERIFICANDO RECURSOS DEL SISTEMA:"
echo "----------------------------------------"

check_disk_space
echo ""

check_memory
echo ""

echo "============================================"
if [ "$all_ok" = true ]; then
    echo -e "${GREEN}✅ TODOS LOS REQUISITOS ESENCIALES ESTÁN CUMPLIDOS${NC}"
    echo ""
    echo "Puedes ejecutar el proyecto con:"
    echo "  docker-compose up --build"
    echo ""
    echo "O si tienes Docker Compose como plugin:"
    echo "  docker compose up --build"
else
    echo -e "${RED}❌ FALTAN ALGUNOS REQUISITOS ESENCIALES${NC}"
    echo ""
    echo "Por favor, instala las herramientas faltantes antes de continuar."
fi
echo "============================================"
