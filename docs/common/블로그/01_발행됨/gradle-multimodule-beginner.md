# Spring Boot + Gradle 멀티모듈 프로젝트 완전 정복 (초보자용)

> 작성일: 2025-12-26
> 카테고리: 개념 정리
> 관련 기술: Gradle, Spring Boot, 멀티모듈
> **업데이트**: 실전 트러블슈팅 섹션 추가 (2025-12-26)

## TL;DR

- **Gradle 멀티모듈**은 하나의 프로젝트를 여러 독립적인 모듈로 나누는 구조
- 코드 재사용, 관심사 분리, 빌드 속도 향상이 주요 장점
- `settings.gradle`로 모듈 정의, `implementation`/`api`로 의존성 관리
- 순환 의존성 금지, 모듈명은 lowercase-hyphen 규칙 따르기
- **루트 build.gradle 설정이 핵심!** (subprojects 블록 필수)

---

## 배경: 왜 멀티모듈이 필요한가?

SADO 프로젝트를 시작하면서 고민이 생겼습니다.

```
"Gateway, DICOM 서비스, BFF...
이걸 전부 하나의 프로젝트에 넣어야 하나?"
```

### 단일 모듈의 한계

기존에는 모든 코드를 하나의 `src/` 폴더에 넣었습니다:

```
sado_be/
└── src/
    └── main/
        └── java/
            ├── gateway/
            ├── dicom/
            ├── bff/
            └── common/   # 이걸 어디서든 쓰고 싶은데...
```

**문제점:**
- Gateway에서만 필요한 코드를 DICOM 서비스에서도 접근 가능
- 공통 코드(common) 변경 시 전체 프로젝트 재빌드
- 각 서비스를 독립적으로 배포하기 어려움
- 테스트 범위가 불명확

### 멀티모듈의 해결책

```
sado_be/
├── sado-common/         # 공통 코드만 여기
├── sado-gateway/        # Gateway만의 코드
├── sado-service-dicom/  # DICOM만의 코드
└── sado-bff/            # BFF만의 코드
```

**장점:**
- ✅ 각 모듈은 명확한 책임을 가짐
- ✅ 필요한 의존성만 선택적으로 추가
- ✅ 변경 영향 범위 최소화 (빌드 속도 향상)
- ✅ 독립적인 배포 가능

---

## 핵심 개념

### 1. Gradle 멀티모듈이란?

> "하나의 루트 프로젝트 안에 여러 서브프로젝트(모듈)를 두는 구조"

#### 기본 구조

```
sado_be/                  # 루트 프로젝트
├── settings.gradle       # 모듈 정의 (핵심!)
├── build.gradle          # 공통 설정
├── sado-common/          # 서브 모듈 1
│   └── build.gradle
├── sado-gateway/         # 서브 모듈 2
│   └── build.gradle
└── sado-service-dicom/   # 서브 모듈 3
    └── build.gradle
```

#### 각 파일의 역할

| 파일 | 역할 | 예시 |
|------|------|------|
| **settings.gradle** | 어떤 모듈들이 있는지 정의 | `include 'sado-common'` |
| **루트 build.gradle** | 모든 모듈의 공통 설정 | Java 버전, 공통 의존성 |
| **각 모듈의 build.gradle** | 해당 모듈만의 설정 | `implementation project(':sado-common')` |

---

### 2. settings.gradle - 모듈 선언의 시작

가장 먼저 만들어야 할 파일입니다!

#### 기본 예시

```gradle
// sado_be/settings.gradle

rootProject.name = 'sado_be'  // 루트 프로젝트 이름

// 서브 모듈 선언
include 'sado-common'
include 'sado-gateway'
include 'sado-service-dicom'
```

#### 동작 원리

1. Gradle이 프로젝트를 빌드할 때 `settings.gradle`을 가장 먼저 읽음
2. `include`로 선언된 모듈들을 찾아서 로드
3. 각 모듈의 `build.gradle`을 실행

