# SADO Backend (Spring Boot 4.0.1)

> **Project Type**: 16-Week Learning Project (MSA Backend Development)
>
> **Language**: Java 21
>
> **Framework**: Spring Boot 4.0.1 (Web MVC)
>
> **Build Tool**: Gradle 9.2.1

---

## ⚠️ IMPORTANT: POC Authentication Warning

### Current Implementation Status (Week 3-4)

**Device Authentication: Simple UUID-based API Key**

```
⚠️ POC ONLY - NOT FOR PRODUCTION ⚠️

Current device authentication uses Simple API Key for learning purposes.
- Mock data only (NO real ePHI)
- Week 12: Migration to OAuth2 Client Credentials (Keycloak)
- Production: OAuth2 + mTLS for HIPAA 2025 compliance

See: docs/be/guides/14_Device_Authentication_Migration_Guide.md
```

**Why API Key is NOT Production-Ready:**
1. ❌ HIPAA 2025 MFA requirement not met (single-factor only)
2. ❌ No automatic token rotation mechanism
3. ❌ Limited audit trail capabilities
4. ❌ Not industry standard (Google Cloud, AWS use OAuth2)

**Migration Timeline:**
- Week 3-4: Simple API Key (current) - Learning & POC
- Week 12: OAuth2 Client Credentials - Industry Standard
- Week 16+: OAuth2 + mTLS - HIPAA 2025 Full Compliance

---

## 🏗️ Project Architecture

### Multi-Module Structure

```
sado_be/
├── sado-common/              # Common shared module
│   ├── entity/               # BaseEntity, TenantAwareEntity, Device
│   ├── dto/                  # ApiResponse
│   ├── exception/            # BusinessException, GlobalExceptionHandler
│   └── code/                 # ApiCode, CommonCode
├── sado-gateway/             # API Gateway (Spring Cloud Gateway)
│   └── filter/               # DeviceAuthFilter (POC), JwtTenantFilter
└── docker-compose.yml        # MySQL, Kafka, Zookeeper
```

### Key Technologies

- **Multi-Tenancy**: Hibernate @Filter with tenant_id
- **Common Module**: ApiCode + ApiResponse pattern (inspired by kingarthur)
- **Database**: MySQL 8.0
- **Messaging**: Kafka 7.5.0
- **ORM**: JPA (Hibernate 6.x) + MyBatis (planned Week 4)
- **Authentication**: JWT (users) + API Key (devices - POC only)

---

## 🚀 Quick Start

### Prerequisites

- Java 21
- Docker & Docker Compose
- Gradle 9.2.1

### 1. Start Infrastructure

```bash
# Start MySQL, Kafka, Zookeeper
docker-compose up -d

# Verify containers
docker ps
# Should see: sado-mysql, sado-kafka, sado-zookeeper
```

### 2. Build Project

```bash
# Build all modules
./gradlew clean build

# Build specific module
./gradlew :sado-common:build
./gradlew :sado-gateway:build
```

### 3. Run Application

```bash
# Run gateway
./gradlew :sado-gateway:bootRun

# Application starts at http://localhost:8080
```

### 4. Test API

```bash
# Test success response
curl http://localhost:8080/api/test/success

Response:
{
  "type": "SUCCESS",
  "code": 100000,
  "message": "Success",
  "data": "API is working!"
}

# Test error handling
curl http://localhost:8080/api/test/error

Response:
{
  "type": "RESOURCE_NOT_FOUND",
  "code": 1000002,
  "message": "Resource not found",
  "data": null
}
```

---

## 🔐 Device API Usage (POC)

### ⚠️ POC ONLY - For Learning Purposes

### 1. Register Device (Manual - POC)

**Week 3-4 POC**: Direct database insertion (Week 12: REST API implementation)

```sql
-- Generate API Key (Java)
String rawApiKey = "dev_" + UUID.randomUUID().toString();
String hashedApiKey = BCryptPasswordEncoder.encode(rawApiKey);

-- Insert Device
INSERT INTO device (
    tenant_id,
    device_name,
    api_key,
    device_type,
    status,
    created_at,
    updated_at
) VALUES (
    1,                                      -- Seoul National University Hospital
    'CT Scanner 001',
    '$2a$10$eU7h8K9KvL...',               -- BCrypt hash of API key
    'CT',
    'ACTIVE',
    NOW(),
    NOW()
);

-- Save raw API key for device configuration
-- Raw API Key: dev_550e8400-e29b-41d4-a716-446655440000
```

### 2. Upload DICOM (Device)

```bash
curl -X POST "http://localhost:8080/api/v1/studies/upload" \
  -H "X-Device-API-Key: dev_550e8400-e29b-41d4-a716-446655440000" \
  -F "file=@study.dcm"

Response:
{
  "type": "SUCCESS",
  "code": 100000,
  "message": "DICOM uploaded successfully",
  "data": {
    "studyInstanceUid": "1.2.840.113619.2.55.3.12159733.323...",
    "patientId": "P12345",
    "seriesCount": 1,
    "instanceCount": 45
  }
}
```

