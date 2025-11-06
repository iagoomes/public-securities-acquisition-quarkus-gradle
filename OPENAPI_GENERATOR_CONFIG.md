# OpenAPI Generator - Configurações jaxrs-spec para Quarkus

## Índice
- [Introdução](#introdução)
- [configOptions vs additionalProperties](#configoptions-vs-additionalproperties)
- [Opções de Configuração](#opções-de-configuração)
  - [Pacotes e Estrutura](#pacotes-e-estrutura)
  - [Comportamento da API](#comportamento-da-api)
  - [Modelos e Validação](#modelos-e-validação)
  - [Específicas para Quarkus](#específicas-para-quarkus)
  - [Build e Artefatos](#build-e-artefatos)
  - [Anotações e Documentação](#anotações-e-documentação)
- [Configuração Recomendada para Quarkus](#configuração-recomendada-para-quarkus)
- [Referências](#referências)

---

## Introdução

O gerador `jaxrs-spec` cria código Java para servidores JAX-RS compatíveis com a especificação 2.0+. Este documento detalha todas as opções de configuração disponíveis, com foco em uso com Quarkus.

## configOptions vs additionalProperties

No `build.gradle`:

```gradle
openApiGenerate {
    // ... outras configs

    // configOptions: Controla COMO o código é gerado
    configOptions = [
        interfaceOnly: "true",
        useJakartaEe: "true"
    ]

    // additionalProperties: Metadados ADICIONAIS injetados no código
    additionalProperties = [
        hideGenerationTimestamp: "true",
        additionalModelTypeAnnotations: "@io.quarkus.runtime.annotations.RegisterForReflection"
    ]
}
```

**Resumo:**
- `configOptions`: Altera estrutura e comportamento do código gerado
- `additionalProperties`: Adiciona metadados, anotações extras, configurações de build

---

## Opções de Configuração

### Pacotes e Estrutura

| Opção | Tipo | Padrão | Descrição |
|-------|------|--------|-----------|
| `apiPackage` | String | `org.openapitools.api` | Pacote das classes de API geradas |
| `modelPackage` | String | `org.openapitools.model` | Pacote dos modelos (DTOs) gerados |
| `invokerPackage` | String | `org.openapitools.api` | Pacote raiz do código gerado |
| `sourceFolder` | String | `src/main/java` | Pasta onde o código será gerado |
| `implFolder` | String | `src/main/java` | Pasta para implementações (quando `interfaceOnly=false`) |

**Recomendação Quarkus:**
```gradle
configOptions = [
    apiPackage: "br.com.seuapp.api",
    modelPackage: "br.com.seuapp.model",
    invokerPackage: "br.com.seuapp.invoker",
    sourceFolder: "src/main/java"
]
```

---

### Comportamento da API

| Opção | Tipo | Padrão | Descrição | Recomendação Quarkus |
|-------|------|--------|-----------|----------------------|
| `interfaceOnly` | Boolean | `false` | Gera apenas interfaces, sem implementações stub | ✅ **true** - Você cria suas próprias implementações |
| `returnResponse` | Boolean | `false` | Retorna `Response` ao invés de objetos deserializados | ✅ **true** - Maior controle sobre status HTTP |
| `useTags` | Boolean | `false` | Usa tags do OpenAPI para nomear APIs | ✅ **true** - Melhor organização |
| `supportAsync` | Boolean | `false` | Usa `CompletionStage` para operações assíncronas | ⚠️ Depende do caso (veja Quarkus abaixo) |
| `useMutinyForAsync` | Boolean | `false` | Usa Mutiny (Quarkus) ao invés de `CompletionStage` | ✅ **true** se usar programação reativa |
| `implicitHeaders` | Boolean | `false` | Pula parâmetros de header usando `@ApiImplicitParams` | ❌ **false** - Melhor explícito |
| `prependFormOrBodyParameters` | Boolean | `false` | Coloca form/body no início da lista de parâmetros | Preferência pessoal |
| `sortParamsByRequiredFlag` | Boolean | `true` | Ordena parâmetros obrigatórios primeiro | ✅ **true** |

**Importante:**
- Para Quarkus reativo: use `useMutinyForAsync: "true"` ao invés de `supportAsync`
- `returnResponse: "true"` permite retornar status corretos (201, 404, 501, etc.)

---

### Modelos e Validação

| Opção | Tipo | Padrão | Descrição | Recomendação Quarkus |
|-------|------|--------|-----------|----------------------|
| `useBeanValidation` | Boolean | `true` | Adiciona anotações Bean Validation (`@NotNull`, etc.) | ✅ **true** |
| `useJakartaEe` | Boolean | `false` | Usa namespace `jakarta.*` ao invés de `javax.*` | ✅ **true** - Quarkus 3+ usa Jakarta EE |
| `dateLibrary` | String | `legacy` | Biblioteca de datas: `java8`, `legacy`, `joda`, `threetenbp` | ✅ **java8** - Usa `LocalDate`, `OffsetDateTime` |
| `bigDecimalAsString` | Boolean | `false` | Trata `BigDecimal` como String (evita perda de precisão) | Depende do caso |
| `generateBuilders` | Boolean | `false` | Gera builders para modelos | Preferência pessoal |
| `openApiNullable` | Boolean | `true` | Usa biblioteca `JsonNullable` para distinguir `null` de "ausente" | ✅ **true** para APIs REST completas |
| `serializableModel` | Boolean | `false` | Implementa `Serializable` nos modelos | ❌ Geralmente desnecessário |
| `withXml` | Boolean | `false` | Adiciona suporte para XML | Apenas se precisar de XML |
| `additionalModelTypeAnnotations` | String | - | Anotações extras em todas as classes de modelo | ✅ `@RegisterForReflection` para native |
| `additionalEnumTypeAnnotations` | String | - | Anotações extras em enums | Semelhante ao acima |

**Exemplo:**
```gradle
configOptions = [
    useBeanValidation: "true",
    useJakartaEe: "true",
    dateLibrary: "java8",
    openApiNullable: "true"
]

additionalProperties = [
    additionalModelTypeAnnotations: "@io.quarkus.runtime.annotations.RegisterForReflection"
]
```

---

### Específicas para Quarkus

| Opção | Tipo | Padrão | Descrição |
|-------|------|--------|-----------|
| `library` | String | - | Usar `quarkus` para template específico |
| `useMutinyForAsync` | Boolean | `false` | Usa Smallrye Mutiny para async (requer `library=quarkus`) |
| `generateMicroprofileOpenAPIAnnotations` | Boolean | `false` | Gera anotações MicroProfile OpenAPI (requer `library=quarkus`) |
| `returnResteasyRestResponse` | Boolean | `false` | Retorna `RestResponse` do RESTEasy Reactive |

**Importante:**
- Para usar opções específicas do Quarkus, configure `library: "quarkus"` no `configOptions`
- `generateMicroprofileOpenAPIAnnotations` adiciona `@Operation`, `@APIResponse`, etc. do MicroProfile
- Não combine `returnResteasyRestResponse` com `returnResponse`

---

### Build e Artefatos

| Opção | Tipo | Padrão | Descrição | Recomendação Quarkus |
|-------|------|--------|-----------|----------------------|
| `generatePom` | Boolean | `true` | Gera `pom.xml` | ❌ **false** - Você usa Gradle |
| `hideGenerationTimestamp` | Boolean | `false` | Remove timestamp de geração dos arquivos | ✅ **true** - Evita diff desnecessário no Git |
| `artifactId` | String | `openapi-jaxrs-server` | ID do artefato no pom.xml | N/A se `generatePom=false` |
| `groupId` | String | `org.openapitools` | Group ID no pom.xml | N/A se `generatePom=false` |
| `artifactVersion` | String | `1.0.0` | Versão no pom.xml | N/A se `generatePom=false` |

---

### Anotações e Documentação

| Opção | Tipo | Padrão | Descrição | Recomendação Quarkus |
|-------|------|--------|-----------|----------------------|
| `useSwaggerAnnotations` | Boolean | `true` | Gera anotações Swagger 2.0 (`@Api`, `@ApiOperation`) | ❌ **false** - Swagger 2.0 está deprecado |
| `generateMicroprofileOpenAPIAnnotations` | Boolean | `false` | Gera anotações OpenAPI 3 do MicroProfile | ✅ **true** se usar `library=quarkus` |
| `openApiSpecFileLocation` | String | `src/main/openapi` | Onde copiar o arquivo spec no output | Configure conforme preferência |

**Importante:**
- Swagger 2.0 annotations (`@Api`) está deprecado
- Prefira MicroProfile OpenAPI annotations (`@Operation`) para Quarkus
- Quarkus SmallRye OpenAPI lê automaticamente seu `openapi.yml` em `src/main/resources`

---

## Configuração Recomendada para Quarkus

### Configuração Básica (Não Reativo)

```gradle
openApiGenerate {
    generatorName = "jaxrs-spec"
    inputSpec = "${project.rootDir}/src/main/resources/openapi.yml"
    outputDir = "${layout.buildDirectory.get()}/generated"

    apiPackage = "br.com.seuapp.api"
    modelPackage = "br.com.seuapp.model"
    invokerPackage = "br.com.seuapp.invoker"

    configOptions = [
        // Estrutura
        interfaceOnly: "true",              // ✅ Você implementa
        returnResponse: "true",             // ✅ Controle de HTTP status
        useTags: "true",                    // ✅ Organização por tags
        sourceFolder: "src/main/java",

        // Jakarta EE (Quarkus 3+)
        useJakartaEe: "true",               // ✅ OBRIGATÓRIO para Quarkus 3+

        // Validação e Modelos
        useBeanValidation: "true",          // ✅ Bean Validation
        dateLibrary: "java8",               // ✅ LocalDate, OffsetDateTime
        openApiNullable: "true",            // ✅ JsonNullable
        sortParamsByRequiredFlag: "true",

        // Build
        generatePom: "false",               // ❌ Você usa Gradle

        // Documentação
        useSwaggerAnnotations: "false"      // ❌ Deprecado
    ]

    additionalProperties = [
        hideGenerationTimestamp: "true",
        additionalModelTypeAnnotations: "@io.quarkus.runtime.annotations.RegisterForReflection"
    ]
}

sourceSets {
    main {
        java {
            srcDir "${layout.buildDirectory.get()}/generated/src/main/java"
        }
    }
}

tasks.named('compileJava') {
    dependsOn tasks.named('openApiGenerate')
}
```

### Configuração Avançada (Reativo com Mutiny)

```gradle
openApiGenerate {
    generatorName = "jaxrs-spec"
    inputSpec = "${project.rootDir}/src/main/resources/openapi.yml"
    outputDir = "${layout.buildDirectory.get()}/generated"

    apiPackage = "br.com.seuapp.api"
    modelPackage = "br.com.seuapp.model"
    invokerPackage = "br.com.seuapp.invoker"

    configOptions = [
        // Quarkus específico
        library: "quarkus",                              // ✅ Template Quarkus
        useMutinyForAsync: "true",                       // ✅ Mutiny (reativo)
        generateMicroprofileOpenAPIAnnotations: "true",  // ✅ OpenAPI 3 annotations

        // Estrutura
        interfaceOnly: "true",
        returnResponse: "false",                         // Mutiny retorna Uni<T>
        useTags: "true",
        sourceFolder: "src/main/java",

        // Jakarta EE
        useJakartaEe: "true",

        // Validação e Modelos
        useBeanValidation: "true",
        dateLibrary: "java8",
        openApiNullable: "true",

        // Build
        generatePom: "false",

        // Documentação
        useSwaggerAnnotations: "false"
    ]

    additionalProperties = [
        hideGenerationTimestamp: "true",
        additionalModelTypeAnnotations: "@io.quarkus.runtime.annotations.RegisterForReflection"
    ]
}
```

**Dependências necessárias (build.gradle):**
```gradle
dependencies {
    // Para interfaceOnly=true + returnResponse=true
    implementation 'io.quarkus:quarkus-rest'
    implementation 'io.quarkus:quarkus-rest-jackson'

    // Para Mutiny (reativo)
    implementation 'io.quarkus:quarkus-resteasy-reactive'

    // OpenAPI UI
    implementation 'io.quarkus:quarkus-smallrye-openapi'

    // Bean Validation
    implementation 'io.quarkus:quarkus-hibernate-validator'

    // OpenAPI Nullable (se openApiNullable=true)
    implementation 'org.openapitools:jackson-databind-nullable:0.2.6'

    // Annotations (se useSwaggerAnnotations=false mas precisa das annotations do spec)
    implementation 'io.swagger.core.v3:swagger-annotations:2.2.26'

    // Validation API
    implementation 'jakarta.validation:jakarta.validation-api:3.1.0'
}
```

---

## Workflow Recomendado

### 1. Defina seu Contrato OpenAPI
```yaml
# src/main/resources/openapi.yml
openapi: 3.0.3
info:
  title: Minha API
  version: 1.0.0
paths:
  /api/users:
    get:
      operationId: listUsers
      responses:
        '200':
          description: OK
```

### 2. Configure o Plugin com interfaceOnly=true
Use a configuração recomendada acima.

### 3. Gere o Código
```bash
./gradlew openApiGenerate
```

### 4. Implemente as Interfaces
```java
// src/main/java/br/com/seuapp/resource/UsersResource.java
package br.com.seuapp.resource;

import br.com.seuapp.api.UsersApi;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.ws.rs.core.Response;

@ApplicationScoped
public class UsersResource implements UsersApi {

    @Override
    public Response listUsers() {
        return Response.ok(List.of("User1", "User2")).build();
    }
}
```

### 5. Execute e Teste
```bash
./gradlew quarkusDev
# Acesse http://localhost:8080/q/swagger-ui
```

---

## Troubleshooting

### Problema: Endpoints não aparecem no Swagger UI

**Causa:** `interfaceOnly=false` gera implementações stub com `return Response.ok().entity("magic!").build()`

**Solução:** Use `interfaceOnly=true` e crie suas próprias implementações

### Problema: Erros de compilação com `javax.*`

**Causa:** Quarkus 3+ usa Jakarta EE

**Solução:** Configure `useJakartaEe: "true"`

### Problema: Native build falha com reflexão

**Causa:** Modelos não registrados para reflexão

**Solução:**
```gradle
additionalProperties = [
    additionalModelTypeAnnotations: "@io.quarkus.runtime.annotations.RegisterForReflection"
]
```

### Problema: `CompletionStage` não funciona

**Causa:** Quarkus prefere Mutiny para programação reativa

**Solução:** Use `library: "quarkus"` e `useMutinyForAsync: "true"`

---

## Referências

- [OpenAPI Generator - jaxrs-spec](https://openapi-generator.tech/docs/generators/jaxrs-spec/)
- [Documentação GitHub](https://github.com/OpenAPITools/openapi-generator/blob/master/docs/generators/jaxrs-spec.md)
- [Quarkus REST Guide](https://quarkus.io/guides/rest)
- [Quarkus OpenAPI Guide](https://quarkus.io/guides/openapi-swaggerui)
- [MicroProfile OpenAPI](https://github.com/eclipse/microprofile-open-api)

---

## Lista Completa de Opções

Para referência rápida, todas as opções disponíveis:

```gradle
configOptions = [
    // Pacotes
    apiPackage: "String",
    modelPackage: "String",
    invokerPackage: "String",
    sourceFolder: "String",
    implFolder: "String",

    // API
    interfaceOnly: "Boolean",
    returnResponse: "Boolean",
    useTags: "Boolean",
    supportAsync: "Boolean",
    implicitHeaders: "Boolean",
    prependFormOrBodyParameters: "Boolean",
    sortParamsByRequiredFlag: "Boolean",

    // Modelos
    useBeanValidation: "Boolean",
    useJakartaEe: "Boolean",
    dateLibrary: "String", // java8, legacy, joda, threetenbp
    bigDecimalAsString: "Boolean",
    generateBuilders: "Boolean",
    openApiNullable: "Boolean",
    serializableModel: "Boolean",
    withXml: "Boolean",

    // Quarkus
    library: "String", // quarkus
    useMutinyForAsync: "Boolean",
    generateMicroprofileOpenAPIAnnotations: "Boolean",
    returnResteasyRestResponse: "Boolean",

    // Build
    generatePom: "Boolean",
    artifactId: "String",
    groupId: "String",
    artifactVersion: "String",

    // Documentação
    useSwaggerAnnotations: "Boolean",
    openApiSpecFileLocation: "String",

    // Outros
    allowUnicodeIdentifiers: "Boolean",
    booleanGetterPrefix: "String",
    ignoreAnyOfInEnum: "Boolean",
    serverPort: "String"
]

additionalProperties = [
    hideGenerationTimestamp: "Boolean",
    additionalModelTypeAnnotations: "String",
    additionalEnumTypeAnnotations: "String",
    additionalOneOfTypeAnnotations: "String",
    parentGroupId: "String",
    parentArtifactId: "String",
    parentVersion: "String"
]
```