**중요!**
- 모듈 선언 순서는 빌드 순서와 무관 (Gradle이 의존성 보고 자동 결정)
- 모듈명은 `lowercase-hyphen` 규칙 따르기 (예: `sado-common`, `sado-gateway`)

---

### 3. 모듈 간 의존성: implementation vs api (중요! ⭐⭐⭐)

모듈끼리 서로 참조할 때 사용하는 **가장 중요한** 키워드입니다.

#### 핵심 개념: 의존성 전이 (Transitive Dependency)

```
Module A → Module B → Library C

A가 B를 의존
B가 C를 의존
```

**핵심 질문:** "A에서 C를 직접 사용할 수 있는가?"
- `api` 사용 시: **YES** ✅ (C가 A에게 전이됨)
- `implementation` 사용 시: **NO** ❌ (C가 A에게 숨겨짐)

---

#### 3.1 컴파일 클래스패스 vs 런타임 클래스패스

**클래스패스란?** Java가 클래스 파일을 찾는 경로

| 클래스패스 종류 | 언제 사용? | 예시 |
|---------------|----------|------|
| **컴파일 클래스패스** | 코드 작성, 빌드 시 | IDE 자동완성, javac 컴파일 |
| **런타임 클래스패스** | 실행 시 | java 명령으로 실행 |

**implementation의 마법:**
```gradle
// Module B
dependencies {
    implementation 'com.google.guava:guava:32.0.0'
}

// Module A
dependencies {
    implementation project(':module-b')
}
```

**Module A에서:**
- Module B의 클래스 import → ✅ 가능
- Guava 라이브러리 import → ❌ **컴파일 에러!** (컴파일 클래스패스에 없음)
- 실행 시 Guava 사용 → ✅ 가능 (런타임 클래스패스에는 포함)

---

#### 3.2 실제 코드로 이해하기

**시나리오: DTO 공유 문제**

```
sado-gateway → sado-common → Jackson (JSON 라이브러리)
```

**sado-common의 DTO:**

```java
// sado-common/src/.../UserDto.java
package com.hanumoka.sado.common.dto;

import com.fasterxml.jackson.annotation.JsonProperty;  // Jackson!
import lombok.Data;

@Data
public class UserDto {
    @JsonProperty("user_id")  // Jackson 어노테이션
    private String userId;

    @JsonProperty("user_name")
    private String userName;
}
```

**❌ Case 1: implementation 사용 (문제 발생!)**

```gradle
// sado-common/build.gradle
dependencies {
    implementation 'com.fasterxml.jackson.core:jackson-annotations:2.15.0'
    api 'org.projectlombok:lombok'
}
```

```java
// sado-gateway에서 사용 시도
import com.hanumoka.sado.common.dto.UserDto;

public class UserController {
    public void test() {
        UserDto user = new UserDto();  // ✅ OK
        user.setUserId("123");         // ✅ OK (lombok)

        // ❌ 컴파일 에러!
        // UserDto의 @JsonProperty가 Jackson에 의존하는데
        // Jackson이 컴파일 클래스패스에 없음!
    }
}
```

**에러 메시지:**
```
error: cannot access JsonProperty
class file for com.fasterxml.jackson.annotation.JsonProperty not found
```

**✅ Case 2: api 사용 (해결!)**

```gradle
// sado-common/build.gradle
dependencies {
    api 'com.fasterxml.jackson.core:jackson-annotations:2.15.0'  // api로 변경!
    api 'org.projectlombok:lombok'
}
```

```java
// sado-gateway에서 사용
import com.hanumoka.sado.common.dto.UserDto;
import com.fasterxml.jackson.databind.ObjectMapper;  // ✅ 이제 가능!

public class UserController {
    public void test() {
        UserDto user = new UserDto();
        user.setUserId("123");

        ObjectMapper mapper = new ObjectMapper();  // ✅ OK!
        String json = mapper.writeValueAsString(user);  // ✅ OK!
    }
}
```