### 3. Authentication Flow

```
CT Scanner (Device)
    │
    ├─→ HTTP Header: X-Device-API-Key: dev_550e8400...
    │
    ↓
[Gateway] DeviceAuthFilter
    │
    ├─→ DeviceRepository.findByApiKey(apiKey)
    ├─→ BCrypt.matches(apiKey, device.hashedApiKey)
    ├─→ device.status == ACTIVE
    ├─→ Inject Headers: X-Tenant-ID, X-Device-ID
    │
    ↓
[Service] TenantFilterAspect
    │
    ├─→ TenantContext.setCurrentTenantId(1)
    ├─→ Hibernate Filter: WHERE tenant_id = 1
    │
    ↓
[Repository] studyRepository.save(study)
    │
    └─→ SQL: INSERT INTO study (..., tenant_id) VALUES (..., 1)
```

---

## 📖 Week 12 Migration Plan (OAuth2)

### OAuth2 Client Credentials (Industry Standard)

**Keycloak Service Account Setup:**

```bash
# 1. Create Keycloak Client
POST http://keycloak:8080/admin/realms/sado/clients
{
  "clientId": "device-ct-scanner-001",
  "serviceAccountsEnabled": true,
  "attributes": {
    "tenant_id": "1",
    "device_id": "d123",
    "device_type": "CT"
  }
}

# 2. Device obtains access token
curl -X POST "http://keycloak:8080/realms/sado/protocol/openid-connect/token" \
  -d "grant_type=client_credentials" \
  -d "client_id=device-ct-scanner-001" \
  -d "client_secret=abc123"

Response:
{
  "access_token": "eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCJ9...",
  "expires_in": 300,
  "token_type": "Bearer"
}

# 3. Upload with JWT
curl -X POST "http://localhost:8080/api/v1/studies/upload" \
  -H "Authorization: Bearer eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCJ9..." \
  -F "file=@study.dcm"
```

**Benefits:**
- ✅ Industry standard (Google Cloud Healthcare API, AWS HealthLake)
- ✅ HIPAA 2025 basic compliance
- ✅ Automatic token rotation
- ✅ Enhanced audit trail (JWT claims)
- ✅ Production-ready

**See:** `docs/be/guides/14_Device_Authentication_Migration_Guide.md`

---

## 🗂️ Database Schema

### Core Entities (Week 3-4)

#### BaseEntity (Common)

```java
@MappedSuperclass
public abstract class BaseEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @CreatedDate
    private LocalDateTime createdAt;

    @LastModifiedDate
    private LocalDateTime updatedAt;
}
```

#### TenantAwareEntity (Multi-Tenancy)

```java
@MappedSuperclass
@FilterDef(name = "tenantFilter", parameters = @ParamDef(name = "tenantId", type = Long.class))
@Filter(name = "tenantFilter", condition = "tenant_id = :tenantId")
@EntityListeners(TenantEntityListener.class)
public abstract class TenantAwareEntity extends BaseEntity {
    @Column(name = "tenant_id", nullable = false)
    private Long tenantId;
}
```

#### Device Entity (POC)

```java
@Entity
@Table(name = "device")
public class Device extends TenantAwareEntity {
    @Column(name = "device_name", nullable = false)
    private String deviceName;

    @Column(name = "api_key", nullable = false, unique = true)
    private String apiKey;  // ⚠️ Week 12 마이그레이션 시 제거 예정

    @Enumerated(EnumType.STRING)
    private DeviceType deviceType;  // CT, MRI, ULTRASOUND, XRAY

    @Enumerated(EnumType.STRING)
    private DeviceStatus status;  // ACTIVE, INACTIVE, REVOKED

    @Column(name = "last_used_at")
    private LocalDateTime lastUsedAt;

    // Week 12 마이그레이션 필드 (주석 처리)
    // @Column(name = "keycloak_client_id")
    // private String keycloakClientId;
}
```

#### Study, Series, Instance (DICOM - Week 3 구현 예정)

```java
@Entity
public class Study extends TenantAwareEntity {
    @Column(name = "study_instance_uid", unique = true)
    private String studyInstanceUid;

    @Column(name = "patient_id")
    private String patientId;

    @OneToMany(mappedBy = "study")
    private List<Series> seriesList;
}

@Entity
public class Series extends TenantAwareEntity {
    @Column(name = "series_instance_uid", unique = true)
    private String seriesInstanceUid;

    @ManyToOne
    private Study study;

    @OneToMany(mappedBy = "series")
    private List<Instance> instanceList;
}

@Entity
public class Instance extends TenantAwareEntity {
    @Column(name = "sop_instance_uid", unique = true)
    private String sopInstanceUid;

    @ManyToOne
    private Series series;

    @Column(name = "file_path")
    private String filePath;
}
```