---

#### 3.3 빌드 시간 차이 (중요!)

**시나리오: 라이브러리 버전 업그레이드**

```
App → Module A → Module B → Library (v1.0 → v2.0)
```

**implementation 사용 시:**
```gradle
// Module B
dependencies {
    implementation 'some-library:2.0'  // 1.0 → 2.0
}
```

**재빌드 범위:**
- ✅ Module B만 재빌드
- ⏭️ Module A 재빌드 **건너뜀**
- ⏭️ App 재빌드 **건너뜀**
- **빌드 시간: ~10초**

**api 사용 시:**
```gradle
// Module B
dependencies {
    api 'some-library:2.0'  // 1.0 → 2.0
}
```

**재빌드 범위:**
- ✅ Module B 재빌드
- ✅ Module A 재빌드 (B의 public API 변경 가능성)
- ✅ App 재빌드 (A의 public API 변경 가능성)
- **빌드 시간: ~60초 (6배 차이!)**

**왜 차이가 날까?**
- `implementation`은 "내부 구현"이므로 변경해도 외부에 영향 없음
- `api`는 "공개 인터페이스"이므로 의존하는 모든 모듈 재검증 필요

---

#### 3.4 언제 무엇을 사용할까? (결정 트리)

```
라이브러리를 추가하려고 한다
    ↓
[질문 1] 이 라이브러리의 타입이 내 모듈의 public API에 노출되는가?
    ↓
    YES → [질문 2로]
    NO  → implementation 사용 ✅

[질문 2] 다른 모듈에서 이 라이브러리를 직접 사용해야 하는가?
    ↓
    YES → api 사용 ✅
    NO  → implementation 사용 ✅
```

**api를 써야 하는 경우:**

```java
// Module B의 public 클래스
public class MyService {
    // ✅ 반환 타입이 Jackson의 JsonNode
    public JsonNode getData() {  // public API에 노출!
        return ...;
    }
}

// Module B의 build.gradle
dependencies {
    api 'com.fasterxml.jackson.core:jackson-databind'  // api 필수!
}
```

**implementation을 써야 하는 경우:**

```java
// Module B의 public 클래스
public class MyService {
    // ❌ Jackson은 내부에서만 사용
    public String getData() {  // 반환 타입은 String (Java 기본)
        ObjectMapper mapper = new ObjectMapper();  // Jackson 내부 사용
        return mapper.writeValueAsString(...);
    }
}

// Module B의 build.gradle
dependencies {
    implementation 'com.fasterxml.jackson.core:jackson-databind'  // implementation OK!
}
```

---

#### 3.5 SADO 프로젝트 실전 가이드

```gradle
// sado-common/build.gradle
dependencies {
    // ✅ api: 다른 모듈에서도 lombok 어노테이션 사용
    api 'org.projectlombok:lombok'

    // ✅ api: DTO에 Jackson 어노테이션 사용
    api 'com.fasterxml.jackson.core:jackson-annotations:2.15.0'

    // ✅ implementation: 유틸리티성 라이브러리 (내부에서만 사용)
    implementation 'com.google.guava:guava:32.0.0'

    // ✅ implementation: 로깅 (내부 구현 세부사항)
    implementation 'ch.qos.logback:logback-classic:1.4.11'
}
```

```gradle
// sado-gateway/build.gradle
dependencies {
    // ✅ implementation: gateway는 다른 모듈이 의존하지 않음
    implementation project(':sado-common')

    implementation 'org.springframework.cloud:spring-cloud-starter-gateway'
}
```

```gradle
// sado-service-dicom/build.gradle
dependencies {
    implementation project(':sado-common')

    // ✅ implementation: DICOM 라이브러리는 내부에서만 사용
    implementation 'org.dcm4che:dcm4che-core:5.31.0'
}
```

---

#### 3.6 요약 비교표

| 구분 | implementation | api |
|------|---------------|-----|
| **의존성 전이** | ❌ 전이 안됨 | ✅ 전이됨 |
| **컴파일 클래스패스** | 자신만 | 자신 + 의존 모듈 전체 |
| **재빌드 범위** | 자신만 | 의존 체인 전체 |
| **빌드 속도** | 빠름 ⚡ | 느림 🐢 |
| **사용 시기** | 내부 구현 | Public API 노출 |
| **기본 선택** | ✅ **대부분의 경우** | 꼭 필요할 때만 |

---

#### 3.7 디버깅 팁

**"어? 왜 import가 안되지?"**

```java
// Module A에서
import com.some.library.SomeClass;  // ❌ Cannot resolve symbol
```

**해결 순서:**
1. Module B의 `build.gradle` 확인
2. `implementation`을 `api`로 변경
3. Gradle Sync (IntelliJ: Ctrl+Shift+O)
4. 재시도

**주의:** 무분별한 `api` 사용은 빌드 속도 저하!

**팁:** 헷갈리면 일단 `implementation`으로 시작! 컴파일 에러 나면 그때 `api`로 변경

---

### 3.8 gradle 폴더의 역할 (Gradle Wrapper)

#### Gradle Wrapper란?

> "Gradle을 수동으로 설치하지 않아도 프로젝트를 빌드할 수 있게 해주는 시스템"

Spring Boot 프로젝트를 Gradle로 생성하면 **자동으로 생성**되는 폴더입니다.

#### 폴더 구조

```
sado_be/
├── gradle/
│   └── wrapper/
│       ├── gradle-wrapper.jar        # Gradle 다운로드 & 실행 로직 (45KB)
│       └── gradle-wrapper.properties # Gradle 버전 설정 파일
├── gradlew                           # Unix/Mac용 실행 스크립트
└── gradlew.bat                       # Windows용 실행 스크립트
```

#### gradle-wrapper.properties 예시

```properties
distributionBase=GRADLE_USER_HOME
distributionPath=wrapper/dists
distributionUrl=https://services.gradle.org/distributions/gradle-9.2.1-bin.zip
networkTimeout=10000
validateDistributionUrl=true
zipStoreBase=GRADLE_USER_HOME
zipStorePath=wrapper/dists
```

**핵심:** `distributionUrl`에서 사용할 Gradle 버전(9.2.1) 지정

#### 동작 원리

```
사용자가 ./gradlew build 실행
    ↓
gradle-wrapper.jar 실행됨
    ↓
gradle-wrapper.properties에서 Gradle 버전 확인
    ↓
해당 버전이 로컬에 없으면?
    ↓
    YES → distributionUrl에서 자동 다운로드
    NO  → 기존 설치된 Gradle 사용
    ↓
다운로드된 Gradle로 빌드 실행
```

#### 장점

- ✅ **팀원 모두 동일한 Gradle 버전 보장** (버전 불일치 문제 해결)
- ✅ **Gradle 수동 설치 불필요** (신규 팀원 온보딩 시간 단축)
- ✅ **CI/CD 환경에서 일관된 빌드** (Jenkins, GitHub Actions 등)
- ✅ **Gradle 버전 변경 시 properties만 수정** (전체 팀 자동 반영)

#### Git 관리 (중요! ⭐)

**반드시 커밋해야 할 파일:**
```
✅ gradle/wrapper/gradle-wrapper.jar
✅ gradle/wrapper/gradle-wrapper.properties
✅ gradlew
✅ gradlew.bat
```

**제외해야 할 항목:**
```
❌ .gradle/   # 빌드 캐시 폴더 (용량 크고 로컬마다 다름)
```

**.gitignore 설정 예시:**
```gitignore
# Gradle 빌드 캐시 제외
.gradle

# ⚠️ 주의: *.jar를 전역 제외했다면 wrapper jar는 예외 처리 필수!
!gradle/wrapper/gradle-wrapper.jar
```