---

## 🔧 Configuration

### application.yml (sado-gateway)

```yaml
spring:
  application:
    name: sado-gateway

  # MySQL DataSource
  datasource:
    url: jdbc:mysql://localhost:3306/sado_db?useSSL=false&serverTimezone=UTC
    username: root
    password: root1234
    driver-class-name: com.mysql.cj.jdbc.Driver

  # JPA Configuration
  jpa:
    hibernate:
      ddl-auto: update
    properties:
      hibernate:
        format_sql: true
        show_sql: true
        # Hibernate 6.x: Dialect 자동 감지

  # Kafka Configuration
  kafka:
    bootstrap-servers: localhost:9092
    consumer:
      group-id: sado-group
    producer:
      key-serializer: org.apache.kafka.common.serialization.StringSerializer
      value-serializer: org.apache.kafka.common.serialization.StringSerializer

server:
  port: 8080
```

### docker-compose.yml

```yaml
version: '3.8'

services:
  mysql:
    image: mysql:8.0
    container_name: sado-mysql
    environment:
      MYSQL_ROOT_PASSWORD: root1234
      MYSQL_DATABASE: sado_db
    ports:
      - "3306:3306"
    volumes:
      - mysql-data:/var/lib/mysql
    networks:
      - sado-network

  zookeeper:
    image: confluentinc/cp-zookeeper:7.5.0
    container_name: sado-zookeeper
    environment:
      ZOOKEEPER_CLIENT_PORT: 2181
    ports:
      - "2181:2181"
    networks:
      - sado-network

  kafka:
    image: confluentinc/cp-kafka:7.5.0
    container_name: sado-kafka
    depends_on:
      - zookeeper
    environment:
      KAFKA_BROKER_ID: 1
      KAFKA_ZOOKEEPER_CONNECT: zookeeper:2181
      KAFKA_ADVERTISED_LISTENERS: PLAINTEXT://localhost:9092
      KAFKA_OFFSETS_TOPIC_REPLICATION_FACTOR: 1
    ports:
      - "9092:9092"
    networks:
      - sado-network

networks:
  sado-network:
    driver: bridge

volumes:
  mysql-data:
```

---

## 📚 Documentation

### Project Documentation

All project documentation is stored in the `sado_docs` repository (separate from code).

**Location**: `../sado_docs/be/`

**Key Documents:**

| Document | Description |
|----------|-------------|
| [00_개발_방식_및_Claude_역할.md](../sado_docs/be/core/00_개발_방식_및_Claude_역할.md) | Development methodology & Claude Code role |
| [07_최종_구현_계획.md](../sado_docs/be/core/07_최종_구현_계획.md) | 16-week implementation plan |
| [12_멀티테넌시_설계_가이드.md](../sado_docs/be/guides/12_멀티테넌시_설계_가이드.md) | Multi-tenancy design guide |
| [14_Device_Authentication_Migration_Guide.md](../sado_docs/be/guides/14_Device_Authentication_Migration_Guide.md) | Device authentication migration (API Key → OAuth2 → mTLS) |
| [CURRENT_CONTEXT.md](../sado_docs/be/tracking/CURRENT_CONTEXT.md) | Current progress & context |
| [PROGRESS.md](../sado_docs/be/tracking/PROGRESS.md) | Detailed progress dashboard |

### Learning Approach

**User writes ALL code** - Claude Code provides guidance only:
- ✅ Concept explanation
- ✅ Architecture advice
- ✅ Code review
- ✅ Debugging support
- ❌ NO automatic code generation

**See**: `../sado_docs/be/core/00_개발_방식_및_Claude_역할.md`

---

## 🧪 Testing

### API Response Tests

```bash
# Success response
curl http://localhost:8080/api/test/success

# Error response
curl http://localhost:8080/api/test/error
```

### Device Authentication Tests (POC)

```bash
# Valid API Key
curl -X POST http://localhost:8080/api/v1/studies/upload \
  -H "X-Device-API-Key: dev_550e8400-e29b-41d4-a716-446655440000" \
  -F "file=@study.dcm"
# Expected: 200 OK

# Invalid API Key
curl -X POST http://localhost:8080/api/v1/studies/upload \
  -H "X-Device-API-Key: invalid-key" \
  -F "file=@study.dcm"
# Expected: 401 Unauthorized

# Revoked Device
# (Device status = REVOKED in DB)
curl -X POST http://localhost:8080/api/v1/studies/upload \
  -H "X-Device-API-Key: dev_revoked_device_key" \
  -F "file=@study.dcm"
# Expected: 403 Forbidden
```

---

## 🔒 Security & Compliance