**주의:** `*.jar`를 .gitignore에 추가한 경우, wrapper jar도 무시되므로 반드시 예외 처리!

#### gradle/ vs .gradle/ 차이

| 폴더 | 용도 | Git 관리 | 용량 |
|------|------|---------|------|
| `gradle/` | Gradle Wrapper 파일 | ✅ **커밋 필수** | ~50KB |
| `.gradle/` | 빌드 캐시, 임시 파일 | ❌ **제외** | 수백 MB |

#### Version Catalog 사용 시 구조

```
gradle/
├── wrapper/
│   ├── gradle-wrapper.jar
│   └── gradle-wrapper.properties
└── libs.versions.toml              # Version Catalog (Git 관리 ✅)
```

---

### 4. 공통 설정 관리 방법

모든 모듈에서 같은 버전의 Spring Boot를 쓰고 싶다면?

#### 방법 1: buildSrc (전통적)

```
sado_be/
├── buildSrc/                    # Gradle이 자동 인식
│   └── src/
│       └── main/
│           └── groovy/
│               └── Dependencies.groovy
└── ...
```

```groovy
// buildSrc/src/main/groovy/Dependencies.groovy
class Dependencies {
    static String SPRING_BOOT_VERSION = '4.0.1'
    static String LOMBOK_VERSION = '1.18.30'
}
```

```gradle
// 각 모듈의 build.gradle에서
dependencies {
    implementation "org.springframework.boot:spring-boot-starter:${Dependencies.SPRING_BOOT_VERSION}"
}
```

**장점:**
- 버전 관리 중앙화
- 타입 안전성

**단점:**
- buildSrc 변경 시 **전체 프로젝트 재빌드** (느림)
- Gradle 7.4 이전에는 거의 유일한 선택지

#### 방법 2: Version Catalog (권장 ⭐ Gradle 7.4+)

```
sado_be/
├── gradle/
│   └── libs.versions.toml    # 여기에 버전 정의!
└── ...
```

```toml
# gradle/libs.versions.toml
[versions]
spring-boot = "4.0.1"
lombok = "1.18.30"

[libraries]
spring-boot-starter = { module = "org.springframework.boot:spring-boot-starter", version.ref = "spring-boot" }
lombok = { module = "org.projectlombok:lombok", version.ref = "lombok" }

[plugins]
spring-boot = { id = "org.springframework.boot", version.ref = "spring-boot" }
```

```gradle
// 각 모듈의 build.gradle에서
dependencies {
    implementation libs.spring.boot.starter
    compileOnly libs.lombok
}
```

**장점:**
- buildSrc보다 훨씬 빠름 (캐시 활용)
- 문법이 간단
- IDE 자동완성 지원

**단점:**
- Gradle 7.4 이상 필요

---

## SADO 프로젝트 실제 적용

### 목표 구조

```
sado_be/
├── settings.gradle              # 모듈 선언
├── build.gradle                 # 공통 설정
├── gradle/
│   └── libs.versions.toml       # 버전 관리 (Version Catalog)
│
├── sado-common/                 # 공통 모듈
│   ├── build.gradle
│   └── src/
│       └── main/java/
│           └── com/hanumoka/sado/common/
│               ├── domain/      # 공통 도메인
│               ├── exception/   # 공통 예외
│               └── util/        # 공통 유틸
│
├── sado-gateway/                # API Gateway
│   ├── build.gradle
│   └── src/
│       └── main/java/
│           └── com/hanumoka/sado/gateway/
│
├── sado-service-dicom/          # DICOM 서비스
│   ├── build.gradle
│   └── src/
│
└── sado-bff/                    # Backend For Frontend
    ├── build.gradle
    └── src/
```

### 모듈 의존성 관계

```
sado-gateway ──┐
               ├──> sado-common
sado-service-dicom ──┘

sado-bff ──────────> sado-common
```

### 루트 build.gradle 예시 (⭐ 중요!)

멀티모듈 프로젝트에서 **가장 중요한** 설정 파일입니다.

```gradle
plugins {
    id 'org.springframework.boot' version '4.0.1' apply false
    id 'io.spring.dependency-management' version '1.1.7' apply false
}

subprojects {
    apply plugin: 'java'
    apply plugin: 'io.spring.dependency-management'

    group = 'com.hanumoka.sado'
    version = '0.0.1-SNAPSHOT'

    java {
        toolchain {
            languageVersion = JavaLanguageVersion.of(21)
        }
    }

    repositories {
        mavenCentral()
    }

    // Spring Boot BOM import (필수!)
    dependencyManagement {
        imports {
            mavenBom "org.springframework.boot:spring-boot-dependencies:4.0.1"
        }
    }

    configurations {
        compileOnly {
            extendsFrom annotationProcessor
        }
    }
}
```

**핵심 포인트:**
1. `apply false`: 루트에는 플러그인만 선언, 서브 모듈에서 선택적 적용
2. `subprojects {}`: 모든 서브 모듈에 공통 설정 자동 적용
3. `dependencyManagement`: Spring Boot BOM import로 버전 자동 관리
4. `repositories`: 모든 모듈이 mavenCentral 사용

### settings.gradle 예시

```gradle
rootProject.name = 'sado_be'

include 'sado-common'
include 'sado-gateway'
include 'sado-service-dicom'
include 'sado-bff'
```

### sado-common/build.gradle 예시

```gradle
plugins {
    id 'java-library'  // 주목! library 플러그인
}

dependencies {
    // 공통으로 사용할 라이브러리
    api 'org.springframework.boot:spring-boot-starter'
    api 'org.projectlombok:lombok'

    annotationProcessor 'org.projectlombok:lombok'
}
```

**왜 `java-library`?**
- 다른 모듈에서 사용할 라이브러리이므로
- `api` 키워드를 사용하려면 필수

### sado-gateway/build.gradle 예시

```gradle
plugins {
    id 'java'
    id 'org.springframework.boot' version '4.0.1'
    id 'io.spring.dependency-management' version '1.1.7'
}

dependencies {
    // 공통 모듈 사용
    implementation project(':sado-common')

    // Gateway 전용 의존성
    implementation 'org.springframework.cloud:spring-cloud-starter-gateway'

    testImplementation 'org.springframework.boot:spring-boot-starter-test'
}
```

---

## 주의사항 / 함정

### 1. 순환 의존성 절대 금지! ❌

```
sado-gateway → sado-common → sado-gateway  (❌ 빌드 실패!)
```

Gradle은 순환 의존성을 허용하지 않습니다. 발견 시 즉시 빌드 실패!

**해결 방법:**
- 공통 코드를 별도 모듈로 분리
- 의존성 방향을 한쪽으로만 유지

### 2. 모듈명 네이밍 규칙

```
✅ sado-common
✅ sado-gateway
✅ sado-service-dicom

❌ sadoCommon   (camelCase 금지)
❌ SADO_COMMON  (대문자 금지)
❌ sado_common  (underscore 비추천)
```

Gradle 공식 권장: **lowercase + hyphen**

### 3. buildSrc 변경 주의

buildSrc 내용을 변경하면 **모든 모듈이 재빌드**됩니다.
→ Version Catalog 사용 권장!

### 4. implementation과 api 헷갈림

```
"sado-common에서 쓰는 라이브러리를
다른 모듈에서도 써야 하나?"
```

- **YES** → `api` 사용
- **NO** → `implementation` 사용 (기본값)

**팁:** 헷갈리면 일단 `implementation`! 필요할 때 `api`로 변경

---

## 실전 트러블슈팅 🔧

> SADO 프로젝트 멀티모듈 전환 과정에서 실제로 겪은 문제와 해결 방법

### 이슈 1: 루트 build.gradle 미설정으로 빌드 실패

#### 문제 상황

```
> Task :sado-common:compileJava FAILED
Could not find org.projectlombok:lombok:.
Required by:
    project ':sado-common'
```