### Current Security Level (POC)

```
⚠️ POC Security - NOT Production-Ready ⚠️

Current Implementation:
- Simple UUID-based API Key
- BCrypt hashing
- Mock data only (NO real ePHI)

Limitations:
- ❌ Single-factor authentication
- ❌ No automatic token rotation
- ❌ HIPAA 2025 MFA requirement NOT met
```

### Production Security Roadmap

```
Phase 1 (Week 3-4): Simple API Key
    └─→ Learning & POC only

Phase 2 (Week 12): OAuth2 Client Credentials
    ├─→ Keycloak Service Accounts
    ├─→ JWT Bearer Tokens
    ├─→ HIPAA 2025 basic compliance
    └─→ Industry standard (Google Cloud, AWS)

Phase 3 (Week 16+): OAuth2 + mTLS
    ├─→ X.509 client certificates
    ├─→ HIPAA 2025 FULL compliance
    ├─→ IHE ATNA Profile
    └─→ Enterprise-grade security
```

### HIPAA 2025 Compliance Status

| Requirement | Phase 1 (POC) | Phase 2 (OAuth2) | Phase 3 (mTLS) |
|-------------|---------------|------------------|----------------|
| **MFA** | ❌ Not met | ⚠️ Partial | ✅ Full |
| **Audit Trail** | ⚠️ Limited | ✅ Complete | ✅ Complete |
| **Token Rotation** | ❌ Manual | ✅ Automatic | ✅ Automatic |
| **Industry Standard** | ❌ No | ✅ Yes | ✅ Yes |
| **Production-Ready** | ❌ No | ✅ Yes | ✅ Yes |

**Reference:**
- [HIPAA MFA Requirements 2025](https://www.strongdm.com/blog/hipaa-mfa-requirements)
- [Google Cloud Healthcare API](https://cloud.google.com/healthcare-api/docs/authentication)
- [AWS HealthLake](https://aws.amazon.com/healthlake/faqs/)

---

## 📦 Gradle Modules

### sado-common

**Purpose**: Shared entities, DTOs, exceptions, codes

**Dependencies:**
```gradle
dependencies {
    api 'org.springframework.boot:spring-boot-starter-data-jpa'
    api 'org.springframework.boot:spring-boot-starter-web'
    compileOnly 'org.projectlombok:lombok'
    annotationProcessor 'org.projectlombok:lombok'
}
```

### sado-gateway

**Purpose**: API Gateway with authentication filters

**Dependencies:**
```gradle
dependencies {
    implementation project(':sado-common')
    implementation 'org.springframework.boot:spring-boot-starter-web'
    implementation 'org.springframework.cloud:spring-cloud-starter-gateway'
    implementation 'mysql:mysql-connector-java'
    implementation 'org.springframework.security:spring-security-crypto'  // BCrypt
}
```

---

## 🚧 Roadmap

### Week 1-2 (Completed ✅)
- [x] Gradle multi-module structure
- [x] sado-common core classes (ApiCode, ApiResponse, GlobalExceptionHandler)
- [x] Docker Compose environment (MySQL, Kafka, Zookeeper)
- [x] TestController API testing

### Week 3-4 (In Progress 🔄)
- [ ] BaseEntity implementation
- [ ] DICOM domain entities (Study, Series, Instance)
- [ ] Device Entity (API Key authentication - POC)
- [ ] JPA Repositories
- [ ] Multi-tenancy testing (Hibernate Filter)

### Week 5-8
- [ ] Kafka Producer/Consumer
- [ ] Keycloak integration
- [ ] Simple workflow
- [ ] Outbox pattern

### Week 9-12
- [ ] Temporal basic setup
- [ ] **OAuth2 Client Credentials migration (Device authentication)**
- [ ] Temporal activity implementation
- [ ] Complete Temporal workflow

### Week 13-16
- [ ] Redis caching
- [ ] Observability (metrics, logging)
- [ ] Enhanced testing
- [ ] **Production security (mTLS - optional)**
- [ ] Project completion

**Detailed Plan**: `../sado_docs/be/core/07_최종_구현_계획.md`

---

## 📝 Contributing

This is a personal learning project. All code is written by the user for educational purposes.

**Development Guidelines:**
1. User writes ALL code manually (no copy-paste from examples)
2. Claude Code provides guidance, review, and debugging support only
3. Focus on understanding over speed
4. Blog each major milestone for knowledge internalization

---

## 📄 License

This is a private learning project (not open source).

---

## 🙋 Support

For questions or issues:
1. Refer to documentation in `sado_docs/be/`
2. Review implementation guides and troubleshooting sections
3. Check `CURRENT_CONTEXT.md` for latest status

---

**Last Updated**: 2025-12-26
**Current Week**: 3-4 (Phase 1 - Foundation)
**Progress**: 80% (Week 1-2 complete)