#### 원인 분석

단일 프로젝트를 멀티모듈로 전환하면서 **루트 build.gradle을 수정하지 않음**

```gradle
// ❌ 잘못된 루트 build.gradle (단일 프로젝트 설정 그대로)
plugins {
    id 'java'
    id 'org.springframework.boot' version '4.0.1'
    id 'io.spring.dependency-management' version '1.1.7'
}

dependencies {
    implementation 'org.springframework.boot:spring-boot-starter-webmvc'
    compileOnly 'org.projectlombok:lombok'
    // ...
}
```

**문제점:**
- ❌ Spring Boot 플러그인이 루트에 직접 적용됨 (멀티모듈에서는 불가)
- ❌ `repositories` 설정이 서브 모듈에 전달되지 않음
- ❌ 서브 모듈들이 의존성을 다운로드할 수 없음

#### 해결 방법

루트 build.gradle을 **멀티모듈 구조로 변경**:

```gradle
// ✅ 올바른 루트 build.gradle
plugins {
    id 'org.springframework.boot' version '4.0.1' apply false
    id 'io.spring.dependency-management' version '1.1.7' apply false
}

subprojects {
    apply plugin: 'java'
    apply plugin: 'io.spring.dependency-management'

    group = 'com.hanumoka.sado'
    version = '0.0.1-SNAPSHOT'

    repositories {
        mavenCentral()  // ✅ 모든 서브 모듈에 적용
    }

    dependencyManagement {
        imports {
            mavenBom "org.springframework.boot:spring-boot-dependencies:4.0.1"
        }
    }
}
```

---

### 이슈 2: java 플러그인 apply false 에러

#### 문제 상황

```
Error resolving plugin [id: 'java', apply: false]
> Plugin 'java' is a core Gradle plugin, which is already on the classpath.
  Requesting it with the 'apply false' option is a no-op.
```

#### 원인 분석

`java` 플러그인은 Gradle core 플러그인이므로 `apply false` 사용 불가

```gradle
// ❌ 잘못된 방법
plugins {
    id 'java' apply false  // core 플러그인은 apply false 불가!
    id 'org.springframework.boot' version '4.0.1' apply false
}
```

#### 해결 방법

`java` 플러그인은 plugins 블록에서 제거하고 subprojects에서 직접 apply:

```gradle
// ✅ 올바른 방법
plugins {
    // java 플러그인 제거!
    id 'org.springframework.boot' version '4.0.1' apply false
    id 'io.spring.dependency-management' version '1.1.7' apply false
}

subprojects {
    apply plugin: 'java'  // ✅ 여기서 직접 적용
    apply plugin: 'io.spring.dependency-management'
}
```

---

### 이슈 3: Lombok 버전 해결 실패

#### 문제 상황

```
Could not find org.projectlombok:lombok:.
```

sado-common/build.gradle에서 버전 없이 선언했지만 버전이 자동으로 설정되지 않음:

```gradle
// sado-common/build.gradle
dependencies {
    api 'org.projectlombok:lombok'  // 버전이 없는데?
}
```

#### 원인 분석

`io.spring.dependency-management` 플러그인만으로는 부족!
Spring Boot BOM을 **명시적으로 import**해야 버전 관리가 작동함

```gradle
// ❌ 부족한 설정
subprojects {
    apply plugin: 'io.spring.dependency-management'
    // BOM import 없음!
}
```

#### 해결 방법

루트 build.gradle의 subprojects 블록에 **dependencyManagement 추가**:

```gradle
// ✅ 완전한 설정
subprojects {
    apply plugin: 'java'
    apply plugin: 'io.spring.dependency-management'

    repositories {
        mavenCentral()
    }

    // Spring Boot BOM import (필수!)
    dependencyManagement {
        imports {
            mavenBom "org.springframework.boot:spring-boot-dependencies:4.0.1"
        }
    }
}
```

이제 서브 모듈에서 버전 없이 의존성 선언 가능:

```gradle
// sado-common/build.gradle
dependencies {
    api 'org.projectlombok:lombok'  // ✅ 버전 자동 적용 (1.18.36)
    api 'org.springframework.boot:spring-boot-starter'  // ✅ 버전 자동 적용
}
```

---

### 체크리스트: 멀티모듈 전환 시 확인사항 ✅

**루트 프로젝트:**
- [ ] plugins 블록에 `apply false` 추가 (Spring Boot, dependency-management)
- [ ] `java` 플러그인은 plugins 블록에서 제거
- [ ] subprojects 블록 작성
- [ ] repositories 설정 (mavenCentral)
- [ ] dependencyManagement에 Spring Boot BOM import

**각 서브 모듈:**
- [ ] 라이브러리 모듈: `java-library` 플러그인
- [ ] 실행 가능 모듈: `java` + `spring-boot` 플러그인
- [ ] 의존성 선언 시 버전 생략 (BOM에서 관리)

**빌드 테스트:**
- [ ] `./gradlew clean build` 성공
- [ ] IntelliJ Gradle Sync 성공
- [ ] 모듈 아이콘이 정상 표시

---

## 결론

### Gradle 멀티모듈의 핵심

1. **settings.gradle**로 모듈 선언
2. **루트 build.gradle**에서 공통 설정 (subprojects 블록)
3. **implementation/api**로 의존성 관리
4. **Version Catalog** 또는 **BOM**으로 버전 중앙화
5. **순환 의존성 금지** 원칙 준수

### SADO 프로젝트 적용 결과

- [x] 멀티모듈 개념 학습
- [x] settings.gradle 작성
- [x] 루트 build.gradle 설정 (subprojects)
- [x] sado-common 모듈 생성
- [x] sado-gateway 모듈 생성
- [x] 빌드 테스트 성공 ✅

### 배운 점

- 루트 build.gradle 설정이 멀티모듈의 핵심
- Spring Boot BOM import 없이는 버전 관리 불가
- core 플러그인(java)은 apply false 사용 불가
- 실전에서는 문서만으로 부족, 직접 겪어봐야 이해됨

### 다음 단계

- **Week 2**: JPA + MyBatis 멀티모듈 적용
- **Week 3**: Docker Compose로 각 모듈 독립 배포 테스트
- **Week 4**: 실제 DICOM 서비스 구현 시작

---

## 참고 자료

### 공식 문서
- [Gradle Multi-Project Builds](https://docs.gradle.org/current/userguide/multi_project_builds.html)
- [Structuring Multi-Project Builds](https://docs.gradle.org/current/userguide/multi_project_builds_intermediate.html)
- [Building Java Applications Sample](https://docs.gradle.org/current/samples/sample_building_java_applications_multi_project.html)

### 실무 튜토리얼
- [Creating a Gradle multi-module project (2025)](https://tmsvr.com/gradle-multi-module-build/)
- [Best practices for Spring Boot multi-module](https://bootify.io/multi-module/best-practices-for-spring-boot-multi-module.html)
- [Building a Multi-Module Spring Boot Application with Gradle](https://reflectoring.io/spring-boot-gradle-multi-module/)

### 한국어 기술 블로그
- [우아한형제들: 멀티모듈 설계 이야기](https://techblog.woowahan.com/2637/)
- [Gradle implementation vs api 완벽 이해](https://perfectacle.github.io/2022/03/12/gradle-implementation-vs-api/)
- [멀티 모듈 적용하기 with Gradle](https://tecoble.techcourse.co.kr/post/2021-09-06-multi-module/)
- [buildSrc 말고 Version Catalog 쓰자](https://hojongs.medium.com/gradle-의존성-버전-중앙-관리-방법들-buildsrc-말고-4f6241beae2a)

---

**다음 글 예고:** "SADO 프로젝트에 Gradle 멀티모듈 직접 적용하기 (실전편)"
